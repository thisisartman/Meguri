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
            "New in 0.7.3", "0.7.3の新機能",
            listOf(
                "Shortcuts: tap Nearby again to search, hold + to scan, hold My book for Settings" to "ショートカット：「近く」をもう一度タップで検索、＋の長押しでスキャン、「コレクション」の長押しで設定",
                "Hold a tile to collect it, with Stamp and Ekitag for a station" to "タイルの長押しで集める（駅はスタンプとエキタグ）",
                "Tips the first time you open each page; Show tips again in Help and about" to "各ページを初めて開いたときにヒント。「ヘルプと情報」でもう一度表示",
                "After you collect, a nudge to photograph the place" to "集めたあとに、場所の写真を撮る提案",
                "Notifications in Settings: Alerts, Suggestions, Backups and Updates, each with its own switch" to "設定に「通知の種類」：近くの通知、提案、バックアップ、アップデートをそれぞれオン・オフ",
            ),
        ),
        Section(
            "New in 0.7.1", "0.7.1の新機能",
            listOf(
                "Places on Nearby: one page per station or shrine, with a ring that fills as you collect" to "近くに「場所」：駅や神社ごとに1ページ、集めるほど埋まるリング",
                "Edit a photo: crop, rotate, straighten" to "写真の編集：切り抜き、回転、傾き補正",
                "Add photos from a file, without replacing anything" to "ファイルから写真を追加（何も置き換えません）",
                "Cover directions, read off the manhole card" to "マンホールカードから蓋の場所へ道案内",
                "Long-press the icon for Add, My book and Trip" to "アイコンの長押しで追加・コレクション・トリップ",
            ),
        ),
        Section(
            "Fixed", "修正",
            listOf(
                "Every find has the date it was added" to "すべての記録に追加した日付が入ります",
                "Photos sort themselves: cards to the card, screenshots to the Ekitag" to "写真は自動で振り分け：カードはカードへ、スクリーンショットはエキタグへ",
                "Alert distances: each category's distance in one list" to "通知の距離：カテゴリーごとの距離を1つの一覧に",
                "Backups show their progress and when they finished" to "バックアップの進み具合と完了がわかります",
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
