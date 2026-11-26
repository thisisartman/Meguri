package io.github.thisisartman.collectibles.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** #203: the cover position printed on a manhole card, as the text reader gets it. */
class CoverCoordsTest {
    private fun assertAt(lat: Double, lng: Double, text: String) {
        val p = CoverCoords.parse(text)
        assertNotNull("read from: $text", p)
        assertEquals("lat from: $text", lat, p!!.lat, 1e-5)
        assertEquals("lng from: $text", lng, p.lng, 1e-5)
    }

    // 37°26'44.5" = 37 + 26/60 + 44.5/3600
    private val lat = 37.0 + 26 / 60.0 + 44.5 / 3600
    private val lng = 138.0 + 51 / 60.0 + 36.2 / 3600

    @Test fun lettersBefore() = assertAt(lat, lng, "N 37°26'44.5\" E 138°51'36.2\"")
    @Test fun lettersAfter() = assertAt(lat, lng, "37°26'44.5\"N 138°51'36.2\"E")
    @Test fun lettersAfterNoSpace() = assertAt(lat, lng, "37°26'44.5\"N138°51'36.2\"E")
    @Test fun japanese() = assertAt(lat, lng, "北緯37°26'44.5\" 東経138°51'36.2\"")
    @Test fun japaneseUnits() = assertAt(lat, lng, "北緯37度26分44.5秒 東経138度51分36.2秒")
    @Test fun fullWidth() = assertAt(lat, lng, "Ｎ３７°２６’４４.５” Ｅ１３８°５１’３６.２”")
    @Test fun decimal() = assertAt(37.44569, 138.86007, "37.44569, 138.86007")
    @Test fun decimalWithLetters() = assertAt(37.44569, 138.86007, "N37.44569 E138.86007")
    @Test fun lngFirstByLetters() = assertAt(lat, lng, "E 138°51'36.2\" N 37°26'44.5\"")

    // As the site lists 15-206-A001 (Shibata), code line and all
    @Test fun wholeCardText() = assertAt(
        37.9468611, 139.3314444,
        "新潟県 新発田市\n15-206-A001\n(378-20-12-1)\n37°56'48.7\"N 139°19'53.2\"E\n花 158 木 118",
    )

    @Test fun degreeReadAsO() = assertAt(lat, lng, "N 37o26'44.5\" E 138º51'36.2\"")
    @Test fun zeroReadAsO() = assertAt(37.0 + 6 / 60.0 + 4.5 / 3600, lng, "N 37°O6'O4.5\" E 138°51'36.2\"")
    @Test fun oneReadAsL() = assertAt(lat, lng, "N 37°26'44.5\" E l38°5l'36.2\"")
    @Test fun curlyAndDoubledQuotes() = assertAt(lat, lng, "N 37°26’44.5'' E 138°51′36.2″")
    @Test fun symbolsMissing() = assertAt(lat, lng, "N37 26 44.5 E138 51 36.2")
    @Test fun lettersMissing() = assertAt(lat, lng, "37°26'44.5\" 138°51'36.2\"")
    @Test fun lettersMissingLngFirst() = assertAt(lat, lng, "138°51'36.2\" 37°26'44.5\"")

    @Test fun outsideJapanIsAMisread() {
        assertNull(CoverCoords.parse("N 47°26'44.5\" E 138°51'36.2\""))
        assertNull(CoverCoords.parse("N 37°26'44.5\" E 168°51'36.2\""))
        assertNull(CoverCoords.parse("S 37°26'44.5\" E 138°51'36.2\""))
        assertNull(CoverCoords.parse("51.50735, -0.12776"))
    }

    @Test fun minutesOrSecondsOver60AreAMisread() {
        assertNull(CoverCoords.parse("N 37°66'44.5\" E 138°51'36.2\""))
        assertNull(CoverCoords.parse("N 37°26'74.5\" E 138°51'36.2\""))
    }

    @Test fun twoLatitudesAreNotAPosition() = assertNull(CoverCoords.parse("N 37°26'44.5\" N 38°51'36.2\""))

    @Test fun cardTextWithoutCoordinates() {
        assertNull(CoverCoords.parse("15-206-A001 (378-20-12-1)"))
        assertNull(CoverCoords.parse("2024.10.01 第8弾"))
        assertNull(CoverCoords.parse(""))
    }

    @Test fun typed() {
        assertEquals(CoverCoords.LatLng(37.4, 138.8), CoverCoords.parseTyped("37.4, 138.8"))
        assertEquals(CoverCoords.LatLng(37.4, 138.8), CoverCoords.parseTyped("138.8 37.4"))
        assertAt(lat, lng, "37°26'44.5\"N 138°51'36.2\"E")
        assertNull(CoverCoords.parseTyped("hello"))
        assertNull(CoverCoords.parseTyped("10, 20"))
    }
}
