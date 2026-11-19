package io.github.thisisartman.collectibles.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URLDecoder

/** Report links from an item's page open the right form with the item filled in (0.6). */
class CatalogueReportTest {
    private val card = Item(
        id = "c", type = ItemType.MANHOLE_CARD, code = "15-202-C001", nameJa = "長岡市 かわぐっち", nameRomaji = "Nagaoka Kawagutchi",
        origin = Origin.CSV_IMPORT, lat = 37.4466, lng = 138.8513, placeName = "アオーレ長岡", createdAt = 1, updatedAt = 1,
    )

    private fun params(url: String) = url.substringAfter('?').split('&').associate {
        it.substringBefore('=') to URLDecoder.decode(it.substringAfter('='), "UTF-8")
    }

    @Test fun availabilityNamesTheItem() {
        val p = params(CatalogueReport.url(CatalogueReport.Kind.AVAILABILITY, card))
        assertEquals("availability.yml", p["template"])
        assertEquals("長岡市 かわぐっち (Nagaoka Kawagutchi) · 15-202-C001", p["item"])
        assertEquals("[Availability] 長岡市 かわぐっち (Nagaoka Kawagutchi) · 15-202-C001", p["title"])
    }

    @Test fun addUsesThePlaceAndAMapLinkButNoItem() {
        val url = CatalogueReport.url(CatalogueReport.Kind.ADD, card)
        val p = params(url)
        assertEquals("new_place.yml", p["template"])
        assertTrue(p["location"]!!.startsWith("アオーレ長岡 https://www.openstreetmap.org/?mlat=37.4466&mlon=138.8513"))
        assertFalse(p.containsKey("item"))
        assertFalse("spaces are encoded", url.contains(' '))
    }

    @Test fun correctionWithoutCodeOrRomaji() {
        val shrine = card.copy(code = null, nameRomaji = null, nameJa = "彌彦神社")
        assertEquals("彌彦神社", params(CatalogueReport.url(CatalogueReport.Kind.CORRECTION, shrine))["item"])
    }
}
