package io.github.thisisartman.collectibles.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/** A position and when it was taken. */
data class Fix(val lat: Double, val lng: Double, val accuracyM: Float?, val timeMs: Long)

/**
 * GPS through Android's own LocationManager (works offline, no Play Services). Never blocks a log:
 * callers save first and fill the position in when [currentOrRecent] returns.
 */
@Singleton
class LocationSource @Inject constructor(@ApplicationContext private val context: Context) {
    private val lm = context.getSystemService(LocationManager::class.java)
    private val executor = Executors.newSingleThreadExecutor()

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun hasFine(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /**
     * GPS when precise location is allowed. With "Approximate" only, Android refuses the GPS provider, so use the
     * fused provider (Android 12+), which hands approximate apps a blurred position, or the network provider.
     */
    private fun provider(): String? {
        val m = lm ?: return null
        fun on(p: String) = runCatching { m.isProviderEnabled(p) }.getOrDefault(false)
        return when {
            hasFine() && on(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S && on(LocationManager.FUSED_PROVIDER) -> LocationManager.FUSED_PROVIDER
            on(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            on(LocationManager.GPS_PROVIDER) && hasFine() -> LocationManager.GPS_PROVIDER
            else -> null
        }
    }

    /** Whether the phone's own location switch is on. */
    fun systemLocationOn(): Boolean = lm?.let { androidx.core.location.LocationManagerCompat.isLocationEnabled(it) } ?: false

    /** Newest last-known position from any provider, or null. */
    @SuppressLint("MissingPermission")
    fun lastKnown(): Fix? {
        if (!hasPermission() || lm == null) return null
        return lm.allProviders.mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }?.toFix()
    }

    /** A fresh GPS fix within [timeoutMs], else a last-known fix no older than [maxAgeMs], else null. */
    suspend fun currentOrRecent(timeoutMs: Long = 20_000, maxAgeMs: Long = 120_000): Fix? {
        val fresh = current(timeoutMs)
        if (fresh != null) return fresh
        val last = lastKnown() ?: return null
        return last.takeIf { System.currentTimeMillis() - it.timeMs <= maxAgeMs }
    }

    @SuppressLint("MissingPermission")
    suspend fun current(timeoutMs: Long = 20_000): Fix? {
        if (!hasPermission() || lm == null) return null
        val provider = provider() ?: return null
        return withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine { cont ->
                val signal = CancellationSignal()
                cont.invokeOnCancellation { signal.cancel() }
                LocationManagerCompat.getCurrentLocation(lm, provider, signal, executor) { loc: Location? ->
                    if (cont.isActive) cont.resume(loc?.toFix())
                }
            }
        }
    }

    /**
     * Positions as they change, at most every [intervalMs] and only after a move of [minMetres], for as long as it is
     * collected (Nearby while it is on screen; finding 41). Empty without permission or a provider.
     */
    @SuppressLint("MissingPermission")
    fun updates(intervalMs: Long = 30_000, minMetres: Float = 20f): kotlinx.coroutines.flow.Flow<Fix> = kotlinx.coroutines.flow.callbackFlow {
        val m = lm
        // The fused provider where there is one (Android 12+): it keeps GPS off when it can, so following costs little
        val fused = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S &&
            m != null && runCatching { m.isProviderEnabled(LocationManager.FUSED_PROVIDER) }.getOrDefault(false)
        val provider = if (!hasPermission()) null else if (fused) LocationManager.FUSED_PROVIDER else provider()
        if (m == null || provider == null) { close(); return@callbackFlow }
        val listener = android.location.LocationListener { trySend(it.toFix()) }
        runCatching { m.requestLocationUpdates(provider, intervalMs, minMetres, listener, android.os.Looper.getMainLooper()) }
            .onFailure { close(); return@callbackFlow }
        awaitClose { runCatching { m.removeUpdates(listener) } }
    }

    private fun Location.toFix() = Fix(latitude, longitude, if (hasAccuracy()) accuracy else null, time)
}

/**
 * A coordinate rounded to 2 decimals, about 1 km (B1, B4): what leaves the phone for place search and what logs
 * record, so neither gives away the exact position.
 */
fun coarse(deg: Double): Double = Math.round(deg * 100) / 100.0

/** "37.67, 138.88": a rounded position as text, the same in every locale. */
fun coarseText(lat: Double, lng: Double): String = String.format(java.util.Locale.US, "%.2f, %.2f", coarse(lat), coarse(lng))

/** Great-circle distance in metres. */
fun distanceM(aLat: Double, aLng: Double, bLat: Double, bLng: Double): Double {
    val r = 6_371_000.0
    val dLat = Math.toRadians(bLat - aLat)
    val dLng = Math.toRadians(bLng - aLng)
    val h = Math.sin(dLat / 2).let { it * it } +
        Math.cos(Math.toRadians(aLat)) * Math.cos(Math.toRadians(bLat)) * Math.sin(dLng / 2).let { it * it }
    return 2 * r * Math.asin(Math.sqrt(h))
}
