package io.github.thisisartman.collectibles.alerts

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.thisisartman.collectibles.data.ItemType
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

/** One line of the alert distance list (#164): a category, the distance it alerts at, and whether that is the default. */
data class CategoryDistance(val type: ItemType, val meters: Int, val isDefault: Boolean)

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

    /** The new-photo hint (M7): off until switched on in Settings, which also asks for gallery access. */
    var photoHintsOn: Boolean
        get() = prefs.getBoolean("photo_hints", false)
        set(v) = prefs.edit().putBoolean("photo_hints", v).apply()

    /** Gallery photos added before this time (seconds) have been looked at. 0: not started; the first run only marks now. */
    var photoHintsSeenSec: Long
        get() = prefs.getLong("photo_hints_seen", 0L)
        set(v) = prefs.edit().putLong("photo_hints_seen", v).apply()

    /** The last geofence registration error from Play Services, 0 when the last one worked. */
    var geofenceError: Int
        get() = prefs.getInt(KEY_GEO_ERROR, 0)
        set(v) = prefs.edit().putInt(KEY_GEO_ERROR, v).apply()

    /** The one distance every category uses unless it has its own (design C, 2026-11-09). */
    var radiusM: Int
        get() = snap(prefs.getInt(KEY_RADIUS, DEFAULT_RADIUS_M))
        set(v) = prefs.edit().putInt(KEY_RADIUS, v).apply()

    /**
     * Kinds that alert. A set saved before dam cards and museum stamps existed (#55) gets them once, switched on like
     * the defaults; the marker records that, so switching them off afterwards sticks.
     */
    var types: Set<ItemType>
        get() {
            val saved = prefs.getStringSet(KEY_TYPES, null) ?: return DEFAULT_TYPES
            val kinds = saved.mapNotNull { runCatching { ItemType.valueOf(it) }.getOrNull() }.toSet()
            return kinds + (if (MARK_V2 in saved) emptySet() else NEW_IN_V2) + (if (MARK_V3 in saved) emptySet() else NEW_IN_V3)
        }
        set(v) = prefs.edit().putStringSet(KEY_TYPES, v.map { it.name }.toSet() + MARK_V2 + MARK_V3).apply()

    var quietOn: Boolean
        get() = prefs.getBoolean(KEY_QUIET, true)
        set(v) = prefs.edit().putBoolean(KEY_QUIET, v).apply()

    /** A category's own distance, or null when it uses the main one ("Same"). */
    fun ownRadius(type: ItemType): Int? =
        resolveOwn(if (prefs.contains(KEY_RADIUS_PREFIX + type.name)) prefs.getInt(KEY_RADIUS_PREFIX + type.name, SAME) else null, type)

    /** The alert distance for one category: its own, else the main one. */
    fun radiusFor(type: ItemType): Int = ownRadius(type) ?: radiusM

    /** null goes back to the main distance. */
    fun setRadiusFor(type: ItemType, meters: Int?) = prefs.edit().apply {
        // A category that starts with its own distance (stations) remembers "Same" explicitly
        when {
            meters != null -> putInt(KEY_RADIUS_PREFIX + type.name, meters)
            type in STARTING_OWN -> putInt(KEY_RADIUS_PREFIX + type.name, SAME)
            else -> remove(KEY_RADIUS_PREFIX + type.name)
        }
    }.apply()

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
        const val DEFAULT_RADIUS_M = 500
        /** Three choices (owner, 2026-11-09). A distance saved before (300 m) reads as the nearest of them. */
        val RADIUS_CHOICES = listOf(200, 500, 1000)
        fun snap(m: Int): Int = RADIUS_CHOICES.minBy { kotlin.math.abs(it - m) }

        /** Owner, 2026-11-09: station stamps start at 200 m (stations sit close together on a line). */
        val STARTING_OWN = mapOf(ItemType.EKI_STAMP to 200)
        /** Saved for a category that was set back to the main distance. */
        const val SAME = -1

        /** A category's own distance from what is saved: nothing saved means its starting one, if any. */
        fun resolveOwn(saved: Int?, type: ItemType): Int? = when {
            saved == SAME -> null
            saved != null -> snap(saved)
            else -> STARTING_OWN[type]
        }
        /**
         * The one list of alert distances (#164): each category that alerts and has something on the phone, in
         * [order], with its distance and whether that is the default. [present] null (not read yet) shows every one on.
         */
        fun categoryDistances(order: List<ItemType>, on: Set<ItemType>, present: Set<ItemType>?, main: Int, own: (ItemType) -> Int?): List<CategoryDistance> =
            order.filter { it in on && (present == null || it in present) }
                .map { t -> own(t)?.let { CategoryDistance(t, it, isDefault = false) } ?: CategoryDistance(t, main, isDefault = true) }

        private const val KEY_RADIUS_PREFIX = "radius_"
        val NEW_IN_V2 = setOf(ItemType.DAM_CARD, ItemType.MUSEUM_STAMP, ItemType.ROADSIDE_STATION)
        /** Ekitag alert from 0.7 (#138), once for sets saved before it, like [NEW_IN_V2]. */
        val NEW_IN_V3 = setOf(ItemType.EKITAG)
        val DEFAULT_TYPES = setOf(ItemType.MANHOLE_CARD, ItemType.GOSHUIN, ItemType.EKI_STAMP) + NEW_IN_V2 + NEW_IN_V3
        private const val MARK_V2 = "_kinds_v2"
        private const val MARK_V3 = "_kinds_v3"
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
