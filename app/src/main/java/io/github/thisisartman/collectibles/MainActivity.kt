package io.github.thisisartman.collectibles

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import dagger.hilt.android.AndroidEntryPoint
import io.github.thisisartman.collectibles.ui.App
import io.github.thisisartman.collectibles.ui.AppRequest
import io.github.thisisartman.collectibles.ui.CollectiblesTheme

/**
 * A random token made once per install (A8), put on the app's own intents to [MainActivity] so their internal
 * extras can be told from another app's. Kept in the app's private settings; never leaves the phone.
 */
object InternalToken {
    @Volatile private var cached: String? = null

    fun get(context: Context): String = cached ?: synchronized(this) {
        cached ?: run {
            val prefs = context.applicationContext.getSharedPreferences("internal", Context.MODE_PRIVATE)
            prefs.getString("token", null) ?: java.util.UUID.randomUUID().toString().also { prefs.edit().putString("token", it).commit() }
        }.also { cached = it }
    }
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    /** What a notification or the Trip mode tile asked for; the UI clears it once handled. */
    private val request = mutableStateOf<AppRequest?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // A restored activity must not act on its old intent a second time
        if (savedInstanceState == null) request.value = AppRequest.from(intent, InternalToken.get(this))
        setContent { CollectiblesTheme { App(request.value, onRequestHandled = { request.value = null }) } }
    }

    @javax.inject.Inject lateinit var alertSettings: io.github.thisisartman.collectibles.alerts.AlertSettings

    override fun onResume() {
        super.onResume()
        io.github.thisisartman.collectibles.ui.Dates.readClock(this)
        io.github.thisisartman.collectibles.alerts.TripModeService.resumeIfOn(this, alertSettings)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        AppRequest.from(intent, InternalToken.get(this))?.let { request.value = it }
    }

    companion object {
        const val EXTRA_OPEN_ITEM = "open_item"
        const val EXTRA_COLLECT_ITEM = "collect_item"
        const val EXTRA_FIX_LAT = "fix_lat"
        const val EXTRA_FIX_LNG = "fix_lng"
        const val EXTRA_FIX_ACC = "fix_acc"
        const val EXTRA_FIX_TIME = "fix_time"
        const val EXTRA_START_TRIP = "start_trip"
        /** The app shortcut's id (#205): the same name as the extra in res/xml/shortcuts.xml. */
        const val EXTRA_SHORTCUT = "shortcut"

        /** Marks an intent as the app's own (A8): see [AppRequest.trusted]. */
        const val EXTRA_TOKEN = "internal_token"

        fun launchIntent(context: Context) = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(EXTRA_TOKEN, InternalToken.get(context))

        fun openItemIntent(context: Context, itemId: String) = launchIntent(context).putExtra(EXTRA_OPEN_ITEM, itemId)
        /** The alert's Collect button: logs the find at the position that set off the alert, then opens the item. */
        fun collectIntent(context: Context, itemId: String, fix: io.github.thisisartman.collectibles.location.Fix?) =
            launchIntent(context).putExtra(EXTRA_COLLECT_ITEM, itemId).apply {
                if (fix != null) {
                    putExtra(EXTRA_FIX_LAT, fix.lat); putExtra(EXTRA_FIX_LNG, fix.lng); putExtra(EXTRA_FIX_TIME, fix.timeMs)
                    fix.accuracyM?.let { putExtra(EXTRA_FIX_ACC, it) }
                }
            }
        fun startTripIntent(context: Context) = launchIntent(context).putExtra(EXTRA_START_TRIP, true)
        /** The new-photo hint's "Attach": that photo onto that item (M7). */
        fun attachIntent(context: Context, itemId: String, photo: android.net.Uri) =
            launchIntent(context).putExtra(EXTRA_ATTACH_ITEM, itemId).putExtra(EXTRA_ATTACH_URI, photo.toString())
        /** The new-photo hint's "Pick another": the share screen with that one photo. */
        fun shareOneIntent(context: Context, photo: android.net.Uri) = launchIntent(context).putExtra(EXTRA_SHARE_URI, photo.toString())
        const val EXTRA_ATTACH_ITEM = "attach_item"
        const val EXTRA_ATTACH_URI = "attach_uri"
        const val EXTRA_SHARE_URI = "share_uri"
    }
}
