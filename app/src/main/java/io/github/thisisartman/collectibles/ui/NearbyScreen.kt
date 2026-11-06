package io.github.thisisartman.collectibles.ui

import kotlinx.coroutines.launch
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.border
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Slider
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlin.math.roundToInt
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import io.github.thisisartman.collectibles.R
import io.github.thisisartman.collectibles.data.ItemRow
import io.github.thisisartman.collectibles.data.ItemType
import io.github.thisisartman.collectibles.data.Origin

/** Nearby, layout B (owner's pick, 2026-11-06): the map fills the screen and a sheet of tiles sits over it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NearbyScreen(vm: MainViewModel, nav: NavHostController, onLog: () -> Unit = {}, locationBlocked: Boolean = false, onAllowLocation: () -> Unit) {
    val ja = isJapanese()
    val here by vm.here.collectAsStateWithLifecycle()
    val rows by vm.rows.collectAsStateWithLifecycle()
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var tab by rememberSaveable { mutableStateOf(MapSection.STAMPS) }
    var kind by rememberSaveable { mutableStateOf<ItemType?>(null) }
    var byName by rememberSaveable { mutableStateOf(false) }
    var tileStep by rememberSaveable { mutableIntStateOf(1) }
    var sortOpen by remember { mutableStateOf(false) }
    var sizeOpen by rememberSaveable { mutableStateOf(false) }
    val sizeLabel = stringResource(R.string.nearby_tile_size)
    var picked by rememberSaveable { mutableStateOf<String?>(null) }

    val inTab = remember(rows, tab) { rows.filter { it.item.type in tab.types } }
    val kinds = remember(inTab) { inTab.map { it.item.type }.distinct().sortedBy { it.ordinal } }
    val shown = remember(inTab, kind, byName, here, ja) {
        val k = inTab.filter { kind == null || it.item.type == kind }
        if (byName) k.sortedBy { it.item.displayName(ja).lowercase() }
        else k.sortedWith(compareBy<ItemRow> { vm.distanceTo(it) ?: Double.MAX_VALUE }.thenBy { it.item.displayName(ja) })
    }
    val pins = remember(shown) { shown.mapNotNull { it.toPin() } }

    val pickedRow = rows.firstOrNull { it.item.id == picked }

    val noCatalogue = rows.none { it.item.origin != Origin.USER }
    val sheet = rememberBottomSheetScaffoldState()
    // The resting sheet shows the header and one whole row of tiles (name lines and distance too),
    // measured, so it fits at any text size and tile size. 300 dp until the first measure.
    val density = LocalDensity.current
    var handlePx by remember { mutableIntStateOf(0) }
    var gridTopPx by remember { mutableIntStateOf(0) }
    var rowPx by remember { mutableIntStateOf(0) }
    val restingPeek = if (handlePx > 0 && gridTopPx > 0 && rowPx > 0) {
        with(density) { (handlePx + gridTopPx + rowPx).toDp() } + 12.dp
    } else 300.dp
    // Taller while the welcome shows, so both of its buttons are in view without dragging
    val peek = if (noCatalogue) 520.dp else restingPeek.coerceIn(220.dp, 480.dp)
    BottomSheetScaffold(
        scaffoldState = sheet,
        sheetPeekHeight = peek,
        sheetDragHandle = {
            Box(Modifier.onSizeChanged { handlePx = it.height }) { BottomSheetDefaults.DragHandle() }
        },
        sheetContainerColor = MaterialTheme.colorScheme.surface,
        sheetContent = {
            Column(Modifier.fillMaxWidth().heightIn(min = 300.dp)) {
                if (noCatalogue) {
                    // #58: a proper welcome, with the bundled Niigata catalogue one tap away
                    val more: MoreViewModel = androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel()
                    val scope = androidx.compose.runtime.rememberCoroutineScope()
                    WelcomeCard(
                        onLoadBundled = { scope.launch { runCatching { more.io.importBundled() } } },
                        onLoadFile = { nav.openMore() },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                    return@Column
                }
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    listOf(MapSection.STAMPS, MapSection.CARDS).forEachIndexed { i, t ->
                        val all = rows.filter { it.item.type in t.types }
                        SegmentedButton(
                            selected = tab == t,
                            onClick = { tab = t; kind = null },
                            shape = SegmentedButtonDefaults.itemShape(i, 2),
                        ) {
                            // Name and count on two lines, so neither is cut at large text
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(stringResource(if (t == MapSection.STAMPS) R.string.nearby_tab_stamps else R.string.nearby_tab_cards), maxLines = 1)
                                Text(
                                    stringResource(R.string.count, all.count { it.findCount > 0 }, all.size),
                                    style = MaterialTheme.typography.labelSmall, maxLines = 1,
                                )
                            }
                        }
                    }
                }
                // Chips scroll on the left; sort and size are icons pinned on the right (owner: icons over words)
                Row(Modifier.fillMaxWidth().padding(end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Row(
                        Modifier.weight(1f).horizontalScroll(rememberScrollState()).padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        FilterChip(selected = kind == null, onClick = { kind = null }, label = { Text(stringResource(R.string.nearby_kind_all)) })
                        if (kinds.size > 1) kinds.forEach { t ->
                            FilterChip(selected = kind == t, onClick = { kind = t }, label = { Text(stringResource(t.pluralLabel())) })
                        }
                    }
                    Box {
                        IconButton(onClick = { sortOpen = true }) {
                            Icon(painterResource(R.drawable.ic_sort), contentDescription = stringResource(R.string.nearby_sort))
                        }
                        DropdownMenu(expanded = sortOpen, onDismissRequest = { sortOpen = false }) {
                            listOf(false to R.string.nearby_sort_near, true to R.string.nearby_sort_az).forEach { (az, label) ->
                                DropdownMenuItem(
                                    text = { Text(stringResource(label)) },
                                    onClick = { byName = az; sortOpen = false },
                                    leadingIcon = {
                                        if (byName == az) Icon(Icons.Filled.Check, contentDescription = null)
                                        else Spacer(Modifier.size(24.dp))
                                    },
                                )
                            }
                        }
                    }
                    IconToggleButton(checked = sizeOpen, onCheckedChange = { sizeOpen = it }) {
                        Icon(
                            painterResource(R.drawable.ic_grid_view),
                            contentDescription = stringResource(R.string.nearby_tile_size),
                            tint = if (sizeOpen) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                        )
                    }
                }
                // The size icon expands into a slider: small circle on the left, large on the right
                AnimatedVisibility(visible = sizeOpen) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).border(1.5.dp, MaterialTheme.colorScheme.onSurfaceVariant, CircleShape).clearAndSetSemantics { })
                        Slider(
                            value = tileStep.toFloat(),
                            onValueChange = { tileStep = it.roundToInt().coerceIn(0, TILE_SIZES.lastIndex) },
                            valueRange = 0f..TILE_SIZES.lastIndex.toFloat(),
                            steps = TILE_SIZES.size - 2,
                            modifier = Modifier.weight(1f).padding(horizontal = 12.dp)
                                .semantics { contentDescription = sizeLabel },
                        )
                        Box(Modifier.size(20.dp).border(1.5.dp, MaterialTheme.colorScheme.onSurfaceVariant, CircleShape).clearAndSetSemantics { })
                    }
                }
                if (shown.isEmpty()) {
                    Text(
                        stringResource(R.string.nearby_empty_kind),
                        Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    val tile = TILE_SIZES[tileStep]
                    // Wider cells with large text, so names wrap between words instead of inside them
                    val cell = (tile + 24.dp) * LocalDensity.current.fontScale.coerceIn(1f, 2f)
                    // A new sort, tab or filter starts from the top (keys would otherwise hold the old place)
                    val grid = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
                    LaunchedEffect(byName, tab, kind) { grid.scrollToItem(0) }
                    LazyVerticalGrid(
                        state = grid,
                        columns = GridCells.Adaptive(cell),
                        // A gap between columns, so two one-line names never read as one
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 96.dp),
                        modifier = Modifier.fillMaxWidth().heightIn(max = 2000.dp)
                            .onGloballyPositioned { gridTopPx = it.positionInParent().y.toInt() },
                    ) {
                        itemsIndexed(shown, key = { _, r -> r.item.id }) { i, r ->
                            // The first row's tallest tile sets the resting height; top padding 4 dp
                            val m = if (i == 0) Modifier.onSizeChanged { rowPx = it.height + with(density) { 4.dp.roundToPx() } } else Modifier
                            Box(m) {
                                RoundTile(r, tile, r.item.displayName(ja), distanceText(vm.distanceTo(r)), onOpen = { nav.openItem(r.item.id) })
                            }
                        }
                    }
                }
            }
        },
    ) { inner ->
        Box(Modifier.fillMaxSize().padding(bottom = inner.calculateBottomPadding() - peek.coerceAtMost(inner.calculateBottomPadding()))) {
            MeguriMap(
                pins, here.fix?.let { it.lat to it.lng }, Modifier.fillMaxSize(),
                onPinTap = { picked = it }, onMapTap = { picked = null },
            )
            // Where you are, and what to do when there is no position
            Surface(
                Modifier.statusBarsPadding().padding(12.dp).fillMaxWidth(),
                shape = RoundedCornerShape(24.dp), shadowElevation = 3.dp,
            ) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.nearby_title), style = MaterialTheme.typography.titleMedium)
                        val fix = here.fix
                        val allowed = LocationAccess.has(ctx)
                        val where = when {
                            fix == null && here.searching -> stringResource(R.string.nearby_finding)
                            fix == null && !allowed -> stringResource(R.string.nearby_location_not_allowed)
                            fix == null && !LocationAccess.systemOn(ctx) -> stringResource(R.string.nearby_location_off)
                            fix == null -> stringResource(R.string.nearby_no_position)
                            here.live -> stringResource(R.string.nearby_from_now)
                            else -> stringResource(R.string.nearby_from_fix, formatTime(fix.timeMs))
                        }
                        Text(where, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (here.fix == null && !here.searching) {
                        val label = when {
                            !LocationAccess.has(ctx) && locationBlocked -> R.string.nearby_allow_in_settings
                            !LocationAccess.has(ctx) -> R.string.nearby_allow_location
                            !LocationAccess.systemOn(ctx) -> R.string.nearby_turn_on_location
                            else -> R.string.nearby_try_again
                        }
                        TextButton(onClick = onAllowLocation) { Text(stringResource(label)) }
                    } else {
                        IconButton(onClick = { nav.openMap(tab) }) {
                            Icon(Icons.Filled.Place, contentDescription = stringResource(R.string.map_expand))
                        }
                    }
                    // Log a find is the middle of the bottom bar now; the top right is the menu (owner, 2026-11-06)
                    MenuButton(nav)
                }
            }
            // A tapped pin: what it is, with Details and Directions (#52)
            pickedRow?.let { r ->
                Surface(
                    Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 84.dp, start = 12.dp, end = 12.dp).fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp), shadowElevation = 6.dp,
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text(r.item.displayName(ja), style = MaterialTheme.typography.titleMedium)
                        Text(
                            listOfNotNull(
                                stringResource(if (r.findCount > 0) R.string.map_collected else R.string.map_not_collected),
                                distanceText(vm.distanceTo(r)),
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(10.dp))
                        PinActions(r, vm, nav, onCollected = { picked = null })
                    }
                }
            }
        }
    }
}

private val TILE_SIZES = listOf(48.dp, 64.dp, 84.dp)

/** One item in the Nearby grid: your mark (photo, seal or an empty dashed ring), name and distance. */
@Composable
private fun RoundTile(row: ItemRow, size: Dp, name: String, distance: String?, onOpen: () -> Unit) {
    Column(
        Modifier.clickable(onClick = onOpen).padding(vertical = 6.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Mark(row, size)
        Spacer(Modifier.height(4.dp))
        Text(
            // Always two lines tall, so every tile in a row is the same height and distances line up
            name, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center,
            minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
        distance?.let {
            Text(it, style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"), color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

private fun lineName(lines: List<io.github.thisisartman.collectibles.data.Line>, lineId: String?, ja: Boolean): String? =
    lines.firstOrNull { it.id == lineId }?.let { if (ja) it.nameJa else it.nameRomaji ?: it.nameJa }

@Composable
fun SectionTitle(text: String) {
    Text(
        text,
        Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 6.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
fun TypeDot(type: ItemType) {
    Box(Modifier.size(8.dp).clip(CircleShape).background(LocalTypeColors.current.of(type)))
}

@Composable
fun TypeChip(type: ItemType, extra: String? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        TypeDot(type)
        Spacer(Modifier.width(4.dp))
        Text(
            stringResource(type.label()) + (extra?.let { sep() + it } ?: ""),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun distanceText(meters: Double?): String? = meters?.let { val (res, v) = formatDistance(it); stringResource(res, v) }

@Composable
private fun NearTile(row: ItemRow, name: String, meters: Double?, onOpen: () -> Unit, onGotIt: () -> Unit, onPhoto: () -> Unit) {
    // Wider with large text, so the name and distance still fit
    val width = 150.dp * LocalDensity.current.fontScale.coerceIn(1f, 1.6f)
    Card(
        Modifier.width(width).clickable(onClick = onOpen),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.padding(10.dp)) {
            Text(name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis)
            TypeChip(row.item.type)
            // Distance on its own line: it is what the tile is for, and it must not be the part that gets cut
            Text(distanceText(meters) ?: "", style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"), maxLines = 1)
            Spacer(Modifier.height(8.dp))
            Button(onClick = onGotIt, modifier = Modifier.fillMaxWidth().spoken(stringResource(R.string.a11y_got_it, name)), contentPadding = PaddingValues(vertical = 4.dp)) { Text(stringResource(R.string.got_it)) }
            InkOutlinedButton(onClick = onPhoto, modifier = Modifier.fillMaxWidth().spoken(stringResource(R.string.a11y_photo, name)), contentPadding = PaddingValues(vertical = 2.dp)) { Text(stringResource(R.string.photo)) }
        }
    }
}

@Composable
fun summaryTitle(s: CollectionSummary, lineName: String?): String = when {
    s.type == ItemType.MANHOLE_CARD -> stringResource(R.string.type_manhole_plural) + sep() + s.title.ifEmpty { stringResource(R.string.book_no_prefecture) }.let { prefectureName(it, isJapanese()) }
    s.type == ItemType.EKI_STAMP && s.lineId != null -> stringResource(R.string.type_eki_plural) + sep() + (lineName ?: s.lineId)
    s.type == ItemType.EKI_STAMP -> stringResource(R.string.type_eki_plural) + sep() + stringResource(R.string.book_other_stations)
    else -> stringResource(s.type.label())
}

@Composable
fun SummaryCard(s: CollectionSummary, title: String, next: Ranked? = null, onClick: () -> Unit) {
    val ja = isJapanese()
    val color = LocalTypeColors.current.of(s.type)
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TypeDot(s.type)
                Spacer(Modifier.width(6.dp))
                Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                CountText(
                    if (s.total != null) stringResource(R.string.count, s.collected, s.total) else s.collected.toString(),
                    complete = s.total != null && s.total > 0 && s.collected == s.total,
                    style = MaterialTheme.typography.titleSmall,
                )
            }
            // Around here: what to get next in this collection, and how far
            next?.let { n ->
                Text(
                    stringResource(R.string.nearby_next, n.row.item.displayName(ja), distanceText(n.meters).orEmpty()),
                    style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    // Two lines, so the distance at the end survives long design names and large text
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 14.dp, top = 2.dp),
                )
            }
            if (s.total != null && s.total > 0) {
                LinearProgressIndicator(
                    progress = { s.collected.toFloat() / s.total },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).height(6.dp),
                    color = color,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    strokeCap = StrokeCap.Round,
                    gapSize = 2.dp,
                    drawStopIndicator = {},
                )
            } else {
                Spacer(Modifier.height(6.dp))
            }
            s.last?.takeIf { next == null }?.let { last ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Mark(last, 26.dp)
                    Spacer(Modifier.width(8.dp))
                    val whenText = if (last.lastAt != null) formatDate(last.lastAt, ja) else stringResource(R.string.had_before_app)
                    Text(
                        stringResource(R.string.last, last.item.displayName(ja) + sep() + whenText),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
