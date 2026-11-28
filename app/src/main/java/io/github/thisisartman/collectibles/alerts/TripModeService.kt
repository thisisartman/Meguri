package io.github.thisisartman.collectibles.alerts

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationManager
import androidx.core.location.LocationListenerCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.location.LocationRequestCompat
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

    private val listener = LocationListenerCompat { loc -> onLocation(loc) }
    private var mode = LocationMode.BATTERY
    private var prefsListener: android.content.SharedPreferences.OnSharedPreferenceChangeListener? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        running = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!settings.tripOn || !location.hasPermission()) {
            if (settings.tripOn) settings.tripStoppedNoLocation = true
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
        mode = settings.locationMode
        request(firstInterval())
        if (prefsListener == null) prefsListener = settings.listen {
            // Location updates changed in Settings while the trip runs: ask again in the new way
            main.post { if (settings.tripOn && settings.locationMode != mode) { mode = settings.locationMode; request(firstInterval()) } }
        }
        log.i("Trip", "On until ${time(settings.tripUntilMs)}")
        return START_STICKY
    }

    private fun firstInterval() = if (mode == LocationMode.REAL_TIME) AlertRules.REALTIME_MIN_MS else AlertRules.MIN_INTERVAL_MS

    /** One provider, one request (#218): GPS and network at once used to mix a coarse fix into the speed. */
    @SuppressLint("MissingPermission")
    private fun request(ms: Long) {
        val m = lm ?: return
        intervalMs = ms
        LocationManagerCompat.removeUpdates(m, listener)
        val fine = ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val enabled = m.allProviders.filter { runCatching { m.isProviderEnabled(it) }.getOrDefault(false) }.toSet()
        val provider = AlertRules.provider(mode, fine, enabled)
        if (provider == null) { log.w("Trip", "No location provider is on"); return }
        val realTime = mode == LocationMode.REAL_TIME
        val req = LocationRequestCompat.Builder(ms)
            .setQuality(if (realTime) LocationRequestCompat.QUALITY_HIGH_ACCURACY else LocationRequestCompat.QUALITY_BALANCED_POWER_ACCURACY)
            .setMinUpdateIntervalMillis(if (realTime) REALTIME_FASTEST_MS else ms)
            .build()
        runCatching { LocationManagerCompat.requestLocationUpdates(m, provider, req, listener, Looper.getMainLooper()) }
            .onSuccess { log.d("Trip", "A position every ${ms / 1000} s from $provider (${mode.name.lowercase()})") }
            .onFailure { log.e("Trip", "Asking for positions failed", it) }
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
            val next = AlertRules.nextIntervalMs(edge, speed, mode)
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
        lm?.let { LocationManagerCompat.removeUpdates(it, listener) }
        main.removeCallbacks(stopAtEnd)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        running = false
        lm?.let { LocationManagerCompat.removeUpdates(it, listener) }
        prefsListener?.let { settings.stopListening(it) }
        prefsListener = null
        main.removeCallbacks(stopAtEnd)
        scope.cancel()
        super.onDestroy()
    }

    private fun time(ms: Long) = io.github.thisisartman.collectibles.ui.Dates.time(ms)

    companion object {
        /** True while the service exists in this process. */
        @Volatile var running = false
            private set

        /** Faster than this, Real time takes no new position (#218). */
        const val REALTIME_FASTEST_MS = 2_000L

        private fun hasLocation(context: Context): Boolean =
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED

        /**
         * Call from the app while it is on screen: Android only allows a location service started from the foreground.
         * False, and nothing started, without location access: a location service that can't go to the foreground
         * crashes the app (A1).
         */
        fun start(context: Context, settings: AlertSettings): Boolean {
            if (!hasLocation(context)) return false
            settings.tripStoppedNoLocation = false
            settings.tripUntilMs = AlertSettings.endOfDay()
            ContextCompat.startForegroundService(context, Intent(context, TripModeService::class.java))
            return true
        }

        /**
         * Trip mode still switched on but its service gone (the app was updated, or Android stopped it): start it
         * again now that the app is on screen. If location access was taken away meanwhile, Trip mode goes off
         * instead, and the Alerts page says why (A1: starting it anyway crashed the app on Android 12 and later).
         */
        fun resumeIfOn(context: Context, settings: AlertSettings) {
            if (!settings.tripOn || running) return
            if (!hasLocation(context)) {
                settings.tripUntilMs = 0L
                settings.tripStoppedNoLocation = true
                android.widget.Toast.makeText(context, io.github.thisisartman.collectibles.R.string.trip_stopped_no_location, android.widget.Toast.LENGTH_LONG).show()
                return
            }
            ContextCompat.startForegroundService(context, Intent(context, TripModeService::class.java))
        }

        fun stop(context: Context) {
            context.getSharedPreferences("alerts", Context.MODE_PRIVATE).edit().putLong("trip_until", 0L).apply()
            context.stopService(Intent(context, TripModeService::class.java))
        }
    }
}
