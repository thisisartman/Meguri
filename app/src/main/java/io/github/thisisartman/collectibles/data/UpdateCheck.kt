package io.github.thisisartman.collectibles.data

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import io.github.thisisartman.collectibles.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Version numbers like "v0.6.0" or "0.6.0-dev", compared by MAJOR.MINOR.PATCH (#131). Pure, so it is unit tested. */
object Versions {
    private val NUMBER = Regex("""\d{1,6}""")

    /** [major, minor, patch] from "v0.6.0", "0.6.0-devrelease" or "0.7.0-beta.9"; null when it is not a version. */
    fun parse(text: String): List<Int>? {
        val core = text.trim().removePrefix("v").removePrefix("V").substringBefore('-').substringBefore('+')
        val parts = core.split('.')
        if (parts.size != 3 || parts.any { !NUMBER.matches(it) }) return null
        return parts.map { it.toInt() }
    }

    /** Whether [latest] is a newer version than [installed]. Anything that does not read as a version is not newer. */
    fun isNewer(latest: String, installed: String): Boolean {
        val a = parse(latest) ?: return false
        val b = parse(installed) ?: return false
        for (i in 0..2) if (a[i] != b[i]) return a[i] > b[i]
        return false
    }
}

/**
 * "Check for updates" in Help and about (#131), off by default. When on, asks GitHub at most once a day for the
 * latest release; if it is newer than this app, More shows a small note with Update, which opens the release page.
 * Nothing is downloaded or installed. Any failure (offline, rate limit, bad answer) is silent; the next day tries again.
 */
object UpdateCheck {
    private const val PREFS = "update_check"
    private const val KEY_ON = "on"
    private const val KEY_LAST = "last_check"
    private const val KEY_TAG = "latest_tag"
    private const val KEY_URL = "latest_url"
    /** The last release a notification told about. */
    private const val KEY_TOLD = "told_tag"
    private const val DAY_MS = 24L * 60 * 60 * 1000
    private const val API = "https://api.github.com/repos/thisisartman/Meguri/releases/latest"

    /** A newer release: its tag ("v0.7.0") and its page. */
    data class Release(val tag: String, val url: String)

    /** The newer release to tell about, or null. */
    val available = mutableStateOf<Release?>(null)
    private val onState = mutableStateOf<Boolean?>(null)

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun on(context: Context): Boolean = onState.value ?: prefs(context).getBoolean(KEY_ON, false).also { onState.value = it }

    fun set(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean(KEY_ON, on).apply()
        onState.value = on
        refreshNote(context)
    }

    /** The note from the last answer, kept between days so it does not need asking again. */
    fun refreshNote(context: Context) {
        val p = prefs(context)
        val tag = p.getString(KEY_TAG, null)
        val url = p.getString(KEY_URL, null)
        available.value = if (on(context) && tag != null && url != null && Versions.isNewer(tag, BuildConfig.VERSION_NAME)) Release(tag, url) else null
    }

    /** Asks GitHub when the switch is on and the last ask was a day or more ago. */
    suspend fun checkIfDue(context: Context) {
        refreshNote(context)
        if (!on(context)) return
        val p = prefs(context)
        val now = System.currentTimeMillis()
        val last = p.getLong(KEY_LAST, 0L)
        if (now - last in 0 until DAY_MS) return
        // Counted as tried even if it fails, so a failure waits until the next day
        p.edit().putLong(KEY_LAST, now).apply()
        val release = withContext(Dispatchers.IO) { runCatching { fetch() }.getOrNull() } ?: return
        p.edit().putString(KEY_TAG, release.tag).putString(KEY_URL, release.url).apply()
        refreshNote(context)
        // Each newer release told about once in a notification too, under Updates (#228)
        if (Versions.isNewer(release.tag, BuildConfig.VERSION_NAME) && p.getString(KEY_TOLD, null) != release.tag) {
            p.edit().putString(KEY_TOLD, release.tag).apply()
            io.github.thisisartman.collectibles.alerts.AlertNotifier(context.applicationContext).notifyUpdate(release.tag, release.url)
        }
    }

    private fun fetch(): Release? {
        val c = (URL(API).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000; readTimeout = 15_000
            setRequestProperty("User-Agent", "Meguri/${BuildConfig.VERSION_NAME} (Android)")
            setRequestProperty("Accept", "application/vnd.github+json")
        }
        try {
            if (c.responseCode != 200) return null
            val o = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
            val tag = o.optString("tag_name").ifBlank { return null }
            val url = o.optString("html_url").takeIf { it.startsWith("https://github.com/") } ?: return null
            return Release(tag, url)
        } finally {
            c.disconnect()
        }
    }
}
