package io.github.thisisartman.collectibles.debug

/**
 * Writes any uncaught exception to the file log before handing it to the previous handler,
 * so the system still shows its crash dialog and the process still ends.
 */
object CrashLogger {
    fun install(log: FileLog) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { log.log(FileLog.Level.FATAL, "Crash", "Uncaught on thread ${thread.name}", error) }
            previous?.uncaughtException(thread, error)
        }
    }
}
