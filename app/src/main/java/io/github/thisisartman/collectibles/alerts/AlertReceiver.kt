package io.github.thisisartman.collectibles.alerts

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import io.github.thisisartman.collectibles.data.CollectionRepository
import io.github.thisisartman.collectibles.data.CollectiblesDb
import io.github.thisisartman.collectibles.data.Via
import io.github.thisisartman.collectibles.debug.FileLog
import io.github.thisisartman.collectibles.location.Fix
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Done, Dismiss and Undo on an alert, and Stop on the Trip mode notification. */
@AndroidEntryPoint
class AlertReceiver : BroadcastReceiver() {
    @Inject lateinit var repo: CollectionRepository
    @Inject lateinit var db: CollectiblesDb
    @Inject lateinit var notifier: AlertNotifier
    @Inject lateinit var geofences: Geofences
    @Inject lateinit var log: FileLog

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                handle(context, intent)
            } catch (e: Exception) {
                log.e("Alert", "Action ${intent.action} failed", e)
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun handle(context: Context, intent: Intent) {
        val itemId = intent.getStringExtra(EXTRA_ITEM)
        when (intent.action) {
            ACTION_DONE -> {
                val item = itemId?.let { db.dao().item(it) } ?: return
                // Logged with the position that set off the alert, when it is recent enough
                val fix = if (intent.hasExtra(EXTRA_LAT)) {
                    Fix(
                        intent.getDoubleExtra(EXTRA_LAT, 0.0), intent.getDoubleExtra(EXTRA_LNG, 0.0),
                        if (intent.hasExtra(EXTRA_ACC)) intent.getFloatExtra(EXTRA_ACC, 0f) else null,
                        intent.getLongExtra(EXTRA_TIME, 0L),
                    )
                } else null
                val findId = repo.logFindAt(item.id, Via.NOTIFICATION, fix)
                notifier.notifyLogged(item, findId)
                log.i("Alert", "Done from the notification for ${item.id}")
            }
            ACTION_UNDO -> {
                val findId = intent.getStringExtra(EXTRA_FIND) ?: return
                repo.undoFind(findId)
                itemId?.let { notifier.cancel(it) }
                log.i("Alert", "Undo from the notification for $itemId")
            }
            ACTION_DISMISS -> { log.d("Alert", "Dismissed from the notification: $itemId"); itemId?.let { notifier.cancel(it) } }
            ACTION_STOP_TRIP -> { log.i("Trip", "Stopped from the notification"); TripModeService.stop(context) }
        }
    }

    companion object {
        const val ACTION_DONE = "io.github.thisisartman.collectibles.alert.DONE"
        const val ACTION_UNDO = "io.github.thisisartman.collectibles.alert.UNDO"
        const val ACTION_DISMISS = "io.github.thisisartman.collectibles.alert.DISMISS"
        const val ACTION_STOP_TRIP = "io.github.thisisartman.collectibles.alert.STOP_TRIP"
        const val EXTRA_ITEM = "item"
        const val EXTRA_FIND = "find"
        const val EXTRA_LAT = "lat"
        const val EXTRA_LNG = "lng"
        const val EXTRA_ACC = "acc"
        const val EXTRA_TIME = "time"
    }
}
