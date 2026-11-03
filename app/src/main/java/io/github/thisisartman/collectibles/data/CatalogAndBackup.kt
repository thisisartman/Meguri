package io.github.thisisartman.collectibles.data

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import androidx.sqlite.db.SimpleSQLiteQuery
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.thisisartman.collectibles.BuildConfig
import io.github.thisisartman.collectibles.debug.FileLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.OutputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

data class ImportResult(val added: Int, val updated: Int, val lines: Int, val skipped: List<String>)
data class BackupResult(val finds: Int, val items: Int, val photos: Int)

@Singleton
class CatalogAndBackup @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: CollectiblesDb,
    private val log: FileLog,
) {
    private val dao = db.dao()

    /** Held by anything that reads or replaces the whole database, so they never overlap. */
    val lock = Mutex()

    /** Adds or updates items by catalogue key; never deletes an item or a find. */
    suspend fun importCatalog(uri: Uri): ImportResult = withContext(Dispatchers.IO) {
        val text = context.contentResolver.openInputStream(uri)!!.use { it.readBytes().toString(Charsets.UTF_8) }
        importCatalogText(text)
    }

    suspend fun importCatalogText(text: String): ImportResult {
        val maps = Csv.parseWithHeader(text)
        return lock.withLock { importRows(maps) }
    }

    private suspend fun importRows(maps: List<Map<String, String>>): ImportResult {
        var added = 0
        var updated = 0
        val lineIds = mutableSetOf<String>()
        val seen = mutableSetOf<String>()
        val skipped = mutableListOf<String>()
        val now = System.currentTimeMillis()
        db.withTransaction {
            maps.forEachIndexed { i, m ->
                val row = CatalogRow.from(m).getOrElse { skipped += "row ${i + 2}: ${it.message}"; return@forEachIndexed }
                val existing = dao.itemByCatalogKey(row.catalogKey)
                val item = row.toItem(existing, UUID.randomUUID().toString(), now)
                dao.upsertItem(item)
                // A station on two lines is two rows of one item: count the item once.
                if (seen.add(row.catalogKey)) { if (existing == null) added++ else updated++ }
                if (row.lineId != null && row.lineSeq != null) {
                    dao.upsertLine(Line(row.lineId, row.lineNameJa ?: row.lineId, row.lineNameRomaji))
                    dao.upsertLineStation(LineStation(row.lineId, item.id, row.lineSeq))
                    lineIds += row.lineId
                }
            }
        }
        log.i("Catalog", "Import: $added added, $updated updated, ${lineIds.size} lines, ${skipped.size} skipped ${skipped.take(5)}")
        return ImportResult(added, updated, lineIds.size, skipped)
    }

    /**
     * Writes a zip: the database file (the restore source) plus readable CSVs of everything.
     * Photos are not included; they stay in the gallery and are relinked by their fingerprint.
     */
    suspend fun exportBackup(uri: Uri): BackupResult = withContext(Dispatchers.IO) {
        val result = context.contentResolver.openOutputStream(uri, "wt")!!.use { writeBackupLocked(it) }
        log.i("Backup", "Exported ${result.finds} finds, ${result.items} items, ${result.photos} photos to $uri")
        result
    }

    suspend fun writeBackupLocked(out: OutputStream): BackupResult = lock.withLock { writeBackup(out) }

    /** Caller holds [lock]. */
    suspend fun writeBackup(out: OutputStream): BackupResult = withContext(Dispatchers.IO) {
        val snapshot = File(context.noBackupFilesDir, "export-${UUID.randomUUID()}.db")
        try {
            val rows = snapshotDatabase(snapshot)
            val (items, finds, photos, lines, ls) = rows
            ZipOutputStream(out).let { zip ->
                fun text(name: String, body: String) {
                    zip.putNextEntry(ZipEntry(name)); zip.write(body.toByteArray(Charsets.UTF_8)); zip.closeEntry()
                }
                zip.putNextEntry(ZipEntry(CollectiblesDb.NAME)); snapshot.inputStream().use { it.copyTo(zip) }; zip.closeEntry()
                text("items.csv", itemsCsv(items))
                text("finds.csv", findsCsv(finds, items.associateBy { it.id }))
                text("photos.csv", photosCsv(photos))
                text("lines.csv", buildString {
                    append(Csv.line("line_id", "name_ja", "name_romaji", "station_item_id", "seq"))
                    val byLine = lines.associateBy { it.id }
                    ls.forEach { s -> append(Csv.line(s.lineId, byLine[s.lineId]?.nameJa, byLine[s.lineId]?.nameRomaji, s.itemId, s.seq)) }
                })
                text("backup.json", """{"app":"${BuildConfig.APPLICATION_ID}","version":"${BuildConfig.VERSION_NAME}","schema":${CollectiblesDb.VERSION},"created_ms":${System.currentTimeMillis()},"items":${items.size},"finds":${finds.size},"photos":${photos.size}}""")
                zip.finish()
                zip.flush()
            }
            BackupResult(finds.size, items.size, photos.size)
        } finally {
            snapshot.delete()
        }
    }

    /**
     * Copies the database file with every write included. The checkpoint must finish and the write-ahead
     * log must be empty, checked inside a transaction so no write can start during the copy. The CSV
     * rows are read in the same transaction, so they match the copy.
     */
    private suspend fun snapshotDatabase(target: File): Snapshot {
        val dbFile = context.getDatabasePath(db.openHelper.databaseName!!)
        val wal = File(dbFile.path + "-wal")
        repeat(SNAPSHOT_TRIES) { attempt ->
            val busy = db.query(SimpleSQLiteQuery("PRAGMA wal_checkpoint(TRUNCATE)")).use { it.moveToFirst(); it.getInt(0) }
            if (busy == 0) {
                val rows = db.withTransaction {
                    if (wal.exists() && wal.length() > 0L) return@withTransaction null
                    dbFile.inputStream().use { input -> target.outputStream().use { input.copyTo(it) } }
                    Snapshot(dao.allItems(), dao.allFinds(), dao.allPhotos(), dao.allLines(), dao.allLineStations())
                }
                if (rows != null) return rows
            }
            delay(100L * (attempt + 1))
        }
        error("Database stayed busy; no backup written")
    }

    private data class Snapshot(
        val items: List<Item>,
        val finds: List<Acquisition>,
        val photos: List<Photo>,
        val lines: List<Line>,
        val lineStations: List<LineStation>,
    )

    companion object {
        private const val SNAPSHOT_TRIES = 8

        fun itemsCsv(items: List<Item>) = buildString {
            append(Csv.line("id", "type", "catalog_key", "code", "name_ja", "name_romaji", "series", "release_date", "prefecture", "municipality",
                "place_name", "address", "place_phone", "lat", "lng", "hours_text", "closed_days", "notes", "source_url", "origin", "created_at", "updated_at"))
            items.forEach { i ->
                append(Csv.line(i.id, i.type, i.catalogKey, i.code, i.nameJa, i.nameRomaji, i.series, i.releaseDate, i.prefecture, i.municipality,
                    i.placeName, i.address, i.placePhone, i.lat, i.lng, i.hoursText, i.closedDays, i.notes, i.sourceUrl, i.origin, i.createdAt, i.updatedAt))
            }
        }

        fun findsCsv(finds: List<Acquisition>, items: Map<String, Item>) = buildString {
            append(Csv.line("id", "item_id", "item_name_ja", "item_code", "acquired_at_iso", "before_app", "lat", "lng", "accuracy_m", "cost_yen", "notes", "via", "acquired_at", "created_at"))
            val iso = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", java.util.Locale.US)
            finds.forEach { a ->
                val it = items[a.itemId]
                append(Csv.line(a.id, a.itemId, it?.nameJa, it?.code, a.acquiredAt?.let { t -> iso.format(java.util.Date(t)) }, a.beforeApp,
                    a.lat, a.lng, a.accuracyM, a.costYen, a.notes, a.via, a.acquiredAt, a.createdAt))
            }
        }

        fun photosCsv(photos: List<Photo>) = buildString {
            append(Csv.line("id", "find_id", "content_uri", "display_name", "relative_path", "size_bytes", "mediastore_id", "taken_at", "missing"))
            photos.forEach { p -> append(Csv.line(p.id, p.acquisitionId, p.contentUri, p.displayName, p.relativePath, p.sizeBytes, p.mediastoreId, p.takenAt, p.missing)) }
        }
    }
}
