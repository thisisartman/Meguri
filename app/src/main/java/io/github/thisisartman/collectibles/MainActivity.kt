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

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    /** What a notification or the Trip mode tile asked for; the UI clears it once handled. */
    private val request = mutableStateOf<AppRequest?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // A restored activity must not act on its old intent a second time
        if (savedInstanceState == null) request.value = AppRequest.from(intent)
        setContent { CollectiblesTheme { App(request.value, onRequestHandled = { request.value = null }) } }
    }

    @javax.inject.Inject lateinit var alertSettings: io.github.thisisartman.collectibles.alerts.AlertSettings

    override fun onResume() {
        super.onResume()
        io.github.thisisartman.collectibles.alerts.TripModeService.resumeIfOn(this, alertSettings)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        AppRequest.from(intent)?.let { request.value = it }
    }

    companion object {
        const val EXTRA_OPEN_ITEM = "open_item"
        const val EXTRA_PHOTO_ITEM = "photo_item"
        const val EXTRA_START_TRIP = "start_trip"

        fun launchIntent(context: Context) = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)

        fun openItemIntent(context: Context, itemId: String) = launchIntent(context).putExtra(EXTRA_OPEN_ITEM, itemId)
        fun photoIntent(context: Context, itemId: String) = launchIntent(context).putExtra(EXTRA_PHOTO_ITEM, itemId)
        fun startTripIntent(context: Context) = launchIntent(context).putExtra(EXTRA_START_TRIP, true)
    }
}
