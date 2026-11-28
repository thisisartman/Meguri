package io.github.thisisartman.collectibles.alerts

import org.junit.Assert.assertEquals
import org.junit.Test

/** Alerts design C: three distances; one saved before (300 m) reads as the nearest. */
class AlertDistanceTest {
    @Test fun choicesAreTheOwnersThree() = assertEquals(listOf(200, 500, 1000), AlertSettings.RADIUS_CHOICES)

    @Test fun mainDefaultIs500() = assertEquals(500, AlertSettings.DEFAULT_RADIUS_M)

    @Test fun stationsStartAt200OthersUseTheMain() {
        assertEquals(200, AlertSettings.resolveOwn(null, io.github.thisisartman.collectibles.data.ItemType.EKI_STAMP))
        assertEquals(null, AlertSettings.resolveOwn(null, io.github.thisisartman.collectibles.data.ItemType.MANHOLE_CARD))
    }

    @Test fun sameOverridesTheStartingDistance() {
        assertEquals(null, AlertSettings.resolveOwn(AlertSettings.SAME, io.github.thisisartman.collectibles.data.ItemType.EKI_STAMP))
        assertEquals(1000, AlertSettings.resolveOwn(1000, io.github.thisisartman.collectibles.data.ItemType.EKI_STAMP))
        assertEquals(200, AlertSettings.resolveOwn(300, io.github.thisisartman.collectibles.data.ItemType.DAM_CARD))
    }

    @Test fun oldValuesSnapToNearest() {
        assertEquals(200, AlertSettings.snap(300))
        assertEquals(500, AlertSettings.snap(400))
        assertEquals(1000, AlertSettings.snap(800))
        assertEquals(1000, AlertSettings.snap(2000))
        assertEquals(200, AlertSettings.snap(50))
    }

    // #164: one list, each category with its distance and the default marked
    private val order = listOf(
        io.github.thisisartman.collectibles.data.ItemType.EKI_STAMP,
        io.github.thisisartman.collectibles.data.ItemType.MANHOLE_CARD,
        io.github.thisisartman.collectibles.data.ItemType.DAM_CARD,
        io.github.thisisartman.collectibles.data.ItemType.GOSHUIN,
    )
    private val own = mapOf(io.github.thisisartman.collectibles.data.ItemType.EKI_STAMP to 200)

    @Test fun everyCategoryOnListedWithDefaultMarked() {
        val on = order.toSet()
        val got = AlertSettings.categoryDistances(order, on, null, 500) { own[it] }
        assertEquals(
            listOf(
                CategoryDistance(io.github.thisisartman.collectibles.data.ItemType.EKI_STAMP, 200, isDefault = false),
                CategoryDistance(io.github.thisisartman.collectibles.data.ItemType.MANHOLE_CARD, 500, isDefault = true),
                CategoryDistance(io.github.thisisartman.collectibles.data.ItemType.DAM_CARD, 500, isDefault = true),
                CategoryDistance(io.github.thisisartman.collectibles.data.ItemType.GOSHUIN, 500, isDefault = true),
            ),
            got,
        )
    }

    @Test fun switchedOffAndAbsentCategoriesAreLeftOut() {
        val on = order.toSet() - io.github.thisisartman.collectibles.data.ItemType.DAM_CARD
        val present = order.toSet() - io.github.thisisartman.collectibles.data.ItemType.GOSHUIN
        val got = AlertSettings.categoryDistances(order, on, present, 1000) { own[it] }
        assertEquals(
            listOf(io.github.thisisartman.collectibles.data.ItemType.EKI_STAMP, io.github.thisisartman.collectibles.data.ItemType.MANHOLE_CARD),
            got.map { it.type },
        )
        assertEquals(listOf(200, 1000), got.map { it.meters })
    }

    @Test fun customEqualToDefaultIsStillCustom() {
        val got = AlertSettings.categoryDistances(order.take(1), order.toSet(), null, 200) { own[it] }
        assertEquals(false, got.single().isDefault)
    }
}
