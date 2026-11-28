package io.github.thisisartman.collectibles.alerts

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import io.github.thisisartman.collectibles.location.distanceM

/**
 * The app's kinds of notification (#228), each its own Android channel and a switch in Settings › Notifications.
 * Off stops that kind everywhere. The Trip mode notification is not among them: Android needs it while Trip runs.
 */
enum class NotifyCategory(val channel: String, val key: String) {
    ALERTS(AlertNotifier.CH_ALERTS, "alerts"),
    /** Calls to action: the photo after a collect, the new-photo hint. */
    SUGGESTIONS(AlertNotifier.CH_PHOTOS, "suggestions"),
    BACKUPS(AlertNotifier.CH_DATA, "backups"),
    UPDATES(AlertNotifier.CH_UPDATES, "updates"),
}

/** The four switches, on by default; held in state too, so Settings shows a change at once. */
object NotifyPrefs {
    private const val PREFS = "notifications"
    private val state = mutableStateOf<Map<NotifyCategory, Boolean>?>(null)

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun all(context: Context): Map<NotifyCategory, Boolean> = state.value
        ?: NotifyCategory.entries.associateWith { prefs(context).getBoolean(it.key, true) }.also { state.value = it }

    fun on(context: Context, c: NotifyCategory): Boolean = all(context).getValue(c)

    fun set(context: Context, c: NotifyCategory, on: Boolean) {
        prefs(context).edit().putBoolean(c.key, on).apply()
        state.value = all(context) + (c to on)
    }
}

/**
 * The photo suggestion after a collect (#228): the finds it is about, at one place. Several collects within a minute
 * at one place make one suggestion, so a station's stamp and Ekitag ask once.
 */
data class Nudge(val finds: List<Pair<String, String>>, val lat: Double?, val lng: Double?, val lastMs: Long) {
    /** The finds' ids, oldest first; the photo goes on the newest. */
    val findIds: List<String> get() = finds.map { it.first }
    val findId: String get() = finds.last().first
    /** What was collected, each name once (a station's stamp and Ekitag share one). */
    val names: List<String> get() = finds.map { it.second }.distinct()
}

/** Pure, so it is unit tested. */
object NudgeGroup {
    const val WINDOW_MS = 60_000L
    /** About the size of a station or a shrine's grounds. */
    const val SAME_PLACE_M = 150.0

    /** Two spots are one place within [SAME_PLACE_M]; two finds with no position (a notebook page) count as one too. */
    fun samePlace(aLat: Double?, aLng: Double?, bLat: Double?, bLng: Double?): Boolean = when {
        aLat == null || aLng == null || bLat == null || bLng == null -> aLat == null && bLat == null
        else -> distanceM(aLat, aLng, bLat, bLng) <= SAME_PLACE_M
    }

    /** [prev] with this collect joined when it is within a minute of the last one at the same place, else a new one. */
    fun add(prev: Nudge?, name: String, findId: String, lat: Double?, lng: Double?, atMs: Long): Nudge =
        if (prev != null && atMs - prev.lastMs in 0..WINDOW_MS && samePlace(prev.lat, prev.lng, lat, lng) && findId !in prev.findIds) {
            prev.copy(finds = prev.finds + (findId to name), lastMs = atMs)
        } else Nudge(listOf(findId to name), lat, lng, atMs)

    /** An undone find leaves the suggestion; null when nothing is left. */
    fun remove(prev: Nudge?, findId: String): Nudge? {
        prev ?: return null
        val left = prev.finds.filter { it.first != findId }
        return if (left.isEmpty()) null else prev.copy(finds = left)
    }
}
