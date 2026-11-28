package io.github.thisisartman.collectibles.data

import io.github.thisisartman.collectibles.ui.AppRequest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files

/** The guards before 0.7.2 (review A2 to A8): each one on its own. */
class SafeFilesTest {
    // A2: a backup with far fewer finds doesn't replace the one file
    @Test fun farFewerFindsIsRefused() {
        assertTrue(SafeFiles.farFewer(0, 120))
        assertTrue(SafeFiles.farFewer(90, 120))
        assertTrue(SafeFiles.farFewer(7, 10))
        assertFalse(SafeFiles.farFewer(110, 120))
        assertFalse(SafeFiles.farFewer(8, 10))
        assertFalse(SafeFiles.farFewer(120, 120))
        assertFalse(SafeFiles.farFewer(200, 120))
        // A first backup has nothing to compare with
        assertFalse(SafeFiles.farFewer(0, 0))
    }

    // A4: rotation keeps the newest and, always, the one with the most finds
    @Test fun pruningKeepsTheBestBackup() {
        val names = listOf(
            "meguri-auto-20261101-090000.zip", "meguri-auto-20261108-090000.zip", "meguri-auto-20261115-090000.zip",
            "meguri-auto-20261122-090000.zip", "meguri-auto-20261129-090000.zip",
        )
        val drop = SafeFiles.toPrune(names, 2, "20261108-090000", AutoBackup::stampOf)
        assertEquals(listOf("meguri-auto-20261115-090000.zip", "meguri-auto-20261101-090000.zip"), drop)
        // Without a best one, only the newest are kept
        assertEquals(3, SafeFiles.toPrune(names, 2, null, AutoBackup::stampOf).size)
        // The best among the newest changes nothing
        assertEquals(3, SafeFiles.toPrune(names, 2, "20261129-090000", AutoBackup::stampOf).size)
    }

    // A5: only plain file names, resolved inside their folder
    @Test fun onlyPlainNames() {
        assertTrue(SafeFiles.plainName("jp-13-map.pmtiles"))
        assertTrue(SafeFiles.plainName("abc_DEF-1.webp"))
        for (bad in listOf("", ".", "..", "../databases/collectibles.db", "a/b", "a\\b", "x..y", "/etc/passwd", "a b", "ü.webp")) {
            assertFalse(bad, SafeFiles.plainName(bad))
        }
    }

    @Test fun photoAddressesStayInThePhotosFolder() {
        val names = io.github.thisisartman.collectibles.photo.PhotoNames
        assertTrue(names.inPhotos(names.copy("0b6f-12")))
        assertTrue(names.inPhotos(names.original("0b6f-12")))
        assertTrue(names.inPhotos(names.edited("0b6f-12", 1_795_791_600_000L)))
        assertFalse(names.inPhotos("app:../databases/collectibles.db"))
        assertFalse(names.inPhotos("app:photos/../databases/collectibles.db"))
        assertFalse(names.inPhotos("app:photos/sub/x.webp"))
        assertFalse(names.inPhotos("app:shared_prefs/backup.xml"))
        assertFalse(names.inPhotos("content://media/external/images/media/1"))
    }

    @Test fun aChildStaysInItsFolder() {
        val dir = Files.createTempDirectory("safe").toFile()
        try {
            assertEquals(File(dir, "ok.bin").canonicalPath, SafeFiles.child(dir, "ok.bin")!!.canonicalPath)
            assertNull(SafeFiles.child(dir, "../x"))
            assertNull(SafeFiles.child(dir, "a/b"))
            assertFalse(SafeFiles.inside(dir, File(dir, "../escape")))
            assertFalse(SafeFiles.inside(dir, dir))
        } finally {
            dir.deleteRecursively()
        }
    }

    // A6: caps on bytes and entries
    @Test fun copyStopsAtItsLimit() {
        val out = ByteArrayOutputStream()
        assertEquals(10L, SafeFiles.copyCapped(ByteArrayInputStream(ByteArray(10)), out, 10))
        try {
            SafeFiles.copyCapped(ByteArrayInputStream(ByteArray(11)), ByteArrayOutputStream(), 10)
            fail("over the limit")
        } catch (e: SafeFiles.TooBig) { }
        assertArrayEquals(ByteArray(5) { 1 }, SafeFiles.readCapped(ByteArrayInputStream(ByteArray(5) { 1 }), 5))
    }

    @Test fun aBudgetCapsTheWholeUnzip() {
        val b = SafeFiles.Budget(maxBytes = 100, maxEntries = 3)
        b.entry(); b.copy(ByteArrayInputStream(ByteArray(60)), ByteArrayOutputStream())
        b.entry()
        try {
            b.copy(ByteArrayInputStream(ByteArray(60)), ByteArrayOutputStream())
            fail("over the total")
        } catch (e: SafeFiles.TooBig) { }
        val c = SafeFiles.Budget(maxBytes = 1000, maxEntries = 2)
        c.entry(); c.entry()
        try { c.entry(); fail("too many files") } catch (e: SafeFiles.TooBig) { }
        // One entry's own cap holds within the budget
        val d = SafeFiles.Budget(maxBytes = 1000, maxEntries = 5)
        try { d.copy(ByteArrayInputStream(ByteArray(50)), ByteArrayOutputStream(), perEntry = 40); fail("over the entry's cap") } catch (e: SafeFiles.TooBig) { }
    }

    // A7: the dev copy names its backups apart, so it never prunes the real app's
    @Test fun devReleaseIsTheDevApp() {
        assertTrue(SafeFiles.devApp("io.github.thisisartman.collectibles.dev", debug = false))
        assertTrue(SafeFiles.devApp("io.github.thisisartman.collectibles", debug = true))
        assertFalse(SafeFiles.devApp("io.github.thisisartman.collectibles", debug = false))
        assertEquals("meguri-dev-auto-", AutoBackup.prefixFor(true))
        assertFalse(AutoBackup.ownFile(dev = false).matches("meguri-dev-auto-20261128-090000.zip"))
        assertTrue(AutoBackup.ownFile(dev = true).matches("meguri-dev-auto-20261128-090000.zip"))
    }

    // A8: internal extras only with this install's token
    @Test fun internalExtrasNeedTheToken() {
        assertTrue(AppRequest.trusted("abc", "abc"))
        assertFalse(AppRequest.trusted(null, "abc"))
        assertFalse(AppRequest.trusted("abd", "abc"))
        assertFalse(AppRequest.trusted("abc", null))
        assertFalse(AppRequest.trusted(null, null))
    }
}
