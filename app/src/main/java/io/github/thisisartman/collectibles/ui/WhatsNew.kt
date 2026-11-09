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
            "New in 0.4.0", "0.4.0の新機能",
            listOf(
                "Map: every pin is its picture, or its category icon; grey until collected. Crowded places show how many; tap to zoom in" to "地図：ピンは写真またはカテゴリーのアイコン。未取得はグレー。多い場所は数で表示、タップで拡大",
                "Collection check: choose a category and a prefecture, then swipe right for each one you have, left to skip" to "コレクションチェック：カテゴリーと都道府県を選び、持っているものは右へ、まだのものは左へスワイプ",
                "Scan a notebook page: Meguri finds each stamp, cleans it and reads the station; choose a date or leave it as had before Meguri" to "手帳のページをスキャン：スタンプを見つけて整え、駅名を読み取り。日付を選ぶか、Meguri以前のものとして保存",
                "Scan a card: when the code can't be read, Retake photo or Type code" to "カードのスキャン：コードが読めないときは撮り直しか入力",
                "Search: any part of a card code, either language, nearest first, in every search box" to "検索：カード番号の一部、どちらの言語でも、近い順。すべての検索で",
                "Stations: a station's stamp and its Ekitag are one tile; the badge fills in when you have the Ekitag" to "駅：駅スタンプとエキタグを1つのタイルに。エキタグを持つとバッジが塗られます",
                "Remove: an item you added, or a catalogue item from your collection (More options on its page)" to "削除：自分で追加したアイテム、またはコレクションから外す（アイテムの「その他のオプション」）",
                "Photos: name an Other photo; Remove asks first" to "写真：「その他」の写真に名前を。削除の前に確認",
            ),
        ),
        Section(
            "Settings", "設定",
            listOf(
                "Appearance: Phone, Light or Dark; Meguri or Wallpaper colours" to "表示：端末・ライト・ダーク、Meguriか壁紙の色",
                "Alerts: Off, Travelling or Always; one distance slider, with custom distances for any category" to "通知：オフ・移動中・常に。距離のスライダー1つ、カテゴリーごとのカスタム距離も",
                "Backup and data: weekly backup with or without photos, how many to keep, Photos kept in Meguri, your categories" to "バックアップとデータ：毎週のバックアップ（写真あり・なし、残す数）、Meguriに保存した写真、自分のカテゴリー",
                "About: credits with links to every source and licence, and a Privacy page" to "情報：すべての出典とライセンスへのリンク、プライバシー",
            ),
        ),
        Section(
            "Fixed", "修正",
            listOf(
                "Dates look the same everywhere" to "日付の表示をアプリ全体でそろえました",
                "Work started in Settings (backups, card pictures) keeps going when you leave" to "設定で始めた作業（バックアップ・カード画像）は画面を離れても続きます",
                "Distances on Nearby update as you move" to "「近く」の距離は移動に合わせて更新",
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
