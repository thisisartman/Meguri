package io.github.thisisartman.collectibles.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
fun BookScreen(vm: MainViewModel, nav: NavHostController, snackbar: SnackbarHostState, onMarkingChange: (Boolean) -> Unit = {}) {
    val ja = isJapanese()
    val rows by vm.rows.collectAsStateWithLifecycle()
    val summaries by vm.summaries.collectAsStateWithLifecycle()
    val lines by vm.lines.collectAsStateWithLifecycle()
    var marking by rememberSaveable { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(marking) { onMarkingChange(marking) }
    DisposableEffect(Unit) { onDispose { onMarkingChange(false) } }

    Column(Modifier.fillMaxSize()) {
        // The title never wraps; the action takes what is left and wraps instead (large text)
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(stringResource(R.string.book_title), style = MaterialTheme.typography.headlineMedium, maxLines = 1, softWrap = false)
            Spacer(Modifier.width(8.dp))
            val action = if (marking) R.string.cancel else R.string.book_mark_owned
            // Only catalogue items still to get can be marked; things added by hand already have their find
            // 48 dp tall like every other control (it measured 32 dp in the 2026-11-06 Poco review)
            if (marking || rows.any { !it.collected && it.item.origin != io.github.thisisartman.collectibles.data.Origin.USER }) TextButton(onClick = { if (marking) { marking = false; selected = emptySet() } else nav.navigate("check") }, modifier = Modifier.weight(1f, fill = false).heightIn(min = 48.dp)) {
                Text(stringResource(action), textAlign = TextAlign.End)
            }
            // The same top-right menu as Nearby: Settings and Logs
            if (!marking) MenuButton(nav)
        }
        // Sections fold on a tap, so a long book stays easy to get around (overnight review round 8)
        var folded by remember { mutableStateOf(setOf<String>()) }
        fun isOpen(key: String) = marking || key !in folded
        fun toggle(key: String) { folded = if (key in folded) folded - key else folded + key }
        val customTypes = LocalCustomTypes.current
        val customLabel = stringResource(R.string.type_custom)
        // No floating button any more (Log is in the bottom bar), so the list only needs a little room at the end
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 16.dp)) {
            // What used to be the profile page (owner, 2026-11-08: one page, called My book)
            if (!marking) {
                item(key = "summary") { BookSummary(vm, nav) }
                // B1 (owner, 2026-11-08): one tile per kind; each kind's collections and finds are a tap away
                item(key = "kinds") {
                    val lineStations by vm.lineStations.collectAsStateWithLifecycle()
                    val kinds = remember(rows, lineStations, customTypes) {
                        val m = lineStations.groupBy({ it.itemId }, { it.lineId })
                        BookKinds.kinds(rows, { m[it].orEmpty().toSet() }, { customTypes[it]?.createdAt ?: Long.MAX_VALUE })
                    }
                    if (kinds.isNotEmpty()) {
                        SectionTitle(stringResource(R.string.book_your_collections))
                        KindTiles(kinds, nav)
                    }
                    // Timeline (M6): every find by day
                    androidx.compose.material3.ListItem(
                        headlineContent = { Text(stringResource(R.string.timeline_title)) },
                        supportingContent = { Text(stringResource(R.string.timeline_sub)) },
                        leadingContent = { Icon(androidx.compose.ui.res.painterResource(R.drawable.ic_history), contentDescription = null) },
                        modifier = Modifier.padding(top = 8.dp).clickable { nav.navigate("timeline") },
                    )
                }
            }
            // The long list is only for marking what you already have
            if (marking) {
            // Manhole cards by prefecture
            val manhole = rows.filter { it.item.type == ItemType.MANHOLE_CARD }.groupBy { it.item.prefecture.orEmpty() }.toSortedMap()
            manhole.forEach { (pref, list) ->
                val key = "h:m:$pref"
                val got = list.count { it.collected }
                stickyHeader(key = key) {
                    GroupHeader(
                        ItemType.MANHOLE_CARD, stringResource(R.string.type_manhole_plural) + sep() + pref.ifEmpty { stringResource(R.string.book_no_prefecture) }.let { prefectureName(it, ja) },
                        "$got / ${list.size}", complete = got == list.size, open = isOpen(key), onToggle = if (marking) null else ({ toggle(key) }),
                    )
                }
                if (isOpen(key)) items(list, key = { it.item.id }) { r ->
                    ItemListRow(r, ja, vm.distanceTo(r), marking, r.item.id in selected,
                        onToggle = { selected = if (r.item.id in selected) selected - r.item.id else selected + r.item.id },
                        onOpen = { nav.openItem(r.item.id) })
                }
            }
            // Eki stamps: one row per line (opens the stamp book), then other stations
            val ekiSummaries = summaries.filter { it.type == ItemType.EKI_STAMP }
            if (ekiSummaries.isNotEmpty()) {
                stickyHeader(key = "h:e") { GroupHeader(ItemType.EKI_STAMP, stringResource(R.string.type_eki_plural), null) }
                items(ekiSummaries, key = { "e:" + it.key }) { s ->
                    val name = lines.firstOrNull { it.id == s.lineId }?.let { if (ja) it.nameJa else it.nameRomaji ?: it.nameJa }
                    // The group header already says "Eki stamps"; the card names only the line
                    SummaryCard(s, name ?: s.lineId ?: stringResource(R.string.book_other_stations), onClick = { nav.openLine(s.lineId.orEmpty()) })
                }
            }
            // Goshuin, dam cards, museum stamps (#55): one section each, open-ended (no fixed total)
            listOf(Triple(ItemType.GOSHUIN, R.string.type_goshuin, "h:g"), Triple(ItemType.DAM_CARD, R.string.type_dam_plural, "h:dam"),
                Triple(ItemType.MUSEUM_STAMP, R.string.type_museum_plural, "h:mus")).forEach { (kind, label, hk) ->
            val goshuin = rows.filter { it.item.type == kind }
            if (goshuin.isNotEmpty()) {
                stickyHeader(key = hk) {
                    // Dam cards are a known list (MLIT), so they count against it; goshuin and museum stamps are open-ended
                    val got = goshuin.count { it.collected }
                    GroupHeader(kind, stringResource(label), if (kind == ItemType.DAM_CARD) "$got / ${goshuin.size}" else got.toString(),
                        open = isOpen(hk), onToggle = if (marking) null else ({ toggle(hk) }))
                }
                if (isOpen(hk)) items(goshuin.sortedWith(compareByDescending<io.github.thisisartman.collectibles.data.ItemRow> { it.lastAt ?: 0L }.thenBy { it.item.displayName(ja) }), key = { it.item.id }) { r ->
                    ItemListRow(r, ja, vm.distanceTo(r), marking, r.item.id in selected,
                        onToggle = { selected = if (r.item.id in selected) selected - r.item.id else selected + r.item.id },
                        onOpen = { nav.openItem(r.item.id) })
                }
            }
            }
            // Your own collections (#64), one section each, newest collections last
            rows.filter { it.item.type == ItemType.CUSTOM }.groupBy { it.item.customTypeId.orEmpty() }
                .toSortedMap(compareBy { customTypes[it]?.createdAt ?: Long.MAX_VALUE })
                .forEach { (typeId, list) ->
                    val key = "h:c:$typeId"
                    val got = list.count { it.collected }
                    stickyHeader(key = key) {
                        GroupHeader(ItemType.CUSTOM, customTypes[typeId]?.name ?: customLabel, "$got / ${list.size}",
                            complete = got == list.size, open = isOpen(key), onToggle = if (marking) null else ({ toggle(key) }))
                    }
                    if (isOpen(key)) items(list.sortedByDescending { it.lastAt ?: 0L }, key = { it.item.id }) { r ->
                        ItemListRow(r, ja, vm.distanceTo(r), marking, r.item.id in selected,
                            onToggle = { selected = if (r.item.id in selected) selected - r.item.id else selected + r.item.id },
                            onOpen = { nav.openItem(r.item.id) })
                    }
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
                // The + button is hidden while marking, so this can take the full width
                modifier = Modifier.fillMaxWidth().padding(12.dp),
            ) {
                Text(if (selected.isEmpty()) stringResource(R.string.book_mark_choose) else stringResource(R.string.book_mark_done, selected.size))
            }
        }
    }
}

@Composable
fun GroupHeader(
    type: ItemType, title: String, count: String?,
    complete: Boolean = false, open: Boolean = true, onToggle: (() -> Unit)? = null,
) {
    // Pinned while its rows scroll, so it needs the page colour behind it
    val tap = if (onToggle != null) Modifier.clickable(onClick = onToggle).heightIn(min = 48.dp) else Modifier
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).then(tap).padding(start = 16.dp, end = 8.dp, top = 16.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TypeDot(type)
        Spacer(Modifier.width(6.dp))
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        if (count != null) CountText(count, complete, MaterialTheme.typography.titleSmall)
        if (onToggle != null) {
            Icon(
                if (open) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = stringResource(if (open) R.string.book_fold else R.string.book_unfold),
                modifier = Modifier.padding(start = 4.dp),
            )
        } else {
            Spacer(Modifier.width(8.dp))
        }
    }
}

/** A count such as "2 / 2", with a check in front when the collection is complete. */
@Composable
fun CountText(text: String, complete: Boolean, style: androidx.compose.ui.text.TextStyle, color: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Unspecified) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (complete) {
            Icon(Icons.Filled.Check, contentDescription = stringResource(R.string.complete), modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
        }
        Text(text, style = style.copy(fontFeatureSettings = "tnum"), color = color)
    }
}

@Composable
fun ItemListRow(
    r: ItemRow, ja: Boolean, meters: Double?, marking: Boolean, checked: Boolean,
    onToggle: () -> Unit, onOpen: () -> Unit,
) {
    val canMark = marking && !r.collected
    val rowAction = if (canMark) {
        Modifier.toggleable(value = checked, role = Role.Checkbox, onValueChange = { onToggle() })
    } else {
        Modifier.clickable(onClick = onOpen)
    }
    val state = stringResource(if (r.collected) R.string.state_collected else R.string.state_to_get)
    Row(
        Modifier.fillMaxWidth().then(rowAction).collectedState(state).heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The row handles the tap; the box only shows the state
        if (marking) Checkbox(checked = checked || r.collected, onCheckedChange = null, enabled = canMark, modifier = Modifier.padding(end = 12.dp))
        Mark(r, 36.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(r.item.displayName(ja), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val sub = listOfNotNull(
                r.item.code,
                distanceText(meters),
                if (r.collected) (r.lastAt?.let { formatDate(it, ja) } ?: stringResource(R.string.no_date)) else null,
            ).joinToString(sep())
            if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"), color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        // Round 5, B2: the book is for looking; logging happens on Nearby, the item page and +
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
    // Round 5, E2: how far each station still to get is, and which one is nearest
    val dist = stations.associate { it.item.id to vm.distanceTo(it) }
    val nearest = stations.filter { !it.collected && dist[it.item.id] != null }.minByOrNull { dist[it.item.id]!! }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            windowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
            title = {
                Column {
                    Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        stringResource(R.string.type_eki_plural) + sep() + stringResource(R.string.count, stations.count { it.collected }, stations.size),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    nearest?.let { n ->
                        Text(
                            stringResource(R.string.line_nearest, n.item.displayName(ja).removeSuffix("駅").removeSuffix(" Station"), distanceText(dist[n.item.id]).orEmpty()),
                            style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"), color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back)) } },
        )
        LazyVerticalGrid(
            // Two columns with large text, so station names wrap between words, not inside them
            columns = GridCells.Fixed(if (androidx.compose.ui.platform.LocalDensity.current.fontScale > 1.3f) 2 else 3),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(stations, key = { it.item.id }) { r ->
                val state = stringResource(if (r.collected) R.string.state_collected else R.string.state_to_get)
                Column(Modifier.clickable { nav.openItem(r.item.id) }.collectedState(state), horizontalAlignment = Alignment.CenterHorizontally) {
                    val isNearest = r.item.id == nearest?.item?.id
                    Box(contentAlignment = Alignment.Center) { if (isNearest) EmptySlot(72.dp, strong = true) else Mark(r, 72.dp) }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        r.item.displayName(ja).removeSuffix("駅").removeSuffix(" Station"),
                        style = MaterialTheme.typography.bodySmall, fontWeight = if (isNearest) FontWeight.Bold else null,
                        textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                    if (r.collected) {
                        Text(r.lastAt?.let { formatDate(it, ja) } ?: stringResource(R.string.no_date), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    } else {
                        distanceText(dist[r.item.id])?.let {
                            Text(it, style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"), color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}
