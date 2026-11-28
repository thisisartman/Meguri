package io.github.thisisartman.collectibles.data

import io.github.thisisartman.collectibles.data.PhotoSuggest.Rule
import io.github.thisisartman.collectibles.data.PhotoSuggest.Target
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/** #206: where a photo goes from the text on it. Invented text only; the owner's photos are in [ownersPhotos]. */
class PhotoSuggestTest {
    private val known = listOf(
        PhotoSuggest.Known("card", ItemType.MANHOLE_CARD, "江戸川区 Y001", code = "13-100-Y001"),
        PhotoSuggest.Known("card2", ItemType.MANHOLE_CARD, "東京23区 L101", code = "13-100-L101"),
        PhotoSuggest.Known("urasa", ItemType.EKI_STAMP, "浦佐駅", "Urasa"),
        PhotoSuggest.Known("urasa-tag", ItemType.EKITAG, "浦佐駅", "Urasa"),
        PhotoSuggest.Known("omiya", ItemType.EKI_STAMP, "大宮駅", "Omiya"),
        PhotoSuggest.Known("hakusan", ItemType.EKI_STAMP, "白山駅", "Hakusan"),
        PhotoSuggest.Known("gotokuji", ItemType.GOSHUIN, "豪徳寺"),
        PhotoSuggest.Known("museum", ItemType.MUSEUM_STAMP, "新津鉄道資料館"),
    )

    private fun s(text: String, screenshot: Boolean = false) = PhotoSuggest.suggest(text, screenshot, known)

    @Test fun aCardCodeIsThatCardWithItsCoverPosition() {
        val r = s("マンホールカード\n13－100－Y001\n北緯35°38'56.4\" 東経139°52'59.6\"")!!
        assertEquals(Rule.CARD, r.rule)
        assertEquals(Target.Existing("card"), r.target)
        assertEquals(PhotoBundle.CARD, r.kind)
        assertEquals(35.649, r.cover!!.lat, 1e-3)
    }

    @Test fun aMisreadCardLetterStillFindsTheOneCard() {
        // L read as I (seen on the owner's batch, 2026-11-09)
        assertEquals(Target.Existing("card2"), s("13-100-I101")!!.target)
    }

    @Test fun aCardNotOnThePhoneStaysUnsorted() = assertNull(s("15-999-Z001"))

    @Test fun aStationNameIsItsStamp() {
        val r = s("上越線\n浦佐駅\n毘沙門堂")!!
        assertEquals(Rule.STATION, r.rule)
        assertEquals(Target.Existing("urasa"), r.target)
    }

    @Test fun aScreenshotOfAStationIsItsEkitag() {
        val r = s("浦佐駅 URASA", screenshot = true)!!
        assertEquals(Rule.EKITAG, r.rule)
        assertEquals(Target.Existing("urasa-tag"), r.target)
        assertEquals(PhotoKind.EKITAG, r.kind)
    }

    @Test fun aStationNotOnThePhoneIsANewOneAsPrinted() {
        val r = s("京成線\n四ツ木駅")!!
        assertEquals(Target.New(ItemType.EKI_STAMP, "四ツ木駅"), r.target)
    }

    @Test fun aLineNameMisreadInFrontStillFindsTheStation() {
        // 線 lost and 崎 read as 埼: the last letters before 駅 are the station
        assertEquals(Target.Existing("kashiwazaki"), PhotoSuggest.suggest("信越本柏埼駅", false, known + PhotoSuggest.Known("kashiwazaki", ItemType.EKI_STAMP, "柏崎駅"))!!.target)
    }

    @Test fun aFerryStampBesideAStationStampIsTheFerryStamp() {
        assertEquals(Rule.OTHER_STAMP, s("浦佐駅\nカーフェリー\n乗船記念")!!.rule)
    }

    @Test fun aStationWithAShrineWordIsStillTheStation() {
        // 大宮 holds 宮: the station comes first
        assertEquals(Target.Existing("omiya"), s("大宮駅\n鉄道のまち")!!.target)
    }

    @Test fun hosaiWithADateIsAGoshuin() {
        val r = s("奉拝\n令和八年五月二十四日\n度津神社")!!
        assertEquals(Rule.GOSHUIN, r.rule)
        assertEquals(Target.New(ItemType.GOSHUIN, "度津神社"), r.target)
    }

    @Test fun aShrineOnThePhoneIsThatShrine() {
        assertEquals(Target.Existing("gotokuji"), s("大谿山\n豪 徳 寺\n釈迦牟尼仏")!!.target)
    }

    @Test fun aShrineNamedLikeAStationIsTheShrine() {
        // 白山 is a station too, but this text has no 駅
        assertEquals(Target.New(ItemType.GOSHUIN, "白山神社"), s("新潟\n白山神社")!!.target)
    }

    @Test fun ferryAndMuseumStampsAreOtherStamps() {
        val ferry = s("佐渡汽船\nカーフェリー乗船記念")!!
        assertEquals(Rule.OTHER_STAMP, ferry.rule)
        assertEquals(Target.New(ItemType.MUSEUM_STAMP, "カーフェリー乗船記念"), ferry.target)
        assertEquals(Target.Existing("museum"), s("新潟市\n新津鉄道資料館\n入館記念")!!.target)
    }

    @Test fun aPhotoFilesUnlistedPhotosAreTheOnesItsListNeverNames() {
        val parsed = PhotoBundle.parse(
            "photo,kind,match,type,key,name,date\n" +
                "a.jpg,stamp,catalogue,EKI_STAMP,EKI_STAMP:浦佐駅,,\n" +
                "b.jpg,stamp,skip,,,,\n",
        )
        assertEquals(setOf("a.jpg", "b.jpg"), parsed.named)
        // A skipped photo was left out on purpose; the list itself and other files are not photos
        assertEquals(listOf("c.JPG", "d.png"), PhotoBundle.unlisted(listOf("d.png", "a.jpg", "b.jpg", "c.JPG", "notes.txt"), parsed.named))
    }

    @Test fun nothingReadableStaysUnsorted() {
        assertNull(s(""))
        assertNull(s("JR EAST\nWelcome to Japan"))
        // A misread scrap before 駅 is no station name to make a new one from
        assertNull(s("いハを心三ネ田駅"))
        // A shrine word alone, with no name in front of it
        assertNull(s("寺"))
    }

    /**
     * Not a check: with BULKSUGGEST_DIR set to a folder holding truth.csv (the owner's 47 photos of 2026-11-27 and
     * where each belongs), text.csv (file,text: what a text reader read off each) and items.csv (a backup's items),
     * prints how each rule did: right, wrong (a suggestion one tap from a mis-filed photo) or unsorted (left for the
     * person, the safe miss). The photos and their text stay out of the repo.
     */
    @Test fun ownersPhotos() {
        val dir = System.getenv("BULKSUGGEST_DIR")?.let(::File)
        assumeTrue("BULKSUGGEST_DIR not set", dir?.isDirectory == true)
        dir!!
        val items = Csv.parseWithHeader(File(dir, "items.csv").readText().removePrefix("﻿"))
        val known = items.mapNotNull { m ->
            val type = runCatching { ItemType.valueOf(m["type"].orEmpty()) }.getOrNull() ?: return@mapNotNull null
            PhotoSuggest.Known(m["id"]!!, type, m["name_ja"]!!, m["name_romaji"]?.ifEmpty { null }, m["code"]?.ifEmpty { null })
        }
        val keyOf = items.associate { it["id"]!! to it["catalog_key"].orEmpty() }
        val text = Csv.parseWithHeader(File(dir, "text.csv").readText().removePrefix("﻿")).associate { it["file"]!! to it["text"].orEmpty() }
        val truth = Csv.parseWithHeader(File(dir, "truth.csv").readText().removePrefix("﻿"))
        val ruleOf = mapOf(
            ItemType.MANHOLE_CARD to Rule.CARD, ItemType.EKI_STAMP to Rule.STATION, ItemType.EKITAG to Rule.EKITAG,
            ItemType.GOSHUIN to Rule.GOSHUIN, ItemType.MUSEUM_STAMP to Rule.OTHER_STAMP,
        )
        data class Score(var right: Int = 0, var wrong: Int = 0, var unsorted: Int = 0)
        val byRule = linkedMapOf<Rule, Score>()
        val lines = mutableListOf<String>()
        var coverRight = 0; var coverAll = 0
        for (col in listOf("type", "mapping_type")) {
            byRule.clear()
            for (t in truth) {
                val type = ItemType.valueOf(t[col]!!)
                val key = if (col == "type") t["key"].orEmpty() else if (type == ItemType.valueOf(t["type"]!!)) t["key"].orEmpty() else ""
                val sug = PhotoSuggest.suggest(text[t["file"]].orEmpty(), screenshot = false, known)
                val score = byRule.getOrPut(ruleOf.getValue(type)) { Score() }
                val outcome = when {
                    sug == null -> { score.unsorted++; "unsorted" }
                    sug.type == type && when (val g = sug.target) {
                        is Target.Existing -> if (key.isNotEmpty()) keyOf[g.id] == key else keyOf[g.id].isNullOrEmpty()
                        is Target.New -> key.isEmpty()
                    } -> { score.right++; "right" }
                    else -> { score.wrong++; "wrong" }
                }
                if (col == "type") {
                    lines += "${t["file"]}\t$type ${key.ifEmpty { t["name"] }}\t$outcome\t${sug?.rule} ${sug?.type} ${sug?.target}"
                    if (type == ItemType.MANHOLE_CARD && t["cover_lat"].orEmpty().isNotEmpty()) {
                        coverAll++
                        val c = sug?.cover
                        if (c != null && kotlin.math.abs(c.lat - t["cover_lat"]!!.toDouble()) < 1e-3 && kotlin.math.abs(c.lng - t["cover_lng"]!!.toDouble()) < 1e-3) coverRight++
                    }
                }
            }
            println("Answer key: $col")
            byRule.forEach { (r, s) -> println("  $r: ${s.right} right, ${s.wrong} wrong, ${s.unsorted} unsorted of ${s.right + s.wrong + s.unsorted}") }
        }
        println("Card cover positions read: $coverRight of $coverAll")
        lines.forEach(::println)
        File(dir, "result.txt").writeText(lines.joinToString("\n"))
    }
}
