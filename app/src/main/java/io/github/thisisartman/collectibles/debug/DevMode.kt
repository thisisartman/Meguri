package io.github.thisisartman.collectibles.debug

import android.content.Context
import io.github.thisisartman.collectibles.BuildConfig

/**
 * Developer mode (owner, 2026-11-09): the dev build always writes the detailed log; the stable release writes it
 * only once developer mode is turned on in About. Errors and crashes are logged either way.
 */
object DevMode {
    private const val PREFS = "dev_mode"
    private const val KEY_ON = "on"

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Whether developer mode has been turned on (always false until someone turns it on in About). */
    fun on(context: Context): Boolean = prefs(context).getBoolean(KEY_ON, false)

    /** Turns developer mode on or off. With [log], the change itself is written to the log. */
    fun set(context: Context, on: Boolean, log: FileLog? = null) {
        prefs(context).edit().putBoolean(KEY_ON, on).apply()
        log?.i("DevMode", if (on) "Developer mode on: detailed log" else "Developer mode off" + if (BuildConfig.DEBUG) " (the dev build keeps the detailed log)" else "")
    }

    /** Whether the detailed log is written now: always in the dev build, else only with developer mode on. */
    fun detailed(context: Context): Boolean = detailed(BuildConfig.DEBUG, on(context))

    internal fun detailed(debugBuild: Boolean, devModeOn: Boolean): Boolean = debugBuild || devModeOn
}
