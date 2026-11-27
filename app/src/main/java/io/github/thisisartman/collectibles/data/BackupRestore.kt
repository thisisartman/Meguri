package io.github.thisisartman.collectibles.data

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.room.Room
import androidx.room.withTransaction
import androidx.sqlite.db.SimpleSQLiteQuery
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.thisisartman.collectibles.debug.FileLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

/** Why a backup file can't be restored. The current data is untouched in every case. */
sealed class BackupError(message: String) : Exception(message) {
    class NotABackup : BackupError("not a backup file")
    class NewerVersion(val schema: Int) : BackupError("made by a newer version of the app (schema $schema)")
    class Damaged(reason: String) : BackupError("damaged: $reason")
}

/** A checked backup, ready to restore. [dir] holds its extracted database until [BackupRestore.discard]. */
data class BackupInfo(
    val schema: Int,
    val items: Int,
    val finds: Int,
    val photos: Int,
    val lines: Int,
    val createdMs: Long?,
    val dir: File,
)

data class CurrentCounts(val items: Int, val finds: Int, val photos: Int)

data class RestoreResult(val items: Int, val finds: Int, val photos: Int, val safetyCopy: String?, val photosLeftToFind: Boolean)

data class RelinkResult(val found: Int, val missing: Int)

/** Everything in the database, for moving between the backup and the live database. */
class AllRows(
    val items: List<Item>,
    val finds: List<Acquisition>,
    val photos: List<Photo>,
    val lines: List<Line>,
    val lineStations: List<LineStation>,
    // Schema v2: your own collections; empty from a v1 backup
    val collectionTypes: List<CollectionType> = emptyList(),
)

/**
 * Restore (PRD FR-19, FR-9a). The backup's database is checked, opened as its own Room database so
 * older schemas run the normal migrations, and copied row by row into the live database in one
 * transaction, so a failure at any point leaves the current data as it was.
 */
@Singleton
class BackupRestore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: CollectiblesDb,
    private val backup: CatalogAndBackup,
    private val log: FileLog,
) {
    private val dao = db.dao()
    private val prefs = context.getSharedPreferences("backup", Context.MODE_PRIVATE)

    /** Reads and checks a backup file without changing anything. Throws [BackupError]. */
    suspend fun inspect(uri: Uri): BackupInfo = withContext(Dispatchers.IO) {
        val t0 = System.currentTimeMillis()
        log.d("Restore", "Checking a backup file: start")
        val dir = File(context.noBackupFilesDir, "restore-${UUID.randomUUID()}").apply { mkdirs() }
        try {
            val dbFile = File(dir, CollectiblesDb.NAME)
            var createdMs: Long? = null
            try {
                context.contentResolver.openInputStream(uri)!!.use { input ->
                    ZipInputStream(input).use { zip ->
                        var entry = zip.nextEntry
                        while (entry != null) {
                            when {
                                entry.name == CollectiblesDb.NAME -> dbFile.outputStream().use { copyCapped(zip, it) }
                                // Pictures carried by a "with photos" backup (#66), and their originals (#208); the name is a photo id only
                                io.github.thisisartman.collectibles.photo.PhotoNames.parseBackup(entry.name) != null -> {
                                    val (id, original) = io.github.thisisartman.collectibles.photo.PhotoNames.parseBackup(entry.name)!!
                                    val dirP = File(dir, if (original) "originals" else "photos").apply { mkdirs() }
                                    File(dirP, id).outputStream().use { copyCapped(zip, it, MAX_PHOTO_BYTES) }
                                }
                                entry.name == "backup.json" -> createdMs = CREATED_MS.find(zip.readBytes().toString(Charsets.UTF_8))?.groupValues?.get(1)?.toLongOrNull()
                            }
                            entry = zip.nextEntry
                        }
                    }
                }
            } catch (e: BackupError) {
                throw e
            } catch (e: Exception) {
                throw BackupError.NotABackup()
            }
            if (!dbFile.exists() || dbFile.length() == 0L) throw BackupError.NotABackup()
            checkDatabase(dbFile, createdMs, dir).also {
                log.d("Restore", "Backup checked in ${System.currentTimeMillis() - t0} ms: schema ${it.schema}, ${it.items} items, ${it.finds} finds, ${it.photos} photos, ${it.lines} lines")
            }
        } catch (e: Exception) {
            log.d("Restore", "Backup refused after ${System.currentTimeMillis() - t0} ms: $e")
            dir.deleteRecursively()
            throw e
        }
    }

    private fun copyCapped(input: ZipInputStream, out: java.io.OutputStream, cap: Long = MAX_DB_BYTES) {
        val buffer = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val n = input.read(buffer)
            if (n < 0) return
            total += n
            if (total > cap) throw BackupError.Damaged("file too large")
            out.write(buffer, 0, n)
        }
    }

    private fun checkDatabase(file: File, createdMs: Long?, dir: File): BackupInfo {
        clearWalFlag(file)
        val sql = try {
            SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY)
        } catch (e: Exception) {
            throw BackupError.Damaged("won't open")
        }
        try {
            return sql.use { d ->
                val schema = d.rawQuery("PRAGMA user_version", null).use { it.moveToFirst(); it.getInt(0) }
                if (schema > CollectiblesDb.VERSION) throw BackupError.NewerVersion(schema)
                if (schema < 1) throw BackupError.Damaged("no schema version")
                val integrity = d.rawQuery("PRAGMA integrity_check", null).use { it.moveToFirst(); it.getString(0) }
                if (integrity != "ok") throw BackupError.Damaged("integrity check failed")
                if (d.rawQuery("PRAGMA foreign_key_check", null).use { it.moveToFirst() }) throw BackupError.Damaged("broken links between rows")
                fun count(table: String) = d.rawQuery("SELECT COUNT(*) FROM $table", null).use { it.moveToFirst(); it.getInt(0) }
                BackupInfo(schema, count("item"), count("acquisition"), count("photo"), count("line"), createdMs, dir)
            }
        } catch (e: BackupError) {
            throw e
        } catch (e: Exception) {
            throw BackupError.Damaged("not this app's database")
        }
    }

    /** A copied WAL-mode database has no WAL beside it; marking it plain lets it open read-only anywhere. */
    private fun clearWalFlag(file: File) {
        java.io.RandomAccessFile(file, "rw").use { f ->
            val magic = ByteArray(16)
            if (f.length() < 100 || f.read(magic) != 16 || String(magic, Charsets.US_ASCII) != "SQLite format 3\u0000") {
                throw BackupError.Damaged("not a database")
            }
            f.seek(18)
            f.write(byteArrayOf(1, 1))
        }
    }

    fun discard(info: BackupInfo) {
        info.dir.deleteRecursively()
    }

    suspend fun currentCounts(): CurrentCounts = CurrentCounts(dao.itemCount(), dao.findTotal(), dao.photoTotal())

    /**
     * Replaces everything with the backup. First saves the current data to Downloads and checks that
     * copy; if it can't be saved, nothing is replaced. Throws on failure, with the current data intact.
     */
    suspend fun restore(info: BackupInfo): RestoreResult = withContext(Dispatchers.IO) {
        val t0 = System.currentTimeMillis()
        log.d("Restore", "Restore: start, ${info.items} items, ${info.finds} finds, ${info.photos} photos")
        try {
            val rows = readBackup(info)
            val safety = backup.lock.withLock {
                val copy = saveSafetyCopy()
                replaceAll(rows)
                prefs.edit().putBoolean(KEY_RELINK, rows.photos.isNotEmpty()).commit()
                copy
            }
            log.i("Restore", "Restored ${rows.finds.size} finds, ${rows.items.size} items, ${rows.photos.size} photos; safety copy $safety")
            // A "with photos" backup (#66): pictures this phone can't open are put back in its gallery first
            val placed = restoreBundledPhotos(File(info.dir, "photos"))
            restoreOriginals(File(info.dir, "originals"))
            val relink = if (rows.photos.isEmpty() || placed == rows.photos.size) null else relinkPhotos()
            // Every photo already has its picture: nothing left to find, so a later start mustn't relink them
            if (rows.photos.isNotEmpty() && placed == rows.photos.size) prefs.edit().putBoolean(KEY_RELINK, false).commit()
            log.i("Restore", "Restore done in ${System.currentTimeMillis() - t0} ms: $placed photos put back from the backup")
            RestoreResult(rows.items.size, rows.finds.size, rows.photos.size, safety, rows.photos.isNotEmpty() && relink == null)
        } catch (e: Exception) {
            // Logged as an error by the screen that started it
            log.d("Restore", "Restore failed after ${System.currentTimeMillis() - t0} ms: $e")
            throw e
        } finally {
            discard(info)
        }
    }

    private suspend fun readBackup(info: BackupInfo): AllRows {
        val file = File(info.dir, CollectiblesDb.NAME)
        val source = Room.databaseBuilder(context, CollectiblesDb::class.java, file.absolutePath)
            .addMigrations(*CollectiblesDb.MIGRATIONS)
            .build()
        try {
            val d = source.dao()
            val rows = AllRows(d.allItems(), d.allFinds(), d.allPhotos(), d.allLines(), d.allLineStations(), d.allCollectionTypes())
            if (rows.items.size != info.items || rows.finds.size != info.finds || rows.photos.size != info.photos) {
                throw BackupError.Damaged("row counts changed while reading")
            }
            return rows
        } catch (e: BackupError) {
            throw e
        } catch (e: Exception) {
            log.e("Restore", "Could not read the backup's database", e)
            throw BackupError.Damaged("not this app's database")
        } finally {
            source.close()
        }
    }

    /** One transaction: on any failure, including a failed check here, the old rows stay. */
    internal suspend fun replaceAll(rows: AllRows) {
        db.withTransaction {
            dao.deleteAllPhotos()
            dao.deleteAllFinds()
            dao.deleteAllLineStations()
            dao.deleteAllLines()
            dao.deleteAllItems()
            dao.deleteAllCollectionTypes()
            dao.insertCollectionTypes(rows.collectionTypes)
            dao.insertItems(rows.items)
            dao.insertLines(rows.lines)
            dao.insertLineStations(rows.lineStations)
            dao.insertFinds(rows.finds)
            dao.insertPhotos(rows.photos)
            check(dao.itemCount() == rows.items.size && dao.findTotal() == rows.finds.size && dao.photoTotal() == rows.photos.size) {
                "Row counts differ after copying"
            }
            db.query(SimpleSQLiteQuery("PRAGMA foreign_key_check")).use { check(!it.moveToFirst()) { "Broken links after copying" } }
        }
    }

    /** Null when there is nothing to protect. Caller holds [CatalogAndBackup.lock]. Also before a photo file (#188). */
    internal suspend fun saveSafetyCopy(prefix: String = "meguri-pre-restore"): String? {
        if (dao.itemCount() == 0 && dao.findTotal() == 0 && dao.lineCount() == 0) return null
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val name = "$prefix-$stamp.zip"
        val resolver = context.contentResolver
        val uri = resolver.insert(
            MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
            ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, "application/zip")
            },
        ) ?: error("Could not create the safety copy in Downloads")
        try {
            // With photos: a restore replaces the photo rows, and their old copies are cleared once nothing points at them
            resolver.openOutputStream(uri, "wt")!!.use { backup.writeBackup(it, withPhotos = true) }
            discard(inspect(uri))
        } catch (e: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            throw e
        }
        // Android may have renamed it if the name was taken: report the name it really has.
        return resolver.query(uri, arrayOf(MediaStore.Downloads.DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null } ?: name
    }

    val relinkPending: Boolean get() = prefs.getBoolean(KEY_RELINK, false)

    fun hasPhotoAccess(): Boolean = context.checkSelfPermission(photoPermission()) == PackageManager.PERMISSION_GRANTED

    /**
     * Points each photo at its picture in the gallery. Null, with the links left alone, without full
     * access to the gallery ("selected photos only" counts as no access, as it can't see the rest).
     */
    suspend fun relinkPhotos(): RelinkResult? = withContext(Dispatchers.IO) {
        if (!hasPhotoAccess()) return@withContext null
        val media = queryMedia()
        backup.lock.withLock {
            var found = 0
            var missing = 0
            val base = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            db.withTransaction {
                dao.allPhotos().forEach { p ->
                    // Its own copy is already here (#79): nothing to find in the gallery
                    if (io.github.thisisartman.collectibles.photo.PhotoStore.exists(context, p.contentUri)) { found++; return@forEach }
                    val m = PhotoMatcher.match(p, media)
                    if (m != null) {
                        dao.setPhotoLink(p.id, ContentUris.withAppendedId(base, m.id).toString(), m.id, false)
                        found++
                    } else {
                        dao.setPhotoMissing(p.id, true)
                        missing++
                    }
                }
            }
            prefs.edit().putBoolean(KEY_RELINK, false).commit()
            log.i("Restore", "Photos relinked: $found found, $missing missing")
            RelinkResult(found, missing)
        }
    }

    /**
     * For each restored photo whose picture this phone can't open, makes Meguri's own copy (#79) from the one carried
     * in the backup and points the photo at it. A photo whose copy or gallery picture is already here is left alone,
     * so a restore on the same phone adds nothing. Backups from before 0.3.0-beta.6 carry the gallery originals,
     * which are resized to the app's WebP copy on the way in. Returns how many photos now have a working picture.
     */
    private suspend fun restoreBundledPhotos(dir: File): Int {
        if (!dir.isDirectory) return 0
        var ok = 0
        for (p in dao.allPhotos()) {
            // On a new phone the same address can be someone else's picture: keep it only if it is this photo
            if (samePicture(p)) { ok++; continue }
            val file = File(dir, p.id).takeIf { it.isFile } ?: continue
            val uri = runCatching { io.github.thisisartman.collectibles.photo.PhotoStore.importCopy(context, file, p.id) }.getOrNull()
            if (uri != null) { dao.setPhotoUri(p.id, uri); ok++ }
        }
        log.i("Restore", "Photos from the backup: $ok of ${dao.photoTotal()} now open")
        return ok
    }

    /**
     * Each restored photo's kept original (#208): left alone when it is already on this phone, else put back from
     * the backup; a photo whose original is in neither place can no longer be widened, so it loses the address.
     */
    private suspend fun restoreOriginals(dir: File) {
        val store = io.github.thisisartman.collectibles.photo.PhotoStore
        var put = 0
        var lost = 0
        for (p in dao.allPhotos()) {
            val o = p.originalUri ?: continue
            if (store.exists(context, o)) continue
            val uri = File(dir, p.id).takeIf { it.isFile }?.let { f -> runCatching { store.importOriginal(context, f, p.id) }.getOrNull() }
            dao.setPhotoOriginal(p.id, uri)
            if (uri != null) put++ else lost++
        }
        if (put + lost > 0) log.i("Restore", "Originals from the backup: $put put back, $lost not in it")
    }

    /**
     * Whether the picture at the photo's stored address is that photo: it opens, and its name and size match the
     * fingerprint where both are known (MediaStore can report size 0 for a fresh picture, which counts as unknown).
     */
    private fun samePicture(p: Photo): Boolean {
        // Meguri's own copy is named by the photo's id, so if it is here it is this photo
        if (io.github.thisisartman.collectibles.photo.PhotoStore.isApp(p.contentUri)) return io.github.thisisartman.collectibles.photo.PhotoStore.exists(context, p.contentUri)
        val uri = runCatching { Uri.parse(p.contentUri) }.getOrNull() ?: return false
        val opens = runCatching { context.contentResolver.openInputStream(uri)!!.use { it.read() }; true }.getOrDefault(false)
        if (!opens) return false
        val cols = arrayOf(MediaStore.Images.Media.DISPLAY_NAME, MediaStore.Images.Media.SIZE)
        val (name, size) = runCatching {
            context.contentResolver.query(uri, cols, null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) to (if (c.isNull(1)) null else c.getLong(1)) else null
            }
        }.getOrNull() ?: return false
        val nameOk = p.displayName == null || name == null || name == p.displayName
        val sizeOk = p.sizeBytes == null || size == null || size == 0L || size == p.sizeBytes
        return nameOk && sizeOk
    }

    /** After a restart that cut a relink short. */
    suspend fun resumeRelinkIfPending() {
        if (relinkPending) relinkPhotos()
    }

    private fun queryMedia(): List<MediaEntry> {
        val cols = arrayOf(
            MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME, MediaStore.Images.Media.RELATIVE_PATH,
            MediaStore.Images.Media.SIZE, MediaStore.Images.Media.DATE_TAKEN,
        )
        val out = mutableListOf<MediaEntry>()
        context.contentResolver.query(MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), cols, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                out += MediaEntry(c.getLong(0), c.getString(1), c.getString(2), if (c.isNull(3)) null else c.getLong(3), if (c.isNull(4)) null else c.getLong(4))
            }
        }
        return out
    }

    companion object {
        private const val KEY_RELINK = "relink_pending"
        private const val MAX_DB_BYTES = 256L * 1024 * 1024
        private const val MAX_PHOTO_BYTES = 64L * 1024 * 1024
        private val CREATED_MS = Regex("\"created_ms\":(\\d+)")

        fun photoPermission(): String =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE
    }
}
