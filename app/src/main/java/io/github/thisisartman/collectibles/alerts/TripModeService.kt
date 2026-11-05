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

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val main = Handler(Looper.getMainLooper())
    private val lm by lazy { getSystemService(LocationManager::class.java) }
    private var intervalMs = AlertRules.MAX_INTERVAL_MS
    private var nearestText: String? = null
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
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).filter { runCatching { m.isProviderEnabled(it) }.getOrDefault(false) }
        providers.forEach { m.requestLocationUpdates(it, ms, 0f, listener, Looper.getMainLooper()) }
        if (providers.isEmpty()) log.w("Trip", "No location provider is on")
    }

    private fun onLocation(loc: Location) {
        if (!settings.tripOn) { stopSelfAndClear(); return }
        val fix = Fix(loc.latitude, loc.longitude, if (loc.hasAccuracy()) loc.accuracy else null, loc.time)
        val p = prev
        val speed = AlertRules.speedMps(if (loc.hasSpeed()) loc.speed else null, p?.lat, p?.lng, p?.timeMs, fix.lat, fix.lng, fix.timeMs)
        prev = fix
        scope.launch {
            val nearest = handler.onPosition(fix, "trip")
            val edge = nearest?.let { it.second - settings.radiusM }
            val next = AlertRules.nextIntervalMs(edge, speed)
            val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
            val quiet = settings.quietOn && AlertRules.inQuietHours(hour, AlertSettings.QUIET_FROM, AlertSettings.QUIET_TO)
            // In quiet hours Trip mode still runs, so say why nothing is alerting
            nearestText = nearest?.let { notifier.nearestText(it.first, it.second) }?.let { if (quiet) notifier.quietSuffix(it) else it }
            main.post {
                updateNotification()
                // Re-ask only on a real change, so GPS is not restarted on every fix
                if (next < intervalMs / 2 || next > intervalMs * 2) request(next)
            }
        }
    }

    private fun notification() = notifier.tripNotification(time(settings.tripUntilMs), nearestText)

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
