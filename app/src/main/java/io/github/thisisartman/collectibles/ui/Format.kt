package io.github.thisisartman.collectibles.ui

import androidx.annotation.StringRes
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
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

private val PREFECTURES_EN = mapOf(
    "北海道" to "Hokkaido", "青森県" to "Aomori", "岩手県" to "Iwate", "宮城県" to "Miyagi", "秋田県" to "Akita",
    "山形県" to "Yamagata", "福島県" to "Fukushima", "茨城県" to "Ibaraki", "栃木県" to "Tochigi", "群馬県" to "Gunma",
    "埼玉県" to "Saitama", "千葉県" to "Chiba", "東京都" to "Tokyo", "神奈川県" to "Kanagawa", "新潟県" to "Niigata",
    "富山県" to "Toyama", "石川県" to "Ishikawa", "福井県" to "Fukui", "山梨県" to "Yamanashi", "長野県" to "Nagano",
    "岐阜県" to "Gifu", "静岡県" to "Shizuoka", "愛知県" to "Aichi", "三重県" to "Mie", "滋賀県" to "Shiga",
    "京都府" to "Kyoto", "大阪府" to "Osaka", "兵庫県" to "Hyogo", "奈良県" to "Nara", "和歌山県" to "Wakayama",
    "鳥取県" to "Tottori", "島根県" to "Shimane", "岡山県" to "Okayama", "広島県" to "Hiroshima", "山口県" to "Yamaguchi",
    "徳島県" to "Tokushima", "香川県" to "Kagawa", "愛媛県" to "Ehime", "高知県" to "Kochi", "福岡県" to "Fukuoka",
    "佐賀県" to "Saga", "長崎県" to "Nagasaki", "熊本県" to "Kumamoto", "大分県" to "Oita", "宮崎県" to "Miyazaki",
    "鹿児島県" to "Kagoshima", "沖縄県" to "Okinawa",
)

/** Prefecture in the current language (romaji in English mode; unknown names unchanged). */
fun prefectureName(ja: String, japanese: Boolean): String = if (japanese) ja else PREFECTURES_EN[ja] ?: ja

@StringRes
fun ItemType.label(): Int = when (this) {
    ItemType.MANHOLE_CARD -> R.string.type_manhole
    ItemType.GOSHUIN -> R.string.type_goshuin
    ItemType.EKI_STAMP -> R.string.type_eki
    ItemType.EKITAG -> R.string.type_ekitag
    ItemType.CUSTOM -> R.string.type_custom
}

/** Short text inside a seal: the design code for manhole cards, else the start of the name. */
fun Item.sealLabel(japanese: Boolean): String {
    if (type == ItemType.MANHOLE_CARD && code != null) return code.substringAfterLast('-').take(4)
    val name = displayName(japanese).removeSuffix("駅").removeSuffix("-eki").removeSuffix(" Station")
    return if (japanese || nameRomaji == null) name.take(2) else name.filter { it.isLetter() }.take(3).uppercase()
}

fun formatDistance(meters: Double): Pair<Int, String> =
    if (meters < HERE_M) R.string.here to ""
    else if (meters < 1000) R.string.m to (Math.round(meters / 10.0) * 10).toString()
    else R.string.km to if (meters < 10_000) String.format(Locale.US, "%.1f", meters / 1000) else Math.round(meters / 1000).toString()

/** "21 finds, 119 items, 1 photo" in the app's language; photos left out when null. */
fun countsText(res: android.content.res.Resources, finds: Int, items: Int, photos: Int? = null): String =
    listOfNotNull(
        res.getQuantityString(R.plurals.finds, finds, finds),
        res.getQuantityString(R.plurals.items, items, items),
        photos?.let { res.getQuantityString(R.plurals.photos, it, it) },
    ).joinToString(res.getString(R.string.count_sep))

/** What a screen reader says for a control, when its visible label alone would not say which item it acts on. */
fun androidx.compose.ui.Modifier.spoken(text: String): androidx.compose.ui.Modifier =
    this.then(androidx.compose.ui.Modifier.semantics { contentDescription = text })

/** "Collected" or "Still to get", read with a row or a stamp slot. */
fun androidx.compose.ui.Modifier.collectedState(text: String): androidx.compose.ui.Modifier =
    this.then(androidx.compose.ui.Modifier.semantics { stateDescription = text })

/**
 * The catalogue's station notes are one of two fixed English sentences (the generator wrote them, and an older
 * backup carries the earlier one). Japanese mode shows them in Japanese; any other note is shown as written.
 * A proper fix is a Japanese notes column in the catalogue (open item, schema v2).
 */
private val GENERATED_STATION_NOTES = setOf(
    "Paper stamp not confirmed at this station. Map data © OpenStreetMap contributors.",
    "Station position from OpenStreetMap (ODbL). Whether this station has a paper stamp is not checked.",
)

// Shown in place of the generator's notes, in the app's own voice (the OpenStreetMap credit is in About)
private const val STATION_NOTE_EN = "Not every station has a stamp. If you can't see one, ask at the ticket gate."
private const val STATION_NOTE_JA = "スタンプがない駅もあります。見当たらないときは改札で聞いてみてください。"

fun localNote(note: String, japanese: Boolean): String =
    if (note.trim() in GENERATED_STATION_NOTES) (if (japanese) STATION_NOTE_JA else STATION_NOTE_EN) else note

/** The separator between parts of a line: " · " in English, "・" in Japanese. */
@androidx.compose.runtime.Composable
fun sep(): String = androidx.compose.ui.res.stringResource(R.string.sep)

fun formatDate(ms: Long, japanese: Boolean): String =
    SimpleDateFormat(if (japanese) "yyyy/MM/dd" else "yyyy-MM-dd", Locale.US).format(Date(ms))

fun formatDateTime(ms: Long, japanese: Boolean): String =
    SimpleDateFormat(if (japanese) "yyyy/MM/dd HH:mm" else "yyyy-MM-dd HH:mm", Locale.US).format(Date(ms))

fun formatTime(ms: Long): String = SimpleDateFormat("HH:mm", Locale.US).format(Date(ms))

/** Closer than this, a distance reads "here": GPS is not more exact than that. */
const val HERE_M = 25.0

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

/** The plural name of a kind, for filters and section titles. */
fun ItemType.pluralLabel(): Int = when (this) {
    ItemType.MANHOLE_CARD -> R.string.type_manhole_plural
    ItemType.EKI_STAMP -> R.string.type_eki_plural
    else -> label()
}
