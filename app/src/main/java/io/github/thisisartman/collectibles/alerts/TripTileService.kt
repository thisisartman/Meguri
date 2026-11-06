package io.github.thisisartman.collectibles.alerts

import android.app.PendingIntent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import io.github.thisisartman.collectibles.MainActivity
import io.github.thisisartman.collectibles.R

/**
 * Quick-settings tile for Trip mode (FR-12a). Turning it on opens the app, because Android only lets a location
 * service start while the app is on screen; turning it off works straight from the tile.
 */
class TripTileService : TileService() {
    private val tripOn get() = getSharedPreferences("alerts", MODE_PRIVATE).getLong("trip_until", 0L) > System.currentTimeMillis()

    override fun onStartListening() {
        qsTile?.apply {
            state = if (tripOn) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            label = getString(R.string.trip_title)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) subtitle = getString(if (tripOn) R.string.tile_on else R.string.tile_off)
            updateTile()
        }
    }

    override fun onClick() {
        if (tripOn) {
            TripModeService.stop(this)
            onStartListening()
            return
        }
        val pi = PendingIntent.getActivity(this, 3, MainActivity.startTripIntent(this), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(pi)
        } else {
            @Suppress("DEPRECATION", "StartActivityAndCollapseDeprecated")
            startActivityAndCollapse(MainActivity.startTripIntent(this).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}
