package io.github.thisisartman.collectibles.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.runtime.mutableStateOf
import io.github.thisisartman.collectibles.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

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
 * "Check for updates" in Help and about (#131), on by default for new installs since 0.7.2 (#222; an install from
 * before keeps the choice it had, off unless turned on). When on, asks GitHub at most once a day for the latest
 * release; if it is newer than this app, More shows a note with Update. Update downloads that release's APK
 * ([UpdateInstall]), checks it, and hands it to Android's installer; nothing is downloaded or installed without
 * that tap. Any failure of the daily check (offline, rate limit, bad answer) is silent; the next day tries again.
 */
object UpdateCheck {
    private const val PREFS = "update_check"
    private const val KEY_ON = "on"
    private const val KEY_LAST = "last_check"
    private const val KEY_TAG = "latest_tag"
    private const val KEY_URL = "latest_url"
    private const val KEY_APK_URL = "apk_url"
    private const val KEY_APK_SIZE = "apk_size"
    private const val KEY_APK_SHA = "apk_sha256"
    private const val DAY_MS = 24L * 60 * 60 * 1000
    private const val API = "https://api.github.com/repos/thisisartman/Meguri/releases/latest"
    private const val PAGES = "https://github.com/thisisartman/Meguri/releases/"
    /** Where a release's APK must come from; GitHub then sends it on from its own file server. */
    const val DOWNLOADS = "https://github.com/thisisartman/Meguri/releases/download/"

    /** A release's APK: where to get it, its size, and its SHA-256 from GitHub's asset digest (null when not given). */
    data class Apk(val url: String, val size: Long, val sha256: String?)

    /** A newer release: its tag ("v0.7.0"), its page, and its APK when it has one named Meguri-x.y.z.apk. */
    data class Release(val tag: String, val url: String, val apk: Apk? = null)

    /** The newer release to tell about, or null. */
    val available = mutableStateOf<Release?>(null)
    private val onState = mutableStateOf<Boolean?>(null)

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * Whether the switch starts on (#222): yes for an app installed fresh (installed and last updated at the same
     * moment), no for one updated from a version that had the switch, so an existing choice of off stands.
     */
    fun startsOn(firstInstall: Long, lastUpdate: Long): Boolean = firstInstall == lastUpdate

    fun on(context: Context): Boolean = onState.value ?: run {
        val p = prefs(context)
        if (!p.contains(KEY_ON)) {
            val info = runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull()
            // Decided once and kept: a later update must not read as a fresh install
            p.edit().putBoolean(KEY_ON, info != null && startsOn(info.firstInstallTime, info.lastUpdateTime)).apply()
        }
        p.getBoolean(KEY_ON, false)
    }.also { onState.value = it }

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
        val apk = p.getString(KEY_APK_URL, null)?.let { Apk(it, p.getLong(KEY_APK_SIZE, -1L), p.getString(KEY_APK_SHA, null)) }
        available.value = if (on(context) && tag != null && url != null && Versions.isNewer(tag, BuildConfig.VERSION_NAME)) Release(tag, url, apk) else null
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
        val e = p.edit().putString(KEY_TAG, release.tag).putString(KEY_URL, release.url)
        val a = release.apk
        if (a != null) e.putString(KEY_APK_URL, a.url).putLong(KEY_APK_SIZE, a.size).putString(KEY_APK_SHA, a.sha256)
        else e.remove(KEY_APK_URL).remove(KEY_APK_SIZE).remove(KEY_APK_SHA)
        e.apply()
        refreshNote(context)
    }

    private fun fetch(): Release? {
        val c = (URL(API).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000; readTimeout = 15_000
            setRequestProperty("User-Agent", "Meguri/${BuildConfig.VERSION_NAME} (Android)")
            setRequestProperty("Accept", "application/vnd.github+json")
        }
        try {
            if (c.responseCode != 200) return null
            return parse(JSONObject(c.inputStream.bufferedReader().use { it.readText() }))
        } finally {
            c.disconnect()
        }
    }

    /**
     * The release in GitHub's answer, or null. Its APK is the one asset named Meguri-<version>.apk for its tag
     * (one APK per release, as Obtainium expects), downloaded only from this repository's releases, with a size;
     * its SHA-256 is read from the asset's `digest` when given.
     */
    fun parse(o: JSONObject): Release? {
        val tag = o.optString("tag_name").ifBlank { return null }
        val url = o.optString("html_url").takeIf { it.startsWith(PAGES) } ?: return null
        val want = "Meguri-${tag.removePrefix("v")}.apk"
        val assets = o.optJSONArray("assets")
        var apk: Apk? = null
        if (assets != null) for (i in 0 until assets.length()) {
            val a = assets.optJSONObject(i) ?: continue
            if (a.optString("name") != want) continue
            val link = a.optString("browser_download_url").takeIf { it.startsWith(DOWNLOADS) } ?: continue
            val size = a.optLong("size", -1L).takeIf { it > 0 } ?: continue
            val sha = a.optString("digest").takeIf { it.startsWith("sha256:") }?.removePrefix("sha256:")?.lowercase()
                ?.takeIf { it.length == 64 && it.all { ch -> ch in '0'..'9' || ch in 'a'..'f' } }
            apk = Apk(link, size, sha)
        }
        return Release(tag, url, apk)
    }
}

/**
 * Update from inside the app (#222): the release's APK is downloaded with progress, checked, and handed to
 * Android's installer, which asks the user once to allow Meguri to install apps. Checked before it is offered:
 * its size, its SHA-256 when GitHub gives one, that it is this app (same package), and that it is signed with the
 * same certificate as the app installed, so only a real Meguri release can replace this one.
 */
object UpdateInstall {
    sealed interface State {
        data class Downloading(val done: Long, val total: Long) : State
        data object Checking : State
        data class Ready(val file: File) : State
        data class Failed(val why: Why) : State
    }

    /** What went wrong, for the note: the download, the check of size and SHA-256, or not this app's signature. */
    enum class Why { DOWNLOAD, CHECK, NOT_THIS_APP }

    val state = mutableStateOf<State?>(null)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Starts downloading [release]'s APK; one at a time. */
    fun start(context: Context, release: UpdateCheck.Release) {
        val apk = release.apk ?: return
        val s = state.value
        if (s is State.Downloading || s is State.Checking) return
        val app = context.applicationContext
        state.value = State.Downloading(0, apk.size)
        scope.launch {
            state.value = runCatching { fetchAndCheck(app, release.tag, apk) }.getOrElse { State.Failed(Why.DOWNLOAD) }
        }
    }

    private fun fetchAndCheck(context: Context, tag: String, apk: UpdateCheck.Apk): State {
        val dir = File(context.cacheDir, "updates").apply { deleteRecursively(); mkdirs() }
        val file = File(dir, "Meguri-${tag.removePrefix("v")}.apk")
        val part = File(dir, file.name + ".part")
        val md = MessageDigest.getInstance("SHA-256")
        val c = (URL(apk.url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000; readTimeout = 30_000; instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Meguri/${BuildConfig.VERSION_NAME} (Android)")
        }
        try {
            if (c.responseCode != 200 || c.url.protocol != "https") return State.Failed(Why.DOWNLOAD)
            c.inputStream.use { input ->
                part.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var n = 0L
                    var shown = 0L
                    while (true) {
                        val r = input.read(buf)
                        if (r < 0) break
                        n += r
                        // Never more than the release says it is
                        if (n > apk.size) return State.Failed(Why.CHECK)
                        out.write(buf, 0, r)
                        md.update(buf, 0, r)
                        if (n - shown > 256 * 1024) { state.value = State.Downloading(n, apk.size); shown = n }
                    }
                }
            }
        } finally {
            c.disconnect()
        }
        state.value = State.Checking
        val sha = md.digest().joinToString("") { "%02x".format(it) }
        if (!sizeAndHashOk(part.length(), sha, apk)) { part.delete(); return State.Failed(Why.CHECK) }
        if (!part.renameTo(file)) return State.Failed(Why.DOWNLOAD)
        if (!sameApp(context, file)) { file.delete(); return State.Failed(Why.NOT_THIS_APP) }
        return State.Ready(file)
    }

    /** The download is the release's: its size, and its SHA-256 when GitHub gave one. Pure, for tests. */
    fun sizeAndHashOk(size: Long, sha256: String, apk: UpdateCheck.Apk): Boolean =
        size == apk.size && (apk.sha256 == null || apk.sha256.equals(sha256, ignoreCase = true))

    /** Signed by the same certificates: both known and the same. Pure, for tests. */
    fun sameSigners(installed: Set<String>, download: Set<String>): Boolean = installed.isNotEmpty() && installed == download

    /** The download is this app (same package) signed with the installed app's certificate. */
    private fun sameApp(context: Context, file: File): Boolean {
        val pm = context.packageManager
        val flags = PackageManager.GET_SIGNING_CERTIFICATES
        val theirs = pm.getPackageArchiveInfo(file.path, flags) ?: return false
        if (theirs.packageName != context.packageName) return false
        val ours = pm.getPackageInfo(context.packageName, flags)
        return sameSigners(certs(ours), certs(theirs))
    }

    /** SHA-256 of the signing certificates in use: all of them for several signers, else the current one. */
    private fun certs(info: android.content.pm.PackageInfo): Set<String> {
        val si = info.signingInfo ?: return emptySet()
        val sigs = if (si.hasMultipleSigners()) si.apkContentsSigners.orEmpty().toList() else listOfNotNull(si.signingCertificateHistory?.lastOrNull())
        return sigs.map { s -> MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) } }.toSet()
    }

    /**
     * Opens Android's installer on the checked file; the user confirms there. If Meguri may not install apps yet,
     * opens the setting to allow it instead (Android asks once); then Install is tapped again.
     */
    fun install(context: Context) {
        val ready = state.value as? State.Ready ?: return
        if (!context.packageManager.canRequestPackageInstalls()) {
            runCatching {
                context.startActivity(
                    Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, android.net.Uri.parse("package:${context.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
            return
        }
        val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.files", ready.file)
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    /** Back to the note after a failure, so Update can be tried again. */
    fun clear() { state.value = null }
}
