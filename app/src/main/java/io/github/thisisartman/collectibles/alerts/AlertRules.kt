package io.github.thisisartman.collectibles.alerts

import io.github.thisisartman.collectibles.data.ItemType
import io.github.thisisartman.collectibles.location.distanceM

/** The alert settings the rules need (PRD FR-13 to FR-15a). */
data class AlertPrefs(
    val radiusM: Int,
    val types: Set<ItemType>,
    val quietOn: Boolean,
    val quietFromHour: Int,
    val quietToHour: Int,
    val cooldownMs: Long,
    // A distance per kind (#73); a kind not in the map uses [radiusM]
    val radii: Map<ItemType, Int> = emptyMap(),
) {
    fun radiusFor(type: ItemType): Int = radii[type] ?: radiusM
}

/** Something still to get, with where you get it. */
data class Candidate(val id: String, val type: ItemType, val lat: Double, val lng: Double)

/**
 * Pure alert decisions, shared by geofencing and Trip mode (PRD FR-12c), so the two can never disagree
 * about whether an item alerts.
 */
object AlertRules {
    const val MIN_INTERVAL_MS = 20_000L
    /** At most 2 minutes between checks: a train covers 3 km in that time, so a 300 m radius is still caught soon after. */
    const val MAX_INTERVAL_MS = 2 * 60_000L

    /** Walking pace, used when the phone reports no speed or a slower one. */
    private const val WALKING_MPS = 1.4f

    /** True when [hour] (0-23) falls in quiet hours from [from] up to, not including, [to]. Equal ends mean none. */
    fun inQuietHours(hour: Int, from: Int, to: Int): Boolean = when {
        from == to -> false
        from < to -> hour in from until to
        else -> hour >= from || hour < to
    }

    /**
     * Which candidates should alert now, nearest first: within the radius, of a switched-on type, not alerted
     * within the cooldown, and nothing during quiet hours.
     */
    fun due(
        lat: Double, lng: Double, candidates: List<Candidate>, prefs: AlertPrefs,
        lastAlertMs: Map<String, Long>, nowMs: Long, hourOfDay: Int,
    ): List<Pair<Candidate, Double>> {
        if (prefs.quietOn && inQuietHours(hourOfDay, prefs.quietFromHour, prefs.quietToHour)) return emptyList()
        return candidates.asSequence()
            .filter { it.type in prefs.types }
            .filter { c -> lastAlertMs[c.id]?.let { nowMs - it >= prefs.cooldownMs } ?: true }
            .map { it to distanceM(lat, lng, it.lat, it.lng) }
            .filter { it.second <= prefs.radiusFor(it.first.type) }
            .sortedBy { it.second }
            .toList()
    }

    /**
     * Speed in m/s: the phone's own when it gives one, else worked out from the previous fix. Null when neither
     * is usable (no previous fix, or the fixes are too close together in time to trust).
     */
    fun speedMps(reported: Float?, prevLat: Double?, prevLng: Double?, prevMs: Long?, lat: Double, lng: Double, nowMs: Long): Float? {
        if (reported != null && reported > 0f) return reported
        if (prevLat == null || prevLng == null || prevMs == null) return reported
        val dt = (nowMs - prevMs) / 1000.0
        if (dt < 5.0) return reported
        return (distanceM(prevLat, prevLng, lat, lng) / dt).toFloat()
    }

    /**
     * How long Trip mode waits before the next position (FR-12a): about half the time it would take to reach
     * the nearest alert edge at the current speed, kept between 20 s and 2 min. [edgeM] is the distance to the
     * nearest item minus the radius; null when nothing is left to get.
     */
    fun nextIntervalMs(edgeM: Double?, speedMps: Float?, mode: LocationMode = LocationMode.BATTERY): Long {
        val (lo, hi) = when (mode) {
            LocationMode.BATTERY -> MIN_INTERVAL_MS to MAX_INTERVAL_MS
            LocationMode.REAL_TIME -> REALTIME_MIN_MS to REALTIME_MAX_MS
        }
        if (edgeM == null) return hi
        val speed = maxOf(speedMps ?: 0f, WALKING_MPS)
        val halfway = (edgeM.coerceAtLeast(0.0) / speed / 2 * 1000).toLong()
        return halfway.coerceIn(lo, hi)
    }

    /** Real time (#218): a position every 5 to 10 s, for driving. */
    const val REALTIME_MIN_MS = 5_000L
    const val REALTIME_MAX_MS = 10_000L

    /**
     * The one provider Trip mode asks (#218; it used to ask GPS and network at once): GPS with precise location, else
     * the fused provider (Android 12+, blurred for approximate location), else network. [enabled] is the
     * set of providers switched on; GPS needs precise location ([fine]). Null when none is usable.
     */
    fun provider(mode: LocationMode, fine: Boolean, enabled: Set<String>): String? {
        val gps = "gps".takeIf { fine && it in enabled }
        val fused = "fused".takeIf { it in enabled }
        val network = "network".takeIf { it in enabled }
        return when (mode) {
            // Both keep GPS when precise location is allowed (as 0.7.1); they differ in how often and how hard they ask
            LocationMode.REAL_TIME, LocationMode.BATTERY -> gps ?: fused ?: network
        }
    }

    /** How soon Play Services reports a fence entry: drive-to places within seconds, so 1 km is not used up at speed. */
    fun responsivenessMs(type: ItemType): Int = if (type in AlertSettings.DRIVE_TO) FAST_RESPONSIVENESS_MS else RESPONSIVENESS_MS
    const val RESPONSIVENESS_MS = 60_000
    const val FAST_RESPONSIVENESS_MS = 5_000
}
