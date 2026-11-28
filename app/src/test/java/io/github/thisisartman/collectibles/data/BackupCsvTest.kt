package io.github.thisisartman.collectibles.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** #170: the readable CSVs in a backup carry every column, one value per header. */
class BackupCsvTest {
    private val item = Item(
        id = "i1", type = ItemType.CUSTOM, nameJa = "テスト", origin = Origin.USER, createdAt = 1, updatedAt = 2,
        customTypeId = "c1", editedAt = 3, parentItemId = "p1",
    )
    private val find = Acquisition(id = "f1", itemId = "i1", acquiredAt = 10, via = Via.MANUAL, createdAt = 11, quantity = 3)
    private val photo = Photo(id = "ph1", acquisitionId = "f1", contentUri = "content://x", role = "cover", exifLat = 1.5, exifLng = 2.5, createdAt = 12)
    private val category = CollectionType(id = "c1", name = "Pins", color = 0xFF00FF00, icon = "pin", createdAt = 13)

    private fun table(csv: String): List<List<String>> = csv.trimEnd('\n').split("\n").map { it.split(",") }

    private fun assertComplete(csv: String, vararg columns: String) {
        val t = table(csv)
        t.drop(1).forEach { assertEquals("one value per header", t[0].size, it.size) }
        columns.forEach { assertTrue("$it is in the header", it in t[0]) }
    }

    @Test fun items() = assertComplete(CatalogAndBackup.itemsCsv(listOf(item)), "custom_type_id", "edited_at", "parent_item_id")
    @Test fun finds() = assertComplete(CatalogAndBackup.findsCsv(listOf(find), mapOf("i1" to item)), "quantity")
    @Test fun photos() = assertComplete(CatalogAndBackup.photosCsv(listOf(photo)), "role", "exif_lat", "exif_lng", "created_at")
    @Test fun categories() = assertComplete(CatalogAndBackup.categoriesCsv(listOf(category)), "id", "name", "name_ja", "icon")

    @Test fun quantityIsWritten() {
        val t = table(CatalogAndBackup.findsCsv(listOf(find), mapOf("i1" to item)))
        assertEquals("3", t[1][t[0].indexOf("quantity")])
    }

    // #209: no "had before Meguri" column, and the line's operator
    @Test fun findsHaveNoBeforeApp() = assertTrue("before_app" !in table(CatalogAndBackup.findsCsv(listOf(find), mapOf("i1" to item)))[0])

    @Test fun linesCarryTheOperator() {
        val csv = CatalogAndBackup.linesCsv(listOf(Line("L1", "越後線", "Echigo Line", "JR東日本")), listOf(LineStation("L1", "i1", 3)))
        assertComplete(csv, "line_id", "operator", "station_item_id", "seq")
        val t = table(csv)
        assertEquals("JR東日本", t[1][t[0].indexOf("operator")])
    }

    @Test fun regionsWithoutTheirItemsAreNoLongerInstalled() {
        fun pack(id: String, type: ItemType?, kind: String = "catalogue", items: Int = 10) =
            Pack(id, kind, type, true, "JP-15", "新潟県", "Niigata", items, "$id.gz", 1, "sha")
        val index = PackIndex("1", listOf(
            pack("JP-15/EKI_STAMP", ItemType.EKI_STAMP), pack("JP-15/MANHOLE_CARD", ItemType.MANHOLE_CARD),
            pack("JP-15/map", null, kind = "map"), pack("JP-15/DAM_CARD", ItemType.DAM_CARD, items = 0),
        ))
        val installed = index.packs.map { it.id }.toSet()
        // The backup had the stations, not the cards: the card pack goes; the map and an empty pack stay
        assertEquals(listOf("JP-15/MANHOLE_CARD"), RegionPacks.emptyPacks(index, installed, setOf("新潟県" to ItemType.EKI_STAMP)))
        assertTrue(RegionPacks.emptyPacks(index, emptySet(), emptySet()).isEmpty())
    }
}
