package io.github.thisisartman.collectibles.data

import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.time.ZoneId

/**
 * Add from a backup (#209): the merge rules and the conflicts, on two in-memory collections, and every plan written
 * into an in-memory SQLite database built from the app's own schema (v7), so the foreign keys and the unique
 * catalogue key are checked as the phone checks them.
 */
class BackupMergeTest {
    private val tokyo = ZoneId.of("Asia/Tokyo")
    private val day1 = 1_780_000_000_000L // 2026-05-28 in Japan
    private val hour = 3_600_000L
    private val day = 24 * hour
    private var ids = 0
    private fun newId() = "new-${++ids}"

    private fun card(id: String, key: String, notes: String? = null, editedAt: Long? = null) = Item(
        id = id, type = ItemType.MANHOLE_CARD, catalogKey = "MANHOLE_CARD:$key", code = key, nameJa = "カード $key",
        prefecture = "新潟県", origin = Origin.CSV_IMPORT, createdAt = 1, updatedAt = 1, notes = notes, editedAt = editedAt,
    )
    private fun own(id: String, name: String, type: ItemType = ItemType.GOSHUIN, cat: String? = null) =
        Item(id = id, type = type, nameJa = name, origin = Origin.USER, createdAt = 1, updatedAt = 1, customTypeId = cat)
    private fun find(id: String, item: String, at: Long, qty: Int = 1, cost: Int? = null) =
        Acquisition(id = id, itemId = item, acquiredAt = at, via = Via.MANUAL, createdAt = at, quantity = qty, costYen = cost)
    private fun photo(id: String, find: String, name: String? = "$id.jpg", size: Long? = 100) =
        Photo(id = id, acquisitionId = find, contentUri = "app:photos/$id.webp", role = PhotoKind.STAMP, displayName = name, sizeBytes = size, createdAt = 5)
    private fun rows(
        items: List<Item> = emptyList(), finds: List<Acquisition> = emptyList(), photos: List<Photo> = emptyList(),
        lines: List<Line> = emptyList(), stations: List<LineStation> = emptyList(), cats: List<CollectionType> = emptyList(),
    ) = AllRows(items, finds, photos, lines, stations, cats)

    private fun plan(phone: AllRows, backup: AllRows) = BackupMerge.plan(phone, backup, tokyo, ::newId)

    // The in-memory database

    private lateinit var sql: Connection

    @Before fun open() {
        sql = DriverManager.getConnection("jdbc:sqlite::memory:")
        sql.createStatement().use { it.execute("PRAGMA foreign_keys = ON") }
        val db = JSONObject(File("schemas/io.github.thisisartman.collectibles.data.CollectiblesDb/7.json").readText()).getJSONObject("database")
        val entities = db.getJSONArray("entities")
        for (i in 0 until entities.length()) {
            val e = entities.getJSONObject(i)
            val table = e.getString("tableName")
            sql.createStatement().use { it.execute(e.getString("createSql").replace("\${TABLE_NAME}", table)) }
            val idx = e.optJSONArray("indices") ?: continue
            for (j in 0 until idx.length()) sql.createStatement().use { it.execute(idx.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table)) }
        }
    }

    @After fun close() = sql.close()

    private fun insert(table: String, values: Map<String, Any?>, update: Boolean = false) {
        val cols = values.keys.toList()
        val q = if (update) "UPDATE `$table` SET ${cols.joinToString { "`$it` = ?" }} WHERE id = ?"
        else "INSERT INTO `$table` (${cols.joinToString { "`$it`" }}) VALUES (${cols.joinToString { "?" }})"
        sql.prepareStatement(q).use { st ->
            cols.forEachIndexed { i, c ->
                when (val v = values[c]) {
                    null -> st.setObject(i + 1, null)
                    is Boolean -> st.setInt(i + 1, if (v) 1 else 0)
                    is Enum<*> -> st.setString(i + 1, v.name)
                    else -> st.setObject(i + 1, v)
                }
            }
            if (update) st.setString(cols.size + 1, values["id"] as String)
            val n = st.executeUpdate()
            if (update) assertEquals("one row updated in $table", 1, n)
        }
    }

    private fun Item.cols() = mapOf(
        "id" to id, "type" to type, "catalog_key" to catalogKey, "code" to code, "name_ja" to nameJa, "name_romaji" to nameRomaji,
        "series" to series, "release_date" to releaseDate, "prefecture" to prefecture, "municipality" to municipality,
        "place_name" to placeName, "address" to address, "place_phone" to placePhone, "lat" to lat, "lng" to lng,
        "hours_text" to hoursText, "closed_days" to closedDays, "notes" to notes, "source_url" to sourceUrl, "origin" to origin,
        "parent_item_id" to parentItemId, "created_at" to createdAt, "updated_at" to updatedAt, "custom_type_id" to customTypeId,
        "none_here_at" to noneHereAt, "cover_photo_id" to coverPhotoId, "edited_at" to editedAt, "cover_lat" to coverLat, "cover_lng" to coverLng,
    )
    private fun Acquisition.cols() = mapOf(
        "id" to id, "item_id" to itemId, "acquired_at" to acquiredAt, "before_app" to beforeApp, "lat" to lat, "lng" to lng,
        "accuracy_m" to accuracyM, "cost_yen" to costYen, "notes" to notes, "via" to via, "created_at" to createdAt, "quantity" to quantity,
    )
    private fun Photo.cols() = mapOf(
        "id" to id, "acquisition_id" to acquisitionId, "content_uri" to contentUri, "role" to role, "taken_at" to takenAt,
        "exif_lat" to exifLat, "exif_lng" to exifLng, "missing" to missing, "mediastore_id" to mediastoreId, "display_name" to displayName,
        "relative_path" to relativePath, "size_bytes" to sizeBytes, "created_at" to createdAt, "original_uri" to originalUri,
    )
    private fun CollectionType.cols() = mapOf("id" to id, "name" to name, "name_ja" to nameJa, "color" to color, "icon" to icon, "has_cost" to hasCost, "created_at" to createdAt)

    /** This phone's rows into the database. */
    private fun load(r: AllRows) {
        r.collectionTypes.forEach { insert("collection_type", it.cols()) }
        r.items.forEach { insert("item", it.cols()) }
        r.lines.forEach { insert("line", mapOf("id" to it.id, "name_ja" to it.nameJa, "name_romaji" to it.nameRomaji, "operator" to it.operator)) }
        r.lineStations.forEach { insert("line_station", mapOf("line_id" to it.lineId, "item_id" to it.itemId, "seq" to it.seq)) }
        r.finds.forEach { insert("acquisition", it.cols()) }
        r.photos.forEach { insert("photo", it.cols()) }
    }

    /** The plan written in the same order as BackupRestore.writeMerge, then the phone's checks. */
    private fun write(ops: MergeOps) {
        ops.newCategories.forEach { insert("collection_type", it.cols()) }
        ops.newItems.forEach { insert("item", it.cols()) }
        ops.itemUpdates.forEach { insert("item", it.cols(), update = true) }
        ops.newLines.forEach { insert("line", mapOf("id" to it.id, "name_ja" to it.nameJa, "name_romaji" to it.nameRomaji, "operator" to it.operator)) }
        ops.newLineStations.forEach { insert("line_station", mapOf("line_id" to it.lineId, "item_id" to it.itemId, "seq" to it.seq)) }
        ops.newFinds.forEach { insert("acquisition", it.cols()) }
        ops.findUpdates.forEach { insert("acquisition", it.cols(), update = true) }
        ops.newPhotos.forEach { insert("photo", it.cols()) }
        sql.createStatement().use { st -> st.executeQuery("PRAGMA foreign_key_check").use { assertFalse("no broken links", it.next()) } }
        // Every display picture is a photo of that item (the column has no key to catch it)
        sql.createStatement().use { st ->
            st.executeQuery(
                "SELECT i.id FROM item i WHERE i.cover_photo_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM photo p JOIN acquisition a ON p.acquisition_id = a.id WHERE p.id = i.cover_photo_id AND a.item_id = i.id)",
            ).use { assertFalse("display pictures are the item's own photos", it.next()) }
        }
    }

    private fun count(table: String) = sql.createStatement().use { st -> st.executeQuery("SELECT COUNT(*) FROM $table").use { it.next(); it.getInt(1) } }

    /** The phone as it is after [ops], for planning the same backup a second time. */
    private fun after(phone: AllRows, ops: MergeOps): AllRows {
        val items = phone.items.map { i -> ops.itemUpdates.firstOrNull { it.id == i.id } ?: i } + ops.newItems
        val finds = phone.finds.map { f -> ops.findUpdates.firstOrNull { it.id == f.id } ?: f } + ops.newFinds
        return AllRows(items, finds, phone.photos + ops.newPhotos, phone.lines + ops.newLines, phone.lineStations + ops.newLineStations, phone.collectionTypes + ops.newCategories)
    }

    // Adding

    @Test fun addsWhatIsNotHereAndRemovesNothing() {
        val phone = rows(items = listOf(card("p1", "15-202-A001")), finds = listOf(find("pf1", "p1", day1)), photos = listOf(photo("pp1", "pf1")))
        val backup = rows(
            items = listOf(card("b1", "15-202-B001"), own("b2", "彌彦神社")),
            finds = listOf(find("bf1", "b1", day1), find("bf2", "b2", day1 + day)),
            photos = listOf(photo("bp1", "bf1"), photo("bp2", "bf2")),
        )
        load(phone)
        val p = plan(phone, backup)
        assertTrue(p.conflicts.isEmpty())
        val ops = p.resolve(emptyMap(), 10)
        write(ops)
        assertEquals(3, count("item"))
        assertEquals(3, count("acquisition"))
        assertEquals(3, count("photo"))
        assertEquals(listOf("b1", "b2"), ops.newItems.map { it.id })
        assertTrue(ops.itemUpdates.isEmpty())
    }

    @Test fun sameBackupTwiceAddsNothing() {
        val phone = rows(items = listOf(card("p1", "15-202-A001")))
        val backup = rows(
            items = listOf(card("b1", "15-202-A001"), own("b2", "彌彦神社")),
            finds = listOf(find("bf1", "b1", day1, cost = 500), find("bf2", "b2", day1)),
            photos = listOf(photo("bp1", "bf1"), photo("bp2", "bf2")),
            lines = listOf(Line("L", "越後線")), stations = listOf(LineStation("L", "b1", 1)),
        )
        val first = plan(phone, backup).resolve(emptyMap(), 10)
        val again = plan(after(phone, first), backup)
        assertTrue(again.nothingNew)
    }

    @Test fun catalogueItemsMatchByKeyAndOwnItemsByTypeAndName() {
        val phone = rows(items = listOf(card("p1", "15-202-A001"), own("p2", "彌彦神社"), own("p3", "彌彦神社", ItemType.MUSEUM_STAMP)))
        val backup = rows(
            items = listOf(card("b1", "15-202-A001"), own("b2", "彌彦神社"), own("b3", "彌彦神社", ItemType.EKI_STAMP)),
            finds = listOf(find("bf1", "b1", day1), find("bf2", "b2", day1), find("bf3", "b3", day1)),
        )
        load(phone)
        val ops = plan(phone, backup).resolve(emptyMap(), 10)
        write(ops)
        assertEquals(listOf("p1", "p2", "b3"), ops.newFinds.map { it.itemId })
        // Same name, another type: its own item
        assertEquals(listOf("b3"), ops.newItems.map { it.id })
    }

    @Test fun yourOwnTypesMatchByNameAndAreNotAddedTwice() {
        val pins = CollectionType("c-phone", "Pins", color = 1, icon = "pin", createdAt = 1)
        val phone = rows(items = listOf(own("p1", "Tokyo pin", ItemType.CUSTOM, "c-phone")), cats = listOf(pins))
        val backup = rows(
            items = listOf(own("b1", "Tokyo pin", ItemType.CUSTOM, "c-backup"), own("b2", "Osaka pin", ItemType.CUSTOM, "c-backup")),
            finds = listOf(find("bf1", "b1", day1), find("bf2", "b2", day1)),
            cats = listOf(CollectionType("c-backup", "pins", color = 2, icon = "pin", createdAt = 2)),
        )
        load(phone)
        val ops = plan(phone, backup).resolve(emptyMap(), 10)
        write(ops)
        assertTrue(ops.newCategories.isEmpty())
        assertEquals("c-phone", ops.newItems.single().customTypeId)
        assertEquals(setOf("p1", "b2"), ops.newFinds.map { it.itemId }.toSet())
    }

    @Test fun aFindOnTheSameDayIsTheSameFind() {
        val phone = rows(items = listOf(card("p1", "15-202-A001")), finds = listOf(find("pf1", "p1", day1, qty = 1)))
        // Five hours later the same day in Japan: the same find; a day later: another
        val backup = rows(
            items = listOf(card("b1", "15-202-A001")),
            finds = listOf(find("bf1", "b1", day1 + 5 * hour, qty = 2, cost = 300), find("bf2", "b1", day1 + day)),
            photos = listOf(photo("bp1", "bf1")),
        )
        load(phone)
        val ops = plan(phone, backup).resolve(emptyMap(), 10)
        write(ops)
        val merged = ops.findUpdates.single()
        assertEquals("pf1", merged.id)
        assertEquals(2, merged.quantity)
        assertEquals(300, merged.costYen)
        assertEquals(listOf("bf2"), ops.newFinds.map { it.id })
        assertEquals("pf1", ops.newPhotos.single().acquisitionId)
    }

    @Test fun aPhotoWithTheSameNameAndSizeIsAlreadyHere() {
        val phone = rows(items = listOf(card("p1", "15-202-A001")), finds = listOf(find("pf1", "p1", day1)), photos = listOf(photo("pp1", "pf1", "IMG_1.jpg", 4000)))
        val backup = rows(
            items = listOf(card("b1", "15-202-A001")), finds = listOf(find("bf1", "b1", day1 + day)),
            photos = listOf(photo("bp1", "bf1", "IMG_1.jpg", 4000), photo("bp2", "bf1", "IMG_1.jpg", 4001), photo("pp1", "bf1")),
        )
        val p = plan(phone, backup)
        assertEquals(2, p.photosAlreadyHere)
        assertEquals(listOf("bp2"), p.resolve(emptyMap(), 10).newPhotos.map { it.id })
    }

    @Test fun emptyHereIsFilledFromTheBackup() {
        val phone = rows(
            items = listOf(card("p1", "15-202-A001"), card("p2", "15-202-B001")),
            finds = listOf(find("pf1", "p1", day1)), photos = listOf(photo("pp1", "pf1", "a.jpg", 10)),
        )
        val backup = rows(
            items = listOf(
                card("b1", "15-202-A001").copy(coverPhotoId = "bp1", coverLat = 37.5, coverLng = 139.0),
                card("b2", "15-202-B001").copy(noneHereAt = 99),
            ),
            finds = listOf(find("bf1", "b1", day1)), photos = listOf(photo("bp1", "bf1", "a.jpg", 10)),
        )
        load(phone)
        val p = plan(phone, backup)
        assertTrue(p.conflicts.isEmpty())
        val ops = p.resolve(emptyMap(), 10)
        write(ops)
        val p1 = ops.itemUpdates.single { it.id == "p1" }
        // The backup's display picture is this phone's copy of the same picture
        assertEquals("pp1", p1.coverPhotoId)
        assertEquals(37.5, p1.coverLat!!, 0.0)
        assertEquals(99L, ops.itemUpdates.single { it.id == "p2" }.noneHereAt)
    }

    @Test fun aNewItemsDisplayPictureFollowsItsPhotoOrIsDropped() {
        val backup = rows(
            items = listOf(card("b1", "15-202-A001").copy(coverPhotoId = "bp1"), card("b2", "15-202-B001").copy(coverPhotoId = "gone")),
            finds = listOf(find("bf1", "b1", day1)), photos = listOf(photo("bp1", "bf1")),
        )
        val ops = plan(rows(), backup).resolve(emptyMap(), 10)
        write(ops)
        assertEquals("bp1", ops.newItems.single { it.id == "b1" }.coverPhotoId)
        assertNull(ops.newItems.single { it.id == "b2" }.coverPhotoId)
    }

    @Test fun anItemIdTakenByAnotherItemGetsANewOne() {
        val phone = rows(items = listOf(card("x", "15-202-A001")))
        val backup = rows(items = listOf(card("x", "15-202-B001")), finds = listOf(find("bf1", "x", day1)), photos = listOf(photo("bp1", "bf1")))
        load(phone)
        val ops = plan(phone, backup).resolve(emptyMap(), 10)
        write(ops)
        val added = ops.newItems.single()
        assertEquals("new-1", added.id)
        assertEquals("new-1", ops.newFinds.single().itemId)
    }

    @Test fun linesAndStationsFollowTheItems() {
        val phone = rows(items = listOf(card("p1", "15-202-A001")), lines = listOf(Line("L1", "越後線")), stations = listOf(LineStation("L1", "p1", 1)))
        val backup = rows(
            items = listOf(card("b1", "15-202-A001"), card("b2", "15-202-B001")),
            lines = listOf(Line("L1", "越後線"), Line("L2", "弥彦線", operator = "JR東日本")),
            stations = listOf(LineStation("L1", "b1", 1), LineStation("L1", "b2", 2), LineStation("L2", "b1", 1)),
        )
        load(phone)
        val ops = plan(phone, backup).resolve(emptyMap(), 10)
        write(ops)
        assertEquals(listOf("L2"), ops.newLines.map { it.id })
        assertEquals(setOf("L1" to "b2", "L2" to "p1"), ops.newLineStations.map { it.lineId to it.itemId }.toSet())
    }

    // Conflicts

    private val phoneWithCover = rows(
        items = listOf(card("p1", "15-202-A001").copy(coverPhotoId = "pp1", coverLat = 37.0, coverLng = 139.0)),
        finds = listOf(find("pf1", "p1", day1)), photos = listOf(photo("pp1", "pf1", "phone.jpg", 10)),
    )
    private val backupWithCover = rows(
        items = listOf(card("b1", "15-202-A001").copy(coverPhotoId = "bp1", coverLat = 37.1, coverLng = 139.0)),
        finds = listOf(find("bf1", "b1", day1 + day)), photos = listOf(photo("bp1", "bf1", "backup.jpg", 20)),
    )

    @Test fun aDifferentDisplayPictureAndCoverPositionAreAsked() {
        val c = plan(phoneWithCover, backupWithCover).conflicts.single()
        assertEquals(listOf(MergeField.COVER_PHOTO, MergeField.COVER_POSITION), c.fields)
        assertEquals("pp1", c.phoneCover!!.id)
        assertEquals("bp1", c.backupCover!!.id)
    }

    @Test fun theSamePictureUnderAnotherIdIsNotAConflict() {
        val backup = rows(
            items = listOf(card("b1", "15-202-A001").copy(coverPhotoId = "bp1", coverLat = 37.000001, coverLng = 139.0)),
            finds = listOf(find("bf1", "b1", day1)), photos = listOf(photo("bp1", "bf1", "phone.jpg", 10)),
        )
        assertTrue(plan(phoneWithCover, backup).conflicts.isEmpty())
    }

    @Test fun keepingThisPhoneChangesNothingAndKeepingTheBackupTakesItsValues() {
        load(phoneWithCover)
        val p = plan(phoneWithCover, backupWithCover)
        val kept = p.resolve(emptyMap(), 10)
        assertTrue(kept.itemUpdates.isEmpty())
        assertEquals(listOf("bp1"), kept.newPhotos.map { it.id })
        val taken = p.resolve(mapOf("p1" to MergePick.BACKUP), 10)
        write(taken)
        val u = taken.itemUpdates.single()
        assertEquals("bp1", u.coverPhotoId)
        assertEquals(37.1, u.coverLat!!, 0.0)
    }

    @Test fun notesAndHoursAreAskedOnlyWhenTheyAreYours() {
        // Two catalogue versions: not asked, this phone's stays
        val phone = rows(items = listOf(card("p1", "15-202-A001", notes = "old text"), card("p2", "15-202-B001", notes = "mine", editedAt = 5), own("p3", "彌彦神社").copy(hoursText = "9-16")))
        val backup = rows(items = listOf(card("b1", "15-202-A001", notes = "new text"), card("b2", "15-202-B001", notes = "theirs"), own("b3", "彌彦神社").copy(hoursText = "9-17", closedDays = "Mon")))
        val conflicts = plan(phone, backup).conflicts
        assertEquals(listOf("p2", "p3"), conflicts.map { it.phone.id })
        assertEquals(listOf(MergeField.NOTES), conflicts[0].fields)
        assertEquals(listOf(MergeField.HOURS), conflicts[1].fields)
        val ops = plan(phone, backup).resolve(mapOf("p3" to MergePick.BACKUP), 10)
        val p3 = ops.itemUpdates.single()
        assertEquals("9-17", p3.hoursText)
        assertEquals("Mon", p3.closedDays)
    }

    @Test fun anEditedNoteFillsAnEmptyOne() {
        val phone = rows(items = listOf(card("p1", "15-202-A001")))
        val backup = rows(items = listOf(card("b1", "15-202-A001", notes = "Ask at the desk", editedAt = 7)))
        val p = plan(phone, backup)
        assertTrue(p.conflicts.isEmpty())
        val u = p.resolve(emptyMap(), 10).itemUpdates.single()
        assertEquals("Ask at the desk", u.notes)
        // So a catalogue update leaves the note alone, as on the phone it came from
        assertEquals(7L, u.editedAt)
    }

    @Test fun noStampHereAgainstAFindIsAsked() {
        val phone = rows(items = listOf(card("p1", "15-202-A001").copy(noneHereAt = 50)))
        val backup = rows(
            items = listOf(card("b1", "15-202-A001").copy(coverPhotoId = "bp1")),
            finds = listOf(find("bf1", "b1", day1)), photos = listOf(photo("bp1", "bf1")),
        )
        load(phone)
        val p = plan(phone, backup)
        val c = p.conflicts.single()
        assertEquals(listOf(MergeField.NONE_HERE), c.fields)
        assertEquals("bf1", c.backupFirstFind!!.id)
        // Keeping this phone: the find, its photo and the display picture stay out
        val kept = p.resolve(emptyMap(), 10)
        assertTrue(kept.newFinds.isEmpty())
        assertTrue(kept.newPhotos.isEmpty())
        assertTrue(kept.itemUpdates.isEmpty())
        // Keeping the backup: all of it comes in, and the item is no longer "No stamp here"
        val taken = p.resolve(mapOf("p1" to MergePick.BACKUP), 10)
        write(taken)
        assertEquals(1, count("acquisition"))
        assertEquals(1, count("photo"))
        val u = taken.itemUpdates.single()
        assertNull(u.noneHereAt)
        assertEquals("bp1", u.coverPhotoId)
    }

    @Test fun keepAllFromTheBackupCoversEveryItem() {
        val phone = rows(items = listOf(own("p1", "A").copy(notes = "1"), own("p2", "B").copy(notes = "2")))
        val backup = rows(items = listOf(own("b1", "A").copy(notes = "x"), own("b2", "B").copy(notes = "y")))
        val p = plan(phone, backup)
        val picks = p.conflicts.associate { it.phone.id to MergePick.BACKUP }
        assertEquals(listOf("x", "y"), p.resolve(picks, 10).itemUpdates.sortedBy { it.id }.map { it.notes })
    }
}
