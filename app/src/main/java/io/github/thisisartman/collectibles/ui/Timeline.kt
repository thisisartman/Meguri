package io.github.thisisartman.collectibles.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import io.github.thisisartman.collectibles.R
import io.github.thisisartman.collectibles.data.ItemRow
import io.github.thisisartman.collectibles.data.TripReport
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Timeline (M6): every find with a day, newest day first, each day with its finds and their times. Finds had before
 * the app have no day and are not here. A day opens its trip report.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimelineScreen(vm: MainViewModel, nav: NavHostController, onBack: () -> Unit) {
    val ja = isJapanese()
    val rows by vm.rows.collectAsStateWithLifecycle()
    val byId = remember(rows) { rows.associateBy { it.item.id } }
    val finds by remember { vm.findsBetween(0L, Long.MAX_VALUE) }.collectAsState(emptyList())
    val days = remember(finds, byId) {
        finds.mapNotNull { f -> val at = f.find.acquiredAt ?: return@mapNotNull null; byId[f.find.itemId]?.let { Triple(at, f.find, it) } }
            .groupBy { TripReport.dayStart(it.first) }.toSortedMap(compareByDescending { it })
    }
    val time = remember { Dates.timeFormat() }
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            windowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
            title = { Text(stringResource(R.string.timeline_title)) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
        )
        if (days.isEmpty()) {
            Text(stringResource(R.string.timeline_empty), Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@Column
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 16.dp)) {
            days.forEach { (day, list) ->
                item(key = "d$day") {
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { nav.navigate("trip-report?day=$day") }.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(dateText(day), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                        Text(pluralStringResource(R.plurals.timeline_count, list.size, list.size), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                items(list.sortedByDescending { it.first }, key = { it.second.id }) { (at, find, row) ->
                    TimelineRow(row, if (TripReport.hasTime(find)) time.format(Date(at)) else null, ja) { nav.openItem(row.item.id) }
                }
            }
        }
    }
}

@Composable
private fun TimelineRow(row: ItemRow, time: String?, ja: Boolean, onOpen: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(onClick = onOpen).padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Mark(row, 40.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(row.item.displayName(ja), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(kindName(row.item), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (time != null) Text(time, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
