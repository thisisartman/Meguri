package io.github.thisisartman.collectibles.data

import java.text.Normalizer

/**
 * Which station a stamp is, from the text read off it (owner, 2026-11-09). Only the station names printed on the
 * stamp count ("浦佐駅", "URASA STA."): stamps also carry line names (上越線), JR東日本 and slogans, and matching
 * against all of that gives false hits. Distance never raises the score: a notebook holds stamps from everywhere.
 * Pure, for tests; the bands are the owner's: 0.75 and up assigns, 0.60 to 0.75 suggests, below that the person picks.
 */
object StationMatch {
    const val ASSIGN = 0.75f
    const val SUGGEST = 0.60f

    data class Candidate(val id: String, val nameJa: String, val nameRomaji: String?)
    data class Match(val id: String, val score: Float)

    private val JA_STATION = Regex("([\\p{IsHan}\\p{IsHiragana}\\p{IsKatakana}ヶヵ々ー]{1,8})駅")
    private val EN_STATION = Regex("([A-Za-z][A-Za-z\\- ]{2,24}?)\\s*(?:STATION|STA\\.?)(?![A-Za-z])", RegexOption.IGNORE_CASE)

    /** The station names on a stamp, without 駅 or "Station": Japanese ones first. */
    fun tokens(text: String): List<String> {
        val t = Normalizer.normalize(text, Normalizer.Form.NFKC).replace(Regex("[\\s　]+(?=\\S*駅)"), "")
        val ja = JA_STATION.findAll(t).map { it.groupValues[1] }
            // A line name sits before the station name on many stamps ("上越線浦佐駅"): keep what follows 線
            .map { it.substringAfterLast('線') }.filter { it.isNotEmpty() }
        val en = EN_STATION.findAll(t).map { it.groupValues[1].trim() }.filter { it.length >= 3 }
        return (ja + en).distinct().toList()
    }

    /**
     * Characters the text reader mixes up on worn stamps, folded the same way on both sides (calibration on the owner's
     * notebook, 2026-11-09: 柏崎 read as 格埼, 浦佐 as 蒲佐), plus old and new forms of the same character.
     */
    private val LOOKALIKE = mapOf('埼' to '崎', '﨑' to '崎', '嵜' to '崎', '格' to '柏', '蒲' to '浦', '澤' to '沢', '濱' to '浜', '邊' to '辺', '邉' to '辺', '髙' to '高')

    private fun foldJa(s: String) = Normalizer.normalize(s, Normalizer.Form.NFKC).removeSuffix("駅").replace("ヶ", "ケ").replace("ヵ", "ケ")
        .map { LOOKALIKE[it] ?: it }.joinToString("")
    private fun foldEn(s: String) = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
        .replace("station", "").replace("ou", "o").replace("uu", "u").filter { it.isLetter() }

    /** 0..1: how well one token is this name. Exact 1; otherwise the longest shared run against the longer of the two. */
    internal fun similarity(token: String, name: String): Float {
        if (token.isEmpty() || name.isEmpty()) return 0f
        if (token == name) return 1f
        var best = 0
        val dp = IntArray(name.length + 1)
        for (i in 1..token.length) {
            var prev = 0
            for (j in 1..name.length) {
                val tmp = dp[j]
                dp[j] = if (token[i - 1] == name[j - 1]) prev + 1 else 0
                if (dp[j] > best) best = dp[j]
                prev = tmp
            }
        }
        return best.toFloat() / maxOf(token.length, name.length)
    }

    /** 0..1 for romaji: one minus the typo distance over the longer name (a misread letter costs little; another place, a lot). */
    internal fun latinSimilarity(a: String, b: String): Float {
        if (a.isEmpty() || b.isEmpty()) return 0f
        if (a == b) return 1f
        val d = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            var prev = d[0]; d[0] = i
            for (j in 1..b.length) {
                val tmp = d[j]
                d[j] = minOf(d[j] + 1, d[j - 1] + 1, prev + if (a[i - 1] == b[j - 1]) 0 else 1)
                prev = tmp
            }
        }
        return (1f - d[b.length].toFloat() / maxOf(a.length, b.length)).coerceAtLeast(0f)
    }

    /**
     * The best matches for [text] among [candidates], best first, with scores. When the runner-up is about as good,
     * the best is held below [ASSIGN], so a near tie is offered, not chosen.
     */
    fun match(text: String, candidates: List<Candidate>): List<Match> {
        val toks = tokens(text)
        if (toks.isEmpty()) return emptyList()
        val scored = candidates.map { c ->
            val ja = foldJa(c.nameJa); val en = c.nameRomaji?.let { foldEn(it) }
            val s = toks.maxOf { t ->
                val isLatin = t.all { it.code < 0x3000 }
                if (isLatin) en?.let { latinSimilarity(foldEn(t), it) } ?: 0f else similarity(foldJa(t), ja)
            }
            Match(c.id, s)
        }.filter { it.score > 0f }.sortedByDescending { it.score }.take(5)
        if (scored.isEmpty()) return scored
        val top = scored[0]
        val topJa = candidates.first { it.id == top.id }.nameJa.let(::foldJa)
        // A near tie, or another station whose name holds this one (三条 inside 東三条: a dropped 東 reads as 三条)
        val nearTie = scored.size >= 2 && scored[1].score >= top.score - 0.1f
        val heldInside = scored.drop(1).any { m -> m.score >= SUGGEST && candidates.first { it.id == m.id }.nameJa.let(::foldJa).let { it != topJa && it.contains(topJa) } }
        if (top.score >= ASSIGN && (nearTie || heldInside)) return listOf(top.copy(score = ASSIGN - 0.01f)) + scored.drop(1)
        return scored
    }
}
