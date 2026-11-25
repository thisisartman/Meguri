package io.github.thisisartman.collectibles.debug

import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Append-only log on disk with a size cap. When the current file passes [maxBytes] it is
 * rotated to `app.1.log`, `app.1.log` to `app.2.log`, and so on; files beyond [keep] are
 * deleted. Plain Kotlin, no Android types, so it runs in JVM unit tests.
 *
 * [detailed] gates the DEBUG level ([d]): every screen change, tap that changes data, scan and alert step.
 * It is asked on every line, so turning developer mode on or off takes effect at once. INFO, WARN, ERROR and
 * FATAL (crashes) are always written.
 */
class FileLog(
    private val dir: File,
    private val maxBytes: Long = 512 * 1024,
    private val keep: Int = 2,
    private val clock: () -> Long = System::currentTimeMillis,
    private val detailed: () -> Boolean = { true },
) {
    private val current = File(dir, "app.log")
    private val stampFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    init {
        dir.mkdirs()
    }

    /** Whether detailed (DEBUG) lines are written now. */
    val isDetailed: Boolean get() = detailed()

    @Synchronized
    fun log(level: Level, tag: String, message: String, error: Throwable? = null) {
        if (level == Level.DEBUG && !detailed()) return
        val line = buildString {
            append(stampFormat.format(Date(clock())))
            append(' ').append(level.letter).append('/').append(tag).append(": ").append(message)
            append('\n')
            if (error != null) append(stackTrace(error))
        }
        current.appendText(line)
        if (current.length() > maxBytes) rotate()
    }

    /**
     * A detailed line, written only while [detailed] is true (dev build, or developer mode on). Never throws:
     * these calls sit in screen code and taps, where a full disk must not crash the app.
     */
    fun d(tag: String, message: String) {
        runCatching { log(Level.DEBUG, tag, message) }
    }
    // The other levels never throw either: an info line runs right after a find is saved, and a full disk or a
    // removed log folder must not turn a saved find into a crash (#169)
    fun i(tag: String, message: String) { runCatching { log(Level.INFO, tag, message) } }
    fun w(tag: String, message: String, error: Throwable? = null) { runCatching { log(Level.WARN, tag, message, error) } }
    fun e(tag: String, message: String, error: Throwable? = null) { runCatching { log(Level.ERROR, tag, message, error) } }

    /** All kept files, oldest first, joined into one text. */
    @Synchronized
    fun readAll(): String = files().joinToString("") { it.readText() }

    /** The last [count] lines across all kept files. */
    fun tail(count: Int): List<String> = readAll().lineSequence().filter { it.isNotEmpty() }.toList().takeLast(count)

    /** Kept files, oldest first. */
    fun files(): List<File> =
        ((keep - 1) downTo 1).map { File(dir, "app.$it.log") }.filter { it.exists() } +
            listOfNotNull(current.takeIf { it.exists() })

    private fun rotate() {
        File(dir, "app.${keep - 1}.log").delete()
        for (n in (keep - 2) downTo 1) {
            File(dir, "app.$n.log").takeIf { it.exists() }?.renameTo(File(dir, "app.${n + 1}.log"))
        }
        if (keep > 1) current.renameTo(File(dir, "app.1.log")) else current.delete()
    }

    enum class Level(val letter: Char) { DEBUG('D'), INFO('I'), WARN('W'), ERROR('E'), FATAL('F') }

    companion object {
        fun stackTrace(error: Throwable): String = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
    }
}
