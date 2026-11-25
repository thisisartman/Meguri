package io.github.thisisartman.collectibles.photo

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Manhole card pictures (#53; owner 2026-11-08: "download onto my phone from the card site, greyed until collected").
 * The pictures belong to GKP and each city, so they never ship in the app or go into backups: the owner's phone
 * fetches them on request into its own storage, one at a time, from Eki kara Manhole's card images.
 */
object CardPictures {
    private const val SOURCE = "https://ekikaramanhole.whitebeach.org/ext/manholecard/image/getimage.cgi?file=card/"
    /** A pause between pictures, to be gentle with a site run by one person. */
    private const val GAP_MS = 400L
    /** A card the site has no picture of is asked about again after a week, not on every download (#160). */
    private const val RECHECK_MS = 7L * 24 * 60 * 60 * 1000

    private val _have = MutableStateFlow<Set<String>>(emptySet())
    /** Card codes with a picture on this phone. */
    val have: StateFlow<Set<String>> = _have.asStateFlow()

    private val _none = MutableStateFlow<Map<String, Long>>(emptyMap())
    /** Card codes the site has no picture of yet (it sends its "image coming soon" picture), with when that was seen. */
    val none: StateFlow<Map<String, Long>> = _none.asStateFlow()
    private fun prefs(context: Context) = context.getSharedPreferences("card_pictures", Context.MODE_PRIVATE)

    private fun dir(context: Context) = File(context.filesDir, "cards").apply { mkdirs() }
    fun file(context: Context, code: String) = File(dir(context), "$code.jpg")
    fun uri(context: Context, code: String) = "file:" + file(context, code).absolutePath

    /**
     * The site's "画像準備中" (image coming soon) picture, sent for cards it has no picture of yet: the same 23,944 bytes
     * every time (13 cards, 2026-11-13). It is not the card, so it is never kept.
     */
    private const val PLACEHOLDER_MD5 = "34fb7a385ad29a719ffef53af4270a8a"
    fun isPlaceholder(bytes: ByteArray) = bytes.size == 23944 &&
        java.security.MessageDigest.getInstance("MD5").digest(bytes).joinToString("") { "%02x".format(it) } == PLACEHOLDER_MD5

    /** Reads which pictures are on the phone; called once at start. Placeholders saved before 0.5 are deleted. */
    fun load(context: Context) {
        dir(context).listFiles()?.filter { it.length() == 23944L && isPlaceholder(it.readBytes()) }?.forEach { it.delete() }
        _have.value = dir(context).listFiles()?.filter { it.name.endsWith(".jpg") && it.length() > 0 }?.map { it.name.removeSuffix(".jpg") }?.toSet().orEmpty()
        _none.value = prefs(context).all.mapNotNull { (k, v) -> (v as? Long)?.let { k to it } }.toMap()
    }

    /** A card code is safe as a file name and a query value: digits, letters and dashes only. */
    fun validCode(code: String) = Regex("^[A-Z]?-?\\d{2}-\\d{3}-[A-Z]\\d{3}$|^\\d{2}-\\d{3}-[A-Z]\\d{3}$").matches(code)

    /** Codes still to fetch: valid, not on the phone, and not a card the site had no picture of in the last week. */
    fun toFetch(codes: Collection<String>, now: Long = System.currentTimeMillis()): List<String> =
        codes.filter { validCode(it) && it !in _have.value && (_none.value[it]?.let { seen -> now - seen > RECHECK_MS } ?: true) }.distinct()

    /** The result of a download: pictures saved, cards the site has no picture of yet, and requests that failed. */
    data class Result(val downloaded: Int, val notOnSite: Int, val failed: Int)

    /** Downloads what is missing, one by one. */
    suspend fun download(context: Context, codes: List<String>, onProgress: (done: Int, total: Int) -> Unit): Result = withContext(Dispatchers.IO) {
        val todo = toFetch(codes)
        var ok = 0; var notOnSite = 0; var failed = 0
        todo.forEachIndexed { i, code ->
            val outcome = runCatching {
                val c = (URL(SOURCE + code + ".jpg").openConnection() as HttpURLConnection).apply {
                    connectTimeout = 10_000; readTimeout = 15_000
                    setRequestProperty("User-Agent", io.github.thisisartman.collectibles.data.UserAgent.value)
                }
                val bytes = c.inputStream.use { it.readBytes() }
                when {
                    // The site's "image coming soon": the card is known, its picture isn't there yet. Remembered, so
                    // it isn't counted as a failure and "Download" doesn't offer it again for a week (#160)
                    isPlaceholder(bytes) -> 'n'
                    // Only a real JPEG is kept (the site answers a missing card with something else)
                    c.responseCode == 200 && bytes.size > 1000 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() -> {
                        val tmp = File(dir(context), "$code.jpg.tmp")
                        tmp.writeBytes(bytes)
                        if (tmp.renameTo(file(context, code))) 'y' else 'f'
                    }
                    else -> 'f'
                }
            }.getOrDefault('f')
            when (outcome) {
                'y' -> {
                    ok++; _have.value = _have.value + code
                    if (code in _none.value) { _none.value = _none.value - code; prefs(context).edit().remove(code).apply() }
                }
                'n' -> {
                    notOnSite++
                    val now = System.currentTimeMillis()
                    _none.value = _none.value + (code to now); prefs(context).edit().putLong(code, now).apply()
                }
                else -> failed++
            }
            withContext(Dispatchers.Main) { onProgress(i + 1, todo.size) }
            delay(GAP_MS)
        }
        Result(ok, notOnSite, failed)
    }

    fun removeAll(context: Context) {
        dir(context).listFiles()?.forEach { it.delete() }
        _have.value = emptySet()
        prefs(context).edit().clear().apply()
        _none.value = emptyMap()
    }

    fun sizeBytes(context: Context): Long = dir(context).listFiles()?.sumOf { it.length() } ?: 0L
}
