package io.github.thisisartman.collectibles.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RailGraphTest {
    // A small copy of the bundled shape: local lines meeting at Miyauchi and Niigata, and a Shinkansen over them
    private val g = RailGraph(
        mapOf(
            "JOETSU" to listOf("yuzawa", "urasa", "koide", "miyauchi"),
            "SHINETSU" to listOf("miyauchi", "nagaoka", "sanjo", "niigata"),
            "ECHIGO" to listOf("kashiwazaki", "yoshida", "niigata"),
            RailGraph.SHINKANSEN to listOf("yuzawa", "urasa", "nagaoka", "niigata"),
        ),
    )

    @Test fun oneLineIsOneLeg() {
        val r = g.route("urasa", "koide")!!
        assertEquals(listOf(Leg("JOETSU", listOf("urasa", "koide"))), r)
    }

    @Test fun shinkansenWinsOnFewerStops() {
        val r = g.route("urasa", "niigata")!!
        assertEquals(listOf(Leg(RailGraph.SHINKANSEN, listOf("urasa", "nagaoka", "niigata"))), r)
    }

    @Test fun localLinesChangeAtTheSharedStation() {
        val r = g.route("urasa", "niigata", avoid = setOf(RailGraph.SHINKANSEN))!!
        assertEquals(listOf("JOETSU", "SHINETSU"), r.map { it.lineId })
        assertEquals("miyauchi", r[0].stations.last())
        assertEquals("miyauchi", r[1].stations.first())
        // The change station is passed once
        assertEquals(listOf("urasa", "koide", "miyauchi", "nagaoka", "sanjo", "niigata"), r.passed().map { it.first })
    }

    @Test fun twoChanges() {
        val r = g.route("koide", "yoshida", avoid = setOf(RailGraph.SHINKANSEN))!!
        assertEquals(listOf("JOETSU", "SHINETSU", "ECHIGO"), r.map { it.lineId })
        assertEquals(listOf("niigata", "yoshida"), r[2].stations)
    }

    @Test fun backwardsAlongALine() {
        assertEquals(listOf("sanjo", "nagaoka", "miyauchi"), g.route("sanjo", "miyauchi")!!.single().stations)
    }

    @Test fun noRoute() {
        assertNull(g.route("urasa", "urasa"))
        assertNull(g.route("urasa", "tokyo"))
        assertNull(RailGraph(mapOf("A" to listOf("a", "b"), "B" to listOf("c", "d"))).route("a", "d"))
    }
}

class TripRulesTest {
    private fun row(id: String, type: ItemType, lat: Double?, lng: Double?, collected: Boolean = false) = ItemRow(
        Item(id = id, type = type, nameJa = id, lat = lat, lng = lng, origin = Origin.CSV_IMPORT, createdAt = 0, updatedAt = 0),
        findCount = if (collected) 1 else 0, lastAt = null, photoUri = null,
    )

    // Two stations about 11 km apart on one line
    private val a = row("a", ItemType.EKI_STAMP, 37.0, 139.0)
    private val b = row("b", ItemType.EKI_STAMP, 37.1, 139.0)
    private val legs = listOf(Leg("L", listOf("a", "b")))

    @Test fun stationStampCountsOnlyWhereYouGetOff() {
        val p = TripRules.plan(legs, listOf(a, b), getOff = setOf("b"))
        assertEquals(listOf("b"), p.countedIds)
        assertEquals(2, p.itemIds.size)
    }

    @Test fun manholeNearAPassedStationCountsWithoutGettingOff() {
        val m = row("m", ItemType.MANHOLE_CARD, 37.0 + 0.015, 139.0) // about 1.7 km north of a
        val p = TripRules.plan(legs, listOf(a, b, m), getOff = emptySet())
        assertEquals(listOf("m"), p.countedIds)
        assertEquals("a", p.stops.first { s -> s.things.any { it.row.item.id == "m" } }.stationId)
    }

    @Test fun beyondTwoKilometresIsNotListed() {
        val far = row("f", ItemType.GOSHUIN, 37.0 + 0.025, 139.03) // about 3.6 km from a, 8.5 km from b
        assertFalse("f" in TripRules.plan(legs, listOf(a, b, far), emptySet()).itemIds)
    }

    @Test fun anotherStationsStampNearbyNeedsGettingOffToo() {
        val other = row("o", ItemType.EKI_STAMP, 37.005, 139.005) // a different station 700 m from a
        assertTrue("o" !in TripRules.plan(legs, listOf(a, b, other), emptySet()).countedIds)
        assertTrue("o" in TripRules.plan(legs, listOf(a, b, other), setOf("a")).countedIds)
    }

    @Test fun collectedIsListedButNotCounted() {
        val m = row("m", ItemType.MANHOLE_CARD, 37.1, 139.001, collected = true)
        val p = TripRules.plan(legs, listOf(a, b, m), setOf("a", "b"))
        assertTrue("m" in p.itemIds)
        assertEquals(listOf("a", "b"), p.countedIds)
        assertEquals(2, p.toCollect)
    }

    @Test fun changeStationSaysTheNextLine() {
        val c = row("c", ItemType.EKI_STAMP, 37.2, 139.0)
        val p = TripRules.plan(listOf(Leg("L", listOf("a", "b")), Leg("M", listOf("b", "c"))), listOf(a, b, c), emptySet())
        assertEquals(listOf("a", "b", "c"), p.stops.map { it.stationId })
        assertEquals("M", p.stops[1].changeTo)
        assertNull(p.stops[0].changeTo)
    }
}

class MapsLinkTest {
    @Test fun documentedApiLink() {
        val t = MapsLink.parse("https://www.google.com/maps/dir/?api=1&origin=Urasa+Station&destination=37.912244%2C139.061329&travelmode=transit")!!
        assertEquals("Urasa Station", t.from!!.name)
        assertEquals(37.912244, t.to!!.lat!!, 1e-9)
        assertEquals(139.061329, t.to!!.lng!!, 1e-9)
        assertEquals("transit", t.mode)
    }

    @Test fun addressBarLinkWithDataPositions() {
        val url = "https://www.google.com/maps/dir/%E6%B5%A6%E4%BD%90%E9%A7%85/Niigata+Station,+Niigata/@37.5,139.0,10z/" +
            "data=!3m1!4b1!4m14!4m13!1m5!1m1!1s0x5ff5:0x1!2m2!1d138.922841!2d37.167224!1m5!1m1!1s0x5ff4:0x2!2m2!1d139.061329!2d37.912244!3e3"
        val t = MapsLink.parse(url)!!
        assertEquals("浦佐駅", t.from!!.name)
        assertEquals(37.167224, t.from!!.lat!!, 1e-9)
        assertEquals(138.922841, t.from!!.lng!!, 1e-9)
        assertEquals("Niigata Station, Niigata", t.to!!.name)
        assertEquals(37.912244, t.to!!.lat!!, 1e-9)
        assertEquals("transit", t.mode)
    }

    @Test fun coordinatesInThePath() {
        val t = MapsLink.parse("https://www.google.co.jp/maps/dir/37.167,138.922/37.912,139.061/")!!
        assertEquals(37.167, t.from!!.lat!!, 1e-9)
        assertEquals(139.061, t.to!!.lng!!, 1e-9)
    }

    @Test fun yourLocationAsStartLeavesItEmpty() {
        val t = MapsLink.parse("https://www.google.com/maps/dir//Nagaoka+Station/@37.4,138.8,12z")!!
        assertNull(t.from)
        assertEquals("Nagaoka Station", t.to!!.name)
    }

    @Test fun consentPageWrapper() {
        val inner = java.net.URLEncoder.encode("https://www.google.com/maps/dir/?api=1&origin=A&destination=B", "UTF-8")
        val t = MapsLink.parse("https://consent.google.com/m?continue=$inner&gl=JP")!!
        assertEquals("A", t.from!!.name)
        assertEquals("B", t.to!!.name)
    }

    @Test fun notADirectionsLink() {
        // A place link is a destination since 2026-11-07 (the owner shares places from Maps): see placeLinkIsADestination
        assertNull(MapsLink.parse("https://example.com/maps/dir/a/b"))
        assertNull(MapsLink.parse("not a link"))
    }

    @Test fun placeLinkIsADestination() {
        val t = MapsLink.parse("https://www.google.com/maps/place/Niigata/@37.9,139.0,12z")!!
        assertNull(t.from)
        assertEquals("Niigata", t.to?.name)
        assertEquals(37.9, t.to!!.lat!!, 1e-9)
    }

    @Test fun shortLinkInSharedText() {
        val u = MapsLink.findUrl("Urasa Station to Niigata Station https://maps.app.goo.gl/AbC123xyz")!!
        assertEquals("https://maps.app.goo.gl/AbC123xyz", u)
        assertTrue(MapsLink.isShort(u))
        assertFalse(MapsLink.isShort("https://www.google.com/maps/dir/a/b"))
    }

    @Test fun stationNamesCompare() {
        assertEquals(MapsLink.stationKey("浦佐駅"), MapsLink.stationKey("浦佐"))
        assertEquals(MapsLink.stationKey("Urasa Station"), MapsLink.stationKey("urasa"))
        assertEquals(MapsLink.stationKey("Echigo-Yuzawa Station, Yuzawa, Niigata"), MapsLink.stationKey("Echigo Yuzawa"))
        assertEquals(MapsLink.stationKey("Tōkyō"), MapsLink.stationKey("Tokyo"))
    }
}
