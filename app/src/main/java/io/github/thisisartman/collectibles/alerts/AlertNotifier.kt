package io.github.thisisartman.collectibles.alerts

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.thisisartman.collectibles.MainActivity
import io.github.thisisartman.collectibles.R
import io.github.thisisartman.collectibles.data.Item
import io.github.thisisartman.collectibles.data.ItemType
import io.github.thisisartman.collectibles.location.Fix
import io.github.thisisartman.collectibles.ui.HERE_M
import io.github.thisisartman.collectibles.ui.formatDistance
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** Alert notifications with Done, Photo and Dismiss (FR-12, M4); the Trip mode notification. */
@Singleton
class AlertNotifier @Inject constructor(@ApplicationContext private val context: Context) {
    private val nm = NotificationManagerCompat.from(context)

    fun createChannels() {
        val m = context.getSystemService(NotificationManager::class.java) ?: return
        m.createNotificationChannel(
            NotificationChannel(CH_ALERTS, context.getString(R.string.alerts_channel), NotificationManager.IMPORTANCE_HIGH)
                .apply { description = context.getString(R.string.alerts_channel_sub) },
        )
        m.createNotificationChannel(
            NotificationChannel(CH_TRIP, context.getString(R.string.trip_channel), NotificationManager.IMPORTANCE_LOW)
                .apply { description = context.getString(R.string.trip_channel_sub); setShowBadge(false) },
        )
    }

    fun canPost(): Boolean = nm.areNotificationsEnabled() &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** Names follow the app's own language (English mode shows romaji, as everywhere else). */
    private fun nameOf(item: Item): String {
        val ja = context.resources.configuration.locales[0]?.language == "ja"
        return if (ja) item.nameJa else item.nameRomaji ?: item.nameJa
    }

    private fun typeLabel(type: ItemType): String = context.getString(
        when (type) {
            ItemType.MANHOLE_CARD -> R.string.type_manhole
            ItemType.GOSHUIN -> R.string.type_goshuin
            ItemType.EKI_STAMP -> R.string.type_eki
            ItemType.EKITAG -> R.string.type_ekitag
            ItemType.CUSTOM -> R.string.type_custom
        },
    )

    /** Same wording as the screens: "here" under 25 m, then metres, then kilometres. */
    private fun distance(m: Double): String = formatDistance(m).let { (res, v) -> context.getString(res, v) }

    fun notifyNear(item: Item, meters: Double, fix: Fix?) {
        if (!canPost()) return
        val id = notificationId(item.id)
        val open = PendingIntent.getActivity(
            context, id, MainActivity.openItemIntent(context, item.id), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val photo = PendingIntent.getActivity(
            context, id + 1, MainActivity.photoIntent(context, item.id), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val done = broadcast(AlertReceiver.ACTION_DONE, id + 2) {
            putExtra(AlertReceiver.EXTRA_ITEM, item.id)
            if (fix != null) {
                putExtra(AlertReceiver.EXTRA_LAT, fix.lat); putExtra(AlertReceiver.EXTRA_LNG, fix.lng)
                putExtra(AlertReceiver.EXTRA_TIME, fix.timeMs)
                fix.accuracyM?.let { putExtra(AlertReceiver.EXTRA_ACC, it) }
            }
        }
        val dismiss = broadcast(AlertReceiver.ACTION_DISMISS, id + 3) { putExtra(AlertReceiver.EXTRA_ITEM, item.id) }
        val where = listOfNotNull(item.placeName?.takeIf { it != item.nameJa }, item.hoursText).joinToString(" · ")
        val text = if (meters < HERE_M) context.getString(R.string.alert_text_here, typeLabel(item.type))
        else context.getString(R.string.alert_text, typeLabel(item.type), distance(meters))
        val n = NotificationCompat.Builder(context, CH_ALERTS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(nameOf(item))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(if (where.isEmpty()) text else "$text\n$where"))
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(open)
            .setAutoCancel(true)
            .addAction(0, context.getString(R.string.alert_done), done)
            .addAction(0, context.getString(R.string.photo), photo)
            .addAction(0, context.getString(R.string.alert_dismiss), dismiss)
            .build()
        post(id, n)
    }

    /** Replaces the alert after Done: what was logged, with Undo for a while (M4). */
    fun notifyLogged(item: Item, findId: String) {
        if (!canPost()) return
        val id = notificationId(item.id)
        val undo = broadcast(AlertReceiver.ACTION_UNDO, id + 4) {
            putExtra(AlertReceiver.EXTRA_ITEM, item.id); putExtra(AlertReceiver.EXTRA_FIND, findId)
        }
        val n = NotificationCompat.Builder(context, CH_ALERTS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.alert_logged, nameOf(item)))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true)
            .setTimeoutAfter(UNDO_WINDOW_MS)
            .addAction(0, context.getString(R.string.undo), undo)
            .build()
        post(id, n)
    }

    fun cancel(itemId: String) = nm.cancel(notificationId(itemId))

    fun tripNotification(untilText: String, nearest: String?): android.app.Notification {
        val open = PendingIntent.getActivity(context, 1, MainActivity.launchIntent(context), PendingIntent.FLAG_IMMUTABLE)
        val stop = broadcast(AlertReceiver.ACTION_STOP_TRIP, 2) {}
        return NotificationCompat.Builder(context, CH_TRIP)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.trip_on_until, untilText))
            .setContentText(nearest ?: context.getString(R.string.trip_checking))
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(open)
            .addAction(0, context.getString(R.string.trip_stop), stop)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    fun quietSuffix(text: String): String = context.getString(R.string.trip_quiet, text, String.format(Locale.US, "%02d:00", AlertSettings.QUIET_TO))

    fun nearestText(item: Item, meters: Double): String = context.getString(R.string.trip_nearest, nameOf(item), distance(meters))

    private fun broadcast(action: String, code: Int, extras: Intent.() -> Unit): PendingIntent =
        PendingIntent.getBroadcast(
            context, code, Intent(context, AlertReceiver::class.java).setAction(action).apply(extras),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    @Suppress("MissingPermission") // checked in canPost()
    private fun post(id: Int, n: android.app.Notification) {
        if (canPost()) nm.notify(id, n)
    }

    companion object {
        const val CH_ALERTS = "alerts"
        const val CH_TRIP = "trip"
        const val TRIP_ID = 1
        const val UNDO_WINDOW_MS = 30_000L

        /** One notification per item; even numbers step by 8 so each item's PendingIntent codes stay apart. */
        fun notificationId(itemId: String): Int = (itemId.hashCode() and 0x0FFFFFFF) / 8 * 8 + 16
    }
}
