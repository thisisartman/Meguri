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
 * The profile page (#72, owner's pick A "at a glance"): totals, a small map of where you collected, progress per
 * collection, and your own collections (moved here from Settings). Opened from the top-right menu.
 */
@Composable
fun ProfileScreen(vm: MainViewModel, nav: NavHostController, myCollections: @Composable () -> Unit, onBack: () -> Unit) {
    val rows by vm.rows.collectAsStateWithLifecycle()
    val summaries by vm.summaries.collectAsStateWithLifecycle()
    val lines by vm.lines.collectAsStateWithLifecycle()
    val ja = isJapanese()
    val totals = remember(rows) { profileTotals(rows) }
    val pins = remember(rows) { rows.filter { it.collected }.mapNotNull { it.toPin() } }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(Modifier.padding(start = 4.dp, top = 8.dp, end = 16.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back)) }
            Text(stringResource(R.string.profile_title), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(start = 4.dp))
        }

        // 1. Totals
        Surface(Modifier.padding(horizontal = 16.dp).fillMaxWidth(), shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
            Row(Modifier.padding(vertical = 16.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                Total(totals.finds, pluralStringResource(R.plurals.profile_finds, totals.finds), Modifier.weight(1f))
                Total(totals.kinds, pluralStringResource(R.plurals.profile_kinds, totals.kinds), Modifier.weight(1f))
                Total(totals.today, stringResource(R.string.profile_today), Modifier.weight(1f))
            }
        }

        // 2. Where you collected; tap for the full map of collected things
        Spacer(Modifier.height(12.dp))
        Surface(
            Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(200.dp),
            shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            if (pins.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.profile_map_empty), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                }
            } else {
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

        // 3. Progress for each collection, like My book but about you
        SectionTitle(stringResource(R.string.profile_collections))
        // Collections you have started come first, then the rest, each in My book's order
        summaries.sortedByDescending { it.collected > 0 }.forEach { s ->
            Surface(
                Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth()
                    .clickable { if (s.lineId != null) nav.openLine(s.lineId) else nav.openBook() },
                shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // The same names as My book: "Manhole cards · Niigata", "Station stamps · Joetsu Line", "Goshuin"
                        val lineName = lines.firstOrNull { it.id == s.lineId }?.let { if (ja) it.nameJa else it.nameRomaji ?: it.nameJa }
                        Text(summaryTitle(s, lineName), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            if (s.total != null) "${s.collected} / ${s.total}" else "${s.collected}",
                            style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium,
                        )
                    }
                    if (s.total != null && s.total > 0) {
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { s.collected.toFloat() / s.total },
                            modifier = Modifier.fillMaxWidth(),
                            color = LocalTypeColors.current.of(s.type),
                        )
                    }
                }
            }
        }

        // 4. Your own collections
        SectionTitle(stringResource(R.string.more_my_collections))
        myCollections()
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Total(value: Int, label: String, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("$value", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
