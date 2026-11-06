package io.github.thisisartman.collectibles.ui

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
    var picked by rememberSaveable { mutableStateOf<String?>(null) }

    val inTab = remember(rows, tab) { rows.filter { it.item.type in tab.types } }
    val kinds = remember(inTab) { inTab.map { it.item.type }.distinct().sortedBy { it.ordinal } }
    val shown = remember(inTab, kind, byName, here, ja) {
        val k = inTab.filter { kind == null || it.item.type == kind }
        if (byName) k.sortedBy { it.item.displayName(ja).lowercase() }
        else k.sortedWith(compareBy<ItemRow> { vm.distanceTo(it) ?: Double.MAX_VALUE }.thenBy { it.item.displayName(ja) })
    }
    val pins = remember(shown) { shown.mapNotNull { it.toPin() } }
    val noCatalogue = rows.none { it.item.origin != Origin.USER }
    val pickedRow = rows.firstOrNull { it.item.id == picked }

    val sheet = rememberBottomSheetScaffoldState()
    BottomSheetScaffold(
        scaffoldState = sheet,
        sheetPeekHeight = 300.dp,
        sheetContainerColor = MaterialTheme.colorScheme.surface,
        sheetContent = {
            Column(Modifier.fillMaxWidth().heightIn(min = 300.dp)) {
                if (noCatalogue) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text(
                            stringResource(R.string.nearby_no_catalogue),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        InkOutlinedButton(onClick = { nav.openMore() }) { Text(stringResource(R.string.nearby_load_catalogue)) }
                    }
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
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FilterChip(selected = kind == null, onClick = { kind = null }, label = { Text(stringResource(R.string.nearby_kind_all)) })
                    if (kinds.size > 1) kinds.forEach { t ->
                        FilterChip(selected = kind == t, onClick = { kind = t }, label = { Text(stringResource(t.pluralLabel())) })
                    }
                }
                // Sort and size on their own row, so the chips never push them off the screen
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { byName = !byName }) {
                        Text(stringResource(if (byName) R.string.nearby_sort_az else R.string.nearby_sort_near), maxLines = 1)
                    }
                    TextButton(onClick = { tileStep = (tileStep + 1) % TILE_SIZES.size }) {
                        Text(stringResource(R.string.nearby_tile_size, tileStep + 1), maxLines = 1)
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
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(cell),
                        contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 96.dp),
                        modifier = Modifier.fillMaxWidth().heightIn(max = 2000.dp),
                    ) {
                        items(shown, key = { it.item.id }) { r ->
                            RoundTile(r, tile, r.item.displayName(ja), distanceText(vm.distanceTo(r)), onOpen = { nav.openItem(r.item.id) })
                        }
                    }
                }
            }
        },
    ) { inner ->
        Box(Modifier.fillMaxSize().padding(bottom = inner.calculateBottomPadding() - 300.dp.coerceAtMost(inner.calculateBottomPadding()))) {
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
                    FilledTonalIconButton(onClick = onLog) {
                        Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.a11y_log_find))
                    }
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
                        Row {
                            Button(onClick = { nav.openItem(r.item.id) }) { Text(stringResource(R.string.map_details)) }
                            Spacer(Modifier.width(8.dp))
                            InkOutlinedButton(onClick = {
                                val i = r.item
                                val uri = android.net.Uri.parse("geo:${i.lat},${i.lng}?q=${i.lat},${i.lng}(${android.net.Uri.encode(i.placeName ?: i.nameJa)})")
                                runCatching { ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, uri)) }
                            }) { Text(stringResource(R.string.item_directions_plain)) }
                        }
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
            name, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center,
            maxLines = 2, overflow = TextOverflow.Ellipsis,
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
