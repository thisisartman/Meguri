package io.github.thisisartman.collectibles.ui

import android.content.Intent
import io.github.thisisartman.collectibles.MainActivity

/** Something the app was opened for from outside: an alert tapped, its Photo action, or the Trip mode tile. */
sealed interface AppRequest {
    data class OpenItem(val itemId: String) : AppRequest
    data class Photo(val itemId: String) : AppRequest
    data object StartTrip : AppRequest

    companion object {
        fun from(intent: Intent?): AppRequest? {
            intent ?: return null
            intent.getStringExtra(MainActivity.EXTRA_PHOTO_ITEM)?.let { return Photo(it) }
            intent.getStringExtra(MainActivity.EXTRA_OPEN_ITEM)?.let { return OpenItem(it) }
            if (intent.getBooleanExtra(MainActivity.EXTRA_START_TRIP, false)) return StartTrip
            return null
        }
    }
}
