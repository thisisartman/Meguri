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
                "No stamp here: mark a place where there was nothing to get" to "スタンプなし：何もなかった場所を記録",
                "Entry picture: tap a photo and choose it" to "表示写真：写真をタップして選択",
                "Proximity mode: the new name for Trip mode" to "近接モード：トリップモードの新しい名前",
                "Taps: a short vibration on pins, tiles and Collect" to "タップ：ピン・タイル・取得で短く振動",
            ),
        ),
        Section(
            "Fixed", "修正",
            listOf(
                "Opening after an update: no more flash of the welcome screen" to "更新後の起動：ようこそ画面が一瞬出なくなりました",
                "Google Maps links: shared places and station names match" to "Googleマップのリンク：共有した場所と駅名が一致",
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
        confirmButton = { TextButton(onClick = onDismiss) { Text(if (ja) "OK" else "Got it") } },
    )
}
