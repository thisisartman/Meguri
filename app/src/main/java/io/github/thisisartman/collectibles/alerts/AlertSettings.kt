package io.github.thisisartman.collectibles.alerts

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.thisisartman.collectibles.data.ItemType
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Alert settings and the per-item cooldown (FR-13 to FR-15a). Kept in preferences, not the database, so the
 * schema stays at v1 and backups are unchanged; a restore simply starts with no cooldowns.
 */
@Singleton
class AlertSettings @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("alerts", Context.MODE_PRIVATE)

    var geofenceOn: Boolean
        get() = prefs.getBoolean(KEY_GEOFENCE, false)
        set(v) = prefs.edit().putBoolean(KEY_GEOFENCE, v).apply()

    /** Trip mode runs until this time; 0 when off. */
    var tripUntilMs: Long
        get() = prefs.getLong(KEY_TRIP_UNTIL, 0L)
        set(v) = prefs.edit().putLong(KEY_TRIP_UNTIL, v).apply()

    val tripOn: Boolean get() = tripUntilMs > System.currentTimeMillis()

    /** The last geofence registration error from Play Services, 0 when the last one worked. */
    var geofenceError: Int
        get() = prefs.getInt(KEY_GEO_ERROR, 0)
        set(v) = prefs.edit().putInt(KEY_GEO_ERROR, v).apply()

    var radiusM: Int
        get() = prefs.getInt(KEY_RADIUS, DEFAULT_RADIUS_M)
        set(v) = prefs.edit().putInt(KEY_RADIUS, v).apply()

    /**
     * Kinds that alert. A set saved before dam cards and museum stamps existed (#55) gets them once, switched on like
     * the defaults; the marker records that, so switching them off afterwards sticks.
     */
    var types: Set<ItemType>
        get() {
            val saved = prefs.getStringSet(KEY_TYPES, null) ?: return DEFAULT_TYPES
            val kinds = saved.mapNotNull { runCatching { ItemType.valueOf(it) }.getOrNull() }.toSet()
            return if (MARK_V2 in saved) kinds else kinds + NEW_IN_V2
        }
        set(v) = prefs.edit().putStringSet(KEY_TYPES, v.map { it.name }.toSet() + MARK_V2).apply()

    var quietOn: Boolean
        get() = prefs.getBoolean(KEY_QUIET, true)
        set(v) = prefs.edit().putBoolean(KEY_QUIET, v).apply()

    /** The alert distance for one kind (#73): what you chose for it, else its default. */
    fun radiusFor(type: ItemType): Int = prefs.getInt(KEY_RADIUS_PREFIX + type.name, DEFAULT_RADII[type] ?: radiusM)

    fun setRadiusFor(type: ItemType, meters: Int) = prefs.edit().putInt(KEY_RADIUS_PREFIX + type.name, meters).apply()

    fun snapshot() = AlertPrefs(radiusM, types, quietOn, QUIET_FROM, QUIET_TO, COOLDOWN_MS, ItemType.entries.associateWith { radiusFor(it) })

    fun lastAlerts(): Map<String, Long> =
        prefs.all.filterKeys { it.startsWith(COOL) }.mapNotNull { (k, v) -> (v as? Long)?.let { k.removePrefix(COOL) to it } }.toMap()

    /** Records alerts and forgets ones older than the cooldown, so the file never grows without end. */
    fun recordAlerts(ids: Collection<String>, nowMs: Long) {
        val e = prefs.edit()
        lastAlerts().filterValues { nowMs - it > COOLDOWN_MS }.keys.forEach { e.remove(COOL + it) }
        ids.forEach { e.putLong(COOL + it, nowMs) }
        e.apply()
    }

    fun listen(onChange: () -> Unit): SharedPreferences.OnSharedPreferenceChangeListener {
        val l = SharedPreferences.OnSharedPreferenceChangeListener { _, key -> if (key?.startsWith(COOL) != true) onChange() }
        prefs.registerOnSharedPreferenceChangeListener(l)
        return l
    }

    fun stopListening(l: SharedPreferences.OnSharedPreferenceChangeListener) = prefs.unregisterOnSharedPreferenceChangeListener(l)

    companion object {
        const val DEFAULT_RADIUS_M = 300
        val RADIUS_CHOICES = listOf(200, 300, 500, 1000)

        /** Per kind (#73): stations are close together on a line, dams sit far out of town. */
        val DEFAULT_RADII = mapOf(
            ItemType.EKI_STAMP to 200, ItemType.EKITAG to 200, ItemType.MANHOLE_CARD to 500,
            ItemType.GOSHUIN to 300, ItemType.DAM_CARD to 1000, ItemType.MUSEUM_STAMP to 300,
        )
        private const val KEY_RADIUS_PREFIX = "radius_"
        val NEW_IN_V2 = setOf(ItemType.DAM_CARD, ItemType.MUSEUM_STAMP)
        val DEFAULT_TYPES = setOf(ItemType.MANHOLE_CARD, ItemType.GOSHUIN, ItemType.EKI_STAMP) + NEW_IN_V2
        private const val MARK_V2 = "_kinds_v2"
        const val QUIET_FROM = 22
        const val QUIET_TO = 7
        const val COOLDOWN_MS = 12 * 60 * 60_000L
        private const val KEY_GEOFENCE = "geofence_on"
        private const val KEY_TRIP_UNTIL = "trip_until"
        private const val KEY_RADIUS = "radius_m"
        private const val KEY_GEO_ERROR = "geofence_error"
        private const val KEY_TYPES = "types"
        private const val KEY_QUIET = "quiet_on"
        private const val COOL = "cool:"

        /** Trip mode switches itself off at the end of the day (FR-12b). */
        fun endOfDay(nowMs: Long = System.currentTimeMillis()): Long = Calendar.getInstance().apply {
            timeInMillis = nowMs
            set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
}
