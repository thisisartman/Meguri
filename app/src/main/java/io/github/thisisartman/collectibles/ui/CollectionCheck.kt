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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.thisisartman.collectibles.R
import io.github.thisisartman.collectibles.data.ItemRow
import io.github.thisisartman.collectibles.data.ItemType
import io.github.thisisartman.collectibles.data.Origin
import kotlinx.coroutines.launch

/** One page of the Collection check: a group of items (a prefecture's cards, a line's stamps) to tick. */
private data class CheckGroup(val key: String, val title: String, val rows: List<ItemRow>)

/** Noon local time on the day of [ms]. */
internal fun noonOf(ms: Long): Long = java.util.Calendar.getInstance().apply {
    timeInMillis = ms; set(java.util.Calendar.HOUR_OF_DAY, 12); set(java.util.Calendar.MINUTE, 0); set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
}.timeInMillis

/**
 * Collection check (#68, owner's name): add everything you already have, one group per page, swiping left and
 * right; manhole cards first (owner, 2026-11-07). Tick the tiles you have and save the page; no dates or places
 * are asked, and each one shows the day it was added. Undo takes the page back off.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionCheckScreen(
    vm: MainViewModel,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
    // "Already went?" on a trip (#46, owner item 9): only the route's things, saved as got on the trip's day
    only: Set<String>? = null,
    tripDay: Boolean = false,
) {
    // The plain Collection check is a deck now: category, prefecture, then swipe (owner, 2026-11-09). The trip's
    // "Already went" keeps the page of tiles below, since it is one route's things on one day.
    if (only == null && !tripDay) { CollectionCheckFlow(vm, onBack); return }
    val ja = isJapanese()
    val rows by vm.rows.collectAsStateWithLifecycle()
    val lines by vm.lines.collectAsStateWithLifecycle()
    val stations by vm.lineStations.collectAsStateWithLifecycle()
    val customTypes = LocalCustomTypes.current
    val manholeLabel = stringResource(R.string.type_manhole_plural)
    val ekiLabel = stringResource(R.string.type_eki_plural)
    val goshuinLabel = stringResource(R.string.type_goshuin)
    val damLabel = stringResource(R.string.type_dam_plural)
    val museumLabel = stringResource(R.string.type_museum_plural)
    val roadsideLabel = stringResource(R.string.type_roadside_plural)
    val otherStations = stringResource(R.string.book_other_stations)
    val customLabel = stringResource(R.string.type_custom)
    // Only catalogue items can be ticked; things you added yourself already have their collection
    val groups = remember(rows, lines, stations, ja, customTypes, only, damLabel, museumLabel) {
        val candidates = rows.filter { it.item.origin != Origin.USER || it.item.type == ItemType.CUSTOM }
            .filter { only == null || it.item.id in only }
        val out = mutableListOf<CheckGroup>()
        candidates.filter { it.item.type == ItemType.MANHOLE_CARD }.groupBy { it.item.prefecture.orEmpty() }.toSortedMap().forEach { (p, l) ->
            out += CheckGroup("m:$p", manholeLabel + (if (p.isNotEmpty()) " · " + prefectureName(p, ja) else ""), l.sortedBy { it.item.code ?: it.item.nameJa })
        }
        val eki = candidates.filter { it.item.type == ItemType.EKI_STAMP || it.item.type == ItemType.EKITAG }
        val placed = mutableSetOf<String>()
        lines.forEach { line ->
            val ids = stations.filter { it.lineId == line.id }.sortedBy { it.seq }.map { it.itemId }
            val l = ids.mapNotNull { id -> eki.firstOrNull { it.item.id == id } }.filter { it.item.id !in placed }
            if (l.isNotEmpty()) { placed += l.map { it.item.id }; out += CheckGroup("e:${line.id}", ekiLabel + " · " + (if (ja) line.nameJa else line.nameRomaji ?: line.nameJa), l) }
        }
        eki.filter { it.item.id !in placed }.takeIf { it.isNotEmpty() }?.let { out += CheckGroup("e:_", "$ekiLabel · $otherStations", it) }
        candidates.filter { it.item.type == ItemType.GOSHUIN }.takeIf { it.isNotEmpty() }?.let { out += CheckGroup("g", goshuinLabel, it) }
        candidates.filter { it.item.type == ItemType.DAM_CARD }.takeIf { it.isNotEmpty() }?.let { out += CheckGroup("dam", damLabel, it) }
        candidates.filter { it.item.type == ItemType.MUSEUM_STAMP }.takeIf { it.isNotEmpty() }?.let { out += CheckGroup("mus", museumLabel, it) }
        candidates.filter { it.item.type == ItemType.ROADSIDE_STATION }.takeIf { it.isNotEmpty() }?.let { out += CheckGroup("road", roadsideLabel, it) }
        candidates.filter { it.item.type == ItemType.CUSTOM }.groupBy { it.item.customTypeId.orEmpty() }.forEach { (id, l) ->
            out += CheckGroup("c:$id", customTypes[id]?.name ?: customLabel, l)
        }
        out.toList()
    }
    // Ticks per item id, kept while you swipe between pages
    var ticked by rememberSaveable { mutableStateOf(setOf<String>()) }
    val pager = rememberPagerState { groups.size }
    val scope = rememberCoroutineScope()
    val addedText = stringResource(R.string.check_added_count)
    val undoText = stringResource(R.string.undo)
    // The trip's day: today unless you pick another (noon, so a time zone change keeps the date)
    var dayMs by rememberSaveable { mutableStateOf(noonOf(System.currentTimeMillis())) }
    var pickDay by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            windowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
            title = { Text(stringResource(if (tripDay) R.string.trip_already_title else R.string.check_title)) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back)) } },
            actions = {
                if (tripDay) androidx.compose.material3.AssistChip(
                    onClick = { pickDay = true },
                    label = { Text(dateText(dayMs)) },
                    leadingIcon = { Icon(Icons.Filled.DateRange, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    modifier = Modifier.padding(end = 8.dp),
                )
            },
        )
        if (pickDay) {
            val state = androidx.compose.material3.rememberDatePickerState(
                initialSelectedDateMillis = Dates.pickerDay(dayMs),
                selectableDates = object : androidx.compose.material3.SelectableDates {
                    override fun isSelectableDate(utcTimeMillis: Long) = Dates.notFuture(utcTimeMillis, System.currentTimeMillis())
                },
            )
            androidx.compose.material3.DatePickerDialog(
                onDismissRequest = { pickDay = false },
                confirmButton = {
                    androidx.compose.material3.TextButton(onClick = {
                        state.selectedDateMillis?.let { dayMs = Dates.noonFromPicker(it) }
                        pickDay = false
                    }) { Text(stringResource(android.R.string.ok)) }
                },
                dismissButton = { androidx.compose.material3.TextButton(onClick = { pickDay = false }) { Text(stringResource(android.R.string.cancel)) } },
            ) { androidx.compose.material3.DatePicker(state) }
        }
        if (groups.isEmpty()) {
            Text(stringResource(R.string.check_nothing), Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@Column
        }
        Text(
            stringResource(if (tripDay) R.string.trip_already_hint else R.string.check_hint),
            Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth()) { page ->
            val g = groups[page]
            Column(Modifier.fillMaxSize()) {
                val have = g.rows.count { it.collected }
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(g.title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(stringResource(R.string.count, have + g.rows.count { !it.collected && it.item.id in ticked }, g.rows.size), style = MaterialTheme.typography.labelLarge)
                }
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(96.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    items(g.rows, key = { it.item.id }) { r ->
                        val on = r.item.id in ticked
                        Column(
                            Modifier.clickable(enabled = !r.collected, role = Role.Checkbox) {
                                ticked = if (on) ticked - r.item.id else ticked + r.item.id
                            }.padding(4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                if (r.collected || !on) Mark(r, 64.dp)
                                else Surface(Modifier.size(64.dp), shape = CircleShape, color = colorOf(r.item)) {
                                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Filled.Check, contentDescription = null, tint = LocalTypeColors.current.onSeal, modifier = Modifier.size(32.dp)) }
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(tileName(r.item.shortName(ja)), style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
        // Page dots: where you are among the groups
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.Center) {
            val shown = groups.size.coerceAtMost(12)
            repeat(shown) { i ->
                val current = i == pager.currentPage.coerceAtMost(shown - 1)
                Surface(Modifier.padding(3.dp).size(if (current) 9.dp else 7.dp), shape = CircleShape,
                    color = if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant) {}
            }
        }
        Button(
            enabled = ticked.isNotEmpty(),
            onClick = {
                val ids = ticked
                if (tripDay) vm.markOn(ids, dayMs) { made ->
                    ticked = emptySet()
                    scope.launch {
                        val r = snackbar.showSnackbar(addedText.format(made.size), actionLabel = undoText, duration = SnackbarDuration.Long)
                        if (r == SnackbarResult.ActionPerformed) vm.removeFinds(made)
                    }
                } else vm.markBeforeApp(ids) { added ->
                    ticked = emptySet()
                    scope.launch {
                        val r = snackbar.showSnackbar(addedText.format(added), actionLabel = undoText, duration = SnackbarDuration.Long)
                        if (r == SnackbarResult.ActionPerformed) ids.forEach { vm.unmarkBeforeApp(it) }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().padding(12.dp).heightIn(min = 48.dp),
        ) {
            Text(if (ticked.isEmpty()) stringResource(R.string.check_tick_some) else pluralStringResource(R.plurals.check_add, ticked.size, ticked.size))
        }
    }
}
