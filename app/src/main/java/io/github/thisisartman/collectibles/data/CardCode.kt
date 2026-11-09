package io.github.thisisartman.collectibles.data

import java.text.Normalizer

/**
 * Finds a manhole card's code (`15-202-A001`: prefecture, municipality, card letter and number) in text read from a
 * photo of the card (M8). OCR mixes full-width and half-width characters, several kinds of dash, spaces around the
 * dashes, and the letters O and I with the digits 0 and 1; the code's shape says which belongs where.
 */
object CardCode {
    /**
     * Degrees to turn a [w] by [h] picture clockwise so the point ([x], [y]) (where the code was read) ends up in the
     * top right quarter, where every manhole card prints its code. 0, 90, 180 or 270.
     */
    fun turnToTopRight(x: Float, y: Float, w: Float, h: Float): Int {
        for (t in listOf(0, 90, 180, 270)) {
            // Where the point lands after turning the picture t degrees clockwise, and that picture's size
            val (nx, ny, nw, nh) = when (t) {
                90 -> listOf(h - y, x, h, w)
                180 -> listOf(w - x, h - y, w, h)
                270 -> listOf(y, w - x, h, w)
                else -> listOf(x, y, w, h)
            }
            if (nx > nw / 2 && ny < nh / 2) return t
        }
        return 0
    }

    private val DASHES = Regex("[\\u2010-\\u2015\\u2212\\u30FC\\uFF0D\\u2500\\u02D7-]")
    private val SHAPE = Regex("(?<![0-9A-Z])([0-9OIlo]{2})\\s*-\\s*([0-9OIlo]{3})\\s*-\\s*([A-Z0-9])\\s*([0-9OIlo]{3})(?![0-9])")

    private fun digits(s: String) = s.map { when (it) { 'O', 'o' -> '0'; 'I', 'l' -> '1'; else -> it } }.joinToString("")
    private fun letter(c: Char) = when (c) { '0' -> 'O'; '1' -> 'I'; '8' -> 'B'; '5' -> 'S'; else -> c }

    /** Every card code in [text], in reading order, normalised to `NN-NNN-ANNN`. */
    fun findAll(text: String): List<String> {
        val t = DASHES.replace(Normalizer.normalize(text, Normalizer.Form.NFKC), "-").uppercase()
        return SHAPE.findAll(t).map { m ->
            "${digits(m.groupValues[1])}-${digits(m.groupValues[2])}-${letter(m.groupValues[3][0])}${digits(m.groupValues[4])}"
        }.distinct().toList()
    }

    /** Card letters the reader mixes up: L read as I (13-100-L101 came back as I101, owner's batch 2026-11-09), O, Q and D. */
    private val LOOKALIKE = listOf(setOf('I', 'L'), setOf('O', 'Q', 'D'))

    /** Other known codes that differ from [code] only by a look-alike card letter, to offer as "Or is it...?". */
    fun lookalikes(code: String, known: Set<String>): List<String> {
        if (code.length < 8) return emptyList()
        val i = code.length - 4
        val group = LOOKALIKE.firstOrNull { code[i] in it } ?: return emptyList()
        return group.filter { it != code[i] }.map { code.substring(0, i) + it + code.substring(i + 1) }.filter { it in known }
    }

    /**
     * Known codes close to a [code] that is not in the catalogue, for "Did you mean ...?": at most three, closest first.
     * The reader misreads a digit now and then (S23 run, 2026-11-10: 15-202-C001 came back as 15-207-C001). Each
     * differing character costs 1 (the card letter 2, it is printed large and rarely misread), up to 2 in all; ties
     * keep the same prefecture, then the same town.
     */
    fun nearest(code: String, known: Set<String>, max: Int = 3): List<String> {
        if (code.length != 11) return emptyList()
        fun cost(k: String): Int = if (k.length != code.length) 99 else
            k.indices.sumOf { i -> if (k[i] == code[i]) 0 else if (i == 7) 2 else 1 }
        return known.asSequence().filter { it != code }.map { it to cost(it) }.filter { it.second <= 2 }
            .sortedWith(compareBy<Pair<String, Int>> { it.second }.thenBy { if (it.first.take(2) == code.take(2)) 0 else 1 }
                .thenBy { if (it.first.take(6) == code.take(6)) 0 else 1 }.thenBy { it.first })
            .map { it.first }.take(max).toList()
    }

    /**
     * A code typed by hand, however it was typed ("15-202-a001", "15202A001", "15 202 A001"), as `NN-NNN-ANNN`;
     * null if it can't be one.
     */
    fun fromTyped(input: String): String? {
        val t = Normalizer.normalize(input, Normalizer.Form.NFKC).uppercase().filter { it.isLetterOrDigit() }
        val m = Regex("^([0-9]{2})([0-9]{3})([A-Z0-9])([0-9]{3})$").find(t) ?: return null
        return "${m.groupValues[1]}-${m.groupValues[2]}-${m.groupValues[3]}${m.groupValues[4]}"
    }

    /** The first code in [text] that is in [known] (catalogue codes), or else the first code found at all. */
    fun best(text: String, known: Set<String>): String? {
        val all = findAll(text)
        return all.firstOrNull { it in known } ?: all.firstOrNull()
    }
}
