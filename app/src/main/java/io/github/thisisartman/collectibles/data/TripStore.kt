package io.github.thisisartman.collectibles.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Today's planned route: the two ends, local lines or not, where you get off, and the stations in order (for Trip
 * mode's "get off next"). A trip lasts one day and routes are not saved (owner, #46, 2026-11-06), so the plan
 * forgets itself at the end of the day it was made. Kept in preferences so it survives the app being stopped.
 */
data class SavedTrip(
    val from: String,
    val to: String,
    val localOnly: Boolean,
    val getOff: Set<String>,
    val stations: List<String>,
    val untilMs: Long,
)

@Singleton
class TripStore @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("trip", Context.MODE_PRIVATE)
    private val _trip = MutableStateFlow(read())
    val trip: StateFlow<SavedTrip?> = _trip.asStateFlow()

    /** Today's plan, or null; an old day's plan is cleared on the way. */
    fun current(nowMs: Long = System.currentTimeMillis()): SavedTrip? {
        val t = _trip.value ?: read()
        if (t != null && nowMs > t.untilMs) { clear(); return null }
        return t
    }

    fun save(t: SavedTrip) {
        prefs.edit()
            .putString(K_FROM, t.from).putString(K_TO, t.to).putBoolean(K_LOCAL, t.localOnly)
            .putString(K_OFF, t.getOff.joinToString(SEP)).putString(K_STATIONS, t.stations.joinToString(SEP))
            .putLong(K_UNTIL, t.untilMs)
            .apply()
        _trip.value = t
    }

    fun clear() {
        prefs.edit().clear().apply()
        _trip.value = null
    }

    private fun read(): SavedTrip? {
        val from = prefs.getString(K_FROM, null) ?: return null
        val to = prefs.getString(K_TO, null) ?: return null
        val until = prefs.getLong(K_UNTIL, 0L)
        if (System.currentTimeMillis() > until) return null
        fun list(k: String) = prefs.getString(k, "").orEmpty().split(SEP).filter { it.isNotEmpty() }
        return SavedTrip(from, to, prefs.getBoolean(K_LOCAL, false), list(K_OFF).toSet(), list(K_STATIONS), until)
    }

    private companion object {
        const val SEP = "\u001f"
        const val K_FROM = "from"; const val K_TO = "to"; const val K_LOCAL = "local_only"
        const val K_OFF = "get_off"; const val K_STATIONS = "stations"; const val K_UNTIL = "until"
    }
}

/**
 * Where to get off next, for Trip mode's notification: the first stop you get off at, from the passed station
 * nearest to you onwards. [positions] are the route's stations in order with their places.
 */
fun nextGetOff(positions: List<Triple<String, Double, Double>>, getOff: Set<String>, lat: Double, lng: Double): Pair<String, Double>? {
    if (positions.isEmpty()) return null
    val nearest = positions.indices.minBy { i -> io.github.thisisartman.collectibles.location.distanceM(lat, lng, positions[i].second, positions[i].third) }
    val next = (nearest until positions.size).firstOrNull { positions[it].first in getOff } ?: return null
    val p = positions[next]
    return p.first to io.github.thisisartman.collectibles.location.distanceM(lat, lng, p.second, p.third)
}
