package io.github.thisisartman.collectibles.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.thisisartman.collectibles.debug.FileLog
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/**
 * Phase 4 added the Joetsu Shinkansen to the bundled catalogue. A phone that loaded the older catalogue gets the new
 * line on the next start without losing or duplicating anything; a phone that never loaded it is left alone.
 */
@RunWith(AndroidJUnit4::class)
class BundledRefreshTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "refresh-test-${UUID.randomUUID()}.db"
    private lateinit var db: CollectiblesDb
    private lateinit var io: CatalogAndBackup
    private lateinit var work: File

    @Before fun setUp() {
        context.getSharedPreferences("catalogue", Context.MODE_PRIVATE).edit().clear().commit()
        work = File(context.cacheDir, "rt-${UUID.randomUUID()}").apply { mkdirs() }
        db = Room.databaseBuilder(context, CollectiblesDb::class.java, name).addMigrations(*CollectiblesDb.MIGRATIONS).build()
        io = CatalogAndBackup(context, db, FileLog(work))
    }

    @After fun tearDown() {
        db.close()
        context.deleteDatabase(name)
        work.deleteRecursively()
        context.getSharedPreferences("catalogue", Context.MODE_PRIVATE).edit().clear().commit()
    }

    /** The catalogue as phones had it before phase 4: the bundled file without its Shinkansen rows. */
    private fun olderCatalogue(): String {
        val text = context.assets.open(BUNDLED_CATALOGUE).use { it.readBytes().toString(Charsets.UTF_8) }
        return text.lines().filterNot { "JR_JOETSU_SHINKANSEN" in it }.joinToString("\n")
    }

    @Test fun olderCataloguePhoneGetsTheShinkansenAndKeepsEverything() = runBlocking {
        io.importCatalogText(olderCatalogue())
        val d = db.dao()
        val urasa = d.allItems().single { it.nameJa == "浦佐駅" && it.type == ItemType.EKI_STAMP }
        d.insertFind(Acquisition(id = "f1", itemId = urasa.id, acquiredAt = 1000, via = Via.MANUAL, createdAt = 1000))
        val itemsBefore = d.allItems().associateBy { it.id }
        assertTrue(d.allLines().none { it.id == RailGraph.SHINKANSEN })

        val r = io.refreshBundledIfChanged()
        assertNotNull(r)
        assertEquals(1, r!!.added) // Jomo-Kogen, the one new station
        val after = d.allItems()
        assertEquals(itemsBefore.size + 1, after.size)
        assertTrue(itemsBefore.keys.all { id -> after.any { it.id == id } })
        assertEquals(after.size, after.map { it.catalogKey }.toSet().size)
        assertEquals(listOf("f1"), d.allFinds().map { it.id })
        val sk = d.allLineStations().filter { it.lineId == RailGraph.SHINKANSEN }.sortedBy { it.seq }
        assertEquals(7, sk.size)
        assertEquals(urasa.id, sk[3].itemId) // Takasaki, Jomo-Kogen, Echigo-Yuzawa, Urasa
        // The planner now rides it
        assertEquals(RailGraph.SHINKANSEN, RailGraph.from(d.allLineStations()).route(urasa.id, after.single { it.nameJa == "新潟駅" && it.type == ItemType.EKI_STAMP }.id)!!.single().lineId)

        // A second start changes nothing
        assertNull(io.refreshBundledIfChanged())
    }

    @Test fun phoneThatNeverLoadedItIsLeftAlone() = runBlocking {
        assertNull(io.refreshBundledIfChanged())
        assertEquals(0, db.dao().allItems().size)
    }

    @Test fun restoredOlderBackupGetsTheLineBack() = runBlocking {
        io.importBundled()
        assertNull(io.refreshBundledIfChanged())
        // As after restoring a backup made before phase 4: same app, the line is gone
        db.dao().deleteAllLineStations()
        db.dao().deleteAllLines()
        io.importCatalogText(olderCatalogue())
        assertNotNull(io.refreshBundledIfChanged())
        assertTrue(db.dao().allLines().any { it.id == RailGraph.SHINKANSEN })
    }

    /** Edits you make to an item's details stay when the catalogue is loaded again (owner, 2026-11-08). */
    @Test fun anEditedItemKeepsYourEditsThroughAReload() = runBlocking {
        io.importBundled()
        val dao = db.dao()
        val card = dao.allItems().first { it.code == "15-213-E001" }
        dao.upsertItem(card.copy(placeName = "My own note on where", lat = 37.0, lng = 138.0, editedAt = 1))
        // A changed bundled file forces a full reload
        context.getSharedPreferences("catalogue", Context.MODE_PRIVATE).edit().clear().commit()
        io.importBundled()
        val after = dao.allItems().first { it.code == "15-213-E001" }
        assertEquals("My own note on where", after.placeName)
        assertEquals(37.0, after.lat!!, 0.0)
        // Items you did not edit are still updated as usual
        assertNotNull(dao.allItems().first { it.code == "15-213-A001" }.placeName)
        Unit
    }
}
