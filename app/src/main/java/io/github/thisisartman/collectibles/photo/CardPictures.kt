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
    private const val UA = "Meguri/0.3 (personal collecting app for Android)"
    /** A pause between pictures, to be gentle with a site run by one person. */
    private const val GAP_MS = 400L

    private val _have = MutableStateFlow<Set<String>>(emptySet())
    /** Card codes with a picture on this phone. */
    val have: StateFlow<Set<String>> = _have.asStateFlow()

    private fun dir(context: Context) = File(context.filesDir, "cards").apply { mkdirs() }
    fun file(context: Context, code: String) = File(dir(context), "$code.jpg")
    fun uri(context: Context, code: String) = "file:" + file(context, code).absolutePath

    /** Reads which pictures are on the phone; called once at start. */
    fun load(context: Context) {
        _have.value = dir(context).listFiles()?.filter { it.name.endsWith(".jpg") && it.length() > 0 }?.map { it.name.removeSuffix(".jpg") }?.toSet().orEmpty()
    }

    /** A card code is safe as a file name and a query value: digits, letters and dashes only. */
    fun validCode(code: String) = Regex("^[A-Z]?-?\\d{2}-\\d{3}-[A-Z]\\d{3}$|^\\d{2}-\\d{3}-[A-Z]\\d{3}$").matches(code)

    /** Downloads what is missing, one by one. Returns (downloaded, failed). */
    suspend fun download(context: Context, codes: List<String>, onProgress: (done: Int, total: Int) -> Unit): Pair<Int, Int> = withContext(Dispatchers.IO) {
        val todo = codes.filter { validCode(it) && it !in _have.value }.distinct()
        var ok = 0; var failed = 0
        todo.forEachIndexed { i, code ->
            val got = runCatching {
                val c = (URL(SOURCE + code + ".jpg").openConnection() as HttpURLConnection).apply {
                    connectTimeout = 10_000; readTimeout = 15_000; setRequestProperty("User-Agent", UA)
                }
                val bytes = c.inputStream.use { it.readBytes() }
                // Only a real JPEG is kept (the site answers a missing card with something else)
                if (c.responseCode == 200 && bytes.size > 1000 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte()) {
                    val tmp = File(dir(context), "$code.jpg.tmp")
                    tmp.writeBytes(bytes)
                    tmp.renameTo(file(context, code))
                } else false
            }.getOrDefault(false)
            if (got) { ok++; _have.value = _have.value + code } else failed++
            withContext(Dispatchers.Main) { onProgress(i + 1, todo.size) }
            delay(GAP_MS)
        }
        ok to failed
    }

    fun removeAll(context: Context) {
        dir(context).listFiles()?.forEach { it.delete() }
        _have.value = emptySet()
    }

    fun sizeBytes(context: Context): Long = dir(context).listFiles()?.sumOf { it.length() } ?: 0L
}
