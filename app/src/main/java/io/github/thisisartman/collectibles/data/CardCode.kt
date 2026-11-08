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

    /** The first code in [text] that is in [known] (catalogue codes), or else the first code found at all. */
    fun best(text: String, known: Set<String>): String? {
        val all = findAll(text)
        return all.firstOrNull { it in known } ?: all.firstOrNull()
    }
}
