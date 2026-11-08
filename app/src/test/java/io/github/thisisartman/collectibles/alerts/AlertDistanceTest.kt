package io.github.thisisartman.collectibles.alerts

import org.junit.Assert.assertEquals
import org.junit.Test

/** Alerts design C: three distances; one saved before (300 m) reads as the nearest. */
class AlertDistanceTest {
    @Test fun choicesAreTheOwnersThree() = assertEquals(listOf(200, 500, 1000), AlertSettings.RADIUS_CHOICES)

    @Test fun mainDefaultIs500() = assertEquals(500, AlertSettings.DEFAULT_RADIUS_M)

    @Test fun oldValuesSnapToNearest() {
        assertEquals(200, AlertSettings.snap(300))
        assertEquals(500, AlertSettings.snap(400))
        assertEquals(1000, AlertSettings.snap(800))
        assertEquals(1000, AlertSettings.snap(2000))
        assertEquals(200, AlertSettings.snap(50))
    }
}
