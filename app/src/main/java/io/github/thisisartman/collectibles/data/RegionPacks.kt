package io.github.thisisartman.collectibles.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.thisisartman.collectibles.BuildConfig
import io.github.thisisartman.collectibles.debug.FileLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.zip.GZIPInputStream
import javax.inject.Inject
import javax.inject.Singleton

/** One downloadable file from the pack index (tools/packs/make_packs.py): a prefecture's catalogue of one type, or its map. */
data class Pack(
    val id: String, val kind: String, val type: ItemType?, val core: Boolean, val code: String,
    val prefecture: String, val prefectureEn: String, val items: Int, val file: String, val bytes: Long, val sha256: String,
    /** The map's outline (longitude, latitude rings), for deciding where the offline map covers. */
    val rings: List<DoubleArray> = emptyList(),
) {
    val isMap get() = kind == "map"
}

data class PackIndex(val version: String, val packs: List<Pack>) {
    val prefectures: List<String> get() = packs.map { it.prefecture }.distinct()
    fun of(prefecture: String) = packs.filter { it.prefecture == prefecture }
}

/**
 * Region packs (0.5, owner 2026-11-11: "region downloads as fast as possible"). The catalogue and the offline map come
 * as files per prefecture instead of inside the app: an index lists them, the phone downloads what you choose, checks
 * each file's checksum, and installs it. A catalogue pack goes through the same import as a catalogue file (adds and
 * updates by key, never deletes a find). Removing a prefecture deletes its map and the items you have not collected,
 * edited or marked; finds, photos and anything you touched stay.
 */
@Singleton
class RegionPacks @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: CollectiblesDb,
    private val catalogue: CatalogAndBackup,
    private val log: FileLog,
) {
    private val dir get() = File(context.filesDir, "packs").apply { mkdirs() }

    init {
        // A download stopped part way leaves a ".part" file: 0.6.0's first-run load did, and Regions counted it in
        // "On this phone" (seen on the 0.6.0 to 0.7.0 upgrade, 2026-11-24). Nothing is downloading yet at start
        runCatching { File(context.filesDir, "packs").listFiles()?.filter { it.name.endsWith(".part") }?.forEach { it.delete() } }
    }
    private val prefs get() = context.getSharedPreferences("packs", Context.MODE_PRIVATE)

    private val _installed = MutableStateFlow(readInstalled())
    /** Pack id to the checksum of the version on this phone. */
    val installed: StateFlow<Map<String, String>> = _installed.asStateFlow()

    private fun readInstalled(): Map<String, String> = prefs.all.mapNotNull { (k, v) -> (v as? String)?.let { k to it } }.toMap()

    /** The last index read, from this phone (works offline). */
    fun cachedIndex(): PackIndex? = runCatching { parse(File(dir, "index.json").readText()) }.getOrNull()

    /** Reads the index from the pack server and keeps a copy. Null without a connection. */
    suspend fun fetchIndex(): PackIndex? = withContext(Dispatchers.IO) {
        runCatching {
            val text = get(BuildConfig.PACKS_URL + "index.json").use { it.readBytes() }.toString(Charsets.UTF_8)
            val index = parse(text)
            File(dir, "index.json").writeText(text)
            index
        }.onFailure { log.w("Packs", "Could not read the pack index: $it") }.getOrNull()
    }

    /** Packs on this phone whose file has changed on the server. */
    fun updates(index: PackIndex): List<Pack> = index.packs.filter { p -> installed.value[p.id]?.let { it != p.sha256 } == true }

    /** A pack's file in the packs folder (A5: the index's name is checked, so it never lands outside it). */
    private fun mapFile(p: Pack): File = SafeFiles.child(dir, p.file) ?: error("bad pack file name")

    /**
     * Downloads and installs [packs] one by one; [onProgress] gets bytes done and bytes in all. Stops at the first
     * failure and says which; what was installed before it stays installed.
     */
    suspend fun install(packs: List<Pack>, onProgress: (Long, Long) -> Unit = { _, _ -> }): Result<Int> = withContext(Dispatchers.IO) {
        val total = packs.sumOf { it.bytes }
        var done = 0L
        var installedNow = 0
        for (p in packs) {
            val ok = runCatching {
                val tmp = SafeFiles.child(dir, p.file + ".part") ?: error("bad pack file name")
                // No more than the index says the file is (A6): an endless answer can't fill the phone
                download(BuildConfig.PACKS_URL + p.file, tmp, p.bytes) { n -> onProgress(done + n, total) }
                val sha = sha256(tmp)
                check(sha == p.sha256) { "checksum differs for ${p.file}" }
                if (p.isMap) {
                    check(tmp.renameTo(mapFile(p)) || (mapFile(p).delete() && tmp.renameTo(mapFile(p)))) { "could not keep ${p.file}" }
                } else {
                    val text = GZIPInputStream(tmp.inputStream()).use { SafeFiles.readCapped(it, MAX_CATALOGUE) }.toString(Charsets.UTF_8)
                    catalogue.importCatalogText(text)
                    tmp.delete()
                }
                prefs.edit().putString(p.id, p.sha256).apply()
                _installed.value = readInstalled()
                if (p.isMap) io.github.thisisartman.collectibles.ui.OfflineMap.changed()
            }
            if (ok.isFailure) {
                log.e("Packs", "Installing ${p.id} failed", ok.exceptionOrNull())
                return@withContext Result.failure(ok.exceptionOrNull()!!)
            }
            done += p.bytes
            installedNow++
            log.i("Packs", "Installed ${p.id} (${p.bytes} bytes)")
        }
        // A phone on packs no longer takes the catalogue that came inside the app
        catalogue.usePacks()
        dropLegacyMap()
        Result.success(installedNow)
    }

    /** Takes a prefecture off this phone: its map, and the items in its packs that you have not touched. */
    suspend fun remove(prefecture: String, index: PackIndex): Int = withContext(Dispatchers.IO) {
        var removed = 0
        for (p in index.of(prefecture).filter { it.id in installed.value }) {
            if (p.isMap) SafeFiles.child(dir, p.file)?.delete()
            else if (p.type != null) removed += catalogue.removeUntouched(prefecture, p.type)
            prefs.edit().remove(p.id).apply()
        }
        _installed.value = readInstalled()
        io.github.thisisartman.collectibles.ui.OfflineMap.changed()
        log.i("Packs", "Removed $prefecture: $removed items")
        removed
    }

    /**
     * After a restore (#209): a catalogue pack marked as installed whose items the backup didn't carry is no longer
     * on the phone, so it stops showing as installed and Regions offers it again. Returns the packs let go.
     */
    fun forgetEmpty(present: Collection<CatalogueRegion>): List<String> {
        val index = cachedIndex() ?: return emptyList()
        val gone = emptyPacks(index, installed.value.keys, present.map { it.prefecture to it.type }.toSet())
        if (gone.isEmpty()) return gone
        // commit: the app restarts straight after a restore
        prefs.edit().apply { gone.forEach { remove(it) } }.commit()
        _installed.value = readInstalled()
        log.i("Packs", "After the restore, no items for ${gone.joinToString()}: shown as not installed")
        return gone
    }

    /**
     * The map that came inside 0.4 and earlier (Niigata, Saitama and Tokyo in one ~100 MB file, copied to app storage)
     * keeps working after the update. Once all three have their own map download it is no longer used, so it goes.
     */
    private fun dropLegacyMap() {
        val legacy = File(context.filesDir, "map/region.pmtiles")
        if (!legacy.exists()) return
        if (LEGACY_MAP_CODES.all { "$it/map" in installed.value }) {
            val mb = legacy.length() / 1_048_576
            if (legacy.delete()) {
                io.github.thisisartman.collectibles.ui.OfflineMap.changed()
                log.i("Packs", "Removed the old built-in map ($mb MB): every prefecture it covered has its own map now")
            }
        }
    }

    /** Bytes the packs use on this phone. */
    fun bytesOnPhone(): Long = dir.listFiles()?.filterNot { it.name.endsWith(".part") }?.sumOf { it.length() } ?: 0L

    private fun get(url: String): java.io.InputStream {
        val c = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000; readTimeout = 30_000; instanceFollowRedirects = true
            setRequestProperty("User-Agent", UserAgent.value)
        }
        check(c.responseCode == 200) { "HTTP ${c.responseCode} for $url" }
        return c.inputStream
    }

    private fun download(url: String, to: File, limit: Long, onBytes: (Long) -> Unit) {
        get(url).use { input ->
            to.outputStream().use { out ->
                val buf = ByteArray(64 * 1024)
                var n = 0L
                var last = 0L
                while (true) {
                    val r = input.read(buf)
                    if (r < 0) break
                    n += r
                    if (n > limit) throw SafeFiles.TooBig("more than the $limit bytes the index gives")
                    out.write(buf, 0, r)
                    if (n - last > 256 * 1024) { onBytes(n); last = n }
                }
                onBytes(n)
            }
        }
    }

    private fun sha256(f: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        f.inputStream().use { s -> val b = ByteArray(1 shl 16); while (true) { val r = s.read(b); if (r < 0) break; md.update(b, 0, r) } }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    companion object {
        /** Prefectures the old built-in map covered: Saitama, Tokyo, Niigata. */
        val LEGACY_MAP_CODES = listOf("JP-11", "JP-13", "JP-15")

        /** The most a catalogue pack may unzip to (A6); today's biggest is a few MB. */
        private const val MAX_CATALOGUE = 64L * 1024 * 1024

        /** Map files on this phone with their outlines (read without the app's graph, for the map). */
        fun installedMaps(context: Context): List<Pair<File, List<DoubleArray>>> {
            val dir = File(context.filesDir, "packs")
            val installed = context.getSharedPreferences("packs", Context.MODE_PRIVATE).all.keys
            val index = runCatching { parse(File(dir, "index.json").readText()) }.getOrNull() ?: return emptyList()
            return index.packs.filter { it.isMap && it.id in installed }
                .mapNotNull { p -> SafeFiles.child(dir, p.file)?.takeIf { it.exists() }?.let { it to p.rings } }
        }

        /** Installed catalogue packs with items in the index but none of their prefecture and type on the phone. */
        fun emptyPacks(index: PackIndex, installed: Set<String>, present: Set<Pair<String, ItemType>>): List<String> =
            index.packs.filter { p -> !p.isMap && p.type != null && p.items > 0 && p.id in installed && (p.prefecture to p.type) !in present }
                .map { it.id }

        /** Changes whenever packs are installed or removed. */
        fun installedStamp(context: Context): Long =
            context.getSharedPreferences("packs", Context.MODE_PRIVATE).all.entries.fold(17L) { h, (k, v) -> h * 31 + (k + v).hashCode() }

        fun parse(text: String): PackIndex {
            val o = JSONObject(text)
            require(o.getInt("format") == 1) { "unknown pack index format" }
            val arr = o.getJSONArray("packs")
            val packs = List(arr.length()) { i ->
                val p = arr.getJSONObject(i)
                val rings = p.optJSONArray("rings")?.let { rs ->
                    List(rs.length()) { r -> val ring = rs.getJSONArray(r); DoubleArray(ring.length() * 2) { k -> ring.getJSONArray(k / 2).getDouble(k % 2) } }
                }.orEmpty()
                Pack(
                    id = p.getString("id"), kind = p.getString("kind"),
                    type = p.optString("type").takeIf { it.isNotEmpty() }?.let { t -> runCatching { ItemType.valueOf(t) }.getOrNull() },
                    core = p.optBoolean("core", true), code = p.getString("code"), prefecture = p.getString("prefecture"),
                    prefectureEn = p.optString("prefecture_en"), items = p.optInt("items"), file = p.getString("file"),
                    bytes = p.getLong("bytes"), sha256 = p.getString("sha256"), rings = rings,
                )
            }
            // Only plain file names (A5): a name with "/" or ".." could reach the app's database
            return PackIndex(o.getString("version"), packs.filter { SafeFiles.plainName(it.file) })
        }
    }
}
