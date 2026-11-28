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
import kotlinx.coroutines.sync.withLock

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

    /**
     * What went wrong last time, for the status line: "login", "certificate", "offline", "folder", or "fewer" (the
     * one file was kept because the new backup had far fewer finds; Back up now then replaces it), or null.
     */
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

    /** [force]: you tapped Back up now after being told the last one was kept (A2), so it is replaced this time. */
    fun runNow(force: Boolean = false) {
        log.d("AutoBackup", "Backup now queued" + if (force) ", replacing the kept one" else "")
        val net = if (where == Where.SERVER) androidx.work.NetworkType.CONNECTED else androidx.work.NetworkType.NOT_REQUIRED
        work.enqueueUniqueWork(
            WORK_NOW, if (force) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<AutoBackupWorker>()
                .setInputData(androidx.work.workDataOf(FORCE to force))
                .setConstraints(androidx.work.Constraints.Builder().setRequiredNetworkType(net).build()).build(),
        )
    }

    fun catchUpIfOverdue(now: Long = System.currentTimeMillis()) {
        if (where == null) return
        val last = lastSuccessMs
        if (last == null || now - last > TimeUnit.DAYS.toMillis(7)) runNow()
    }

    /**
     * Writes one backup, checks it by reading it back, then removes old ones. One run at a time (A3): the weekly
     * job and Back up now can both be queued, and each waits for the other.
     */
    suspend fun runOnce(now: Long = System.currentTimeMillis(), force: Boolean = false): Outcome = runLock.withLock {
        when (where) {
            null -> Outcome.NoFolder.also { log.d("AutoBackup", "Weekly backup is off: nothing to do") }
            Where.FOLDER -> runToFolder(now)
            Where.FILE, Where.SERVER -> runViaCache(now, force)
        }
    }

    /** The most finds a saved backup has had, and that backup's date and time (A4): it is never pruned. */
    private val bestFinds: Int get() = prefs.getInt(KEY_BEST_FINDS, prefs.getInt(KEY_FINDS, 0))
    private val bestStamp: String? get() = prefs.getString(KEY_BEST_STAMP, null)

    /** Records a saved backup: the last finds, and the best one when it has at least as many ([reset]: replace it). */
    private fun saved(now: Long, finds: Int, stamp: String?, reset: Boolean = false) {
        val e = prefs.edit().putLong(KEY_LAST, now).putInt(KEY_FINDS, finds).putBoolean(KEY_FAILED, false).remove(KEY_PROBLEM)
        if (reset || finds >= bestFinds) {
            e.putInt(KEY_BEST_FINDS, finds)
            if (stamp != null) e.putString(KEY_BEST_STAMP, stamp) else e.remove(KEY_BEST_STAMP)
        }
        e.apply()
    }

    /**
     * One file or the server: the backup is written to the app's own storage and checked there first, so a backup
     * that fails never replaces the good copy in the file, and the server only ever receives checked files.
     */
    private suspend fun runViaCache(now: Long, force: Boolean): Outcome {
        val t0 = System.currentTimeMillis()
        val target = where
        // A file of its own for each run (A3), so two runs never share a half-written file
        val tmp = File(context.cacheDir, "weekly-backup-${java.util.UUID.randomUUID()}.zip")
        var stamp: String? = null
        try {
            tmp.outputStream().use { backup.writeBackupLocked(it, withPhotos = includePhotos) }
            val info = restore.inspect(Uri.fromFile(tmp))
            restore.discard(info)
            when (target) {
                Where.FILE -> {
                    val doc = file!!
                    // The one file is the only copy (A2): never replace it with one that has far fewer finds
                    // unless you asked to, after being told
                    val best = bestFinds
                    if (!force && SafeFiles.farFewer(info.finds, best)) {
                        log.w("AutoBackup", "Finds fell from $best to ${info.finds}; kept the backup file as it was")
                        prefs.edit().putBoolean(KEY_FAILED, true).putString(KEY_PROBLEM, "fewer").apply()
                        return Outcome.Kept
                    }
                    writeChecked(doc, tmp, info.finds)?.let { return it }
                }
                Where.SERVER -> {
                    val dav = server.login?.client() ?: return lost("No server saved")
                    dav.ensureFolder()
                    val name = "$PREFIX${SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date(now))}.zip"
                    stamp = stampOf(name)
                    dav.upload(name, tmp)
                    val files = dav.list()
                    val sent = files.firstOrNull { it.name == name }
                    if (sent == null || (sent.size >= 0 && sent.size != tmp.length())) throw java.io.IOException("upload check failed")
                    val previousFinds = prefs.getInt(KEY_FINDS, 0)
                    if (info.finds >= previousFinds) {
                        // The backup with the most finds is always kept (A4)
                        val best = if (info.finds >= bestFinds) stamp else bestStamp
                        SafeFiles.toPrune(files.map { it.name }.filter { OWN_FILE.matches(it) }, keep, best, ::stampOf).forEach { dav.delete(it) }
                    } else log.w("AutoBackup", "Finds fell from $previousFinds to ${info.finds}; keeping all backups")
                }
                else -> Unit
            }
            saved(now, info.finds, stamp, reset = force)
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

    /**
     * Writes [tmp] over the one backup file [doc] and reads it back (A2). Android's file picker gives no way to
     * write a new file and swap it in, so the copy is checked instead and, if it doesn't read back with the same
     * finds, written once more. Null when it is good; an outcome to return when it isn't.
     */
    private suspend fun writeChecked(doc: Uri, tmp: File, finds: Int): Outcome? {
        repeat(2) { attempt ->
            val out = try { context.contentResolver.openOutputStream(doc, "wt") } catch (e: SecurityException) { null }
                ?: return lost("No access to the backup file any more")
            out.use { o -> tmp.inputStream().use { it.copyTo(o) } }
            val back = runCatching { restore.inspect(doc).also { restore.discard(it) } }.getOrNull()
            if (back != null && back.finds == finds) return null
            log.w("AutoBackup", "The backup file didn't read back right (try ${attempt + 1})")
        }
        throw java.io.IOException("The backup file didn't read back right")
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
            saved(now, info.finds, stampOf(file.name ?: name))
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

    /** Keeps the newest [keep] and, always, the backup with the most finds (A4). */
    private fun prune(dir: DocumentFile) {
        val own = dir.listFiles().filter { OWN_FILE.matches(it.name ?: "") }
        val drop = SafeFiles.toPrune(own.map { it.name ?: "" }, keep, bestStamp, ::stampOf).toSet()
        own.filter { it.name in drop }.forEach { it.delete() }
    }

    /** Kept: the one backup file was left as it was, as the new backup had far fewer finds (A2). */
    enum class Outcome { Saved, NoFolder, LostAccess, Failed, Kept }

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
        /** The dev copy, debug or devRelease (A7: devRelease is not a debug build, but it is the dev app). */
        private val DEV = SafeFiles.devApp(BuildConfig.APPLICATION_ID, BuildConfig.DEBUG)
        val PREFIX = prefixFor(DEV)
        val OWN_FILE = ownFile(DEV)
        const val FORCE = "force"
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
        private const val KEY_BEST_FINDS = "auto_best_finds"
        private const val KEY_BEST_STAMP = "auto_best_stamp"
        private val runLock = kotlinx.coroutines.sync.Mutex()
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
        val outcome = auto.runOnce(force = inputData.getBoolean(AutoBackup.FORCE, false))
        EntryPointAccessors.fromApplication(applicationContext, AutoBackupEntryPoint::class.java).log()
            .d("AutoBackup", "Worker attempt ${runAttemptCount + 1}: $outcome")
        return when (outcome) {
            AutoBackup.Outcome.Saved, AutoBackup.Outcome.NoFolder, AutoBackup.Outcome.Kept -> Result.success()
            AutoBackup.Outcome.LostAccess -> Result.failure()
            AutoBackup.Outcome.Failed -> if (runAttemptCount < 2) Result.retry() else Result.failure()
        }
    }
}
