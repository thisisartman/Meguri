package io.github.thisisartman.collectibles.data

import android.content.Context
import android.content.Intent
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

    fun enable(tree: Uri) {
        context.contentResolver.takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        folder?.takeIf { it != tree }?.let { old ->
            runCatching { context.contentResolver.releasePersistableUriPermission(old, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
        }
        prefs.edit().putString(KEY_FOLDER, tree.toString()).putBoolean(KEY_FAILED, false).apply()
        work.enqueueUniquePeriodicWork(
            WORK_WEEKLY, ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<AutoBackupWorker>(7, TimeUnit.DAYS).build(),
        )
        runNow()
    }

    fun disable() {
        folder?.let { old ->
            runCatching { context.contentResolver.releasePersistableUriPermission(old, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
        }
        prefs.edit().remove(KEY_FOLDER).remove(KEY_FAILED).apply()
        work.cancelUniqueWork(WORK_WEEKLY)
    }

    fun runNow() {
        work.enqueueUniqueWork(WORK_NOW, ExistingWorkPolicy.KEEP, OneTimeWorkRequestBuilder<AutoBackupWorker>().build())
    }

    fun catchUpIfOverdue(now: Long = System.currentTimeMillis()) {
        if (folder == null) return
        val last = lastSuccessMs
        if (last == null || now - last > TimeUnit.DAYS.toMillis(7)) runNow()
    }

    /** Writes one backup, checks it by reading it back, then removes old ones. False if it failed. */
    suspend fun runOnce(now: Long = System.currentTimeMillis()): Outcome {
        val tree = folder ?: return Outcome.NoFolder
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
            context.contentResolver.openOutputStream(file.uri, "wt")!!.use { out -> backup.writeBackupLocked(out) }
            val info = restore.inspect(file.uri)
            restore.discard(info)
            val previousFinds = prefs.getInt(KEY_FINDS, 0)
            prefs.edit().putLong(KEY_LAST, now).putInt(KEY_FINDS, info.finds).putBoolean(KEY_FAILED, false).apply()
            // Fewer finds than last time may mean something went wrong: keep the older copies.
            if (info.finds >= previousFinds) prune(dir) else log.w("AutoBackup", "Finds fell from $previousFinds to ${info.finds}; keeping all backups")
            log.i("AutoBackup", "Saved ${file.name}: ${info.finds} finds, ${info.items} items")
            return Outcome.Saved
        } catch (e: Exception) {
            log.e("AutoBackup", "Backup failed", e)
            runCatching { file.delete() }
            prefs.edit().putBoolean(KEY_FAILED, true).apply()
            return Outcome.Failed
        }
    }

    private fun prune(dir: DocumentFile) {
        dir.listFiles().filter { OWN_FILE.matches(it.name ?: "") }.sortedByDescending { it.name }.drop(KEEP).forEach { it.delete() }
    }

    enum class Outcome { Saved, NoFolder, LostAccess, Failed }

    companion object {
        const val KEEP = 4
        const val PREFIX = "collectibles-auto-"
        val OWN_FILE = Regex("^collectibles-auto-\\d{8}-\\d{6}( \\(\\d+\\))?(\\.zip)?$")
        private const val WORK_WEEKLY = "auto-backup-weekly"
        private const val WORK_NOW = "auto-backup-now"
        private const val KEY_FOLDER = "auto_folder"
        private const val KEY_LAST = "auto_last_ms"
        private const val KEY_FINDS = "auto_last_finds"
        private const val KEY_FAILED = "auto_failed"
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface AutoBackupEntryPoint {
    fun autoBackup(): AutoBackup
}

class AutoBackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val auto = EntryPointAccessors.fromApplication(applicationContext, AutoBackupEntryPoint::class.java).autoBackup()
        return when (auto.runOnce()) {
            AutoBackup.Outcome.Saved, AutoBackup.Outcome.NoFolder -> Result.success()
            AutoBackup.Outcome.LostAccess -> Result.failure()
            AutoBackup.Outcome.Failed -> if (runAttemptCount < 2) Result.retry() else Result.failure()
        }
    }
}
