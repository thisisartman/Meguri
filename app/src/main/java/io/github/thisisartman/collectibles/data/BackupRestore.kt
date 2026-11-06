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
        val dir = File(context.noBackupFilesDir, "restore-${UUID.randomUUID()}").apply { mkdirs() }
        try {
            val dbFile = File(dir, CollectiblesDb.NAME)
            var createdMs: Long? = null
            try {
                context.contentResolver.openInputStream(uri)!!.use { input ->
                    ZipInputStream(input).use { zip ->
                        var entry = zip.nextEntry
                        while (entry != null) {
                            when (entry.name) {
                                CollectiblesDb.NAME -> dbFile.outputStream().use { copyCapped(zip, it) }
                                "backup.json" -> createdMs = CREATED_MS.find(zip.readBytes().toString(Charsets.UTF_8))?.groupValues?.get(1)?.toLongOrNull()
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
            checkDatabase(dbFile, createdMs, dir)
        } catch (e: Exception) {
            dir.deleteRecursively()
            throw e
        }
    }

    private fun copyCapped(input: ZipInputStream, out: java.io.OutputStream) {
        val buffer = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val n = input.read(buffer)
            if (n < 0) return
            total += n
            if (total > MAX_DB_BYTES) throw BackupError.Damaged("database too large")
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
        try {
            val rows = readBackup(info)
            val safety = backup.lock.withLock {
                val copy = saveSafetyCopy()
                replaceAll(rows)
                prefs.edit().putBoolean(KEY_RELINK, rows.photos.isNotEmpty()).commit()
                copy
            }
            log.i("Restore", "Restored ${rows.finds.size} finds, ${rows.items.size} items, ${rows.photos.size} photos; safety copy $safety")
            val relink = if (rows.photos.isEmpty()) null else relinkPhotos()
            RestoreResult(rows.items.size, rows.finds.size, rows.photos.size, safety, rows.photos.isNotEmpty() && relink == null)
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

    /** Null when there is nothing to protect. Caller holds [CatalogAndBackup.lock]. */
    private suspend fun saveSafetyCopy(): String? {
        if (dao.itemCount() == 0 && dao.findTotal() == 0 && dao.lineCount() == 0) return null
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val name = "collectibles-pre-restore-$stamp.zip"
        val resolver = context.contentResolver
        val uri = resolver.insert(
            MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
            ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, "application/zip")
            },
        ) ?: error("Could not create the safety copy in Downloads")
        try {
            resolver.openOutputStream(uri, "wt")!!.use { backup.writeBackup(it) }
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
        private val CREATED_MS = Regex("\"created_ms\":(\\d+)")

        fun photoPermission(): String =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE
    }
}
