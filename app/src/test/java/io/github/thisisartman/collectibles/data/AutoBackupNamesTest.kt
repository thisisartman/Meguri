package io.github.thisisartman.collectibles.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pruning deletes files in the user's own folder, so only the app's own names may match. */
class AutoBackupNamesTest {
    private fun own(name: String) = AutoBackup.ownFile(dev = false).matches(name)
    private fun ownDev(name: String) = AutoBackup.ownFile(dev = true).matches(name)

    @Test fun matchesOwnBackups() {
        assertTrue(own("meguri-auto-20261103-110348.zip"))
        assertTrue(own("meguri-auto-20261103-110348 (1).zip"))
        assertTrue(own("meguri-auto-20261103-110348"))
    }

    /** Renamed in 0.4.0: weekly backups written under the old name are still the app's own, so they are pruned too. */
    @Test fun oldNamesStillCount() {
        assertTrue(own("collectibles-auto-20261103-110348.zip"))
        assertTrue(ownDev("collectibles-dev-auto-20261103-110348.zip"))
        assertFalse(own("collectibles-dev-auto-20261103-110348.zip"))
        assertFalse(ownDev("collectibles-auto-20261103-110348.zip"))
    }

    /** Old and new names together: newest first by the date in the name, not by the name ("m" sorts after "c"). */
    @Test fun newestFirstAcrossNames() {
        val names = listOf("meguri-auto-20261110-090000.zip", "collectibles-auto-20261201-090000.zip", "collectibles-auto-20261101-090000.zip")
        assertEquals(
            listOf("collectibles-auto-20261201-090000.zip", "meguri-auto-20261110-090000.zip", "collectibles-auto-20261101-090000.zip"),
            names.sortedByDescending { AutoBackup.stampOf(it) },
        )
    }

    @Test fun manualBackupsAreNamedMeguri() {
        assertTrue(io.github.thisisartman.collectibles.ui.backupFileName(photos = false).startsWith("meguri-backup-"))
        assertTrue(io.github.thisisartman.collectibles.ui.backupFileName(photos = true).startsWith("meguri-backup-photos-"))
    }

    @Test fun leavesEverythingElseAlone() {
        assertFalse(own("collectibles-backup-20261103-0226.zip"))
        assertFalse(own("collectibles-pre-restore-20261103-110245.zip"))
        assertFalse(own("collectibles-auto-notes.zip"))
        assertFalse(own("my collectibles-auto-20261103-110348.zip"))
        assertFalse(own("collectibles-auto-20261103-110348.zip.txt"))
    }

    /** #42: in a shared folder, pruning in one build must not delete the other build's backups. */
    @Test fun devAndRealBuildsNeverMatchEachOther() {
        val real = AutoBackup.prefixFor(dev = false) + "20261103-110348.zip"
        val dev = AutoBackup.prefixFor(dev = true) + "20261103-110348.zip"
        assertTrue(own(real))
        assertTrue(ownDev(dev))
        assertFalse(own(dev))
        assertFalse(ownDev(real))
    }
}
