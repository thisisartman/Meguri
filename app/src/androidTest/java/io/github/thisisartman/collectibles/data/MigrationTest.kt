package io.github.thisisartman.collectibles.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * v1 to v2 on rows shaped like the owner's real data: a find with a date and position, a "had before the app"
 * find with no date, a photo, and a line with a station. Every row and value must come through, with quantity 1.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    private val name = "migration-test.db"

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), CollectiblesDb::class.java)

    @Test
    fun v1RowsSurviveTheMoveToV2() {
        helper.createDatabase(name, 1).apply {
            execSQL("INSERT INTO item (id, type, catalog_key, code, name_ja, name_romaji, origin, lat, lng, created_at, updated_at) VALUES ('i1', 'MANHOLE_CARD', 'MANHOLE_CARD:15-202-A001', '15-202-A001', '長岡市 A001', 'Nagaoka-shi A001', 'CSV_IMPORT', 37.44, 138.85, 1, 2)")
            execSQL("INSERT INTO item (id, type, catalog_key, name_ja, name_romaji, origin, created_at, updated_at) VALUES ('i2', 'GOSHUIN', 'GOSHUIN:豪徳寺', '豪徳寺', 'Gotokuji', 'CSV_IMPORT', 3, 4)")
            execSQL("INSERT INTO acquisition (id, item_id, acquired_at, before_app, lat, lng, accuracy_m, notes, via, created_at) VALUES ('f1', 'i1', 1000, 0, 37.1, 138.2, 5.0, 'メモ', 'MANUAL', 1000)")
            execSQL("INSERT INTO acquisition (id, item_id, acquired_at, before_app, via, created_at) VALUES ('f2', 'i2', NULL, 1, 'BULK', 1001)")
            execSQL("INSERT INTO photo (id, acquisition_id, content_uri, role, missing, mediastore_id, display_name, relative_path, size_bytes, taken_at, created_at) VALUES ('p1', 'f1', 'content://media/external_primary/images/media/38183', 'other', 0, 38183, 'CT_1.jpg', 'DCIM/CollectiblesTracker/', 1704003, 7, 1)")
            execSQL("INSERT INTO line (id, name_ja, name_romaji) VALUES ('JR_YAHIKO', '弥彦線', 'Yahiko Line')")
            execSQL("INSERT INTO line_station (line_id, item_id, seq) VALUES ('JR_YAHIKO', 'i1', 3)")
            close()
        }

        val db = helper.runMigrationsAndValidate(name, CollectiblesDb.VERSION, true, *CollectiblesDb.MIGRATIONS)

        fun one(sql: String): String? = db.query(sql).use { c -> if (c.moveToFirst()) (if (c.isNull(0)) null else c.getString(0)) else "no row" }
        assertEquals("2", one("SELECT COUNT(*) FROM item"))
        assertEquals("2", one("SELECT COUNT(*) FROM acquisition"))
        assertEquals("1", one("SELECT COUNT(*) FROM photo"))
        assertEquals("1", one("SELECT COUNT(*) FROM line_station"))
        // Every find starts as one of a thing
        assertEquals("0", one("SELECT COUNT(*) FROM acquisition WHERE quantity != 1"))
        // "Had before the app" keeps its missing date and its flag
        assertEquals(null, one("SELECT acquired_at FROM acquisition WHERE id = 'f2'"))
        assertEquals("1", one("SELECT before_app FROM acquisition WHERE id = 'f2'"))
        // Values on the dated find and its photo are unchanged
        assertEquals("メモ", one("SELECT notes FROM acquisition WHERE id = 'f1'"))
        assertEquals("38183", one("SELECT mediastore_id FROM photo WHERE id = 'p1'"))
        assertEquals("1704003", one("SELECT size_bytes FROM photo WHERE id = 'p1'"))
        assertEquals("GOSHUIN:豪徳寺", one("SELECT catalog_key FROM item WHERE id = 'i2'"))
        // New column is empty for old items; the new table starts empty
        assertEquals(null, one("SELECT custom_type_id FROM item WHERE id = 'i1'"))
        assertEquals("0", one("SELECT COUNT(*) FROM collection_type"))
        db.close()
    }

    @Test
    fun v2ItemsGetEmptyNewColumns() {
        helper.createDatabase("migration-test-2.db", 2).apply {
            execSQL("INSERT INTO item (id, type, catalog_key, name_ja, origin, created_at, updated_at) VALUES ('s1', 'EKI_STAMP', 'EKI_STAMP:南吉田駅', '南吉田駅', 'CSV_IMPORT', 1, 2)")
            execSQL("INSERT INTO acquisition (id, item_id, acquired_at, before_app, via, created_at, quantity) VALUES ('f1', 's1', 5, 0, 'MANUAL', 5, 2)")
            close()
        }
        val db = helper.runMigrationsAndValidate("migration-test-2.db", CollectiblesDb.VERSION, true, *CollectiblesDb.MIGRATIONS)
        fun one(sql: String): String? = db.query(sql).use { c -> if (c.moveToFirst()) (if (c.isNull(0)) null else c.getString(0)) else "no row" }
        assertEquals(null, one("SELECT none_here_at FROM item WHERE id = 's1'"))
        assertEquals(null, one("SELECT cover_photo_id FROM item WHERE id = 's1'"))
        assertEquals("2", one("SELECT quantity FROM acquisition WHERE id = 'f1'"))
        db.close()
    }

    @Test
    fun v3ItemsKeepEverythingAndAreNotEdited() {
        helper.createDatabase("migration-test-3.db", 3).apply {
            execSQL("INSERT INTO item (id, type, catalog_key, name_ja, origin, created_at, updated_at, none_here_at) VALUES ('s1', 'EKI_STAMP', 'EKI_STAMP:吉田駅', '吉田駅', 'CSV_IMPORT', 1, 2, 9)")
            close()
        }
        val db = helper.runMigrationsAndValidate("migration-test-3.db", 4, true, *CollectiblesDb.MIGRATIONS)
        fun one(sql: String): String? = db.query(sql).use { c -> if (c.moveToFirst()) (if (c.isNull(0)) null else c.getString(0)) else "no row" }
        assertEquals("吉田駅", one("SELECT name_ja FROM item WHERE id = 's1'"))
        assertEquals("9", one("SELECT none_here_at FROM item WHERE id = 's1'"))
        assertEquals(null, one("SELECT edited_at FROM item WHERE id = 's1'"))
        db.close()
    }

    /** #203: v4 cards keep everything, edits included, and have no cover position yet. */
    @Test
    fun v4CardsGetAnEmptyCoverPosition() {
        helper.createDatabase("migration-test-4.db", 4).apply {
            execSQL("INSERT INTO item (id, type, catalog_key, code, name_ja, origin, lat, lng, created_at, updated_at, edited_at) VALUES ('c1', 'MANHOLE_CARD', 'MANHOLE_CARD:15-202-A001', '15-202-A001', '長岡市 A001', 'CSV_IMPORT', 37.44, 138.85, 1, 2, 3)")
            close()
        }
        val db = helper.runMigrationsAndValidate("migration-test-4.db", 5, true, *CollectiblesDb.MIGRATIONS)
        fun one(sql: String): String? = db.query(sql).use { c -> if (c.moveToFirst()) (if (c.isNull(0)) null else c.getString(0)) else "no row" }
        assertEquals("37.44", one("SELECT lat FROM item WHERE id = 'c1'"))
        assertEquals("3", one("SELECT edited_at FROM item WHERE id = 'c1'"))
        assertEquals(null, one("SELECT cover_lat FROM item WHERE id = 'c1'"))
        assertEquals(null, one("SELECT cover_lng FROM item WHERE id = 'c1'"))
        db.close()
    }
}
