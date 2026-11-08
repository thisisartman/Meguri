package io.github.thisisartman.collectibles.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import io.github.thisisartman.collectibles.R
import io.github.thisisartman.collectibles.data.ItemRow
import java.util.Calendar

/** Totals shown at the top of the profile page. */
data class ProfileTotals(val finds: Int, val kinds: Int, val today: Int)

/** Finds counts every collection (duplicates too); today leaves out things marked as had before the app. */
fun profileTotals(rows: List<ItemRow>, now: Long = System.currentTimeMillis()): ProfileTotals {
    val start = Calendar.getInstance().apply {
        timeInMillis = now
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    val got = rows.filter { it.collected }
    return ProfileTotals(
        finds = got.sumOf { it.findCount },
        kinds = got.map { it.item.type }.distinct().size,
        today = got.count { (it.lastAt ?: 0) >= start && it.lastBeforeApp != true },
    )
}

/**
 * The top of My book (was the profile page, #72; merged into My book, owner 2026-11-08): totals, a small map of where
 * you collected, and what you collected last, each opening its page (where photos are added). The collections and
 * their progress follow below, in My book's own sections.
 */
@Composable
fun BookSummary(vm: MainViewModel, nav: NavHostController) {
    val rows by vm.rows.collectAsStateWithLifecycle()
    val summaries by vm.summaries.collectAsStateWithLifecycle()
    val totals = remember(rows) { profileTotals(rows) }
    val pins = remember(rows) { rows.filter { it.collected }.mapNotNull { it.toPin() } }

    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Surface(Modifier.padding(horizontal = 16.dp).fillMaxWidth(), shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
            Row(Modifier.padding(vertical = 14.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                Total(totals.finds, pluralStringResource(R.plurals.profile_finds, totals.finds), Modifier.weight(1f))
                // Collections started (owner, 2026-11-08: "kinds" didn't say anything)
                val started = summaries.count { it.collected > 0 }
                Total(started, pluralStringResource(R.plurals.profile_collections, started), Modifier.weight(1f))
                Total(totals.today, stringResource(R.string.profile_today), Modifier.weight(1f))
            }
        }
        if (pins.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Surface(
                Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(170.dp),
                shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Box(Modifier.fillMaxSize()) {
                    MeguriMap(pins, null, Modifier.fillMaxSize(), interactive = false, onMapTap = { nav.openMap(MapSection.COLLECTED) }, memoryKey = null, inCard = true)
                    Surface(
                        Modifier.align(Alignment.BottomEnd).padding(10.dp).heightIn(min = 40.dp).clickable { nav.openMap(MapSection.COLLECTED) },
                        shape = RoundedCornerShape(20.dp), shadowElevation = 2.dp,
                    ) {
                        Text(stringResource(R.string.profile_open_map), Modifier.padding(horizontal = 14.dp, vertical = 10.dp), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
        // Recently collected moved into each kind's page (B1, 2026-11-08)
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun Total(value: Int, label: String, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("$value", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
