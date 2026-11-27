package io.github.thisisartman.collectibles.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class PhotoBundleTest {
    private val header = "photo,folder,kind,match,type,key,name,prefecture,date,before_app,note\n"

    @Test fun readsCatalogueOwnExtraAndSkips() {
        val p = PhotoBundle.parse(
            "﻿" + header +
                "a.jpg,Cards,manhole,catalogue,MANHOLE_CARD,MANHOLE_CARD:15-202-F001,15-202-F001,,,yes,\n" +
                "b.jpg,Cards,manhole,skip,MANHOLE_CARD,MANHOLE_CARD:15-202-F001,15-202-F001,,,yes,another photo\n" +
                "c.jpg,Book,goshuin,own,GOSHUIN,,彌彦神社,,2026-11-07,no,\n" +
                "d.jpg,Book,goshuin,extra,GOSHUIN,,彌彦神社,,,yes,second photo\n",
        )
        assertEquals(1, p.skipped)
        assertTrue(p.bad.isEmpty())
        assertEquals(listOf("a.jpg", "c.jpg", "d.jpg"), p.rows.map { it.photo })
        assertEquals(PhotoBundle.Target.Catalogue("MANHOLE_CARD:15-202-F001"), p.rows[0].target)
        assertEquals(PhotoBundle.Target.Own(ItemType.GOSHUIN, "彌彦神社"), p.rows[1].target)
        // An extra photo goes where the row above went, whatever its own columns say
        assertEquals(p.rows[1].target, p.rows[2].target)
        assertEquals(LocalDate.of(2026, 11, 7), p.rows[1].date)
        assertEquals(null, p.rows[0].date)
    }

    @Test fun badLinesAreLeftOutNotGuessed() {
        val p = PhotoBundle.parse(
            header +
                "e.jpg,X,ekitag,extra,EKITAG,,,,,yes,\n" + // nothing above it
                "f.jpg,X,sticker,own,EKITAG,,弥彦駅,,,yes,\n" + // unknown kind
                "g.jpg,X,ekitag,own,EKITAG,,弥彦駅,,7 Nov,no,\n" + // date not yyyy-mm-dd
                "../h.jpg,X,ekitag,own,EKITAG,,弥彦駅,,,yes,\n" + // a path out of the file
                "i.jpg,X,ekitag,catalogue,EKITAG,,弥彦駅,,,yes,\n" + // no key
                "j.jpg,X,ekitag,own,CUSTOM,,mine,,,yes,\n" + // your own kinds have no type to name
                "k.jpg,X,ekitag,maybe,EKITAG,EKITAG:弥彦駅,弥彦駅,,,yes,\n",
        )
        assertTrue(p.rows.isEmpty())
        assertEquals(7, p.bad.size)
    }

    @Test fun planCountsMissingRegionsAndOwnItemsAlreadyThere() {
        val rows = PhotoBundle.parse(
            header +
                "a.jpg,C,manhole,catalogue,MANHOLE_CARD,MANHOLE_CARD:15-202-F001,,,,yes,\n" +
                "b.jpg,C,manhole,catalogue,MANHOLE_CARD,MANHOLE_CARD:47-201-A001,,,,yes,\n" +
                "c.jpg,B,goshuin,own,GOSHUIN,,彌彦神社,,2026-11-07,no,\n" +
                "d.jpg,B,goshuin,extra,GOSHUIN,,,,,yes,\n" +
                "e.jpg,B,stamp,own,EKI_STAMP,,三田駅 (都営三田線),,,yes,\n",
        ).rows
        val plan = PhotoBundle.plan(rows, keysOnPhone = setOf("MANHOLE_CARD:15-202-F001"), ownOnPhone = setOf(ItemType.EKI_STAMP to "三田駅 (都営三田線)"))
        assertEquals(listOf("MANHOLE_CARD:47-201-A001"), plan.missing)
        // a.jpg, c.jpg, d.jpg, e.jpg: the Okinawa card is left out
        assertEquals(4, plan.photos)
        // The card and 三田 are already on the phone; only 彌彦神社 is new
        assertEquals(1, plan.newItems)
        assertEquals(2, plan.toItems)
    }

    @Test fun aRowWithNoDateIsFoundNowNeverBeforeMeguri() {
        val tokyo = ZoneId.of("Asia/Tokyo")
        val rows = PhotoBundle.parse(
            header +
                "a.jpg,B,stamp,own,EKI_STAMP,,三田駅,,,yes,\n" +
                "b.jpg,B,goshuin,own,GOSHUIN,,彌彦神社,,2026-11-07,no,\n",
        ).rows
        val now = 1_790_000_000_000L
        // The before_app column is ignored: no date means the moment the file is added
        assertEquals(now, PhotoBundle.findDate(rows[0], now, tokyo))
        assertEquals(PhotoBundle.dayMs(LocalDate.of(2026, 11, 7), tokyo), PhotoBundle.findDate(rows[1], now, tokyo))
    }

    @Test fun onlyStampPhotosAreCutToTheStamp() {
        assertTrue(PhotoBundle.cropsStamp(ItemType.EKI_STAMP, PhotoKind.STAMP))
        assertTrue(PhotoBundle.cropsStamp(ItemType.MUSEUM_STAMP, PhotoKind.STAMP))
        assertTrue(PhotoBundle.cropsStamp(ItemType.ROADSIDE_STATION, PhotoKind.STAMP))
        // A goshuin stays whole; a card's photo and an Ekitag screenshot have their own crops; a place photo is whole
        assertFalse(PhotoBundle.cropsStamp(ItemType.GOSHUIN, PhotoKind.STAMP))
        assertFalse(PhotoBundle.cropsStamp(ItemType.MANHOLE_CARD, PhotoKind.STAMP))
        assertFalse(PhotoBundle.cropsStamp(ItemType.EKI_STAMP, PhotoKind.EKITAG))
        assertFalse(PhotoBundle.cropsStamp(ItemType.EKI_STAMP, PhotoKind.PLACE))
    }

    @Test fun readsCardAsAKind() {
        val p = PhotoBundle.parse(
            header +
                "a.jpg,Cards,card,catalogue,MANHOLE_CARD,MANHOLE_CARD:15-202-F001,,,,,\n" +
                "b.jpg,Cards,Card,extra,,,,,,,\n" +
                "c.jpg,Cards,manhole,extra,,,,,,,cover\n" +
                "d.jpg,Dams,CARD,own,DAM_CARD,,奥只見ダム,,2026-11-01,,\n",
        )
        assertTrue(p.bad.toString(), p.bad.isEmpty())
        assertEquals(listOf(PhotoBundle.CARD, PhotoBundle.CARD, PhotoKind.MANHOLE, PhotoBundle.CARD), p.rows.map { it.kind })
        assertEquals(PhotoBundle.Target.Own(ItemType.DAM_CARD, "奥只見ダム"), p.rows[3].target)
    }

    @Test fun aRowWithNoKindIsKeptAsOtherToBeSorted() {
        val p = PhotoBundle.parse(header + "Screenshot_1.png,Tags,,own,EKITAG,,弥彦駅,,,,\n")
        assertTrue(p.bad.toString(), p.bad.isEmpty())
        assertEquals(PhotoKind.OTHER, p.rows.single().kind)
        // Sorted when added: a screenshot of a station is its Ekitag
        val shot = io.github.thisisartman.collectibles.photo.PhotoSort.Facts(screenshot = true)
        assertEquals(PhotoKind.EKITAG, io.github.thisisartman.collectibles.photo.PhotoSort.decide(ItemType.EKITAG, p.rows.single().kind, shot).kind)
    }

    @Test fun aCardRowIsFiledInTheCardSlotNeverAsCard() {
        // photo.role must never be the literal "card": the item page would show it as Other
        val sort = io.github.thisisartman.collectibles.photo.PhotoSort
        val none = io.github.thisisartman.collectibles.photo.PhotoSort.Facts()
        assertEquals(PhotoKind.STAMP, sort.decide(ItemType.MANHOLE_CARD, PhotoBundle.CARD, none).kind)
        assertEquals(PhotoKind.STAMP, sort.decide(ItemType.DAM_CARD, PhotoBundle.CARD, none).kind)
        for (t in ItemType.entries) assertTrue(t.name, sort.decide(t, PhotoBundle.CARD, none).kind != PhotoBundle.CARD)
    }

    @Test fun aDayIsKeptAtNoon() {
        val tokyo = ZoneId.of("Asia/Tokyo")
        val ms = PhotoBundle.dayMs(LocalDate.of(2025, 12, 30), tokyo)
        val back = java.time.Instant.ofEpochMilli(ms).atZone(tokyo)
        assertEquals(LocalDate.of(2025, 12, 30), back.toLocalDate())
        assertEquals(12, back.hour)
    }

    private val coverHeader = "photo,kind,match,type,key,name,date,cover_lat,cover_lng\n"

    @Test fun readsACoverPosition() {
        val p = PhotoBundle.parse(coverHeader + "a.jpg,manhole,catalogue,MANHOLE_CARD,MANHOLE_CARD:15-202-F001,,,37.44569,138.86007\n")
        assertTrue(p.bad.isEmpty())
        assertEquals(CoverCoords.LatLng(37.44569, 138.86007), p.rows.single().cover)
    }

    @Test fun aFileWithoutTheColumnsHasNoCoverPosition() {
        val p = PhotoBundle.parse(header + "a.jpg,Cards,manhole,catalogue,MANHOLE_CARD,MANHOLE_CARD:15-202-F001,15-202-F001,,,yes,\n")
        assertTrue(p.bad.isEmpty())
        assertNull(p.rows.single().cover)
    }

    @Test fun aHalfPairOrNonNumberIsIgnoredAndTheRowKept() {
        val p = PhotoBundle.parse(
            coverHeader +
                "a.jpg,manhole,catalogue,MANHOLE_CARD,MANHOLE_CARD:15-202-F001,,,37.44569,\n" +
                "b.jpg,manhole,catalogue,MANHOLE_CARD,MANHOLE_CARD:15-202-F002,,,,138.86007\n" +
                "c.jpg,manhole,catalogue,MANHOLE_CARD,MANHOLE_CARD:15-202-F003,,,37.4,abc\n",
        )
        assertTrue(p.bad.isEmpty())
        assertEquals(listOf("a.jpg", "b.jpg", "c.jpg"), p.rows.map { it.photo })
        assertTrue(p.rows.all { it.cover == null })
    }

    @Test fun aPositionOutsideJapanIsIgnoredAndTheRowKept() {
        val p = PhotoBundle.parse(
            coverHeader +
                "a.jpg,manhole,catalogue,MANHOLE_CARD,MANHOLE_CARD:15-202-F001,,,51.5,-0.1\n" +
                "b.jpg,manhole,catalogue,MANHOLE_CARD,MANHOLE_CARD:15-202-F002,,,19.9,138.8\n" +
                "c.jpg,manhole,catalogue,MANHOLE_CARD,MANHOLE_CARD:15-202-F003,,,37.4,154.1\n" +
                "d.jpg,manhole,catalogue,MANHOLE_CARD,MANHOLE_CARD:15-202-F004,,,46.0,122.0\n",
        )
        assertTrue(p.bad.isEmpty())
        assertEquals(4, p.rows.size)
        assertEquals(listOf(null, null, null, CoverCoords.LatLng(46.0, 122.0)), p.rows.map { it.cover })
    }

    @Test fun anExtraRowsPositionIsIgnored() {
        val p = PhotoBundle.parse(
            coverHeader +
                "a.jpg,manhole,catalogue,MANHOLE_CARD,MANHOLE_CARD:15-202-F001,,,,\n" +
                "b.jpg,manhole,extra,,,,,37.44569,138.86007\n",
        )
        assertTrue(p.bad.isEmpty())
        assertEquals(2, p.rows.size)
        assertNull(p.rows[1].cover)
    }

    @Test fun aCoverPositionOnlyFillsAnEmptyOne() {
        val row = PhotoBundle.Row("a.jpg", "manhole", PhotoBundle.Target.Catalogue("MANHOLE_CARD:15-202-F001"), null, CoverCoords.LatLng(37.44569, 138.86007))
        assertEquals(CoverCoords.LatLng(37.44569, 138.86007), PhotoBundle.coverToSet(row, null))
        assertNull(PhotoBundle.coverToSet(row, 37.5))
        assertNull(PhotoBundle.coverToSet(row.copy(cover = null), null))
    }
}
