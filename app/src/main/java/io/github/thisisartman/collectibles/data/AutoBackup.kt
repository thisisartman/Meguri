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
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Weekly backup (PRD FR-20) to one of three places (0.6): a folder the user picks; one file the user picks, which is
 * how Google Drive works through Android's own picker (Drive doesn't hand out whole folders); or the user's own
 * WebDAV server. Android can stop scheduled work for an app left unused, so opening the app catches up on a backup
 * that is overdue.
 */
@Singleton
class AutoBackup @Inject constructor(
    @ApplicationContext private val context: Context,
    private val backup: CatalogAndBackup,
    private val restore: BackupRestore,
    val server: BackupServer,
    private val log: FileLog,
) {
    private val prefs = context.getSharedPreferences("backup", Context.MODE_PRIVATE)
    private val work get() = WorkManager.getInstance(context)

    enum class Where { FOLDER, FILE, SERVER }

    /** Where weekly backups go, or null when the weekly backup is off. */
    val where: Where?
        get() = when {
            prefs.getString(KEY_FILE, null) != null -> Where.FILE
            prefs.getBoolean(KEY_SERVER, false) && server.login != null -> Where.SERVER
            prefs.getString(KEY_FOLDER, null) != null -> Where.FOLDER
            else -> null
        }

    val folder: Uri? get() = prefs.getString(KEY_FOLDER, null)?.let(Uri::parse)
    val file: Uri? get() = prefs.getString(KEY_FILE, null)?.let(Uri::parse)

    /** What went wrong last time, for the status line: "login", "certificate", "offline", "folder" or null. */
    val lastProblem: String? get() = prefs.getString(KEY_PROBLEM, null)
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

    /** Weekly backups into a folder. */
    fun enable(tree: Uri) {
        log.i("AutoBackup", "Weekly backup on: folder")
        releaseAll(except = tree)
        context.contentResolver.takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        prefs.edit().putString(KEY_FOLDER, tree.toString()).remove(KEY_FILE).putBoolean(KEY_SERVER, false).putBoolean(KEY_FAILED, false).remove(KEY_PROBLEM).apply()
        schedule()
    }

    /** Weekly backups into one file, replaced each week (Google Drive and other cloud pickers). */
    fun enableFile(doc: Uri) {
        log.i("AutoBackup", "Weekly backup on: one file")
        releaseAll(except = doc)
        context.contentResolver.takePersistableUriPermission(doc, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        prefs.edit().putString(KEY_FILE, doc.toString()).remove(KEY_FOLDER).putBoolean(KEY_SERVER, false).putBoolean(KEY_FAILED, false).remove(KEY_PROBLEM).apply()
        schedule()
    }

    /** Weekly backups to the server saved in [BackupServer]. */
    fun enableServer() {
        log.i("AutoBackup", "Weekly backup on: server")
        releaseAll(except = null)
        prefs.edit().putBoolean(KEY_SERVER, true).remove(KEY_FOLDER).remove(KEY_FILE).putBoolean(KEY_FAILED, false).remove(KEY_PROBLEM).apply()
        schedule()
    }

    private fun schedule() {
        val constraints = androidx.work.Constraints.Builder()
            .setRequiredNetworkType(if (where == Where.SERVER) androidx.work.NetworkType.CONNECTED else androidx.work.NetworkType.NOT_REQUIRED)
            .build()
        work.enqueueUniquePeriodicWork(
            WORK_WEEKLY, ExistingPeriodicWorkPolicy.UPDATE,
            // The first backup is the one-off below; starting this now as well would write two.
            PeriodicWorkRequestBuilder<AutoBackupWorker>(7, TimeUnit.DAYS).setInitialDelay(7, TimeUnit.DAYS).setConstraints(constraints).build(),
        )
        runNow()
    }

    private fun releaseAll(except: Uri?) {
        listOfNotNull(folder, file).filter { it != except }.forEach { old ->
            runCatching { context.contentResolver.releasePersistableUriPermission(old, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
        }
    }

    fun disable() {
        log.i("AutoBackup", "Weekly backup off")
        releaseAll(except = null)
        prefs.edit().remove(KEY_FOLDER).remove(KEY_FILE).putBoolean(KEY_SERVER, false).remove(KEY_FAILED).remove(KEY_PROBLEM).apply()
        work.cancelUniqueWork(WORK_WEEKLY)
    }

    fun runNow() {
        log.d("AutoBackup", "Backup now queued")
        val net = if (where == Where.SERVER) androidx.work.NetworkType.CONNECTED else androidx.work.NetworkType.NOT_REQUIRED
        work.enqueueUniqueWork(
            WORK_NOW, ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<AutoBackupWorker>().setConstraints(androidx.work.Constraints.Builder().setRequiredNetworkType(net).build()).build(),
        )
    }

    fun catchUpIfOverdue(now: Long = System.currentTimeMillis()) {
        if (where == null) return
        val last = lastSuccessMs
        if (last == null || now - last > TimeUnit.DAYS.toMillis(7)) runNow()
    }

    /** Writes one backup, checks it by reading it back, then removes old ones. False if it failed. */
    suspend fun runOnce(now: Long = System.currentTimeMillis()): Outcome = when (where) {
        null -> Outcome.NoFolder.also { log.d("AutoBackup", "Weekly backup is off: nothing to do") }
        Where.FOLDER -> runToFolder(now)
        Where.FILE, Where.SERVER -> runViaCache(now)
    }

    /**
     * One file or the server: the backup is written to the app's own storage and checked there first, so a backup
     * that fails never replaces the good copy in the file, and the server only ever receives checked files.
     */
    private suspend fun runViaCache(now: Long): Outcome {
        val t0 = System.currentTimeMillis()
        val target = where
        val tmp = File(context.cacheDir, "weekly-backup.zip")
        try {
            tmp.outputStream().use { backup.writeBackupLocked(it, withPhotos = includePhotos) }
            val info = restore.inspect(Uri.fromFile(tmp))
            restore.discard(info)
            when (target) {
                Where.FILE -> {
                    val doc = file!!
                    val out = try { context.contentResolver.openOutputStream(doc, "wt") } catch (e: SecurityException) { null }
                    if (out == null) return lost("No access to the backup file any more")
                    out.use { o -> tmp.inputStream().use { it.copyTo(o) } }
                }
                Where.SERVER -> {
                    val dav = server.login?.client() ?: return lost("No server saved")
                    dav.ensureFolder()
                    val name = "$PREFIX${SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date(now))}.zip"
                    dav.upload(name, tmp)
                    val files = dav.list()
                    val sent = files.firstOrNull { it.name == name }
                    if (sent == null || (sent.size >= 0 && sent.size != tmp.length())) throw java.io.IOException("upload check failed")
                    val previousFinds = prefs.getInt(KEY_FINDS, 0)
                    if (info.finds >= previousFinds) {
                        files.filter { OWN_FILE.matches(it.name) }.sortedByDescending { stampOf(it.name) }.drop(keep).forEach { dav.delete(it.name) }
                    } else log.w("AutoBackup", "Finds fell from $previousFinds to ${info.finds}; keeping all backups")
                }
                else -> Unit
            }
            prefs.edit().putLong(KEY_LAST, now).putInt(KEY_FINDS, info.finds).putBoolean(KEY_FAILED, false).remove(KEY_PROBLEM).apply()
            log.i("AutoBackup", "Saved to ${target?.name?.lowercase()}: ${info.finds} finds, ${info.items} items, ${info.photos} photos in ${System.currentTimeMillis() - t0} ms")
            return Outcome.Saved
        } catch (e: ServerError) {
            // Only the kind of problem goes in the log: never the address, user name or password
            log.w("AutoBackup", "Server backup failed: ${e.javaClass.simpleName}")
            val problem = when (e) {
                is ServerError.WrongLogin -> "login"
                is ServerError.Untrusted -> "certificate"
                is ServerError.Offline -> "offline"
                is ServerError.NotFound -> "folder"
                else -> "server"
            }
            prefs.edit().putBoolean(KEY_FAILED, true).putString(KEY_PROBLEM, problem).apply()
            return if (e is ServerError.Offline) Outcome.Failed else Outcome.LostAccess
        } catch (e: Exception) {
            log.e("AutoBackup", "Backup failed after ${System.currentTimeMillis() - t0} ms", e)
            prefs.edit().putBoolean(KEY_FAILED, true).remove(KEY_PROBLEM).apply()
            return Outcome.Failed
        } finally {
            tmp.delete()
        }
    }

    private fun lost(why: String): Outcome {
        log.w("AutoBackup", why)
        prefs.edit().putBoolean(KEY_FAILED, true).putString(KEY_PROBLEM, "folder").apply()
        return Outcome.LostAccess
    }

    /** The backups on the server, newest first (for Restore). */
    suspend fun serverBackups(): List<RemoteFile> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val dav = server.login?.client() ?: return@withContext emptyList()
        dav.list().filter { OWN_FILE.matches(it.name) }.sortedByDescending { stampOf(it.name) }
    }

    /** Downloads one backup from the server into the app's own storage, to check and restore like any file. */
    suspend fun downloadFromServer(name: String): Uri = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val dav = server.login!!.client()
        val target = serverDownload()
        dav.download(name, target)
        Uri.fromFile(target)
    }

    private fun serverDownload() = File(context.cacheDir, "server-restore.zip")

    /** Deletes the downloaded server backup: checking it copies what a restore needs, so it isn't kept (#170). */
    fun clearServerDownload() { runCatching { serverDownload().delete() } }

    private suspend fun runToFolder(now: Long): Outcome {
        val t0 = System.currentTimeMillis()
        val tree = folder ?: return Outcome.NoFolder
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
        private const val KEY_FILE = "auto_file"
        private const val KEY_SERVER = "auto_server"
        private const val KEY_PROBLEM = "auto_problem"
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
