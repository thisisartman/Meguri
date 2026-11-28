package io.github.thisisartman.collectibles.alerts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NudgeGroupTest {
    // Urasa station, and a spot about 1 km away
    private val lat = 37.1650
    private val lng = 138.9230
    private val farLat = 37.1740

    @Test fun `collects within a minute at one place make one suggestion`() {
        val a = NudgeGroup.add(null, "Urasa", "f1", lat, lng, atMs = 0)
        val b = NudgeGroup.add(a, "Urasa", "f2", lat + 0.0003, lng, atMs = 40_000)
        assertEquals(listOf("f1", "f2"), b.findIds)
        // The stamp and the Ekitag share a name: said once
        assertEquals(listOf("Urasa"), b.names)
        assertEquals("f2", b.findId)
    }

    @Test fun `a minute is counted from the last collect`() {
        val a = NudgeGroup.add(null, "A", "f1", lat, lng, atMs = 0)
        val b = NudgeGroup.add(a, "B", "f2", lat, lng, atMs = 50_000)
        val c = NudgeGroup.add(b, "C", "f3", lat, lng, atMs = 100_000)
        assertEquals(3, c.findIds.size)
        val d = NudgeGroup.add(c, "D", "f4", lat, lng, atMs = 100_000 + 60_001)
        assertEquals(listOf("f4"), d.findIds)
    }

    @Test fun `another place starts a new suggestion`() {
        val a = NudgeGroup.add(null, "Urasa", "f1", lat, lng, atMs = 0)
        val b = NudgeGroup.add(a, "Muikamachi", "f2", farLat, lng, atMs = 10_000)
        assertEquals(listOf("f2"), b.findIds)
    }

    @Test fun `finds with no position group together, but not with a placed one`() {
        val a = NudgeGroup.add(null, "Added 1", "f1", null, null, atMs = 0)
        val b = NudgeGroup.add(a, "Added 2", "f2", null, null, atMs = 1_000)
        assertEquals(2, b.findIds.size)
        val c = NudgeGroup.add(b, "Urasa", "f3", lat, lng, atMs = 2_000)
        assertEquals(listOf("f3"), c.findIds)
    }

    @Test fun `undo takes the find out, and the last one ends the suggestion`() {
        val a = NudgeGroup.add(NudgeGroup.add(null, "A", "f1", lat, lng, 0), "B", "f2", lat, lng, 1_000)
        val b = NudgeGroup.remove(a, "f2")!!
        assertEquals(listOf("f1"), b.findIds)
        assertEquals(listOf("A"), b.names)
        assertNull(NudgeGroup.remove(b, "f1"))
        assertEquals(b, NudgeGroup.remove(b, "other"))
    }
}
