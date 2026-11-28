package io.github.thisisartman.collectibles.mockup

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.thisisartman.collectibles.ui.CollectiblesTheme

/**
 * Page-by-page mockups (dev builds only, like eSaifu's): opens straight onto one page with dummy data in memory, no
 * database, no bottom bar, no alerts, no network.
 *
 *   adb shell am start -n io.github.thisisartman.collectibles.dev/io.github.thisisartman.collectibles.mockup.MockupActivity --es page hold-tile
 */
class MockupActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val page = intent.getStringExtra("page").orEmpty()
        setContent {
            CollectiblesTheme {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding()) { MockupPage(page) }
            }
        }
    }
}

/** Each mockup page by its name; a new feature adds its own line here. */
val MOCKUP_PAGES = listOf(
    "hold-tile" to "Press and hold a tile: A wide then icons, B hold and drag (#227)",
)

@Composable
fun MockupPage(page: String) {
    when (page) {
        "hold-tile" -> HoldTileMockup()
        else -> Column(Modifier.padding(24.dp)) {
            Text(if (page.isEmpty()) "No page given" else "Unknown page: $page", style = MaterialTheme.typography.titleLarge)
            Text("Open one with --es page <name>. Pages:", Modifier.padding(top = 12.dp, bottom = 8.dp))
            MOCKUP_PAGES.forEach { (name, what) -> Text("$name: $what", Modifier.padding(vertical = 4.dp)) }
        }
    }
}
