package io.github.thisisartman.collectibles.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The owner's real links from the Niigata trip plan (2026-11-05 to 07) and one shared place link. */
class OwnerLinksTest {
    private fun trip(url: String) = MapsLink.parse(url)!!

    @Test fun directionsBetweenStations() {
        val t = trip("https://www.google.com/maps/dir/?api=1&origin=Urasa+Station&destination=Nagaoka+Station+Niigata&travelmode=transit")
        assertEquals("Urasa Station", t.from?.name)
        assertEquals("Nagaoka Station Niigata", t.to?.name)
        assertEquals("transit", t.mode)
    }

    @Test fun stationNamesWithTheirTownMatchTheCatalogue() {
        // Google names carry the town after the station; the station part has to match the catalogue's "Yoshida Station"
        val t = trip("https://www.google.com/maps/dir/?api=1&origin=Nagaoka+Station+Niigata&destination=Yoshida+Station+Tsubame&travelmode=transit")
        assertTrue(MapsLink.stationKey(t.from!!.name!!).startsWith(MapsLink.stationKey("Nagaoka Station")))
        assertTrue(MapsLink.stationKey(t.to!!.name!!).startsWith(MapsLink.stationKey("Yoshida Station")))
        assertTrue(MapsLink.stationKey("Hakusan Station Niigata").startsWith("hakusan"))
    }

    @Test fun walkingAndPlaces() {
        val t = trip("https://www.google.com/maps/dir/?api=1&origin=Nagaoka+Station+Niigata&destination=Mirai-e+Nagaoka&travelmode=walking")
        assertEquals("Mirai-e Nagaoka", t.to?.name)
        assertEquals("walking", t.mode)
        val h = trip("https://www.google.com/maps/dir/?api=1&origin=Hotel+Tsubame+Hills+Yoshida+Hinodecho+30-15+Tsubame&destination=Tsubame+City+Hall&travelmode=walking")
        assertEquals("Hotel Tsubame Hills Yoshida Hinodecho 30-15 Tsubame", h.from?.name)
    }

    @Test fun placeLinkFromAShortLinkIsADestination() {
        // What maps.app.goo.gl/ZUvDMqnEjC3KM4G17 expands to (checked 2026-11-07): a name, no position
        val t = trip("https://www.google.com/maps/place/Yahiko+Shrine,+Yahiko,+Niigata/data=!4m2!3m1!1s0x5ff4ef6ee5f883d5:0x65920da6ba5f943d!18m1!1e1?utm_source=mstt_1&entry=gps")
        assertNull(t.from)
        assertEquals("Yahiko Shrine, Yahiko, Niigata", t.to?.name)
        assertNull(t.to?.lat)
    }

    @Test fun placeLinkWithAPosition() {
        val t = trip("https://www.google.com/maps/place/Yahiko+Station/@37.6990,138.8318,17z/data=!3m1!4b1!4m6!3m5!1s0x0:0x0!8m2!3d37.699256!4d138.831953")
        assertEquals(37.699256, t.to!!.lat!!, 1e-6)
        assertEquals(138.831953, t.to!!.lng!!, 1e-6)
    }

    @Test fun shortLinksAreRecognised() {
        assertTrue(MapsLink.isShort("https://maps.app.goo.gl/ZUvDMqnEjC3KM4G17?g_st=ac"))
    }

    @Test fun matchesTheStationNotItsNeighbour() {
        val stations = listOf(
            "nagaoka" to listOf("長岡駅", "Nagaoka Station"),
            "kita" to listOf("北長岡駅", "Kita-Nagaoka Station"),
            "yoshida" to listOf("吉田駅", "Yoshida Station"),
            "minami" to listOf("南吉田駅", "Minami-Yoshida Station"),
            "niigata" to listOf("新潟駅", "Niigata Station"),
            "daigaku" to listOf("新潟大学前駅", "Niigata Daigaku-mae Station"),
        )
        assertEquals("nagaoka", MapsLink.matchStation("Nagaoka Station Niigata", stations))
        assertEquals("yoshida", MapsLink.matchStation("Yoshida Station Tsubame", stations))
        assertEquals("niigata", MapsLink.matchStation("Niigata Station", stations))
        assertEquals("minami", MapsLink.matchStation("南吉田駅", stations))
        assertNull(MapsLink.matchStation("Mirai-e Nagaoka", stations))
    }
}
