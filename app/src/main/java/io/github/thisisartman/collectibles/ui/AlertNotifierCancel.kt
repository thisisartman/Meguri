package io.github.thisisartman.collectibles.ui

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import io.github.thisisartman.collectibles.alerts.AlertNotifier

/** Clears an item's alert once the app has opened for it. */
object AlertNotifierCancel {
    fun cancel(context: Context, itemId: String) = NotificationManagerCompat.from(context).cancel(AlertNotifier.notificationId(itemId))
}
