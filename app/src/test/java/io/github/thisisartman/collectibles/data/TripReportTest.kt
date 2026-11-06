package io.github.thisisartman.collectibles.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.TimeZone

class TripReportTest {
    private val tokyo = TimeZone.getTimeZone("Asia/Tokyo")
    // 2026-11-07 00:00 in Tokyo is 2026-11-06 15:00 UTC
    private val nov7 = 1_793_977_200_000L

    private fun row(id: String, at: Long, type: ItemType = ItemType.MANHOLE_CARD, qty: Int = 1, cost: Int? = null, collection: String? = null) = ReportRow(
        Acquisition(id = id, itemId = "i$id", acquiredAt = at, via = Via.MANUAL, createdAt = at, quantity = qty, costYen = cost),
        Item(id = "i$id", type = type, nameJa = "長岡市, \"火焔\"", nameRomaji = "Nagaoka", code = "15-202", prefecture = "新潟県", lat = 37.4, lng = 138.8,
            origin = Origin.CSV_IMPORT, createdAt = 0, updatedAt = 0),
        collection,
    )

    @Test fun daysFollowTheLocalDateNotUtc() {
        val late = nov7 + 23 * 3_600_000L + 50 * 60_000L // 23:50 on the 7th, Tokyo
        val next = nov7 + 24 * 3_600_000L + 10 * 60_000L // 00:10 on the 8th
        val d = TripReport.days(listOf(nov7 + 3_600_000L, late, next), tokyo)
        assertEquals(listOf(nov7 + 86_400_000L to 1, nov7 to 2), d)
        assertEquals(nov7, TripReport.dayStart(late, tokyo))
        assertEquals(nov7 + 86_400_000L, TripReport.nextDay(nov7, tokyo))
    }

    @Test fun csvHasOneLinePerFindQuotedForSpreadsheets() {
        val csv = TripReport.csv(listOf(row("1", nov7 + 10 * 3_600_000L + 5 * 60_000L, qty = 2), row("2", nov7 + 11 * 3_600_000L, ItemType.GOSHUIN, cost = 500)), tokyo)
        val lines = csv.trimEnd().lines()
        assertEquals("date,time,type,collection,name_ja,name_romaji,code,prefecture,quantity,cost_yen,lat,lng", lines[0])
        assertEquals(3, lines.size)
        assertEquals("2026-11-07,10:05,MANHOLE_CARD,,\"長岡市, \"\"火焔\"\"\",Nagaoka,15-202,新潟県,2,,37.4,138.8", lines[1])
        assertEquals(true, lines[2].contains(",GOSHUIN,") && lines[2].contains(",1,500,"))
    }

    @Test fun alreadyWentFindsHaveADayButNoTime() {
        val r = row("1", nov7 + 12 * 3_600_000L).let { it.copy(find = it.find.copy(via = Via.BULK)) }
        assertEquals(true, TripReport.csv(listOf(r), tokyo).trimEnd().lines()[1].startsWith("2026-11-07,,MANHOLE_CARD,"))
    }

    @Test fun kindsCountQuantityAndCustomCollectionsByName() {
        val k = TripReport.byKind(listOf(row("1", nov7, qty = 2), row("2", nov7), row("3", nov7, ItemType.CUSTOM, collection = "Museum stamps")))
        assertEquals(listOf("MANHOLE_CARD" to 3, "Museum stamps" to 1), k)
    }
}
