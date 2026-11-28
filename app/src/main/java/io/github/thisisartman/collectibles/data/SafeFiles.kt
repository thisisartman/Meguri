package io.github.thisisartman.collectibles.data

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import kotlin.math.max

/**
 * The guards around backups and files from outside (review before 0.7.2, A2 to A7). Pure, for tests.
 */
object SafeFiles {
    private val PLAIN = Regex("[A-Za-z0-9._-]{1,128}")

    /**
     * A5: a file name from a backup or a region index is used only when it is a plain name: letters, digits, dot,
     * dash and underscore, no "/" or "\", never "." or "..", so it can't reach outside its folder.
     */
    fun plainName(name: String): Boolean = PLAIN.matches(name) && name != "." && !name.contains("..")

    /** A5: whether [file] lies inside [dir] once both are resolved (links and ".." followed). */
    fun inside(dir: File, file: File): Boolean = runCatching {
        file.canonicalPath.startsWith(dir.canonicalPath + File.separator)
    }.getOrDefault(false)

    /** A5: [name] in [dir], or null when it isn't a plain name or would land outside [dir]. */
    fun child(dir: File, name: String): File? = File(dir, name).takeIf { plainName(name) && inside(dir, it) }

    /** A6: too much in a zip, a download or an unzipped stream; nothing past the limit is written. */
    class TooBig(what: String) : IOException("Over the limit: $what")

    /**
     * A6: a budget for one unzip: at most [maxEntries] entries and [maxBytes] unzipped bytes in all. [entry] counts
     * an entry, [copy] copies one through the budget.
     */
    class Budget(private val maxBytes: Long, private val maxEntries: Int) {
        var bytes = 0L; private set
        var entries = 0; private set

        fun entry() {
            if (++entries > maxEntries) throw TooBig("more than $maxEntries files")
        }

        /** Copies [input] to [out], at most [perEntry] bytes of it and what is left of the budget. */
        fun copy(input: InputStream, out: OutputStream, perEntry: Long = Long.MAX_VALUE): Long {
            val n = copyCapped(input, out, minOf(perEntry, maxBytes - bytes))
            bytes += n
            return n
        }
    }

    /** A6: copies [input] to [out]; throws [TooBig] as soon as more than [limit] bytes come. */
    fun copyCapped(input: InputStream, out: OutputStream, limit: Long): Long {
        val buf = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val n = input.read(buf)
            if (n < 0) return total
            total += n
            if (total > limit) throw TooBig("more than $limit bytes")
            out.write(buf, 0, n)
        }
    }

    /** A6: reads [input] whole, but no more than [limit] bytes. */
    fun readCapped(input: InputStream, limit: Long): ByteArray =
        java.io.ByteArrayOutputStream().also { copyCapped(input, it, limit) }.toByteArray()

    /**
     * A2: whether a backup of [finds] finds has far fewer than the [best] one kept: a fifth fewer or more, and at
     * least three. A few finds removed on purpose never trips it; a collection emptied by mistake does.
     */
    fun farFewer(finds: Int, best: Int): Boolean = best > 0 && best - finds >= max(3, (best + 4) / 5)

    /**
     * A4: which backups to delete when keeping the newest [keep]: those beyond them, newest first, but never the
     * one stamped [bestStamp], the backup with the most finds so far. [stampOf] reads a name's date and time.
     */
    fun toPrune(names: List<String>, keep: Int, bestStamp: String?, stampOf: (String) -> String): List<String> =
        names.sortedByDescending(stampOf).drop(keep).filter { bestStamp == null || stampOf(it) != bestStamp }

    /** A7: the dev copy of the app, a debug build or the devRelease build (package ending .dev). */
    fun devApp(applicationId: String, debug: Boolean): Boolean = debug || applicationId.endsWith(".dev")
}
