package io.github.thisisartman.collectibles.ui

import android.content.Intent
import io.github.thisisartman.collectibles.MainActivity
import io.github.thisisartman.collectibles.location.Fix

/** Something the app was opened for from outside: an alert tapped, its Collect action, or the Trip mode tile. */
sealed interface AppRequest {
    data class OpenItem(val itemId: String) : AppRequest
    data class Collect(val itemId: String, val fix: Fix?) : AppRequest
    data object StartTrip : AppRequest

    companion object {
        fun from(intent: Intent?): AppRequest? {
            intent ?: return null
            intent.getStringExtra(MainActivity.EXTRA_COLLECT_ITEM)?.let { id ->
                val fix = if (intent.hasExtra(MainActivity.EXTRA_FIX_LAT)) Fix(
                    intent.getDoubleExtra(MainActivity.EXTRA_FIX_LAT, 0.0), intent.getDoubleExtra(MainActivity.EXTRA_FIX_LNG, 0.0),
                    if (intent.hasExtra(MainActivity.EXTRA_FIX_ACC)) intent.getFloatExtra(MainActivity.EXTRA_FIX_ACC, 0f) else null,
                    intent.getLongExtra(MainActivity.EXTRA_FIX_TIME, 0L),
                ) else null
                return Collect(id, fix)
            }
            intent.getStringExtra(MainActivity.EXTRA_OPEN_ITEM)?.let { return OpenItem(it) }
            if (intent.getBooleanExtra(MainActivity.EXTRA_START_TRIP, false)) return StartTrip
            return null
        }
    }
}
