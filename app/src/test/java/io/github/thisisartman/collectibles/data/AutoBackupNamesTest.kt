package io.github.thisisartman.collectibles.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pruning deletes files in the user's own folder, so only the app's own names may match. */
class AutoBackupNamesTest {
    private fun own(name: String) = AutoBackup.OWN_FILE.matches(name)

    @Test fun matchesOwnBackups() {
        assertTrue(own("collectibles-auto-20261103-110348.zip"))
        assertTrue(own("collectibles-auto-20261103-110348 (1).zip"))
        assertTrue(own("collectibles-auto-20261103-110348"))
    }

    @Test fun leavesEverythingElseAlone() {
        assertFalse(own("collectibles-backup-20261103-0226.zip"))
        assertFalse(own("collectibles-pre-restore-20261103-110245.zip"))
        assertFalse(own("collectibles-auto-notes.zip"))
        assertFalse(own("my collectibles-auto-20261103-110348.zip"))
        assertFalse(own("collectibles-auto-20261103-110348.zip.txt"))
    }
}
