package io.github.thisisartman.collectibles.debug

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class FileLogTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun newLog(maxBytes: Long = 512 * 1024, keep: Int = 2) =
        FileLog(File(tmp.root, "logs"), maxBytes = maxBytes, keep = keep, clock = { 0L })

    @Test
    fun writesLevelTagAndMessage() {
        val log = newLog()
        log.i("App", "Start")
        val line = log.tail(1).single()
        assertTrue(line, line.endsWith(" I/App: Start"))
    }

    @Test
    fun includesStackTraceOfError() {
        val log = newLog()
        log.e("Logs", "Export failed", IllegalStateException("disk full"))
        val text = log.readAll()
        assertTrue(text.contains("E/Logs: Export failed"))
        assertTrue(text.contains("java.lang.IllegalStateException: disk full"))
        assertTrue(text.contains("at io.github.thisisartman.collectibles.debug.FileLogTest"))
    }

    @Test
    fun rotationKeepsSizeBoundedAndNewestLast() {
        val log = newLog(maxBytes = 1_000, keep = 2)
        repeat(200) { log.i("T", "line %03d".format(it)) }
        val files = log.files()
        assertEquals(listOf("app.1.log", "app.log"), files.map { it.name })
        assertTrue(files.sumOf { it.length() } <= 2 * 1_000 + 100)
        assertTrue(log.tail(1).single().endsWith("line 199"))
        assertFalse(log.readAll().contains("line 000"))
    }

    @Test
    fun readAllIsOldestFirst() {
        val log = newLog(maxBytes = 60, keep = 3)
        repeat(6) { log.i("T", "n$it") }
        val numbers = log.tail(100).map { it.substringAfterLast("n").toInt() }
        assertEquals(numbers.sorted(), numbers)
    }

    @Test
    fun detailedLinesOnlyWhileDetailed() {
        var detailed = false
        val log = FileLog(File(tmp.root, "logs"), clock = { 0L }, detailed = { detailed })
        log.d("Nav", "hidden")
        assertFalse(log.readAll().contains("hidden"))
        detailed = true
        log.d("Nav", "shown")
        assertTrue(log.tail(1).single().endsWith(" D/Nav: shown"))
        // Switching it off again (developer mode turned off in About) takes effect at once
        detailed = false
        log.d("Nav", "hidden again")
        assertFalse(log.readAll().contains("hidden again"))
        assertFalse(log.isDetailed)
    }

    @Test
    fun errorsWarningsAndInfoAreWrittenWithoutDetailed() {
        val log = FileLog(File(tmp.root, "logs"), clock = { 0L }, detailed = { false })
        log.i("App", "start")
        log.w("Geofence", "warned")
        log.e("Backup", "failed", IllegalStateException("disk full"))
        log.log(FileLog.Level.FATAL, "Crash", "fatal")
        val text = log.readAll()
        assertTrue(text.contains("I/App: start"))
        assertTrue(text.contains("W/Geofence: warned"))
        assertTrue(text.contains("E/Backup: failed"))
        assertTrue(text.contains("F/Crash: fatal"))
    }

    @Test
    fun detailedLineNeverThrows() {
        // The log folder is gone and a file sits in its place: writing fails, the tap that logged must not crash
        val dir = File(tmp.root, "logs")
        val log = FileLog(dir, clock = { 0L })
        dir.deleteRecursively()
        dir.writeText("not a folder")
        log.d("Tap", "Got it")
    }

    @Test
    fun crashLoggerWritesFatalAndCallsPreviousHandler() {
        val log = newLog()
        var previousCalled = false
        val original = Thread.getDefaultUncaughtExceptionHandler()
        try {
            Thread.setDefaultUncaughtExceptionHandler { _, _ -> previousCalled = true }
            CrashLogger.install(log)
            val t = Thread { throw RuntimeException("boom") }
            t.start()
            t.join()
            assertTrue(previousCalled)
            assertTrue(log.readAll().contains("F/Crash: Uncaught on thread"))
            assertTrue(log.readAll().contains("java.lang.RuntimeException: boom"))
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(original)
        }
    }
}
