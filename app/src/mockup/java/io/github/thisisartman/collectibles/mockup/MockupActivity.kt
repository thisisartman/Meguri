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
import dagger.hilt.android.AndroidEntryPoint
import io.github.thisisartman.collectibles.data.CollectionRepository
import io.github.thisisartman.collectibles.ui.CollectiblesTheme
import javax.inject.Inject

/**
 * Page-by-page mockups and checks (dev builds only, like eSaifu's): opens straight onto one page, no bottom bar, no
 * alerts.
 *
 *   adb shell am start -n io.github.thisisartman.collectibles.dev/io.github.thisisartman.collectibles.mockup.MockupActivity --es page bulk-suggest --es dir /sdcard/Pictures/MeguriTest
 */
@AndroidEntryPoint
class MockupActivity : ComponentActivity() {
    @Inject lateinit var repo: CollectionRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val page = intent.getStringExtra("page").orEmpty()
        val dir = intent.getStringExtra("dir")
        setContent {
            CollectiblesTheme {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding()) { MockupPage(page, dir, repo) }
            }
        }
    }
}

/** Each page by its name; a new one adds its own line here. */
val MOCKUP_PAGES = listOf(
    "bulk-suggest" to "Reads every picture in --es dir and shows what #206 suggests; writes files/bulk-suggest.csv",
)

@Composable
fun MockupPage(page: String, dir: String?, repo: CollectionRepository) {
    when (page) {
        "bulk-suggest" -> BulkSuggestPage(dir, repo)
        else -> Column(Modifier.padding(24.dp)) {
            Text(if (page.isEmpty()) "No page given" else "Unknown page: $page", style = MaterialTheme.typography.titleLarge)
            Text("Open one with --es page <name>. Pages:", Modifier.padding(top = 12.dp, bottom = 8.dp))
            MOCKUP_PAGES.forEach { (name, what) -> Text("$name: $what", Modifier.padding(vertical = 4.dp)) }
        }
    }
}
