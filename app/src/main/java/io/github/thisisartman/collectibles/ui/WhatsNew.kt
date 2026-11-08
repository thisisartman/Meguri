package io.github.thisisartman.collectibles.ui

import android.content.Context
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.thisisartman.collectibles.BuildConfig

/**
 * "What's new" on the first open after an update (#77), in the owner's changelog format (2026-11-07): short sections,
 * one item per line, each starting with the feature's name. Never shown on a fresh install.
 */
object WhatsNew {
    private const val PREFS = "whats_new"
    private const val KEY_SEEN = "seen_version_code"

    class Section(val en: String, val ja: String, val items: List<Pair<String, String>>)

    /** Notes for this version; empty means nothing to show. */
    val current: List<Section> = listOf(
        Section(
            "New", "新機能",
            listOf(
                "Opening: a map of Japan that flies to you and what is near" to "起動：日本地図から現在地と近くのものへ",
                "My book: one tile per type, each opens its collections with progress rings" to "マイブック：種類ごとのタイル。開くと各コレクションの進み具合",
                "Share to Meguri: share photos from your gallery, grouped by place and date, then pick what each is" to "Meguriに共有：ギャラリーの写真を共有すると、場所と日付でまとめて登録",
                "Edit details: names, place, hours, notes and position of any item; kept through catalogue updates" to "詳細の編集：名前・場所・時間・メモ・位置。カタログ更新でも残ります",
                "Photo suggestions: a photo taken near something you haven't collected offers to attach itself (Settings, Alerts; off until you turn it on)" to "写真の提案：未取得のものの近くで撮った写真を追加するよう提案（設定のアラートでオン）",
                "Photos: tap one to see it large, then full screen with zoom" to "写真：タップで大きく、全画面でズームも",
                "Timeline: everything you collected, by day (My book)" to "タイムライン：集めたものを日ごとに（マイブック）",
                "Filters: To get / Have and card series in each list" to "絞り込み：各リストで未取得・取得済み・弾",
                "Map: a flat look, the same online and offline" to "地図：オンラインでもオフラインでも同じ、すっきりした見た目",
                "Menu: on My book (⋮); the search bar is only search" to "メニュー：マイブックの「⋮」に。検索バーは検索だけに",
                "Ekitag: Niigata's Ekitag stations listed" to "エキタグ：新潟のエキタグ駅を追加",
                "Stations: JR East stamps for Saitama and Tokyo (217 stations, 19 lines); the Joetsu Shinkansen from Tokyo" to "駅：埼玉・東京のJR東日本の駅スタンプ（217駅・19路線）、上越新幹線を東京から",
                "Roadside stations: a new type, 64 michi-no-eki stamps in Niigata, Saitama and Tokyo" to "道の駅：新しい種類。新潟・埼玉・東京の64駅のスタンプ",
                "Dam cards: Saitama and Tokyo added (14)" to "ダムカード：埼玉・東京を追加（14）",
                "Photos on an item page: smaller squares, all on one line" to "アイテムの写真：小さめの枠で1行に",
            ),
        ),
        Section(
            "Fixed", "修正",
            listOf(
                "Niigata: station order and positions checked; Kamitokoro added" to "新潟：駅の順番と位置を確認、上所駅を追加",
                "Photos: a picture that can't be read shows the seal, not a grey box" to "写真：読めない写真は灰色ではなく印で表示",
                "Pin card: icons all one size" to "ピンのカード：アイコンを同じ大きさに",
            ),
        ),
        Section(
            "Faster", "高速化",
            listOf(
                "Opening: the catalogue isn't read again on every start" to "起動：毎回カタログを読み直さなくなりました",
                "Loading a region: written in one go" to "地域の読み込み：一度にまとめて保存",
            ),
        ),
    )

    fun shouldShow(context: Context): Boolean {
        if (current.isEmpty()) return false
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val seen = prefs.getLong(KEY_SEEN, -1L)
        if (seen >= BuildConfig.VERSION_CODE) return false
        val info = runCatching {
            if (Build.VERSION.SDK_INT >= 33) context.packageManager.getPackageInfo(context.packageName, android.content.pm.PackageManager.PackageInfoFlags.of(0))
            else @Suppress("DEPRECATION") context.packageManager.getPackageInfo(context.packageName, 0)
        }.getOrNull()
        // A fresh install has nothing "new" to tell: remember this version quietly
        if (info != null && info.firstInstallTime == info.lastUpdateTime) { markSeen(context); return false }
        return true
    }

    fun markSeen(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putLong(KEY_SEEN, BuildConfig.VERSION_CODE.toLong()).apply()
    }
}

@Composable
fun WhatsNewDialog(onDismiss: () -> Unit) {
    val ja = isJapanese()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text((if (ja) "新しくなったこと · " else "What's new · ") + BuildConfig.VERSION_NAME) },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                WhatsNew.current.forEach { s ->
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(if (ja) s.ja else s.en, style = MaterialTheme.typography.titleSmall)
                        s.items.forEach { (en, jp) -> Text("•  " + if (ja) jp else en, style = MaterialTheme.typography.bodyMedium) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text(if (ja) "OK" else "Got it") } },
    )
}
