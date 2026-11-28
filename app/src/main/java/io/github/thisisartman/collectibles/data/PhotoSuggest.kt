package io.github.thisisartman.collectibles.data

import java.text.Normalizer

/**
 * Where a photo most likely goes, from the text read off it on the phone (#206, owner 2026-11-27). Used when many
 * photos come in at once (sharing several photos, or a photo file's photos that its list doesn't place). Only ever a
 * suggestion: the person confirms each with one tap, and a photo with nothing readable stays unsorted (null).
 * Pure, for tests.
 *
 * The rules, first that applies wins:
 *  1. [Rule.CARD] A manhole card code (13-100-Y001) of a card on this phone: that card, the photo in its Card slot,
 *     with the cover position printed on it.
 *  2. [Rule.GOSHUIN] 奉拝 with a date: a goshuin.
 *  3. [Rule.EKITAG] A screenshot with a station name on it: that station's Ekitag.
 *  4. [Rule.OTHER_STAMP] 乗船記念, 搭乗記念 or 入館記念: a ferry or museum stamp, even with a station's stamp on the
 *     same page.
 *  5. [Rule.STATION] A name ending in 駅: that station's stamp, matched with the notebook's station matcher
 *     ([StationMatch]), or by its last letters when a line name was misread in front of it; a station not on this
 *     phone becomes a new one of your own. Before the shrine and museum words, as many stations hold them (大宮駅,
 *     国分寺駅).
 *  6. [Rule.GOSHUIN] 神社, 寺 or 宮: a goshuin.
 *  7. [Rule.OTHER_STAMP] 記念 or 館 (乗船記念, 入館記念, 資料館): a museum, ferry or other stamp.
 * A goshuin or other stamp is the item on this phone whose name is in the text, else a new one of your own named
 * from the text; with no name to give it, the photo stays unsorted.
 */
object PhotoSuggest {
    enum class Rule { CARD, EKITAG, STATION, GOSHUIN, OTHER_STAMP }

    /** An item on this phone, as the rules need it. */
    data class Known(val id: String, val type: ItemType, val nameJa: String, val nameRomaji: String? = null, val code: String? = null)

    fun known(items: List<Item>): List<Known> = items.map { Known(it.id, it.type, it.nameJa, it.nameRomaji, it.code) }

    /** How many photos coming in at once get read: 1, so a single shared photo is read too (owner question open; 2 turns it off). */
    const val READ_FROM = 1

    sealed interface Target {
        data class Existing(val id: String) : Target
        /** A new item of your own, made when the person confirms. */
        data class New(val type: ItemType, val name: String) : Target
    }

    /** [kind] is the photo kind it is filed as (the app still sorts it by what it shows); [cover] a card's printed position. */
    data class Suggestion(val rule: Rule, val type: ItemType, val target: Target, val kind: String, val cover: CoverCoords.LatLng? = null)

    private val DATE = Regex("令和|平成|[0-9０-９一二三四五六七八九十元]+\\s*[年月日]")
    private val SHRINE = Regex("神社|寺|宮")
    private val OTHER = Regex("記念|館")
    private val STRONG_OTHER = Regex("乗船記念|搭乗記念|入館記念")
    // A name ending in one of the words: the characters right before it
    private val SHRINE_NAME = Regex("([\\p{IsHan}々ヶ]{1,8}(?:神社|大社|神宮|寺|宮|院))")
    private val OTHER_NAME = Regex("([\\p{IsHan}\\p{IsHiragana}\\p{IsKatakana}々ヶー]{1,12}?(?:記念館|資料館|博物館|美術館|水族館|科学館|館|乗船記念|搭乗記念|入館記念|記念))")

    /**
     * Spaces next to Japanese dropped (a reader spaces out each letter: "柏 崎 駅"), spaces between English words kept,
     * and lines kept apart, so names don't run together.
     */
    internal fun fold(s: String) = Normalizer.normalize(s, Normalizer.Form.NFKC)
        .replace(Regex("(?<=[^\\x00-\\x7F])[ \\t]+|[ \\t]+(?=[^\\x00-\\x7F])"), "").trim()

    /** The name without a note in brackets: "豪徳寺" for "豪徳寺 (世田谷)". */
    private fun core(name: String) = fold(name).replace(Regex("[(（].*?[)）]"), "").trim()

    fun suggest(text: String, screenshot: Boolean, known: List<Known>): Suggestion? {
        val t = fold(text)
        if (t.isEmpty()) return null
        card(text, known)?.let { return it }
        if ("奉拝" in t && DATE.containsMatchIn(t)) named(Rule.GOSHUIN, ItemType.GOSHUIN, t, known, SHRINE_NAME)?.let { return it }
        if (screenshot) station(t, ItemType.EKITAG, Rule.EKITAG, PhotoKind.EKITAG, known)?.let { return it }
        // 乗船記念, 入館記念: said only by a ferry or museum stamp, even beside a station's stamp on the same page
        if (STRONG_OTHER.containsMatchIn(t)) named(Rule.OTHER_STAMP, ItemType.MUSEUM_STAMP, t, known, OTHER_NAME)?.let { return it }
        station(t, ItemType.EKI_STAMP, Rule.STATION, PhotoKind.STAMP, known)?.let { return it }
        if (SHRINE.containsMatchIn(t)) named(Rule.GOSHUIN, ItemType.GOSHUIN, t, known, SHRINE_NAME)?.let { return it }
        if (OTHER.containsMatchIn(t)) named(Rule.OTHER_STAMP, ItemType.MUSEUM_STAMP, t, known, OTHER_NAME)?.let { return it }
        return null
    }

    private fun card(text: String, known: List<Known>): Suggestion? {
        val cards = known.filter { it.type == ItemType.MANHOLE_CARD && it.code != null }.associateBy { it.code!! }
        if (cards.isEmpty()) return null
        val codes = CardCode.findAll(text)
        // A code read exactly, else the one card a look-alike letter points to. Not the nearest code by misread digits:
        // with many photos and no one checking each, that gave the wrong card (the owner's photos, 2026-11-28)
        val hit = codes.firstNotNullOfOrNull { cards[it] }
            ?: codes.firstNotNullOfOrNull { c -> CardCode.lookalikes(c, cards.keys).singleOrNull()?.let { cards[it] } }
            ?: return null
        return Suggestion(Rule.CARD, ItemType.MANHOLE_CARD, Target.Existing(hit.id), PhotoBundle.CARD, CoverCoords.parse(text))
    }

    private fun station(text: String, type: ItemType, rule: Rule, kind: String, known: List<Known>): Suggestion? {
        val tokens = StationMatch.tokens(text)
        if (tokens.isEmpty()) return null
        val cands = known.filter { it.type == type }.map { StationMatch.Candidate(it.id, it.nameJa, it.nameRomaji) }
        val best = StationMatch.match(text, cands).firstOrNull()?.takeIf { it.score >= StationMatch.SUGGEST }
            // A line name misread in front ("信越本柏崎駅", 線 lost): try the last two to four letters before 駅
            ?: tokens.asSequence().filter { tok -> tok.length > 2 && tok.any { it.code >= 0x3000 } }
                .flatMap { tok -> (2..minOf(4, tok.length - 1)).map { tok.takeLast(it) + "駅" } }
                .mapNotNull { StationMatch.match(it, cands).firstOrNull()?.takeIf { m -> m.score >= StationMatch.ASSIGN - 0.01f } }
                .firstOrNull()
        if (best != null) return Suggestion(rule, type, Target.Existing(best.id), kind)
        // Not on this phone (a private line's station): a new one, named as printed
        // Only a name that reads like one: up to six letters, no hiragana (misread scraps before 駅 have them)
        val ja = tokens.firstOrNull { tok -> tok.any { it.code >= 0x3000 } && tok.length <= 6 && tok.none { it in 'ぁ'..'ゖ' } } ?: return null
        return Suggestion(rule, type, Target.New(type, ja + "駅"), kind)
    }

    private fun named(rule: Rule, type: ItemType, t: String, known: List<Known>, pattern: Regex): Suggestion? {
        // The longest name of this type on the phone that the text holds (two letters at least, so 寺 alone never matches)
        val flat = t.replace("\n", "")
        val hit = known.filter { it.type == type }.map { it to core(it.nameJa) }.filter { (_, n) -> n.length >= 2 && n in flat }
            .maxByOrNull { it.second.length }?.first
        if (hit != null) return Suggestion(rule, type, Target.Existing(hit.id), PhotoKind.STAMP)
        // 奉拝 ("worshipped") heads the page, it is no part of the name
        val name = pattern.find(t.replace("奉拝", "\n"))?.groupValues?.get(1)?.takeIf { it.length >= 2 } ?: return null
        return Suggestion(rule, type, Target.New(type, name), PhotoKind.STAMP)
    }
}
