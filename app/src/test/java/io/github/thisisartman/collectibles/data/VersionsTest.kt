package io.github.thisisartman.collectibles.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionsTest {
    @Test
    fun parsesPlainAndPrefixed() {
        assertEquals(listOf(0, 6, 0), Versions.parse("0.6.0"))
        assertEquals(listOf(0, 6, 0), Versions.parse("v0.6.0"))
        assertEquals(listOf(1, 2, 3), Versions.parse("V1.2.3"))
    }

    @Test
    fun dropsSuffixes() {
        assertEquals(listOf(0, 6, 0), Versions.parse("0.6.0-dev"))
        assertEquals(listOf(0, 6, 0), Versions.parse("0.6.0-devrelease"))
        assertEquals(listOf(0, 7, 0), Versions.parse("v0.7.0-beta.9"))
    }

    @Test
    fun newerByEachPart() {
        assertTrue(Versions.isNewer("v0.7.0", "0.6.0"))
        assertTrue(Versions.isNewer("v0.6.1", "0.6.0"))
        assertTrue(Versions.isNewer("v1.0.0", "0.9.9"))
        assertFalse(Versions.isNewer("v0.5.9", "0.6.0"))
    }

    @Test
    fun comparesAsNumbersNotText() {
        assertTrue(Versions.isNewer("v0.10.0", "0.9.0"))
        assertFalse(Versions.isNewer("v0.9.0", "0.10.0"))
    }

    @Test
    fun equalIsNotNewer() {
        assertFalse(Versions.isNewer("v0.6.0", "0.6.0"))
        assertFalse(Versions.isNewer("0.6.0", "0.6.0"))
    }

    @Test
    fun installedSuffixesAreIgnored() {
        assertFalse(Versions.isNewer("v0.6.0", "0.6.0-dev"))
        assertFalse(Versions.isNewer("v0.6.0", "0.6.0-devrelease"))
        assertTrue(Versions.isNewer("v0.7.0", "0.6.0-devrelease"))
        assertFalse(Versions.isNewer("v0.7.0-beta.9", "0.7.0"))
    }

    @Test
    fun malformedIsNotNewer() {
        listOf("", "v", "latest", "1.2", "1.2.3.4", "1.x.0", "v.1.2", "1..2", " ", "99999999.0.0").forEach {
            assertNull(it, Versions.parse(it))
            assertFalse(it, Versions.isNewer(it, "0.6.0"))
        }
        assertFalse(Versions.isNewer("v9.0.0", "garbage"))
    }
}
