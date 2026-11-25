package io.github.thisisartman.collectibles.ui

import io.github.thisisartman.collectibles.data.ItemType
import org.junit.Assert.assertEquals
import org.junit.Test

/** #136: what the map frames first. */
class MapFramingTest {
    private val niigata = (0 until 12).map { MapPin("p$it", 37.9 + it * 0.01, 139.0 + it * 0.01, ItemType.MANHOLE_CARD, false) }
    private val nearNiigata = 37.85 to 138.95
    private val california = 37.42 to -122.08

    private fun ids(points: List<org.maplibre.android.geometry.LatLng>) = points.map { it.latitude to it.longitude }

    @Test fun nearYouFramesYouAndTheEightNearest() {
        val f = framing(niigata, nearNiigata, all = false)
        assertEquals(9, f.size)
        assertEquals(nearNiigata, ids(f).last())
    }

    @Test fun farFromEveryPinFramesThePinsOnly() {
        assertEquals(niigata.map { it.lat to it.lng }, ids(framing(niigata, california, all = false)))
    }

    @Test fun yourFindsFramesEveryPin() {
        assertEquals(12, framing(niigata, nearNiigata, all = true).size)
    }

    @Test fun noPositionFramesThePinsAndNoPinsFramesYou() {
        assertEquals(12, framing(niigata, null, all = false).size)
        assertEquals(listOf(nearNiigata), ids(framing(emptyList(), nearNiigata, all = false)))
    }
}
