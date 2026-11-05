package io.github.thisisartman.collectibles.alerts

import io.github.thisisartman.collectibles.data.ItemType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertRulesTest {
    // Yoshida Station, and points north of it (0.001 degree of latitude is about 111 m)
    private val here = 37.687060 to 138.878695
    private fun north(m: Double) = here.first + m / 111_195.0
    private val prefs = AlertPrefs(
        radiusM = 300, types = setOf(ItemType.MANHOLE_CARD, ItemType.GOSHUIN, ItemType.EKI_STAMP),
        quietOn = true, quietFromHour = 22, quietToHour = 7, cooldownMs = 12 * HOUR,
    )
    private val noon = 12
    private fun c(id: String, m: Double, type: ItemType = ItemType.EKI_STAMP) = Candidate(id, type, north(m), here.second)

    @Test fun insideTheRadiusAlertsNearestFirst() {
        val due = AlertRules.due(here.first, here.second, listOf(c("far", 280.0), c("near", 50.0), c("out", 320.0)), prefs, emptyMap(), 0L, noon)
        assertEquals(listOf("near", "far"), due.map { it.first.id })
    }

    @Test fun onceAVisit() {
        val now = 100 * HOUR
        val recent = mapOf("near" to now - 2 * HOUR)
        assertTrue(AlertRules.due(here.first, here.second, listOf(c("near", 50.0)), prefs, recent, now, noon).isEmpty())
        val old = mapOf("near" to now - 13 * HOUR)
        assertEquals(1, AlertRules.due(here.first, here.second, listOf(c("near", 50.0)), prefs, old, now, noon).size)
    }

    @Test fun typesSwitchedOffStayQuiet() {
        val onlyCards = prefs.copy(types = setOf(ItemType.MANHOLE_CARD))
        val due = AlertRules.due(here.first, here.second, listOf(c("stamp", 50.0), c("card", 60.0, ItemType.MANHOLE_CARD)), onlyCards, emptyMap(), 0L, noon)
        assertEquals(listOf("card"), due.map { it.first.id })
    }

    @Test fun quietHoursAcrossMidnight() {
        assertTrue(AlertRules.inQuietHours(23, 22, 7))
        assertTrue(AlertRules.inQuietHours(3, 22, 7))
        assertFalse(AlertRules.inQuietHours(7, 22, 7))
        assertFalse(AlertRules.inQuietHours(12, 22, 7))
        assertTrue(AlertRules.inQuietHours(13, 12, 14))
        assertFalse(AlertRules.inQuietHours(14, 12, 14))
        assertFalse(AlertRules.inQuietHours(5, 6, 6))
        assertTrue(AlertRules.due(here.first, here.second, listOf(c("near", 50.0)), prefs, emptyMap(), 0L, 23).isEmpty())
        assertEquals(1, AlertRules.due(here.first, here.second, listOf(c("near", 50.0)), prefs.copy(quietOn = false), emptyMap(), 0L, 23).size)
    }

    @Test fun checksMoreOftenWhenCloseOrFast() {
        val walkingFar = AlertRules.nextIntervalMs(5_000.0, 1.0f)
        // 20 m from the edge on foot: about 14 s away, so the shortest wait
        val walkingClose = AlertRules.nextIntervalMs(20.0, 1.0f)
        // 200 m away on foot: about 143 s to the edge, so check after about half of that
        assertEquals(71_428L, AlertRules.nextIntervalMs(200.0, 1.0f))
        val trainFar = AlertRules.nextIntervalMs(5_000.0, 25f)
        assertEquals(AlertRules.MAX_INTERVAL_MS, walkingFar)
        assertEquals(AlertRules.MIN_INTERVAL_MS, walkingClose)
        assertTrue(trainFar < walkingFar)
        assertTrue(trainFar >= AlertRules.MIN_INTERVAL_MS)
        assertEquals(AlertRules.MAX_INTERVAL_MS, AlertRules.nextIntervalMs(null, null))
    }

    @Test fun speedFromTwoFixesWhenThePhoneGivesNone() {
        // 1 km in 40 s is 25 m/s, a local train
        val v = AlertRules.speedMps(null, north(0.0), here.second, 0L, north(1_000.0), here.second, 40_000L)!!
        assertEquals(25f, v, 0.1f)
        assertEquals(3f, AlertRules.speedMps(3f, north(0.0), here.second, 0L, north(1_000.0), here.second, 40_000L)!!, 0f)
        assertEquals(null, AlertRules.speedMps(null, null, null, null, north(0.0), here.second, 40_000L))
        // Two fixes a second apart say nothing reliable
        assertEquals(null, AlertRules.speedMps(null, north(0.0), here.second, 0L, north(50.0), here.second, 1_000L))
    }

    private companion object { const val HOUR = 3_600_000L }
}
