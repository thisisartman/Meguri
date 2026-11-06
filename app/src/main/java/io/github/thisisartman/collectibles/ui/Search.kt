package io.github.thisisartman.collectibles.ui

import java.text.Normalizer

/**
 * Fuzzy search (owner, 2026-11-07). Names are compared after folding case, accents and long-vowel marks
 * (Ōkōchi = okochi), and dropping spaces, hyphens and "station"/駅, so "minamiyoshida", "Minami Yoshida" and
 * "南吉田" all find 南吉田駅. A score above 0 is a match; higher is better:
 * 100 starts with the query, 80 contains it, 50 has its letters in order, 30 is within one or two typos.
 */
fun fuzzyScore(query: String, candidates: List<String?>): Int =
    candidates.filterNotNull().maxOfOrNull { scoreOne(fold(query), fold(it)) } ?: 0

/** The name with the typed text in bold where it appears as written (ignoring case); otherwise plain. */
fun boldMatch(name: String, query: String): androidx.compose.ui.text.AnnotatedString {
    val q = query.trim()
    val at = if (q.isEmpty()) -1 else name.indexOf(q, ignoreCase = true)
    return androidx.compose.ui.text.buildAnnotatedString {
        append(name)
        if (at >= 0) addStyle(androidx.compose.ui.text.SpanStyle(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold), at, at + q.length)
    }
}

/** A name ready for a narrow tile: a break chance after each hyphen, and " · " kept with the word before it. */
fun tileName(name: String): String = name.replace("-", "-​").replace(" · ", " · ")

internal fun fold(s: String): String {
    val noMarks = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
    return noMarks
        .replace("station", "").replace("駅", "").replace("ou", "o").replace("uu", "u")
        .filter { it.isLetterOrDigit() }
}

private fun scoreOne(q: String, c: String): Int {
    if (q.isEmpty() || c.isEmpty()) return 0
    if (c.startsWith(q)) return 100
    if (c.contains(q)) return 80
    if (isSubsequence(q, c) && q.length >= 3) return 50
    // Typos: compare with the start of the name of the same length, and allow 1 (short) or 2 (long) edits
    if (q.length >= 4) {
        val allowed = if (q.length >= 7) 2 else 1
        val head = c.take(q.length)
        if (editDistance(q, head) <= allowed) return 30
    }
    return 0
}

private fun isSubsequence(q: String, c: String): Boolean {
    var i = 0
    for (ch in c) if (i < q.length && q[i] == ch) i++
    return i == q.length
}

internal fun editDistance(a: String, b: String): Int {
    val d = IntArray(b.length + 1) { it }
    for (i in 1..a.length) {
        var prev = d[0]
        d[0] = i
        for (j in 1..b.length) {
            val tmp = d[j]
            d[j] = minOf(d[j] + 1, d[j - 1] + 1, prev + if (a[i - 1] == b[j - 1]) 0 else 1)
            prev = tmp
        }
    }
    return d[b.length]
}
