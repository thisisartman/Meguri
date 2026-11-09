package io.github.thisisartman.collectibles.data

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import io.github.thisisartman.collectibles.BuildConfig
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.thisisartman.collectibles.debug.FileLog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Weekly backup to a folder the user picks (PRD FR-20). Android can stop scheduled work for an app
 * left unused, so opening the app catches up on a backup that is overdue.
 */
@Singleton
class AutoBackup @Inject constructor(
    @ApplicationContext private val context: Context,
    private val backup: CatalogAndBackup,
    private val restore: BackupRestore,
    private val log: FileLog,
) {
    private val prefs = context.getSharedPreferences("backup", Context.MODE_PRIVATE)
    private val work get() = WorkManager.getInstance(context)

    val folder: Uri? get() = prefs.getString(KEY_FOLDER, null)?.let(Uri::parse)
    val lastSuccessMs: Long? get() = prefs.getLong(KEY_LAST, 0L).takeIf { it > 0 }
    val lastFailed: Boolean get() = prefs.getBoolean(KEY_FAILED, false)

    /** Weekly backups carry the photos (default) or only the finds (owner, 2026-11-09: a setting on the weekly page). */
    var includePhotos: Boolean
        get() = prefs.getBoolean(KEY_PHOTOS, true)
        set(v) = prefs.edit().putBoolean(KEY_PHOTOS, v).apply()

    /** How many weekly backups stay in the folder. */
    var keep: Int
        get() = prefs.getInt(KEY_KEEP, KEEP).takeIf { it in KEEP_CHOICES } ?: KEEP
        set(v) = prefs.edit().putInt(KEY_KEEP, v).apply()

    /** Calls [onChange] when the folder or the last result changes. Returns the listener, to pass to [stopListening]. */
    fun listen(onChange: () -> Unit): SharedPreferences.OnSharedPreferenceChangeListener {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> onChange() }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        return listener
    }

    fun stopListening(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
    }

    fun enable(tree: Uri) {
        log.i("AutoBackup", "Weekly backup on")
        context.contentResolver.takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        folder?.takeIf { it != tree }?.let { old ->
            runCatching { context.contentResolver.releasePersistableUriPermission(old, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
        }
        prefs.edit().putString(KEY_FOLDER, tree.toString()).putBoolean(KEY_FAILED, false).apply()
        work.enqueueUniquePeriodicWork(
            WORK_WEEKLY, ExistingPeriodicWorkPolicy.UPDATE,
            // The first backup is the one-off below; starting this now as well would write two.
            PeriodicWorkRequestBuilder<AutoBackupWorker>(7, TimeUnit.DAYS).setInitialDelay(7, TimeUnit.DAYS).build(),
        )
        runNow()
    }

    fun disable() {
        log.i("AutoBackup", "Weekly backup off")
        folder?.let { old ->
            runCatching { context.contentResolver.releasePersistableUriPermission(old, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
        }
        prefs.edit().remove(KEY_FOLDER).remove(KEY_FAILED).apply()
        work.cancelUniqueWork(WORK_WEEKLY)
    }

    fun runNow() {
        log.d("AutoBackup", "Backup now queued")
        work.enqueueUniqueWork(WORK_NOW, ExistingWorkPolicy.KEEP, OneTimeWorkRequestBuilder<AutoBackupWorker>().build())
    }

    fun catchUpIfOverdue(now: Long = System.currentTimeMillis()) {
        if (folder == null) return
        val last = lastSuccessMs
        if (last == null || now - last > TimeUnit.DAYS.toMillis(7)) runNow()
    }

    /** Writes one backup, checks it by reading it back, then removes old ones. False if it failed. */
    suspend fun runOnce(now: Long = System.currentTimeMillis()): Outcome {
        val t0 = System.currentTimeMillis()
        val tree = folder ?: return Outcome.NoFolder.also { log.d("AutoBackup", "No folder chosen: nothing to do") }
        log.d("AutoBackup", "Start")
        val dir = DocumentFile.fromTreeUri(context, tree)
        if (dir == null || !dir.canWrite()) {
            log.w("AutoBackup", "No access to the folder any more")
            prefs.edit().putBoolean(KEY_FAILED, true).apply()
            return Outcome.LostAccess
        }
        val name = "$PREFIX${SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date(now))}"
        val file = dir.createFile("application/zip", name)
        if (file == null) {
            log.w("AutoBackup", "Could not create a file in the folder")
            return Outcome.Failed
        }
        try {
            context.contentResolver.openOutputStream(file.uri, "wt")!!.use { out -> backup.writeBackupLocked(out, withPhotos = includePhotos) }
            val info = restore.inspect(file.uri)
            restore.discard(info)
            val previousFinds = prefs.getInt(KEY_FINDS, 0)
            prefs.edit().putLong(KEY_LAST, now).putInt(KEY_FINDS, info.finds).putBoolean(KEY_FAILED, false).apply()
            // Fewer finds than last time may mean something went wrong: keep the older copies.
            if (info.finds >= previousFinds) prune(dir) else log.w("AutoBackup", "Finds fell from $previousFinds to ${info.finds}; keeping all backups")
            log.i("AutoBackup", "Saved ${file.name}: ${info.finds} finds, ${info.items} items, ${info.photos} photos in ${System.currentTimeMillis() - t0} ms")
            return Outcome.Saved
        } catch (e: Exception) {
            log.e("AutoBackup", "Backup failed after ${System.currentTimeMillis() - t0} ms", e)
            runCatching { file.delete() }
            prefs.edit().putBoolean(KEY_FAILED, true).apply()
            return Outcome.Failed
        }
    }

    private fun prune(dir: DocumentFile) {
        dir.listFiles().filter { OWN_FILE.matches(it.name ?: "") }.sortedByDescending { stampOf(it.name ?: "") }.drop(keep).forEach { it.delete() }
    }

    enum class Outcome { Saved, NoFolder, LostAccess, Failed }

    companion object {
        const val KEEP = 4
        val KEEP_CHOICES = listOf(2, 4, 8)
        /** The dev build has its own names, so pruning in a shared folder never touches the real app's backups (#42). */
        fun prefixFor(dev: Boolean) = if (dev) "meguri-dev-auto-" else "meguri-auto-"
        /** Before 0.4.0 the files were named collectibles-...; they are still this app's own, so pruning includes them. */
        private fun oldPrefixFor(dev: Boolean) = if (dev) "collectibles-dev-auto-" else "collectibles-auto-"
        fun ownFile(dev: Boolean) = Regex(
            "^(?:" + Regex.escape(prefixFor(dev)) + "|" + Regex.escape(oldPrefixFor(dev)) + ")(\\d{8}-\\d{6})( \\(\\d+\\))?(\\.zip)?$",
        )
        private val STAMP = Regex("(\\d{8}-\\d{6})")
        /** The date and time in a backup's name, for newest-first order across old and new names. */
        fun stampOf(name: String) = STAMP.find(name)?.value ?: ""
        val PREFIX = prefixFor(BuildConfig.DEBUG)
        val OWN_FILE = ownFile(BuildConfig.DEBUG)
        private const val WORK_WEEKLY = "auto-backup-weekly"
        private const val WORK_NOW = "auto-backup-now"
        private const val KEY_FOLDER = "auto_folder"
        private const val KEY_LAST = "auto_last_ms"
        private const val KEY_FINDS = "auto_last_finds"
        private const val KEY_FAILED = "auto_failed"
        private const val KEY_PHOTOS = "auto_photos"
        private const val KEY_KEEP = "auto_keep"
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface AutoBackupEntryPoint {
    fun autoBackup(): AutoBackup
    fun log(): FileLog
}

class AutoBackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val auto = EntryPointAccessors.fromApplication(applicationContext, AutoBackupEntryPoint::class.java).autoBackup()
        val outcome = auto.runOnce()
        EntryPointAccessors.fromApplication(applicationContext, AutoBackupEntryPoint::class.java).log()
            .d("AutoBackup", "Worker attempt ${runAttemptCount + 1}: $outcome")
        return when (outcome) {
            AutoBackup.Outcome.Saved, AutoBackup.Outcome.NoFolder -> Result.success()
            AutoBackup.Outcome.LostAccess -> Result.failure()
            AutoBackup.Outcome.Failed -> if (runAttemptCount < 2) Result.retry() else Result.failure()
        }
    }
}
