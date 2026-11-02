package io.github.thisisartman.collectibles.ui

import android.app.LocaleManager
import android.content.Context
import android.os.Build
import android.os.LocaleList
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration

/**
 * App-level language (docs/DESIGN.md §6): English or Japanese, independent of the phone's language.
 * Uses Android 13+ per-app languages (both test phones); older phones follow the system language.
 */
object AppLanguage {
    /** On first start, pick English unless the user already chose a language for the app. */
    fun ensureDefault(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val lm = context.getSystemService(LocaleManager::class.java) ?: return
        if (lm.applicationLocales.isEmpty) lm.applicationLocales = LocaleList.forLanguageTags("en")
    }

    fun set(context: Context, japanese: Boolean) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        context.getSystemService(LocaleManager::class.java)?.applicationLocales =
            LocaleList.forLanguageTags(if (japanese) "ja" else "en")
    }
}

/** True when the app is showing Japanese. */
@Composable
fun isJapanese(): Boolean = LocalConfiguration.current.locales[0]?.language == "ja"
