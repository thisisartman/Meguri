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
        // The four categories (#228), named as in Settings › Notifications; the ids stay, so earlier choices in the
        // phone's settings are kept
        m.createNotificationChannel(
            NotificationChannel(CH_ALERTS, context.getString(R.string.channel_alerts), NotificationManager.IMPORTANCE_HIGH)
                .apply { description = context.getString(R.string.alerts_channel_sub) },
        )
        // A fresh id, so phones that had the quiet photo-hint channel get this one's importance; the old one goes
        m.deleteNotificationChannel(OLD_CH_PHOTOS)
        m.createNotificationChannel(
            NotificationChannel(CH_PHOTOS, context.getString(R.string.channel_suggestions), NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = context.getString(R.string.channel_suggestions_sub); setShowBadge(false) },
        )
        m.createNotificationChannel(
            NotificationChannel(CH_UPDATES, context.getString(R.string.channel_updates), NotificationManager.IMPORTANCE_LOW)
                .apply { description = context.getString(R.string.channel_updates_sub) },
        )
        m.createNotificationChannel(
            NotificationChannel(CH_TRIP, context.getString(R.string.trip_channel), NotificationManager.IMPORTANCE_LOW)
                .apply { description = context.getString(R.string.trip_channel_sub); setShowBadge(false) },
        )
        m.createNotificationChannel(
            NotificationChannel(CH_DATA, context.getString(R.string.channel_backups), NotificationManager.IMPORTANCE_LOW)
                .apply { description = context.getString(R.string.job_channel_sub); setShowBadge(false) },
        )
    }

    fun canPost(): Boolean = nm.areNotificationsEnabled() &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** Allowed by the phone and by its category's switch in Settings › Notifications (#228). */
    fun canPost(category: NotifyCategory): Boolean = canPost() && NotifyPrefs.on(context, category)

    /** Names follow the app's own language (English mode shows romaji, as everywhere else). */
    internal fun nameOf(item: Item): String {
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
            ItemType.DAM_CARD -> R.string.type_dam
            ItemType.PLACE -> R.string.type_place
            ItemType.MUSEUM_STAMP -> R.string.type_museum
            ItemType.ROADSIDE_STATION -> R.string.type_roadside
        },
    )

    /** Same wording as the screens: "here" under 25 m, then metres, then kilometres. */
    private fun distance(m: Double): String = formatDistance(m).let { (res, v) -> context.getString(res, v) }

    fun notifyNear(item: Item, meters: Double, fix: Fix?) {
        if (!canPost(NotifyCategory.ALERTS)) return
        val id = notificationId(item.id)
        val open = PendingIntent.getActivity(
            context, id, MainActivity.openItemIntent(context, item.id), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        // Collect opens the item page (owner, 2026-11-07: photos are added there, so no Photo button)
        val collect = PendingIntent.getActivity(
            context, id + 1, MainActivity.collectIntent(context, item.id, fix), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
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
            .addAction(0, context.getString(R.string.alert_done), collect)
            .addAction(0, context.getString(R.string.alert_dismiss), dismiss)
            .build()
        post(id, n)
    }

    /**
     * A new camera photo taken near something still to get (M7): "Attach" puts it on that item, "Pick another" opens
     * the share screen with it. Quiet, on its own channel, so it can be turned off separately from alerts.
     */
    fun notifyPhoto(item: Item, photo: android.net.Uri, meters: Double) {
        if (!canPost(NotifyCategory.SUGGESTIONS)) return
        val id = notificationId("photo:" + photo)
        val attach = PendingIntent.getActivity(
            context, id + 1, MainActivity.attachIntent(context, item.id, photo), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val pick = PendingIntent.getActivity(
            context, id + 2, MainActivity.shareOneIntent(context, photo), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val text = context.getString(R.string.photo_hint_text, typeLabel(item.type), distance(meters))
        val n = NotificationCompat.Builder(context, CH_PHOTOS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.photo_hint_title, nameOf(item)))
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true)
            .setContentIntent(pick)
            .setAutoCancel(true)
            .addAction(0, context.getString(R.string.photo_hint_attach), attach)
            .addAction(0, context.getString(R.string.photo_hint_pick), pick)
            .build()
        post(id, n)
    }

    /** Replaces the alert after Done: what was logged, with Undo for a while (M4). Only reached from alerts posted by builds before 0.3.0-beta.6. */
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

    /**
     * After a collect (#228): "Collected <name>. Take a photo of the place, or a selfie with it?", with Take photo
     * (the camera, the picture going on the newest find as its place photo) and Not now. One notification for a
     * whole [Nudge], updated as it grows; quiet after the first.
     */
    fun notifyNudge(n: Nudge) {
        if (!canPost(NotifyCategory.SUGGESTIONS)) return
        val take = PendingIntent.getActivity(
            context, NUDGE_ID + 1, MainActivity.placePhotoIntent(context, n.findId), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notNow = broadcast(AlertReceiver.ACTION_NUDGE_DISMISS, NUDGE_ID + 2) {}
        val names = n.names.joinToString(context.getString(R.string.nudge_names_sep))
        val text = context.getString(R.string.nudge_text)
        val built = NotificationCompat.Builder(context, CH_PHOTOS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.nudge_title, names))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .setOnlyAlertOnce(true)
            .setContentIntent(take)
            .setAutoCancel(true)
            .setTimeoutAfter(NUDGE_TIMEOUT_MS)
            .addAction(0, context.getString(R.string.nudge_take), take)
            .addAction(0, context.getString(R.string.nudge_not_now), notNow)
            .build()
        post(NUDGE_ID, built)
    }

    fun cancelNudge() = nm.cancel(NUDGE_ID)

    /** A newer Meguri (#228, Updates): opens the release page. Only when Check for updates found one. */
    fun notifyUpdate(tag: String, url: String) {
        if (!canPost(NotifyCategory.UPDATES)) return
        val open = PendingIntent.getActivity(
            context, UPDATE_ID, Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, CH_UPDATES)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.update_notify_title, tag.removePrefix("v")))
            .setContentText(context.getString(R.string.update_notify_text))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        post(UPDATE_ID, n)
    }

    /** "浦佐 → 新潟 until 23:59" when a route is planned for today. */
    fun tripRouteTitle(from: Item, to: Item, untilText: String): String = context.getString(R.string.trip_route_until, nameOf(from), nameOf(to), untilText)

    fun getOffText(station: Item, meters: Double): String = context.getString(R.string.trip_get_off_next, nameOf(station), distance(meters))

    fun tripNotification(untilText: String, nearest: String?, title: String? = null): android.app.Notification {
        val open = PendingIntent.getActivity(context, 1, MainActivity.launchIntent(context), PendingIntent.FLAG_IMMUTABLE)
        val stop = broadcast(AlertReceiver.ACTION_STOP_TRIP, 2) {}
        return NotificationCompat.Builder(context, CH_TRIP)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title ?: context.getString(R.string.trip_on_until, untilText))
            .setContentText(nearest ?: context.getString(R.string.trip_checking))
            .setStyle(NotificationCompat.BigTextStyle().bigText(nearest ?: context.getString(R.string.trip_checking)))
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
        const val CH_PHOTOS = "suggestions"
        /** The photo hints' channel before Suggestions (deleted at start). */
        const val OLD_CH_PHOTOS = "photo_hints"
        /** Back up now, Restore and Add photos from a file while they run (#217). */
        const val CH_DATA = "backup_restore"
        /** A newer release (#228). */
        const val CH_UPDATES = "updates"
        const val TRIP_ID = 1
        /** The photo suggestion after a collect (#228), and its two buttons' codes; below every item's id (16 and up). */
        const val NUDGE_ID = 4
        const val UPDATE_ID = 8
        /** A suggestion not acted on goes by itself after an hour: you have left the place by then. */
        const val NUDGE_TIMEOUT_MS = 60 * 60_000L
        const val UNDO_WINDOW_MS = 30_000L

        /** One notification per item; even numbers step by 8 so each item's PendingIntent codes stay apart. */
        fun notificationId(itemId: String): Int = (itemId.hashCode() and 0x0FFFFFFF) / 8 * 8 + 16
    }
}
