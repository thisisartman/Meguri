package io.github.thisisartman.collectibles.ui

import android.net.Uri
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import io.github.thisisartman.collectibles.R
import io.github.thisisartman.collectibles.data.ItemRow
import io.github.thisisartman.collectibles.data.ItemType

/**
 * My book as kinds (owner's pick B1, 2026-11-08: "a lot more categorized and siloed", no lists that keep going): My
 * book shows one tile per kind; a tile opens that kind's page with its collections as rings and its recent finds; a
 * ring opens the full book (a line's stamp book, or the list for one prefecture).
 */
object BookKinds {
    /** One tile on My book. [key] is the type's name, or "custom:<id>" for one of your own collections. */
    data class Kind(val key: String, val type: ItemType, val customTypeId: String?, val collected: Int, val total: Int?, val groups: Int)

    /** One collection inside a kind: a line's stamps, a prefecture's cards, or all of an open-ended kind. */
    data class Group(val key: String, val lineId: String?, val prefecture: String?, val rows: List<ItemRow>, val collected: Int, val total: Int?)

    private val ORDER = listOf(ItemType.MANHOLE_CARD, ItemType.EKI_STAMP, ItemType.EKITAG, ItemType.GOSHUIN, ItemType.DAM_CARD, ItemType.ROADSIDE_STATION, ItemType.MUSEUM_STAMP)

    /** Goshuin and museum stamps have no fixed list, so they count up with no total. */
    fun openEnded(type: ItemType) = type == ItemType.GOSHUIN || type == ItemType.MUSEUM_STAMP

    fun rowsOf(key: String, rows: List<ItemRow>): List<ItemRow> =
        if (key.startsWith("custom:")) rows.filter { it.item.type == ItemType.CUSTOM && it.item.customTypeId.orEmpty() == key.removePrefix("custom:") }
        else rows.filter { it.item.type.name == key }

    /** The kinds you have anything of, in a fixed order, then your own collections oldest first. */
    fun kinds(rows: List<ItemRow>, lineOf: (String) -> Set<String>, customOrder: (String) -> Long = { 0L }): List<Kind> {
        val builtIn = ORDER.mapNotNull { t ->
            val of = rows.filter { it.item.type == t && !it.noneHere }
            if (of.isEmpty()) null else Kind(t.name, t, null, of.count { it.collected }, if (openEnded(t)) null else of.size, groups(t.name, of, lineOf).size)
        }
        val custom = rows.filter { it.item.type == ItemType.CUSTOM }.groupBy { it.item.customTypeId.orEmpty() }
            .toSortedMap(compareBy(customOrder))
            .map { (id, of) -> Kind("custom:$id", ItemType.CUSTOM, id, of.count { it.collected }, of.size, 1) }
        return builtIn + custom
    }

    /**
     * A kind's collections: station stamps by line (a station on two lines counts in both; stations on no line go
     * last), everything else by prefecture; your own collections are one group.
     */
    fun groups(key: String, rows: List<ItemRow>, lineOf: (String) -> Set<String>): List<Group> {
        val of = rowsOf(key, rows).filter { !it.noneHere }
        if (key.startsWith("custom:")) return listOf(Group(key, null, null, of, of.count { it.collected }, of.size))
        val type = ItemType.valueOf(key)
        if (type == ItemType.EKI_STAMP) {
            val byLine = linkedMapOf<String, MutableList<ItemRow>>()
            for (r in of) lineOf(r.item.id).ifEmpty { setOf("") }.forEach { byLine.getOrPut(it) { mutableListOf() } += r }
            return byLine.entries.sortedBy { it.key.isEmpty() }.map { (line, list) ->
                Group("l:$line", line.ifEmpty { null }, null, list, list.count { it.collected }, list.size)
            }
        }
        return of.groupBy { it.item.prefecture.orEmpty() }.toSortedMap(compareBy<String> { it.isEmpty() }.thenBy { it })
            .map { (pref, list) -> Group("p:$pref", null, pref.ifEmpty { null }, list, list.count { it.collected }, if (openEnded(type)) null else list.size) }
    }
}

fun NavHostController.openKind(key: String) = navigate("kind/${Uri.encode(key)}")
fun NavHostController.openGroup(kind: String, group: String) = navigate("group/${Uri.encode(kind)}/${Uri.encode(group)}")

@Composable
fun kindTitle(kind: BookKinds.Kind): String = when (kind.type) {
    ItemType.CUSTOM -> LocalCustomTypes.current[kind.customTypeId.orEmpty()]?.name ?: stringResource(R.string.type_custom)
    else -> stringResource(kindPlural(kind.type))
}

private fun kindPlural(type: ItemType): Int = when (type) {
    ItemType.MANHOLE_CARD -> R.string.type_manhole_plural
    ItemType.EKI_STAMP -> R.string.type_eki_plural
    ItemType.DAM_CARD -> R.string.type_dam_plural
    ItemType.MUSEUM_STAMP -> R.string.type_museum_plural
    ItemType.ROADSIDE_STATION -> R.string.type_roadside_plural
    else -> type.label()
}

/** How far you are: an arc in the kind's colour over a faint track, the count inside. Open-ended kinds show a full ring. */
@Composable
fun ProgressRing(collected: Int, total: Int?, color: Color, size: Dp, modifier: Modifier = Modifier) {
    val track = LocalTypeColors.current.dash.copy(alpha = 0.35f)
    val sweep = if (total == null) (if (collected > 0) 360f else 0f) else if (total == 0) 0f else 360f * collected / total
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = 6.dp.toPx()
            val arc = Size(this.size.width - w, this.size.height - w)
            val at = Offset(w / 2, w / 2)
            drawArc(track, 0f, 360f, false, at, arc, style = Stroke(w))
            if (sweep > 0f) drawArc(color, -90f, sweep, false, at, arc, style = Stroke(w, cap = StrokeCap.Round))
        }
        Text(
            if (total == null) "$collected" else "$collected/$total",
            style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, maxLines = 1,
        )
    }
}

/** My book's tiles, three to a row; each opens its kind. */
@Composable
fun KindTiles(kinds: List<BookKinds.Kind>, nav: NavHostController) {
    val ja = isJapanese()
    Column(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        kinds.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { k ->
                    Surface(
                        Modifier.weight(1f).clickable { nav.openKind(k.key) },
                        shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, tonalElevation = 1.dp,
                    ) {
                        Column(Modifier.padding(vertical = 12.dp, horizontal = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            ProgressRing(k.collected, k.total, kindColor(k), 56.dp)
                            Spacer(Modifier.height(6.dp))
                            Text(kindTitle(k), style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(kindSub(k), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun kindColor(k: BookKinds.Kind): Color =
    if (k.type == ItemType.CUSTOM) LocalCustomTypes.current[k.customTypeId.orEmpty()]?.let { Color(it.color) } ?: LocalTypeColors.current.of(k.type)
    else LocalTypeColors.current.of(k.type)

@Composable
private fun kindSub(k: BookKinds.Kind): String = when {
    k.type == ItemType.CUSTOM -> stringResource(R.string.kind_your_own)
    k.type == ItemType.EKI_STAMP -> pluralStringResource(R.plurals.kind_lines, k.groups, k.groups)
    else -> pluralStringResource(R.plurals.kind_prefectures, k.groups, k.groups)
}

/** One kind: its map, its collections as rings, its recent finds. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KindScreen(vm: MainViewModel, key: String, nav: NavHostController, onBack: () -> Unit) {
    val ja = isJapanese()
    val rows by vm.rows.collectAsStateWithLifecycle()
    val lines by vm.lines.collectAsStateWithLifecycle()
    val lineStations by vm.lineStations.collectAsStateWithLifecycle()
    val customTypes = LocalCustomTypes.current
    val lineOf = remember(lineStations) { val m = lineStations.groupBy({ it.itemId }, { it.lineId }); { id: String -> m[id].orEmpty().toSet() } }
    val kind = remember(rows, lineStations) { BookKinds.kinds(rows, lineOf).firstOrNull { it.key == key } }
    val groups = remember(rows, lineStations) { BookKinds.groups(key, rows, lineOf) }
    val mine = remember(rows) { BookKinds.rowsOf(key, rows).filter { it.collected } }
    val pins = remember(mine) { mine.mapNotNull { it.toPin() } }
    val recent = remember(mine) { mine.sortedByDescending { it.lastAt ?: 0L }.take(20) }
    val color = kind?.let { kindColor(it) } ?: LocalTypeColors.current.of(ItemType.CUSTOM)

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            windowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
            title = { Text(kind?.let { kindTitle(it) } ?: "") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
        )
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 16.dp)) {
            if (pins.isNotEmpty()) item(key = "map") {
                Surface(Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(170.dp), shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                    MeguriMap(
                        pins, null, Modifier.fillMaxSize(), interactive = false,
                        onPinTap = { nav.openItem(it) }, onMapTap = { nav.openMap(MapSection.COLLECTED, kind = key) }, memoryKey = null, inCard = true,
                    )
                }
            }
            item(key = "groups") {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    groups.chunked(2).forEach { pair ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            pair.forEach { g ->
                                Surface(
                                    Modifier.weight(1f).clickable {
                                        if (g.key.startsWith("l:")) nav.openLine(g.lineId.orEmpty()) else nav.openGroup(key, g.key)
                                    },
                                    shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, tonalElevation = 1.dp,
                                ) {
                                    Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                        ProgressRing(g.collected, g.total, color, 64.dp)
                                        Spacer(Modifier.height(6.dp))
                                        Text(groupTitle(g, kind, lines, ja, customTypes), style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        nextIn(g, vm)?.let { next ->
                                            Text(stringResource(R.string.kind_next, next.item.tileLabel(ja)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                    }
                                }
                            }
                            if (pair.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
            if (recent.isNotEmpty()) item(key = "recent") {
                SectionTitle(stringResource(R.string.profile_recent))
                LazyRow(contentPadding = PaddingValues(horizontal = 12.dp)) {
                    items(recent, key = { it.item.id }) { r ->
                        Column(Modifier.width(80.dp).clickable { nav.openItem(r.item.id) }.padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Mark(r, 52.dp)
                            Text(tileName(r.item.tileLabel(ja)), style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

/** The nearest one still to get in a group, when we know where you are. */
private fun nextIn(g: BookKinds.Group, vm: MainViewModel): ItemRow? =
    g.rows.filter { !it.collected }.mapNotNull { r -> vm.distanceTo(r)?.let { r to it } }.minByOrNull { it.second }?.first

@Composable
private fun groupTitle(
    g: BookKinds.Group, kind: BookKinds.Kind?, lines: List<io.github.thisisartman.collectibles.data.Line>, ja: Boolean,
    customTypes: Map<String, io.github.thisisartman.collectibles.data.CollectionType>,
): String = when {
    g.key.startsWith("l:") -> g.lineId?.let { id -> lines.firstOrNull { it.id == id }?.let { if (ja) it.nameJa else it.nameRomaji ?: it.nameJa } }
        ?: stringResource(R.string.book_other_stations)
    kind?.type == ItemType.CUSTOM -> kindTitle(kind)
    else -> g.prefecture?.let { prefectureName(it, ja) } ?: stringResource(R.string.book_no_prefecture)
}

/** The full list for one prefecture of a kind (or one of your own collections). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupScreen(vm: MainViewModel, kindKey: String, groupKey: String, nav: NavHostController, onBack: () -> Unit) {
    val ja = isJapanese()
    val rows by vm.rows.collectAsStateWithLifecycle()
    val lines by vm.lines.collectAsStateWithLifecycle()
    val lineStations by vm.lineStations.collectAsStateWithLifecycle()
    val customTypes = LocalCustomTypes.current
    val lineOf = remember(lineStations) { val m = lineStations.groupBy({ it.itemId }, { it.lineId }); { id: String -> m[id].orEmpty().toSet() } }
    val kind = remember(rows, lineStations) { BookKinds.kinds(rows, lineOf).firstOrNull { it.key == kindKey } }
    val group = remember(rows, lineStations) { BookKinds.groups(kindKey, rows, lineOf).firstOrNull { it.key == groupKey } }
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            windowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
            title = {
                Text(group?.let { groupTitle(it, kind, lines, ja, customTypes) + (kind?.takeIf { it.type != ItemType.CUSTOM }?.let { sep() + kindTitle(it) } ?: "") } ?: "",
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
        )
        // Owner, 2026-11-08 (S23): a grid like everywhere else, short names, and only two filters you switch on or off:
        // collected and uncollected (both on, the default, is everything). No "All", no series
        var showHave by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(true) }
        var showToGet by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(true) }
        Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // The last one on can't be switched off: both off would show nothing
            androidx.compose.material3.FilterChip(selected = showHave, onClick = { if (!showHave || showToGet) showHave = !showHave },
                label = { Text(stringResource(R.string.filter_have)) }, modifier = Modifier.heightIn(min = 48.dp))
            androidx.compose.material3.FilterChip(selected = showToGet, onClick = { if (!showToGet || showHave) showToGet = !showToGet },
                label = { Text(stringResource(R.string.filter_to_get)) }, modifier = Modifier.heightIn(min = 48.dp))
        }
        val list = group?.rows.orEmpty()
            .filter { r -> (r.collected && showHave) || (!r.collected && showToGet) }
            .sortedWith(compareBy<ItemRow> { it.item.code ?: it.item.displayName(ja) })
        androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
            columns = androidx.compose.foundation.lazy.grid.GridCells.Adaptive(88.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 24.dp),
            modifier = Modifier.weight(1f),
        ) {
            items(list.size, key = { list[it].item.id }) { i ->
                val r = list[i]
                RoundTile(r, 64.dp, r.item.shortName(ja), if (r.noneHere) null else distanceText(vm.distanceTo(r)), onOpen = { nav.openItem(r.item.id) })
            }
        }
    }
}
