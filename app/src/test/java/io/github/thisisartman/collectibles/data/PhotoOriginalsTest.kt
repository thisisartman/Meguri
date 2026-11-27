package io.github.thisisartman.collectibles.data

import io.github.thisisartman.collectibles.photo.PhotoNames
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** #208: a cut photo's original, in the database (schema v7), in the photos folder and in backups. */
class PhotoOriginalsTest {
    @Test fun theMigrationChainRunsToTheCurrentVersionWithNoGaps() {
        assertEquals(7, CollectiblesDb.VERSION)
        val steps = CollectiblesDb.MIGRATIONS.map { it.startVersion to it.endVersion }
        assertEquals((1 until CollectiblesDb.VERSION).map { it to it + 1 }, steps)
    }

    @Test fun v7AddsOneNullableColumnToPhotos() {
        assertEquals("ALTER TABLE `photo` ADD COLUMN `original_uri` TEXT", CollectiblesDb.SQL_6_7)
        // No NOT NULL and no default: every older photo simply has no original
        assertTrue("NOT NULL" !in CollectiblesDb.SQL_6_7 && "DEFAULT" !in CollectiblesDb.SQL_6_7)
    }

    @Test fun theExportedV7SchemaHasTheColumnAsTheMigrationAddsIt() {
        // Tests run in the app module; the schema is written there by the build
        val f = File("schemas/io.github.thisisartman.collectibles.data.CollectiblesDb/7.json")
        org.junit.Assume.assumeTrue("schema not exported yet", f.isFile)
        val json = f.readText()
        val photo = json.substringAfter("\"tableName\": \"photo\"").substringBefore("\"tableName\"")
        assertTrue(photo.contains("`original_uri` TEXT,"))
        val field = photo.substringAfter("\"columnName\": \"original_uri\"").substringBefore("}")
        assertTrue(field.contains("\"affinity\": \"TEXT\""))
        assertTrue("nullable", !field.contains("\"notNull\": true"))
        // v6 had no such column
        val v6 = File("schemas/io.github.thisisartman.collectibles.data.CollectiblesDb/6.json").readText()
        assertTrue("original_uri" !in v6)
    }

    @Test fun anOriginalSitsBesideItsCopy() {
        assertEquals("app:photos/p1.webp", PhotoNames.copy("p1"))
        assertEquals("app:photos/p1.orig.webp", PhotoNames.original("p1"))
        assertEquals("app:photos/p1-e42.webp", PhotoNames.edited("p1", 42))
    }

    @Test fun theUnusedCopySweepKeepsOriginals() {
        val wanted = PhotoNames.wanted(listOf(PhotoNames.edited("p1", 42), PhotoNames.original("p1"), "content://media/1", null))
        assertEquals(setOf("p1-e42.webp", "p1.orig.webp"), wanted)
    }

    @Test fun aBackupNamesOriginalsApartFromPhotos() {
        assertEquals("photos/p1", PhotoNames.backupPhoto("p1"))
        assertEquals("originals/p1", PhotoNames.backupOriginal("p1"))
        assertEquals("p1" to false, PhotoNames.parseBackup("photos/p1"))
        assertEquals("ab-12" to true, PhotoNames.parseBackup("originals/ab-12"))
        // Anything else in the zip, or a name that isn't an id, is not a picture
        assertNull(PhotoNames.parseBackup("photos/../x"))
        assertNull(PhotoNames.parseBackup("originals/a.b"))
        assertNull(PhotoNames.parseBackup("collectibles.db"))
        assertNull(PhotoNames.parseBackup("photos/"))
    }

    @Test fun theBackupsPhotoListCarriesTheOriginal() {
        val p = Photo(id = "p1", acquisitionId = "f1", contentUri = "app:photos/p1.webp", createdAt = 1, originalUri = "app:photos/p1.orig.webp")
        val t = CatalogAndBackup.photosCsv(listOf(p, p.copy(id = "p2", originalUri = null))).trimEnd('\n').split("\n").map { Csv.parse(it).single() }
        val col = t[0].indexOf("original_uri")
        assertTrue(col >= 0)
        assertEquals("app:photos/p1.orig.webp", t[1][col])
        assertEquals("", t[2][col])
    }
}
