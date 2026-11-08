package io.github.thisisartman.collectibles.alerts

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import io.github.thisisartman.collectibles.data.Item
import io.github.thisisartman.collectibles.data.SavedTrip
import io.github.thisisartman.collectibles.data.TripStore
import io.github.thisisartman.collectibles.data.nextGetOff
import io.github.thisisartman.collectibles.debug.FileLog
import io.github.thisisartman.collectibles.location.Fix
import io.github.thisisartman.collectibles.location.LocationSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/**
 * Trip mode (FR-12a, FR-12b): a location foreground service for trains and close tracking. It reads GPS through
 * LocationManager (no Play Services), checks more often near an item or at speed, and stops at the end of the day.
 */
@AndroidEntryPoint
class TripModeService : Service() {
    @Inject lateinit var settings: AlertSettings
    @Inject lateinit var handler: AlertHandler
    @Inject lateinit var notifier: AlertNotifier
    @Inject lateinit var location: LocationSource
    @Inject lateinit var log: FileLog
    @Inject lateinit var trips: TripStore

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val main = Handler(Looper.getMainLooper())
    private val lm by lazy { getSystemService(LocationManager::class.java) }
    private var intervalMs = AlertRules.MAX_INTERVAL_MS
    private var nearestText: String? = null
    private var titleText: String? = null
    /** Today's route stations with their places, loaded once per plan. */
    private var routeCache: Pair<SavedTrip, List<Pair<Item, Pair<Double, Double>>>>? = null
    private var prev: Fix? = null
    private val stopAtEnd = Runnable { log.i("Trip", "End of the trip time"); stopSelfAndClear() }

    private val listener = LocationListener { loc -> onLocation(loc) }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        running = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!settings.tripOn || !location.hasPermission()) {
            stopSelfAndClear()
            return START_NOT_STICKY
        }
        try {
            ServiceCompat.startForeground(this, AlertNotifier.TRIP_ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } catch (e: Exception) {
            // Android refuses a location service started from the background; the switch in More says so
            log.e("Trip", "Could not start in the foreground", e)
            stopSelfAndClear()
            return START_NOT_STICKY
        }
        main.removeCallbacks(stopAtEnd)
        main.postDelayed(stopAtEnd, (settings.tripUntilMs - System.currentTimeMillis()).coerceAtLeast(0))
        request(AlertRules.MIN_INTERVAL_MS)
        log.i("Trip", "On until ${time(settings.tripUntilMs)}")
        return START_STICKY
    }

    @SuppressLint("MissingPermission")
    private fun request(ms: Long) {
        val m = lm ?: return
        intervalMs = ms
        m.removeUpdates(listener)
        val fine = androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val candidates = if (fine) listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
        else listOfNotNull(if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) LocationManager.FUSED_PROVIDER else null, LocationManager.NETWORK_PROVIDER)
        val providers = candidates.filter { runCatching { m.isProviderEnabled(it) }.getOrDefault(false) }.take(if (fine) 2 else 1)
        providers.forEach { runCatching { m.requestLocationUpdates(it, ms, 0f, listener, Looper.getMainLooper()) } }
        if (providers.isEmpty()) log.w("Trip", "No location provider is on")
        else log.d("Trip", "A position every ${ms / 1000} s from ${providers.joinToString()}")
    }

    private fun onLocation(loc: Location) {
        if (!settings.tripOn) { stopSelfAndClear(); return }
        val fix = Fix(loc.latitude, loc.longitude, if (loc.hasAccuracy()) loc.accuracy else null, loc.time)
        val p = prev
        val speed = AlertRules.speedMps(if (loc.hasSpeed()) loc.speed else null, p?.lat, p?.lng, p?.timeMs, fix.lat, fix.lng, fix.timeMs)
        prev = fix
        scope.launch {
            val nearest = handler.onPosition(fix, "trip")
            val edge = nearest?.let { it.second - settings.radiusFor(it.first.type) }
            val next = AlertRules.nextIntervalMs(edge, speed)
            val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
            val quiet = settings.quietOn && AlertRules.inQuietHours(hour, AlertSettings.QUIET_FROM, AlertSettings.QUIET_TO)
            // In quiet hours Trip mode still runs, so say why nothing is alerting
            val near = nearest?.let { notifier.nearestText(it.first, it.second) }?.let { if (quiet) notifier.quietSuffix(it) else it }
            // With a route planned for today, the notification leads with where you get off next (#46, #62)
            val route = trips.current()?.takeIf { it.stations.isNotEmpty() }
            val places = route?.let { r -> routeCache?.takeIf { it.first == r }?.second ?: handler.places(r.stations).also { routeCache = r to it } }
            val off = if (route != null && places != null) {
                nextGetOff(places.map { Triple(it.first.id, it.second.first, it.second.second) }, route.getOff, fix.lat, fix.lng)
                    ?.let { (id, m) -> places.first { it.first.id == id }.first to m }
            } else null
            titleText = if (route != null && places != null) {
                val from = places.firstOrNull { it.first.id == route.from }?.first
                val to = places.firstOrNull { it.first.id == route.to }?.first
                if (from != null && to != null) notifier.tripRouteTitle(from, to, time(settings.tripUntilMs)) else null
            } else null
            nearestText = listOfNotNull(off?.let { notifier.getOffText(it.first, it.second) }, near).joinToString("\n").ifEmpty { null }
            main.post {
                updateNotification()
                // Re-ask only on a real change, so GPS is not restarted on every fix
                if (next < intervalMs / 2 || next > intervalMs * 2) request(next)
            }
        }
    }

    private fun notification() = notifier.tripNotification(time(settings.tripUntilMs), nearestText, titleText)

    @SuppressLint("MissingPermission")
    private fun updateNotification() {
        if (notifier.canPost()) NotificationManagerCompat.from(this).notify(AlertNotifier.TRIP_ID, notification())
    }

    private fun stopSelfAndClear() {
        settings.tripUntilMs = 0L
        lm?.removeUpdates(listener)
        main.removeCallbacks(stopAtEnd)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        running = false
        lm?.removeUpdates(listener)
        main.removeCallbacks(stopAtEnd)
        scope.cancel()
        super.onDestroy()
    }

    private fun time(ms: Long) = SimpleDateFormat("HH:mm", Locale.US).format(Date(ms))

    companion object {
        /** True while the service exists in this process. */
        @Volatile var running = false
            private set

        /** Call from the app while it is on screen: Android only allows a location service started from the foreground. */
        fun start(context: Context, settings: AlertSettings) {
            settings.tripUntilMs = AlertSettings.endOfDay()
            ContextCompat.startForegroundService(context, Intent(context, TripModeService::class.java))
        }

        /**
         * Trip mode still switched on but its service gone (the app was updated, or Android stopped it): start it
         * again now that the app is on screen.
         */
        fun resumeIfOn(context: Context, settings: AlertSettings) {
            if (settings.tripOn && !running) ContextCompat.startForegroundService(context, Intent(context, TripModeService::class.java))
        }

        fun stop(context: Context) {
            context.getSharedPreferences("alerts", Context.MODE_PRIVATE).edit().putLong("trip_until", 0L).apply()
            context.stopService(Intent(context, TripModeService::class.java))
        }
    }
}
