package io.github.thisisartman.collectibles.ui

import android.content.Intent
import io.github.thisisartman.collectibles.MainActivity
import io.github.thisisartman.collectibles.location.Fix

/** Something the app was opened for from outside: an alert tapped, its Collect action, or the Trip mode tile. */
sealed interface AppRequest {
    data class OpenItem(val itemId: String) : AppRequest
    data class Collect(val itemId: String, val fix: Fix?) : AppRequest
    data object StartTrip : AppRequest
    data class Share(val uris: List<android.net.Uri>) : AppRequest
    data class Attach(val itemId: String, val uri: android.net.Uri) : AppRequest

    companion object {
        fun from(intent: Intent?): AppRequest? {
            intent ?: return null
            // Photos shared from the gallery (#80): one, or several at once
            when (intent.action) {
                Intent.ACTION_SEND -> androidx.core.content.IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, android.net.Uri::class.java)
                    ?.let { return Share(listOf(it)) }
                Intent.ACTION_SEND_MULTIPLE -> androidx.core.content.IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, android.net.Uri::class.java)
                    ?.takeIf { it.isNotEmpty() }?.let { return Share(it.toList()) }
            }
            // The new-photo hint (M7)
            intent.getStringExtra(MainActivity.EXTRA_ATTACH_ITEM)?.let { id ->
                intent.getStringExtra(MainActivity.EXTRA_ATTACH_URI)?.let { return Attach(id, android.net.Uri.parse(it)) }
            }
            intent.getStringExtra(MainActivity.EXTRA_SHARE_URI)?.let { return Share(listOf(android.net.Uri.parse(it))) }
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
