package io.github.thisisartman.collectibles.ui

import io.github.thisisartman.collectibles.data.Item
import io.github.thisisartman.collectibles.data.ItemRow
import io.github.thisisartman.collectibles.data.ItemType
import io.github.thisisartman.collectibles.data.Origin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** #192 (owner approved round 3, 2026-11-26): how Nearby's Places view groups what is near you into places. */
class PlacesTest {
    private fun row(
        id: String, type: ItemType, lat: Double?, lng: Double?, found: Int = 0,
        nameJa: String = id, romaji: String? = null, placeName: String? = null, noneHere: Boolean = false,
    ) = ItemRow(
        Item(
            id = id, type = type, nameJa = nameJa, nameRomaji = romaji, placeName = placeName, origin = Origin.CSV_IMPORT,
            lat = lat, lng = lng, createdAt = 1, updatedAt = 1, noneHereAt = if (noneHere) 1L else null,
        ),
        findCount = found, lastAt = null, lastBeforeApp = null, photoUri = null,
    )

    // About 1 m of latitude in degrees, to place things a known distance north of Urasa
    private val m = 1.0 / 111_195.0
    private val uLat = 37.16695
    private val uLng = 138.92145

    private val urasa = listOf(
        row("urasa-stamp", ItemType.EKI_STAMP, uLat, uLng, found = 1, nameJa = "浦佐駅", romaji = "Urasa Station"),
        row("urasa-tag", ItemType.EKITAG, uLat, uLng),
        row("card-50", ItemType.MANHOLE_CARD, uLat + 50 * m, uLng),
    )

    private fun ids(places: List<Place>) = places.map { p -> p.things.map { it.item.id } }.toSet()

    @Test fun aStationsStampAndEkitagAreOnePlace() {
        val p = Places.group(urasa.take(2))
        assertEquals(setOf(listOf("urasa-stamp", "urasa-tag")), ids(p))
        assertEquals(PlaceKind.STATION, p.single().kind)
    }

    @Test fun aCardWithin60mJoinsTheStationAndOneFurtherDoesNot() {
        val far = row("card-70", ItemType.MANHOLE_CARD, uLat - 70 * m, uLng)
        assertEquals(setOf(listOf("urasa-stamp", "urasa-tag", "card-50"), listOf("card-70")), ids(Places.group(urasa + far)))
    }

    @Test fun twoCardsAtOneOfficeAreOnePlace() {
        val a = row("nagaoka-a", ItemType.MANHOLE_CARD, 37.44, 138.85)
        val c = row("nagaoka-c", ItemType.MANHOLE_CARD, 37.44 + 5 * m, 138.85)
        val p = Places.group(listOf(c, a))
        assertEquals(setOf(listOf("nagaoka-a", "nagaoka-c")), ids(p))
        assertEquals(PlaceKind.CITY, p.single().kind)
    }

    @Test fun twoStationsCloseTogetherStayTwoPlaces() {
        val a = row("a", ItemType.EKI_STAMP, uLat, uLng)
        val b = row("b", ItemType.EKI_STAMP, uLat + 30 * m, uLng)
        assertEquals(2, Places.group(listOf(a, b)).size)
    }

    @Test fun aThingWithNoPositionIsItsOwnPlace() {
        val lost = row("goshuin", ItemType.GOSHUIN, null, null)
        val lostToo = row("goshuin-2", ItemType.GOSHUIN, null, null)
        assertEquals(setOf(listOf("goshuin"), listOf("goshuin-2")), ids(Places.group(listOf(lost, lostToo))))
    }

    @Test fun theResultDoesNotDependOnTheOrderOfTheList() {
        val all = urasa + listOf(
            row("card-70", ItemType.MANHOLE_CARD, uLat - 70 * m, uLng),
            row("dam", ItemType.DAM_CARD, uLat - 100 * m, uLng),
            row("shrine", ItemType.GOSHUIN, 37.2, 138.9),
        )
        val expected = ids(Places.group(all))
        assertEquals(expected, ids(Places.group(all.reversed())))
        assertEquals(expected, ids(Places.group(all.shuffled(java.util.Random(7)))))
    }

    @Test fun aStampComesFirstThenItsEkitagThenTheRest() {
        // The card is listed first, and still goes after the stamp and the Ekitag on the ring
        val p = Places.group(listOf(urasa[2], urasa[1], urasa[0])).single()
        assertEquals(listOf("urasa-stamp", "urasa-tag", "card-50"), p.things.map { it.item.id })
        assertEquals(1, p.collected)
        assertTrue(!p.complete)
    }

    @Test fun kindsGoByWhatIsThere() {
        assertEquals(PlaceKind.STATION, Places.kindOf(listOf(ItemType.MANHOLE_CARD, ItemType.EKITAG)))
        assertEquals(PlaceKind.ROADSIDE, Places.kindOf(listOf(ItemType.MANHOLE_CARD, ItemType.ROADSIDE_STATION)))
        assertEquals(PlaceKind.SHRINE, Places.kindOf(listOf(ItemType.GOSHUIN)))
        assertEquals(PlaceKind.DAM, Places.kindOf(listOf(ItemType.DAM_CARD, ItemType.MANHOLE_CARD)))
        assertEquals(PlaceKind.CITY, Places.kindOf(listOf(ItemType.MANHOLE_CARD, ItemType.MANHOLE_CARD)))
        // A manhole card with something else that is not a kind of its own
        assertEquals(PlaceKind.OTHER, Places.kindOf(listOf(ItemType.MANHOLE_CARD, ItemType.MUSEUM_STAMP)))
        assertEquals(PlaceKind.OTHER, Places.kindOf(listOf(ItemType.CUSTOM)))
    }

    @Test fun aPlaceIsNamedForItsStationOrWhereItIsHandedOut() {
        val station = Places.group(urasa).single()
        assertEquals("Urasa", Places.name(station, japanese = false))
        assertEquals("浦佐駅", Places.name(station, japanese = true))
        val card = Place(listOf(row("tsubame", ItemType.MANHOLE_CARD, 37.6, 138.9, nameJa = "燕市 合併20周年", romaji = "Tsubame · 20 years as one city", placeName = "燕市役所 2階 下水道課 20番窓口（土日祝は夜間休日受付）")))
        assertEquals("燕市役所", Places.name(card, japanese = true))
        // The place names are Japanese only: in English the card's own name
        assertEquals("Tsubame · 20 years as one city", Places.name(card, japanese = false))
        val noPlace = Place(listOf(row("dam", ItemType.DAM_CARD, 37.0, 139.0, nameJa = "奥只見ダム")))
        assertEquals("奥只見ダム", Places.name(noPlace, japanese = true))
        assertEquals("新潟県下水道公社", Places.shortPlace("新潟県下水道公社（新潟浄化センター管理棟 2階事務室）"))
    }

    @Test fun nearestFirstWithNothingHereLastAndGroupsInHeadingOrder() {
        val near = Place(listOf(row("dam", ItemType.DAM_CARD, uLat, uLng)))
        val mid = Place(listOf(row("shrine", ItemType.GOSHUIN, uLat + 1000 * m, uLng)))
        val none = Place(listOf(row("none", ItemType.EKI_STAMP, uLat, uLng, noneHere = true)))
        val far = Place(listOf(row("station", ItemType.EKI_STAMP, uLat + 5000 * m, uLng)))
        val sorted = Places.sorted(listOf(none, far, mid, near), byName = false, japanese = false) { Places.nearestM(it, uLat, uLng) }
        assertEquals(listOf("dam", "shrine", "station", "none"), sorted.map { it.id })
        assertEquals(listOf(PlaceKind.STATION, PlaceKind.SHRINE, PlaceKind.DAM), Places.byKind(sorted).map { it.first })
        assertEquals(listOf("station", "none"), Places.byKind(sorted).first().second.map { it.id })
    }

    @Test fun aPlacesDistanceIsItsNearestThing() {
        val p = Places.group(urasa).single()
        assertEquals(0.0, Places.nearestM(p, uLat, uLng)!!, 0.5)
        assertEquals(50.0, Places.nearestM(p, uLat + 100 * m, uLng)!!, 0.5)
    }

    // One page per place, no inner pages (owner, 2026-11-26): every thing opens the page of the place it is at

    @Test fun anyThingAtAStationFindsTheStationsPlace() {
        val rows = urasa + row("card-70", ItemType.MANHOLE_CARD, uLat - 70 * m, uLng)
        for (id in listOf("urasa-stamp", "urasa-tag", "card-50")) {
            assertEquals(listOf("urasa-stamp", "urasa-tag", "card-50"), Places.placeOf(rows, id)!!.things.map { it.item.id })
        }
    }

    @Test fun aLoneThingIsAPlaceOfOneEvenWithNoPositionOrYourOwn() {
        val own = row("mine", ItemType.CUSTOM, null, null).let { it.copy(item = it.item.copy(origin = Origin.USER)) }
        val far = row("card-70", ItemType.MANHOLE_CARD, uLat - 70 * m, uLng)
        val rows = urasa + own + far
        assertEquals(listOf("mine"), Places.placeOf(rows, "mine")!!.things.map { it.item.id })
        assertEquals(listOf("card-70"), Places.placeOf(rows, "card-70")!!.things.map { it.item.id })
    }

    @Test fun anEkitagWithoutItsStampIsItsOwnPlace() {
        assertEquals(listOf("urasa-tag"), Places.placeOf(listOf(urasa[1]), "urasa-tag")!!.things.map { it.item.id })
    }

    @Test fun anUnknownIdHasNoPlaceAndTheNextIdIsTried() {
        assertEquals(null, Places.placeOf(urasa, "gone"))
        assertEquals("urasa-stamp", Places.placeOf(urasa, listOf("gone", "card-50"))!!.id)
    }

    @Test fun aPlacesTileOpensTheFirstThingStillToGet() {
        val p = Places.group(urasa).single()
        assertEquals("urasa-tag", Places.toOpen(p))
        val done = Places.group(urasa.map { it.copy(findCount = 1) }).single()
        assertEquals("urasa-stamp", Places.toOpen(done))
        val none = Places.group(listOf(urasa[0], row("urasa-tag", ItemType.EKITAG, uLat, uLng, noneHere = true))).single()
        assertEquals("urasa-stamp", Places.toOpen(none))
    }
}
