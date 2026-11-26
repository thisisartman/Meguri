package io.github.thisisartman.collectibles.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import androidx.room.withTransaction
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.thisisartman.collectibles.BuildConfig
import io.github.thisisartman.collectibles.debug.FileLog
import kotlinx.coroutines.Dispatchers
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

data class ImportResult(val added: Int, val updated: Int, val lines: Int, val skipped: List<String>, val unchanged: Int = 0)

/** Whether a catalogue row changes what is stored; a reload of the same file changes nothing. */
fun catalogRowChanges(existing: Item?, incoming: Item): Boolean =
    existing == null || incoming.copy(updatedAt = existing.updatedAt) != existing
data class BackupResult(val finds: Int, val items: Int, val photos: Int, val photoFiles: Int = 0)

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
        val t0 = System.currentTimeMillis()
        log.d("Catalog", "Import from a file: start")
        val text = context.contentResolver.openInputStream(uri)!!.use { it.readBytes().toString(Charsets.UTF_8) }
        log.d("Catalog", "Read ${text.length} characters in ${System.currentTimeMillis() - t0} ms")
        // Logged here: the welcome card shows the failure but does not log it
        runCatching { importCatalogText(text) }
            .onFailure { if (it !is kotlinx.coroutines.CancellationException) log.e("Catalog", "Import from a file failed after ${System.currentTimeMillis() - t0} ms", it) }
            .getOrThrow()
            .also { log.d("Catalog", "Import from a file done in ${System.currentTimeMillis() - t0} ms") }
    }

    /**
     * The catalogue that ships inside the app (#58), so a first start needs no file. [regions] limits it to those
     * prefectures (onboarding, #81) and is remembered, so later refreshes keep to them; null loads everything.
     */
    suspend fun importBundled(regions: Set<String>? = null): ImportResult = withContext(Dispatchers.IO) {
        val t0 = System.currentTimeMillis()
        log.d("Catalog", "Bundled catalogue: start, ${regions?.let { "${it.size} prefectures" } ?: "everything"}")
        try {
            val text = bundledText()
            val edit = bundledPrefs.edit()
            if (regions == null) edit.remove(KEY_REGIONS) else edit.putStringSet(KEY_REGIONS, regions)
            edit.apply()
            importRegions(text, regions).also {
                bundledPrefs.edit().putString(KEY_BUNDLED_SHA, sha(text)).apply()
                log.i("Catalog", "Bundled catalogue loaded in ${System.currentTimeMillis() - t0} ms: ${it.added} added, ${it.updated} updated, ${it.lines} lines")
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            // The onboarding and the welcome card show a message but do not log it
            log.e("Catalog", "Loading the bundled catalogue failed after ${System.currentTimeMillis() - t0} ms", e)
            throw e
        }
    }

    private fun linesIn(maps: List<Map<String, String>>, regions: Set<String>?): Set<String> =
        maps.filter { regions == null || it["prefecture"]?.trim() in regions }.mapNotNull { it["line_id"]?.trim()?.ifEmpty { null } }.toSet()

    /**
     * Prefectures in the bundled catalogue and how many things each has, for the onboarding's region list. Worked out
     * once per catalogue file and kept, since parsing the file is slow on a phone.
     */
    suspend fun bundledPrefectures(): Map<String, Map<ItemType, Int>> = withContext(Dispatchers.IO) {
        val text = bundledText()
        val sha = sha(text)
        bundledPrefs.getString(KEY_COUNTS, null)?.takeIf { bundledPrefs.getString(KEY_COUNTS_SHA, null) == sha }?.let { return@withContext decodeCounts(it) }
        countPrefectures(text).also { bundledPrefs.edit().putString(KEY_COUNTS, encodeCounts(it)).putString(KEY_COUNTS_SHA, sha).apply() }
    }

    private fun countPrefectures(text: String): Map<String, Map<ItemType, Int>> =
        Csv.parseWithHeader(text)
            .mapNotNull { m -> m["prefecture"]?.trim()?.ifEmpty { null }?.let { it to m } }
            // A station on several lines is one item: count it once
            .distinctBy { (_, m) -> m["catalog_key"].orEmpty().ifEmpty { m["code"].orEmpty().ifEmpty { m["name_ja"].orEmpty() } } + m["type"] }
            .groupBy({ it.first }, { it.second["type"].orEmpty() })
            .mapValues { (_, types) -> types.mapNotNull { t -> runCatching { ItemType.valueOf(t) }.getOrNull() }.groupingBy { it }.eachCount() }

    private val savedRegions: Set<String>? get() = bundledPrefs.getStringSet(KEY_REGIONS, null)

    private suspend fun importRegions(text: String, regions: Set<String>?): ImportResult {
        val tParse = System.currentTimeMillis()
        val maps = Csv.parseWithHeader(text).let { all -> if (regions == null) all else all.filter { it["prefecture"]?.trim() in regions } }
        log.i("Catalog", "Parsed the catalogue in ${System.currentTimeMillis() - tParse} ms")
        bundledPrefs.edit().putStringSet(KEY_BUNDLED_LINES, linesIn(maps, null)).apply()
        val asked = System.currentTimeMillis()
        return lock.withLock {
            val waited = System.currentTimeMillis() - asked
            if (waited > 200) log.w("Catalog", "Import waited ${waited} ms for the database lock")
            importRows(maps)
        }
    }

    /**
     * An app update can bring a changed bundled catalogue (phase 4 added the Joetsu Shinkansen for route trips).
     * A phone that loaded the bundled catalogue before gets the new rows on the next start; the import only adds
     * and updates by catalogue key, never deletes an item or a find. A phone that never loaded it is left alone.
     */
    suspend fun refreshBundledIfChanged(): ImportResult? = withContext(Dispatchers.IO) {
        if (usesPacks) return@withContext null
        val text = bundledText()
        val sha = sha(text)
        val have = dao.allLines().map { it.id }.toSet()
        // Loaded before: its lines are here, or regions were chosen (a Tokyo-only choice has no lines)
        val loadedBefore = have.any { it in BUNDLED_LINE_IDS } || savedRegions != null
        if (!loadedBefore) return@withContext null
        // Same file and every line it brings is here: nothing to do, without parsing the file (owner, 2026-11-08:
        // parsing it on every start was part of the delay before the catalogue showed). A restored backup made before
        // a line was added still gets it back, since that line is missing from the database.
        if (bundledPrefs.getString(KEY_BUNDLED_SHA, null) == sha) {
            val expected = bundledPrefs.getStringSet(KEY_BUNDLED_LINES, null)
                ?: linesIn(Csv.parseWithHeader(text), savedRegions).also { bundledPrefs.edit().putStringSet(KEY_BUNDLED_LINES, it).apply() }
            if ((expected - have).isEmpty()) return@withContext null
        }
        importRegions(text, savedRegions).also {
            bundledPrefs.edit().putString(KEY_BUNDLED_SHA, sha).apply()
            log.i("Catalog", "Bundled catalogue updated: ${it.added} added, ${it.updated} updated, ${it.lines} lines")
        }
    }

    /** Region packs are in use (0.5): the catalogue inside the app no longer refreshes over them. */
    fun usePacks() = bundledPrefs.edit().putBoolean(KEY_USES_PACKS, true).apply()
    val usesPacks: Boolean get() = bundledPrefs.getBoolean(KEY_USES_PACKS, false)

    /** For removing a prefecture's pack: its untouched items of [type], then lines left empty. */
    suspend fun removeUntouched(prefecture: String, type: ItemType): Int = lock.withLock {
        // Stale display pictures from before 0.7 kept items from going with their region (#168)
        db.withTransaction { dao.clearMissingCovers(); dao.deleteUntouched(prefecture, type.name).also { dao.deleteEmptyLines() } }
    }

    private val bundledPrefs get() = context.getSharedPreferences("catalogue", Context.MODE_PRIVATE)

    private fun bundledText() = context.assets.open(BUNDLED_CATALOGUE).use { it.readBytes().toString(Charsets.UTF_8) }

    private fun sha(text: String) = java.security.MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }

    suspend fun importCatalogText(text: String): ImportResult {
        val maps = Csv.parseWithHeader(text)
        return lock.withLock { importRows(maps) }
    }

    private suspend fun importRows(maps: List<Map<String, String>>): ImportResult {
        var added = 0
        var updated = 0
        var unchanged = 0
        val lineIds = mutableSetOf<String>()
        val skipped = mutableListOf<String>()
        val now = System.currentTimeMillis()
        val t0 = System.currentTimeMillis()
        val stages = mutableListOf<String>()
        val parsed = maps.mapIndexedNotNull { i, m -> CatalogRow.from(m).getOrElse { skipped += "row ${i + 2}: ${it.message}"; null } }
        // A handful of queries for the whole file, not three per row: one row at a time took 36 s for 156 rows on the
        // emulator (2026-11-08), the "opening delay" the owner timed at 8 s on the S23
        stages += "rows ${System.currentTimeMillis() - t0}"
        val tTx = System.currentTimeMillis()
        db.withTransaction {
            stages += "open ${System.currentTimeMillis() - tTx}"
            val keys = parsed.map { it.catalogKey }.distinct()
            val tLookup = System.currentTimeMillis()
            val existing = keys.chunked(500).flatMap { dao.itemsByCatalogKeys(it) }.associateBy { it.catalogKey }
            stages += "lookup ${System.currentTimeMillis() - tLookup}"
            val items = LinkedHashMap<String, Item>()
            val toWrite = mutableListOf<Item>()
            val lines = LinkedHashMap<String, Line>()
            val stations = mutableListOf<LineStation>()
            parsed.forEach { row ->
                // A station on two lines is two rows of one item: the first row makes it, the second reuses it
                val item = items.getOrPut(row.catalogKey) {
                    val before = existing[row.catalogKey]
                    // You edited it: the catalogue leaves it as you made it (owner, 2026-11-08)
                    val made = if (before?.editedAt != null) before else row.toItem(before, UUID.randomUUID().toString(), now)
                    val changes = catalogRowChanges(before, made)
                    // Unchanged rows are left alone, so their update time (and "recent" order) stays put
                    if (changes) toWrite += made
                    when {
                        before == null -> added++
                        changes -> updated++
                        else -> unchanged++
                    }
                    made
                }
                if (row.lineId != null && row.lineSeq != null) {
                    lines.getOrPut(row.lineId) { Line(row.lineId, row.lineNameJa ?: row.lineId, row.lineNameRomaji) }
                    stations += LineStation(row.lineId, item.id, row.lineSeq)
                    lineIds += row.lineId
                }
            }
            val tWrite = System.currentTimeMillis()
            if (toWrite.isNotEmpty()) dao.upsertItems(toWrite)
            stages += "items ${System.currentTimeMillis() - tWrite}"
            val tLines = System.currentTimeMillis()
            if (lines.isNotEmpty()) dao.upsertLines(lines.values.toList())
            if (stations.isNotEmpty()) dao.upsertLineStations(stations)
            stages += "lines ${System.currentTimeMillis() - tLines}"
        }
        log.i("Catalog", "Import: $added added, $updated updated, $unchanged unchanged, ${lineIds.size} lines, ${skipped.size} skipped ${skipped.take(5)} in ${System.currentTimeMillis() - t0} ms (${stages.joinToString()})")
        return ImportResult(added, updated, lineIds.size, skipped, unchanged)
    }

    /**
     * Writes a zip: the database file (the restore source) plus readable CSVs of everything.
     * Photos are not included; they stay in the gallery and are relinked by their fingerprint.
     */
    suspend fun exportBackup(uri: Uri, withPhotos: Boolean = false): BackupResult = withContext(Dispatchers.IO) {
        val t0 = System.currentTimeMillis()
        log.d("Backup", "Export: start${if (withPhotos) ", with photos" else ""}")
        // A failure here is logged as an error by the screen that started it
        val result = runCatching {
            context.contentResolver.openOutputStream(uri, "wt")!!.use { out -> lock.withLock { writeBackup(out, withPhotos) } }
        }.onFailure { log.d("Backup", "Export failed after ${System.currentTimeMillis() - t0} ms: $it") }.getOrThrow()
        log.i("Backup", "Exported ${result.finds} finds, ${result.items} items, ${result.photos} photos (${result.photoFiles} files) to $uri in ${System.currentTimeMillis() - t0} ms")
        result
    }

    suspend fun writeBackupLocked(out: OutputStream, withPhotos: Boolean = false): BackupResult = lock.withLock { writeBackup(out, withPhotos) }

    /**
     * Caller holds [lock]. [withPhotos] (#66, moving to a new phone): each photo's picture goes into the zip as
     * `photos/<photo id>`, so a phone that doesn't have it can put it back in its gallery on restore.
     */
    suspend fun writeBackup(out: OutputStream, withPhotos: Boolean = false): BackupResult = withContext(Dispatchers.IO) {
        val snapshot = File(context.noBackupFilesDir, "export-${UUID.randomUUID()}.db")
        try {
            val rows = snapshotDatabase(snapshot)
            val (items, finds, photos, lines, ls) = rows
            val categories = rows.categories
            ZipOutputStream(out).let { zip ->
                fun text(name: String, body: String) {
                    zip.putNextEntry(ZipEntry(name)); zip.write(body.toByteArray(Charsets.UTF_8)); zip.closeEntry()
                }
                zip.putNextEntry(ZipEntry(CollectiblesDb.NAME)); snapshot.inputStream().use { it.copyTo(zip) }; zip.closeEntry()
                text("items.csv", itemsCsv(items))
                text("finds.csv", findsCsv(finds, items.associateBy { it.id }))
                text("photos.csv", photosCsv(photos))
                // Your own categories, which items.csv points at by custom_type_id (#170)
                text("categories.csv", categoriesCsv(categories))
                text("lines.csv", buildString {
                    append(Csv.line("line_id", "name_ja", "name_romaji", "station_item_id", "seq"))
                    val byLine = lines.associateBy { it.id }
                    ls.forEach { s -> append(Csv.line(s.lineId, byLine[s.lineId]?.nameJa, byLine[s.lineId]?.nameRomaji, s.itemId, s.seq)) }
                })
                var files = 0
                if (withPhotos) photos.forEach { p ->
                    // A picture that can't be read (deleted from the gallery) is left out; the row still travels
                    runCatching {
                        io.github.thisisartman.collectibles.photo.PhotoStore.open(context, p.contentUri)!!.use { input ->
                            zip.putNextEntry(ZipEntry("photos/${p.id}")); input.copyTo(zip); zip.closeEntry()
                        }
                        files++
                    }.onFailure { log.w("Backup", "Photo ${p.id} could not be read, left out") }
                }
                text("backup.json", """{"app":"${BuildConfig.APPLICATION_ID}","version":"${BuildConfig.VERSION_NAME}","schema":${CollectiblesDb.VERSION},"created_ms":${System.currentTimeMillis()},"items":${items.size},"finds":${finds.size},"photos":${photos.size},"photo_files":$files}""")
                zip.finish()
                zip.flush()
                BackupResult(finds.size, items.size, photos.size, files)
            }
        } finally {
            listOf("", "-wal", "-shm", "-journal").forEach { File(snapshot.path + it).delete() }
        }
    }

    /**
     * Copies the database with every write included. A transaction holds off writers, so the file and
     * its write-ahead log can be copied together however busy the app is, and the CSV rows read in the
     * same transaction match the copy. The log is then merged into the copy, leaving one complete file.
     */
    private suspend fun snapshotDatabase(target: File): Snapshot {
        val dbFile = context.getDatabasePath(db.openHelper.databaseName!!)
        val wal = File(dbFile.path + "-wal")
        val targetWal = File(target.path + "-wal")
        val rows = db.withTransaction {
            dbFile.inputStream().use { input -> target.outputStream().use { input.copyTo(it) } }
            if (wal.exists() && wal.length() > 0L) wal.inputStream().use { input -> targetWal.outputStream().use { input.copyTo(it) } }
            Snapshot(dao.allItems(), dao.allFinds(), dao.allPhotos(), dao.allLines(), dao.allLineStations(), dao.allCollectionTypes())
        }
        SQLiteDatabase.openDatabase(target.path, null, SQLiteDatabase.OPEN_READWRITE).use { copy ->
            copy.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null).use { c ->
                check(c.moveToFirst() && c.getInt(0) == 0) { "Could not merge the write-ahead log into the backup" }
            }
        }
        return rows
    }

    private data class Snapshot(
        val items: List<Item>,
        val finds: List<Acquisition>,
        val photos: List<Photo>,
        val lines: List<Line>,
        val lineStations: List<LineStation>,
        val categories: List<CollectionType>,
    )

    companion object {
        fun itemsCsv(items: List<Item>) = buildString {
            append(Csv.line("id", "type", "catalog_key", "code", "name_ja", "name_romaji", "series", "release_date", "prefecture", "municipality",
                "place_name", "address", "place_phone", "lat", "lng", "hours_text", "closed_days", "notes", "source_url", "origin", "created_at", "updated_at", "none_here_at", "cover_photo_id", "parent_item_id", "custom_type_id", "edited_at", "cover_lat", "cover_lng"))
            items.forEach { i ->
                append(Csv.line(i.id, i.type, i.catalogKey, i.code, i.nameJa, i.nameRomaji, i.series, i.releaseDate, i.prefecture, i.municipality,
                    i.placeName, i.address, i.placePhone, i.lat, i.lng, i.hoursText, i.closedDays, i.notes, i.sourceUrl, i.origin, i.createdAt, i.updatedAt, i.noneHereAt, i.coverPhotoId, i.parentItemId, i.customTypeId, i.editedAt, i.coverLat, i.coverLng))
            }
        }

        fun findsCsv(finds: List<Acquisition>, items: Map<String, Item>) = buildString {
            append(Csv.line("id", "item_id", "item_name_ja", "item_code", "acquired_at_iso", "before_app", "lat", "lng", "accuracy_m", "cost_yen", "notes", "via", "acquired_at", "created_at", "quantity"))
            val iso = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", java.util.Locale.US)
            finds.forEach { a ->
                val it = items[a.itemId]
                append(Csv.line(a.id, a.itemId, it?.nameJa, it?.code, a.acquiredAt?.let { t -> iso.format(java.util.Date(t)) }, a.beforeApp,
                    a.lat, a.lng, a.accuracyM, a.costYen, a.notes, a.via, a.acquiredAt, a.createdAt, a.quantity))
            }
        }

        fun photosCsv(photos: List<Photo>) = buildString {
            append(Csv.line("id", "find_id", "content_uri", "display_name", "relative_path", "size_bytes", "mediastore_id", "taken_at", "missing",
                "role", "exif_lat", "exif_lng", "created_at"))
            photos.forEach { p ->
                append(Csv.line(p.id, p.acquisitionId, p.contentUri, p.displayName, p.relativePath, p.sizeBytes, p.mediastoreId, p.takenAt, p.missing,
                    p.role, p.exifLat, p.exifLng, p.createdAt))
            }
        }

        fun categoriesCsv(categories: List<CollectionType>) = buildString {
            append(Csv.line("id", "name", "name_ja", "color", "icon", "has_cost", "created_at"))
            categories.forEach { c -> append(Csv.line(c.id, c.name, c.nameJa, c.color, c.icon, c.hasCost, c.createdAt)) }
        }
    }
}

/** Built by tools/trip-catalog/make_catalog.py; copy the new CSV here when it changes. */
const val BUNDLED_CATALOGUE = "catalogue/trip-niigata.csv"
private const val KEY_BUNDLED_SHA = "bundled_sha"
private const val KEY_REGIONS = "regions"
private const val KEY_USES_PACKS = "uses_packs"
private const val KEY_BUNDLED_LINES = "bundled_lines"
private const val KEY_COUNTS = "region_counts"
private const val KEY_COUNTS_SHA = "region_counts_sha"

/** "新潟県|EKI_STAMP=76,MANHOLE_CARD=48;…": small, and no JSON library needed in unit tests. */
internal fun encodeCounts(c: Map<String, Map<ItemType, Int>>): String =
    c.entries.joinToString(";") { (p, m) -> p + "|" + m.entries.joinToString(",") { (t, n) -> "${t.name}=$n" } }

internal fun decodeCounts(s: String): Map<String, Map<ItemType, Int>> =
    s.split(";").filter { "|" in it }.associate { e ->
        val (p, rest) = e.split("|", limit = 2)
        p to rest.split(",").filter { "=" in it }.mapNotNull { kv -> kv.split("=").let { (t, n) -> runCatching { ItemType.valueOf(t) to n.toInt() }.getOrNull() } }.toMap()
    }
/** Lines only the bundled catalogue brings: one of them in the database means it was loaded. */
private val BUNDLED_LINE_IDS = setOf("JR_JOETSU", "JR_SHINETSU", "JR_ECHIGO", "JR_YAHIKO")
