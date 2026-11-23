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
}
