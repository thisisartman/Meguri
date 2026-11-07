package io.github.thisisartman.collectibles.alerts

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofenceStatusCodes
import com.google.android.gms.location.GeofencingEvent
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.tasks.Task
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.thisisartman.collectibles.debug.FileLog
import io.github.thisisartman.collectibles.location.Fix
import io.github.thisisartman.collectibles.location.LocationSource
import io.github.thisisartman.collectibles.location.distanceM
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Geofencing (FR-12): the nearest items still to get, up to Android's limit, plus one larger "refresh" fence
 * around the current position; leaving it registers the next nearest set. Needs Play Services and
 * "Allow all the time" location. Everything it finds goes through [AlertHandler].
 */
@Singleton
class Geofences @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: AlertSettings,
    private val handler: AlertHandler,
    private val location: LocationSource,
    private val log: FileLog,
) {
    private val client by lazy { LocationServices.getGeofencingClient(context) }

    fun playServicesOk(): Boolean =
        GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS

    fun hasBackgroundLocation(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun hasFine(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** Whether alerts can really run in the background now; the switch in More shows why not. */
    fun ready(): Boolean = playServicesOk() && hasFine() && hasBackgroundLocation()

    private val pendingIntent: PendingIntent by lazy {
        // Mutable: Play Services writes the event into this intent
        PendingIntent.getBroadcast(
            context, 7, Intent(context, GeofenceReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
    }

    /** Registers fences around [around], or the last known position; removes them all when alerts are off. */
    @SuppressLint("MissingPermission")
    suspend fun refresh(around: Fix? = null) {
        if (!playServicesOk()) return
        if (!settings.geofenceOn || !ready()) {
            runCatching { client.removeGeofences(pendingIntent).await() }
            settings.geofenceError = 0
            return
        }
        val here = around ?: location.lastKnown()
        val items = handler.candidates().filter { it.type in settings.types }
        val ranked = if (here != null) items.sortedBy { distanceM(here.lat, here.lng, it.lat!!, it.lng!!) } else items
        val chosen = ranked.take(MAX_ITEMS)
        val fences = chosen.map {
            Geofence.Builder()
                .setRequestId(it.id)
                .setCircularRegion(it.lat!!, it.lng!!, settings.radiusFor(it.type).toFloat())
                .setExpirationDuration(Geofence.NEVER_EXPIRE)
                .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER)
                .setNotificationResponsiveness(RESPONSIVENESS_MS)
                .build()
        }.toMutableList()
        if (here != null && chosen.size == MAX_ITEMS) {
            // Halfway to the farthest registered item: leaving it means the nearest set has changed
            val far = distanceM(here.lat, here.lng, chosen.last().lat!!, chosen.last().lng!!)
            fences += Geofence.Builder()
                .setRequestId(REFRESH_ID)
                .setCircularRegion(here.lat, here.lng, (far / 2).coerceIn(1_000.0, 50_000.0).toFloat())
                .setExpirationDuration(Geofence.NEVER_EXPIRE)
                .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_EXIT)
                .build()
        }
        runCatching { client.removeGeofences(pendingIntent).await() }
        if (fences.isEmpty()) return
        val request = GeofencingRequest.Builder()
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
            .addGeofences(fences)
            .build()
        runCatching { client.addGeofences(request, pendingIntent).await() }
            .onSuccess {
                settings.geofenceError = 0
                log.i("Geofence", "Registered ${fences.size} fences, a distance per kind")
            }
            .onFailure {
                // 1000 means Google Location Accuracy is off; More says how to fix it
                settings.geofenceError = (it as? com.google.android.gms.common.api.ApiException)?.statusCode ?: -1
                log.e("Geofence", "Registering failed (${settings.geofenceError})", it)
            }
    }

    fun refreshInBackground(around: Fix? = null) {
        CoroutineScope(Dispatchers.IO).launch { runCatching { refresh(around) }.onFailure { log.e("Geofence", "Refresh failed", it) } }
    }

    companion object {
        /** Android allows 100 fences per app; one is the refresh fence. */
        const val MAX_ITEMS = 99
        const val REFRESH_ID = "refresh"
        const val RESPONSIVENESS_MS = 60_000
    }
}

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnCompleteListener { t ->
        if (t.isSuccessful) cont.resume(t.result) else cont.resumeWith(Result.failure(t.exception ?: IllegalStateException("Task failed")))
    }
}

/** Geofence events from Play Services. */
@AndroidEntryPoint
class GeofenceReceiver : BroadcastReceiver() {
    @Inject lateinit var handler: AlertHandler
    @Inject lateinit var geofences: Geofences
    @Inject lateinit var log: FileLog

    override fun onReceive(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent) ?: return
        if (event.hasError()) {
            log.w("Geofence", "Error ${GeofenceStatusCodes.getStatusCodeString(event.errorCode)}")
            // Location was switched off or Play Services reset: the fences are gone and need adding again
            if (event.errorCode == GeofenceStatusCodes.GEOFENCE_NOT_AVAILABLE) geofences.refreshInBackground()
            return
        }
        val fix = event.triggeringLocation?.let { Fix(it.latitude, it.longitude, if (it.hasAccuracy()) it.accuracy else null, it.time) }
        val ids = event.triggeringGeofences.orEmpty().map { it.requestId }
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (event.geofenceTransition == Geofence.GEOFENCE_TRANSITION_EXIT && Geofences.REFRESH_ID in ids) {
                    log.i("Geofence", "Left the refresh area")
                    geofences.refresh(fix)
                }
                val items = ids - Geofences.REFRESH_ID
                if (event.geofenceTransition == Geofence.GEOFENCE_TRANSITION_ENTER && items.isNotEmpty()) handler.onGeofence(items, fix)
            } catch (e: Exception) {
                log.e("Geofence", "Handling an event failed", e)
            } finally {
                pending.finish()
            }
        }
    }
}

/** Fences are cleared by a reboot and an app update; add them again (FR-12). Trip mode is not restarted. */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {
    @Inject lateinit var geofences: Geofences
    @Inject lateinit var settings: AlertSettings

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        // Android does not let a location service start from here, so a trip left on ends with the reboot
        // (after an update it is started again the next time the app is opened)
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) settings.tripUntilMs = 0L
        geofences.refreshInBackground()
    }
}
