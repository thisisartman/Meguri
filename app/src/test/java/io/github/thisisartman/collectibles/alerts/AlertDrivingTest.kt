package io.github.thisisartman.collectibles.alerts

import io.github.thisisartman.collectibles.data.ItemType
import io.github.thisisartman.collectibles.location.coarse
import io.github.thisisartman.collectibles.location.coarseText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** #218: places you drive to alert from 1 km, Real time location updates, and rounded positions in logs. */
class AlertDrivingTest {
    @Test fun driveToCategoriesStartAt1km() {
        for (t in listOf(ItemType.ROADSIDE_STATION, ItemType.MANHOLE_CARD, ItemType.DAM_CARD)) {
            assertEquals(t.name, 1000, AlertSettings.resolveOwn(null, t))
        }
    }

    @Test fun otherCategoriesKeepTheirDistance() {
        assertEquals(200, AlertSettings.resolveOwn(null, ItemType.EKI_STAMP))
        for (t in listOf(ItemType.GOSHUIN, ItemType.MUSEUM_STAMP, ItemType.EKITAG, ItemType.CUSTOM, ItemType.PLACE)) {
            assertNull(t.name, AlertSettings.resolveOwn(null, t))
        }
    }

    @Test fun aDistanceTheUserChoseWins() {
        assertEquals(200, AlertSettings.resolveOwn(200, ItemType.ROADSIDE_STATION))
        assertEquals(500, AlertSettings.resolveOwn(500, ItemType.MANHOLE_CARD))
        // "Same" goes back to the main distance
        assertNull(AlertSettings.resolveOwn(AlertSettings.SAME, ItemType.DAM_CARD))
    }

    @Test fun aRoadsideStation900mAwayAlertsAGoshuinDoesNot() {
        val types = setOf(ItemType.ROADSIDE_STATION, ItemType.GOSHUIN)
        val prefs = AlertPrefs(
            500, types, quietOn = false, quietFromHour = 22, quietToHour = 7, cooldownMs = 1000L,
            radii = ItemType.entries.associateWith { AlertSettings.resolveOwn(null, it) ?: 500 },
        )
        // 0.0081 degrees of latitude is about 900 m
        val cands = listOf(
            Candidate("road", ItemType.ROADSIDE_STATION, 37.0081, 138.0),
            Candidate("shrine", ItemType.GOSHUIN, 37.0081, 138.0),
        )
        val due = AlertRules.due(37.0, 138.0, cands, prefs, emptyMap(), 0L, 12)
        assertEquals(listOf("road"), due.map { it.first.id })
    }

    @Test fun batterySaverKeepsTheOldIntervals() {
        assertEquals(AlertRules.MAX_INTERVAL_MS, AlertRules.nextIntervalMs(null, null, LocationMode.BATTERY))
        assertEquals(AlertRules.MIN_INTERVAL_MS, AlertRules.nextIntervalMs(0.0, 30f, LocationMode.BATTERY))
        assertEquals(71_428L, AlertRules.nextIntervalMs(200.0, 1.0f, LocationMode.BATTERY))
    }

    @Test fun realTimeIsEvery5To10Seconds() {
        assertEquals(10_000L, AlertRules.nextIntervalMs(null, null, LocationMode.REAL_TIME))
        assertEquals(5_000L, AlertRules.nextIntervalMs(0.0, 20f, LocationMode.REAL_TIME))
        // Far away on foot would be minutes; Real time still checks every 10 s
        assertEquals(10_000L, AlertRules.nextIntervalMs(5_000.0, 1.0f, LocationMode.REAL_TIME))
        // 160 m to the edge at 20 m/s (72 km/h): halfway is 4 s, kept at 5 s
        assertEquals(5_000L, AlertRules.nextIntervalMs(160.0, 20f, LocationMode.REAL_TIME))
    }

    @Test fun oneProviderPerMode() {
        val all = setOf("gps", "network", "fused")
        assertEquals("gps", AlertRules.provider(LocationMode.REAL_TIME, fine = true, enabled = all))
        // Battery saver keeps GPS too (as 0.7.1), it just asks less often
        assertEquals("gps", AlertRules.provider(LocationMode.BATTERY, fine = true, enabled = all))
        // Approximate location only: never GPS
        assertEquals("fused", AlertRules.provider(LocationMode.REAL_TIME, fine = false, enabled = all))
        // Before Android 12 there is no fused provider
        assertEquals("network", AlertRules.provider(LocationMode.BATTERY, fine = false, enabled = setOf("gps", "network")))
        assertEquals("fused", AlertRules.provider(LocationMode.BATTERY, fine = true, enabled = setOf("fused", "network")))
        assertEquals("gps", AlertRules.provider(LocationMode.BATTERY, fine = true, enabled = setOf("gps")))
        assertNull(AlertRules.provider(LocationMode.BATTERY, fine = false, enabled = setOf("gps")))
    }

    @Test fun driveToFencesAnswerFaster() {
        assertEquals(AlertRules.FAST_RESPONSIVENESS_MS, AlertRules.responsivenessMs(ItemType.MANHOLE_CARD))
        assertEquals(AlertRules.FAST_RESPONSIVENESS_MS, AlertRules.responsivenessMs(ItemType.ROADSIDE_STATION))
        assertEquals(AlertRules.RESPONSIVENESS_MS, AlertRules.responsivenessMs(ItemType.EKI_STAMP))
    }

    @Test fun positionsRoundToAbout1km() {
        assertEquals(37.67, coarse(37.670349), 1e-9)
        assertEquals(138.88, coarse(138.879449), 1e-9)
        assertEquals(-0.01, coarse(-0.0074), 1e-9)
        assertEquals("37.67, 138.88", coarseText(37.670349, 138.879449))
        assertTrue(coarseText(37.670349, 138.879449).count { it == '.' } == 2)
    }
}
