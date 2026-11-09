package io.github.thisisartman.collectibles.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.core.content.ContextCompat

/** Location permission checks and the two Settings pages Nearby can send you to. */
object LocationAccess {
    fun has(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun hasPrecise(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun systemOn(context: Context): Boolean =
        context.getSystemService(android.location.LocationManager::class.java)?.let { androidx.core.location.LocationManagerCompat.isLocationEnabled(it) } ?: false

    fun openAppSettings(context: Context) {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    fun openLocationSettings(context: Context) {
        context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /**
     * Whether to ask for location on its own as the app opens (finding 42): at most once per run of the app (not again
     * when the screen turns or the language changes), and never after the person said no, until they tap something
     * that needs it ("Allow location", "Try again"), which asks whatever this says.
     */
    fun askAtStart(granted: Boolean, askedThisRun: Boolean, declined: Boolean, blocked: Boolean): Boolean =
        !granted && !askedThisRun && !declined && !blocked

    /** Set once the app has asked in this run; a process-level flag, so it survives the activity being recreated. */
    @Volatile var askedThisRun = false

    private fun prefs(context: Context) = context.getSharedPreferences("location", Context.MODE_PRIVATE)
    fun declined(context: Context): Boolean = prefs(context).getBoolean("declined", false)
    fun setDeclined(context: Context, declined: Boolean) { prefs(context).edit().putBoolean("declined", declined).apply() }
}
