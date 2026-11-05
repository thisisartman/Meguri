package io.github.thisisartman.collectibles.alerts

import io.github.thisisartman.collectibles.data.CollectiblesDb
import io.github.thisisartman.collectibles.data.Item
import io.github.thisisartman.collectibles.debug.FileLog
import io.github.thisisartman.collectibles.location.Fix
import io.github.thisisartman.collectibles.location.distanceM
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The one place an alert is decided (FR-12c): geofences and Trip mode both end here, so cooldown, quiet hours and
 * type switches apply the same way, and an item never alerts twice for one visit.
 */
@Singleton
class AlertHandler @Inject constructor(
    private val db: CollectiblesDb,
    private val settings: AlertSettings,
    private val notifier: AlertNotifier,
    private val log: FileLog,
) {
    private val dao = db.dao()
    private val lock = Mutex()

    /** Items still to get that have a place, as alert candidates. */
    suspend fun candidates(): List<Item> = dao.uncollectedPlaces()

    /** A position from Trip mode. Returns the nearest item still to get and its distance, for the status line. */
    suspend fun onPosition(fix: Fix, source: String): Pair<Item, Double>? = lock.withLock {
        val items = candidates()
        alert(fix, items, source)
        items.minByOrNull { distanceM(fix.lat, fix.lng, it.lat!!, it.lng!!) }?.let { it to distanceM(fix.lat, fix.lng, it.lat!!, it.lng!!) }
    }

    /** A geofence entry: only the items whose fences fired are looked at, still through the same rules. */
    suspend fun onGeofence(itemIds: List<String>, fix: Fix?) = lock.withLock {
        val items = candidates().filter { it.id in itemIds }
        if (fix != null) {
            alert(fix, items, "geofence")
        } else {
            // No position in the event: trust the fence, but keep the cooldown and quiet hours
            val now = System.currentTimeMillis()
            val p = settings.snapshot()
            val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            if (p.quietOn && AlertRules.inQuietHours(hour, p.quietFromHour, p.quietToHour)) return@withLock
            val last = settings.lastAlerts()
            val due = items.filter { it.type in p.types && (last[it.id]?.let { t -> now - t >= p.cooldownMs } ?: true) }.take(MAX_PER_CHECK)
            due.forEach { notifier.notifyNear(it, p.radiusM.toDouble(), null) }
            settings.recordAlerts(due.map { it.id }, now)
            if (due.isNotEmpty()) log.i("Alert", "geofence (no position): ${due.size} alerts")
        }
    }

    private fun alert(fix: Fix, items: List<Item>, source: String) {
        val now = System.currentTimeMillis()
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val byId = items.associateBy { it.id }
        val cands = items.map { Candidate(it.id, it.type, it.lat!!, it.lng!!) }
        val due = AlertRules.due(fix.lat, fix.lng, cands, settings.snapshot(), settings.lastAlerts(), now, hour).take(MAX_PER_CHECK)
        due.forEach { (c, m) -> byId[c.id]?.let { notifier.notifyNear(it, m, fix) } }
        settings.recordAlerts(due.map { it.first.id }, now)
        if (due.isNotEmpty()) log.i("Alert", "$source: ${due.size} alerts, nearest ${due.first().second.toInt()} m")
    }

    companion object {
        /** A station with several things nearby should not flood the shade. */
        const val MAX_PER_CHECK = 3
    }
}
