package io.github.thisisartman.collectibles.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import io.github.thisisartman.collectibles.R
import io.github.thisisartman.collectibles.data.ItemRow
import io.github.thisisartman.collectibles.data.ItemType
import kotlinx.coroutines.launch

@Composable
fun BookScreen(vm: MainViewModel, nav: NavHostController, snackbar: SnackbarHostState) {
    val ja = isJapanese()
    val rows by vm.rows.collectAsStateWithLifecycle()
    val summaries by vm.summaries.collectAsStateWithLifecycle()
    val lines by vm.lines.collectAsStateWithLifecycle()
    var marking by rememberSaveable { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.book_title), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            if (marking) TextButton(onClick = { marking = false; selected = emptySet() }) { Text(stringResource(R.string.cancel)) }
            else TextButton(onClick = { marking = true }) { Text(stringResource(R.string.book_mark_owned)) }
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 96.dp)) {
            // Manhole cards by prefecture
            val manhole = rows.filter { it.item.type == ItemType.MANHOLE_CARD }.groupBy { it.item.prefecture.orEmpty() }.toSortedMap()
            manhole.forEach { (pref, list) ->
                item(key = "h:m:$pref") {
                    GroupHeader(ItemType.MANHOLE_CARD, stringResource(R.string.type_manhole_plural) + " · " + pref.ifEmpty { stringResource(R.string.book_no_prefecture) }, "${list.count { it.collected }} / ${list.size}")
                }
                items(list, key = { it.item.id }) { r ->
                    ItemListRow(r, ja, vm.distanceTo(r), marking, r.item.id in selected,
                        onToggle = { selected = if (r.item.id in selected) selected - r.item.id else selected + r.item.id },
                        onOpen = { nav.openItem(r.item.id) },
                        onGotIt = { vm.gotIt(r, r.item.displayName(ja)) })
                }
            }
            // Eki stamps: one row per line (opens the stamp book), then other stations
            val ekiSummaries = summaries.filter { it.type == ItemType.EKI_STAMP }
            if (ekiSummaries.isNotEmpty()) {
                item(key = "h:e") { GroupHeader(ItemType.EKI_STAMP, stringResource(R.string.type_eki_plural), null) }
                items(ekiSummaries, key = { "e:" + it.key }) { s ->
                    val name = lines.firstOrNull { it.id == s.lineId }?.let { if (ja) it.nameJa else it.nameRomaji ?: it.nameJa }
                    SummaryCard(s, summaryTitle(s, name), onClick = { nav.openLine(s.lineId.orEmpty()) })
                }
            }
            // Goshuin
            val goshuin = rows.filter { it.item.type == ItemType.GOSHUIN }
            if (goshuin.isNotEmpty()) {
                item(key = "h:g") { GroupHeader(ItemType.GOSHUIN, stringResource(R.string.type_goshuin), goshuin.count { it.collected }.toString()) }
                items(goshuin.sortedByDescending { it.lastAt ?: 0L }, key = { it.item.id }) { r ->
                    ItemListRow(r, ja, vm.distanceTo(r), marking, r.item.id in selected,
                        onToggle = { selected = if (r.item.id in selected) selected - r.item.id else selected + r.item.id },
                        onOpen = { nav.openItem(r.item.id) },
                        onGotIt = { vm.gotIt(r, r.item.displayName(ja)) })
                }
            }
            if (rows.isEmpty()) {
                item { Text(stringResource(R.string.nearby_empty_book), Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
        if (marking) {
            Button(
                onClick = {
                    val ids = selected
                    vm.markBeforeApp(ids) { n -> scope.launch { snackbar.showSnackbar(context.getString(R.string.book_marked, n)) } }
                    marking = false
                    selected = emptySet()
                },
                enabled = selected.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().padding(12.dp),
            ) { Text(stringResource(R.string.book_mark_done, selected.size)) }
        }
    }
}

@Composable
fun GroupHeader(type: ItemType, title: String, count: String?) {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        TypeDot(type)
        Spacer(Modifier.width(6.dp))
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        if (count != null) Text(count, style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = "tnum"))
    }
}

@Composable
fun ItemListRow(
    r: ItemRow, ja: Boolean, meters: Double?, marking: Boolean, checked: Boolean,
    onToggle: () -> Unit, onOpen: () -> Unit, onGotIt: () -> Unit,
) {
    val canMark = marking && !r.collected
    Row(
        Modifier.fillMaxWidth().clickable(onClick = if (canMark) onToggle else onOpen).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (marking) Checkbox(checked = checked || r.collected, onCheckedChange = if (canMark) { _ -> onToggle() } else null, enabled = canMark)
        Mark(r, 36.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(r.item.displayName(ja), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val sub = listOfNotNull(
                r.item.code,
                distanceText(meters),
                if (r.collected) (r.lastAt?.let { formatDate(it, ja) } ?: stringResource(R.string.had_before_app)) else null,
            ).joinToString(" · ")
            if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"), color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (!marking && !r.collected) OutlinedButton(onClick = onGotIt, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)) { Text(stringResource(R.string.got_it)) }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LineScreen(vm: MainViewModel, lineId: String, nav: NavHostController, onBack: () -> Unit) {
    val ja = isJapanese()
    val rows by vm.rows.collectAsStateWithLifecycle()
    val lines by vm.lines.collectAsStateWithLifecycle()
    val ls by vm.lineStations.collectAsStateWithLifecycle()
    val line = lines.firstOrNull { it.id == lineId }
    val byId = rows.associateBy { it.item.id }
    val stations: List<ItemRow> = if (lineId.isEmpty()) {
        val onLine = ls.map { it.itemId }.toSet()
        rows.filter { it.item.type == ItemType.EKI_STAMP && it.item.id !in onLine }.sortedBy { it.item.nameJa }
    } else {
        ls.filter { it.lineId == lineId }.sortedBy { it.seq }.mapNotNull { byId[it.itemId] }
    }
    val title = line?.let { if (ja) it.nameJa else it.nameRomaji ?: it.nameJa } ?: stringResource(R.string.book_other_stations)

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Column {
                    Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        stringResource(R.string.type_eki_plural) + " · " + stringResource(R.string.count, stations.count { it.collected }, stations.size),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null) } },
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(stations, key = { it.item.id }) { r ->
                Column(Modifier.clickable { nav.openItem(r.item.id) }, horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(contentAlignment = Alignment.Center) { Mark(r, 72.dp) }
                    Spacer(Modifier.height(4.dp))
                    Text(r.item.displayName(ja).removeSuffix("駅").removeSuffix(" Station"), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (r.collected) {
                        Text(r.lastAt?.let { formatDate(it, ja) } ?: stringResource(R.string.had_before_app), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                }
            }
        }
    }
}
