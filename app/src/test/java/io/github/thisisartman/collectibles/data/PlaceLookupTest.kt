package io.github.thisisartman.collectibles.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaceLookupTest {
    @Test fun parsesNominatimResults() {
        val json = """[{"osm_type":"node","osm_id":123,"lat":"37.6703","lon":"138.8794","name":"南吉田駅",
            "display_name":"南吉田駅, 燕市, 新潟県, 日本"},
            {"osm_type":"way","osm_id":0,"lat":"37.7","lon":"138.8","name":"","display_name":"彌彦神社, 弥彦村"}]"""
        val hits = PlaceLookup.parse(json)
        assertEquals(2, hits.size)
        assertEquals("南吉田駅", hits[0].name)
        assertEquals("南吉田駅, 燕市, 新潟県, 日本", hits[0].address)
        assertEquals(37.6703, hits[0].lat, 1e-9)
        assertEquals("https://www.openstreetmap.org/node/123", hits[0].osmUrl)
        assertEquals("彌彦神社", hits[1].name)
        assertNull(hits[1].osmUrl)
    }
}
