package io.github.thisisartman.collectibles.ui

import androidx.annotation.StringRes
import io.github.thisisartman.collectibles.R
import io.github.thisisartman.collectibles.data.Item
import io.github.thisisartman.collectibles.data.ItemType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** English mode: romaji everywhere (Japanese if no romaji); Japanese mode: Japanese. */
fun Item.displayName(japanese: Boolean): String = if (japanese) nameJa else nameRomaji ?: nameJa

/** The other script, shown on the item page in English mode only. */
fun Item.secondaryName(japanese: Boolean): String? = if (japanese) null else nameJa.takeIf { nameRomaji != null && it != nameRomaji }

@StringRes
fun ItemType.label(): Int = when (this) {
    ItemType.MANHOLE_CARD -> R.string.type_manhole
    ItemType.GOSHUIN -> R.string.type_goshuin
    ItemType.EKI_STAMP -> R.string.type_eki
    ItemType.EKITAG -> R.string.type_ekitag
}

/** Short text inside a seal: the design code for manhole cards, else the start of the name. */
fun Item.sealLabel(japanese: Boolean): String {
    if (type == ItemType.MANHOLE_CARD && code != null) return code.substringAfterLast('-').take(4)
    val name = displayName(japanese).removeSuffix("駅").removeSuffix("-eki").removeSuffix(" Station")
    return if (japanese || nameRomaji == null) name.take(2) else name.filter { it.isLetter() }.take(3).uppercase()
}

fun formatDistance(meters: Double): Pair<Int, String> =
    if (meters < 1000) R.string.m to (Math.round(meters / 10.0) * 10).toString()
    else R.string.km to if (meters < 10_000) String.format(Locale.US, "%.1f", meters / 1000) else Math.round(meters / 1000).toString()

fun formatDate(ms: Long, japanese: Boolean): String =
    SimpleDateFormat(if (japanese) "yyyy/MM/dd" else "yyyy-MM-dd", Locale.US).format(Date(ms))

fun formatDateTime(ms: Long, japanese: Boolean): String =
    SimpleDateFormat(if (japanese) "yyyy/MM/dd HH:mm" else "yyyy-MM-dd HH:mm", Locale.US).format(Date(ms))

fun formatTime(ms: Long): String = SimpleDateFormat("HH:mm", Locale.US).format(Date(ms))

/** Search: case-insensitive, macrons and long-vowel spellings folded (Jōetsu = Joetsu = Jouetsu). */
fun foldForSearch(s: String): String = s.lowercase(Locale.ROOT)
    .replace('ō', 'o').replace('ū', 'u').replace('ā', 'a').replace('ē', 'e').replace('ī', 'i')
    .replace("ou", "o").replace("uu", "u").replace("oo", "o")
    .replace(Regex("[\\s\\-・()（）]"), "")

fun Item.matches(query: String): Boolean {
    val q = foldForSearch(query)
    if (q.isEmpty()) return true
    return listOfNotNull(nameJa, nameRomaji, code, municipality, placeName).any { foldForSearch(it).contains(q) }
}
