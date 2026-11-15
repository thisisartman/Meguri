package io.github.thisisartman.collectibles.ui

import android.content.Context
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
            "New in 0.5.0", "0.5.0の新機能",
            listOf(
                "Regions: each prefecture is its own download (Settings, Catalogue, Regions): its cards and stamps, and an offline map if you want one. The app itself is much smaller" to "地域：都道府県ごとにダウンロード（設定 › カタログ › 地域）。カード・スタンプと、必要ならオフライン地図。アプリ本体はかなり小さくなりました",
                "Places: for a spot with several things to collect, like a mountain with a stamp and a goshuin. Add one with Add, Category, Place" to "スポット：スタンプと御朱印がある山など、いくつも集められる場所に。追加 › カテゴリー › スポット",
                "Change type: the pencil on something you added has a Type row (a station stamp can become a Place; photos stay)" to "種類の変更：自分で追加したものの鉛筆ボタンに「種類」。駅スタンプをスポットに変えられます（写真はそのまま）",
                "Opening hours: Open now, Opens at or Closed today, with holidays and the closed-Monday rule; Trip warns about closed stops" to "営業時間：営業中・何時から・本日休みを表示。祝日と月曜休みのルールにも対応。旅程では休みの場所をお知らせ",
                "Send feedback: a note and screenshots by email (Settings, Help and about)" to "フィードバック：メモとスクリーンショットをメールで（設定 › ヘルプと情報）",
                "One station page: a station's stamp and its Ekitag together, each with its own Collect" to "駅のページを1つに：駅スタンプとエキタグを一緒に。それぞれ「集めた」ボタン付き",
            ),
        ),
        Section(
            "Better", "改善",
            listOf(
                "Scan: a misread card code offers the closest cards and Type code" to "スキャン：読み違えたカード番号には近いカードと入力を提案",
                "Map: category icons when zoomed out, pictures up close, road numbers on the offline map, smoother groups" to "地図：縮小時はカテゴリーのアイコン、拡大で写真。オフライン地図に道路番号。グループの動きもなめらかに",
                "Motion: pages and steps slide and fade instead of jumping" to "動き：ページや手順が急に切り替わらず、スライド・フェードします",
                "Ekitag pictures: a saved stamp image becomes the whole stamp on its tile" to "エキタグの画像：保存したスタンプ画像がタイルいっぱいに表示されます",
                "My book: started prefectures and lines first" to "マイブック：集め始めた都道府県・路線を先頭に",
            ),
        ),
        Section(
            "Fixed", "修正",
            listOf(
                "Card pictures: the site's \"image coming soon\" picture is no longer kept as a card" to "カード画像：サイトの「画像準備中」をカード画像として保存しません",
                "A card's code shows once on its page" to "カードのページで番号が二重に表示されなくなりました",
                "Your collection, photos and edits carry over unchanged" to "コレクション・写真・編集内容はそのまま引き継がれます",
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
    // Laid out like AFinity's changelog (owner, 2026-11-10: "difficult to tell whether the changelog is scrollable"):
    // an icon, the title and the version in a pill, then the notes in an inset box whose rounded edge cuts them off,
    // so it reads as a box that scrolls; Close at the bottom
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        androidx.compose.material3.Surface(
            shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 8.dp), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                androidx.compose.material3.Icon(
                    androidx.compose.ui.res.painterResource(io.github.thisisartman.collectibles.R.drawable.ic_new_releases),
                    contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp),
                )
                Spacer(Modifier.height(12.dp))
                Text(if (ja) "新しくなったこと" else "What's new", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(10.dp))
                androidx.compose.material3.Surface(
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                ) {
                    Text(
                        BuildConfig.VERSION_NAME, Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
                Spacer(Modifier.height(16.dp))
                androidx.compose.material3.Surface(
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
                    // A visibly different tone from the dialog (the lowest container was the same white in light mode)
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                ) {
                    Column(
                        Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        WhatsNew.current.forEach { s ->
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(if (ja) s.ja else s.en, style = MaterialTheme.typography.titleMedium)
                                // Bullets hang: a wrapped line starts under the text, not under the dot
                                s.items.forEach { (en, jp) ->
                                    androidx.compose.foundation.layout.Row {
                                        Text("•", Modifier.width(16.dp), style = MaterialTheme.typography.bodyMedium)
                                        Text(if (ja) jp else en, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                            }
                        }
                    }
                }
                androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth().padding(top = 4.dp), contentAlignment = androidx.compose.ui.Alignment.CenterEnd) {
                    TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text(if (ja) "閉じる" else "Close") }
                }
            }
        }
    }
}
