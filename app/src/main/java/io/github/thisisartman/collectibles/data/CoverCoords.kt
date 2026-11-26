package io.github.thisisartman.collectibles.data

import java.text.Normalizer

/**
 * Reads a manhole cover's position from text: what the reader got from a scanned card (#203), or what you type or
 * paste in Edit details. Every card prints its cover's position, as `37°26'44.5"N 138°51'36.2"E`,
 * `N 37°26'44.5" E 138°51'36.2"`, `北緯37°26'44.5" 東経138°51'36.2"` or decimal `37.44569, 138.86007`.
 * OCR mixes up symbols (º or o for °, curly quotes, '' for "), drops some of them, and reads O for 0 and l for 1;
 * anything outside Japan is taken for a misread.
 */
object CoverCoords {
    data class LatLng(val lat: Double, val lng: Double)

    /** Japan with its outlying islands (Okinotorishima to Etorofu, Yonaguni to Minamitorishima). */
    fun inJapan(lat: Double, lng: Double): Boolean = lat in 20.0..46.0 && lng in 122.0..154.0

    private class Token(val at: Int, val value: Double, val hint: Char?)

    private val DMS = Regex(
        """(?:(?<![A-Z])([NSEW]))?\s*(?<![\d.])(\d{2,3})\s*(?:°|度|\s)\s*(\d{1,2})\s*(?:'|分|\s)\s*(\d{1,2}(?:\.\d{1,3})?)\s*(?:"|秒)?\s*([NSEW](?![A-Z]))?""",
    )
    private val DECIMAL = Regex("""(?:(?<![A-Z])([NSEW]))?\s*(?<![\d.])(\d{2,3}\.\d{3,})(?![\d.])\s*°?\s*([NSEW](?![A-Z]))?""")

    /** The first cover position in [text], or null when none reads as a place in Japan. */
    fun parse(text: String): LatLng? {
        val t = tidy(text)
        val tokens = mutableListOf<Token>()
        // Degrees, minutes and seconds first; what they used is blanked so their parts aren't read again as decimals
        val rest = StringBuilder(t)
        DMS.findAll(t).forEach { m ->
            val d = m.groupValues[2].toInt()
            val min = m.groupValues[3].toInt()
            val s = m.groupValues[4].toDouble()
            if (min < 60 && s < 60) {
                tokens += Token(m.range.first, d + min / 60.0 + s / 3600.0, hint(m.groupValues[1], m.groupValues[5]))
                for (i in m.range) rest.setCharAt(i, ' ')
            }
        }
        DECIMAL.findAll(rest).forEach { m ->
            tokens += Token(m.range.first, m.groupValues[2].toDouble(), hint(m.groupValues[1], m.groupValues[3]))
        }
        tokens.sortBy { it.at }
        return tokens.zipWithNext().firstNotNullOfOrNull { (a, b) -> pair(a, b) }
    }

    /** Typed or pasted by hand: the same forms as a card, plus a plain "lat, lng" with fewer decimals. */
    fun parseTyped(text: String): LatLng? = parse(text) ?: run {
        val m = Regex("""^\s*(-?\d{1,3}(?:\.\d+)?)\s*[,、\s]\s*(-?\d{1,3}(?:\.\d+)?)\s*$""").find(tidy(text)) ?: return null
        val a = m.groupValues[1].toDouble(); val b = m.groupValues[2].toDouble()
        when {
            inJapan(a, b) -> LatLng(a, b)
            inJapan(b, a) -> LatLng(b, a)
            else -> null
        }
    }

    private fun hint(lead: String, trail: String): Char? = (lead.ifEmpty { trail }).firstOrNull()

    /** Which of two numbers in a row is the latitude: the letters say, else Japan's bounds do. */
    private fun pair(a: Token, b: Token): LatLng? {
        fun sign(t: Token) = if (t.hint == 'S' || t.hint == 'W') -t.value else t.value
        val aLat = a.hint == 'N' || a.hint == 'S'
        val aLng = a.hint == 'E' || a.hint == 'W'
        val bLat = b.hint == 'N' || b.hint == 'S'
        val bLng = b.hint == 'E' || b.hint == 'W'
        if ((aLat && bLat) || (aLng && bLng)) return null
        val p = when {
            aLat || bLng -> LatLng(sign(a), sign(b))
            aLng || bLat -> LatLng(sign(b), sign(a))
            inJapan(a.value, b.value) -> LatLng(a.value, b.value)
            else -> LatLng(b.value, a.value)
        }
        return p.takeIf { inJapan(it.lat, it.lng) }
    }

    /** One way of writing everything: half-width, plain symbols, N/E for 北緯/東経, digits for O and l beside digits. */
    private fun tidy(text: String): String {
        var t = text.replace(Regex("[º˚ᵒ゜]"), "°").replace(Regex("[’‘′`´]"), "'").replace(Regex("[”“″〃]"), "\"")
        t = Normalizer.normalize(t, Normalizer.Form.NFKC)
        t = t.replace("北緯", "N").replace("南緯", "S").replace("東経", "E").replace("西経", "W")
            .replace("緯度", "N").replace("経度", "E")
        // A degree sign read as the letter o: "37o26'44.5"
        t = t.replace(Regex("""(\d{2,3})[oO](?=\s*[\dOoIl|]{1,2}\s*['分])"""), "$1°")
        t = t.replace(Regex("""(?<=[\d.])[Oo]|[Oo](?=[\d.])"""), "0")
        t = t.replace(Regex("""(?<=[\d.])[lI|]|[lI|](?=[\d.])"""), "1")
        t = t.replace("''", "\"")
        return t.uppercase()
    }
}
