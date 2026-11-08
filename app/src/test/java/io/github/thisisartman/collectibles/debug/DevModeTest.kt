package io.github.thisisartman.collectibles.debug

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DevModeTest {
    @Test
    fun devBuildIsAlwaysDetailed() {
        assertTrue(DevMode.detailed(debugBuild = true, devModeOn = false))
        assertTrue(DevMode.detailed(debugBuild = true, devModeOn = true))
    }

    @Test
    fun releaseIsDetailedOnlyWithDeveloperMode() {
        assertFalse(DevMode.detailed(debugBuild = false, devModeOn = false))
        assertTrue(DevMode.detailed(debugBuild = false, devModeOn = true))
    }
}
