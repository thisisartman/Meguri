package io.github.thisisartman.collectibles.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.thisisartman.collectibles.R
import io.github.thisisartman.collectibles.data.ItemRow
import io.github.thisisartman.collectibles.data.ItemType

/**
 * The place page (#192, owner approved round 3, 2026-11-26), opened from a Places tile with more than one thing:
 * the place's ring, name, kind, line and distance at the top; a row of round tiles, one per thing; a tap on one
 * opens its card just under the row (picture, name, where it is handed out, Directions and Collect), a second tap
 * closes it. The photos of everything at the place below. Each thing's own page is one tap from its card.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaceScreen(vm: MainViewModel, id: String, onBack: () -> Unit, onOpenItem: (String) -> Unit) {
    val ja = isJapanese()
    val rows by vm.rows.collectAsStateWithLifecycle()
    val here by vm.here.collectAsStateWithLifecycle()
    val lines by vm.lines.collectAsStateWithLifecycle()
    val stations by vm.lineStations.collectAsStateWithLifecycle()
    // Grouped as on Nearby, from the same list, so the page shows the same place and the same count
    val place = remember(rows, id) { Places.group(rows).firstOrNull { p -> p.things.any { it.item.id == id } } }
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            windowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
            title = { Text(place?.let { stringResource(it.kind.one()) } ?: "") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back)) } },
        )
        if (place == null) return@Column
        val ids = place.things.map { it.item.id }
        val photos by remember(ids) {
            kotlinx.coroutines.flow.combine(ids.map { vm.finds(it) }) { all ->
                all.flatMap { finds -> finds.flatMap { it.photos } }.filter { !it.missing }.sortedByDescending { it.createdAt }
            }
        }.collectAsStateWithLifecycle(emptyList())
        val meters = here.fix?.let { Places.nearestM(place, it.lat, it.lng) }
        val lineNames = lines.filter { l -> stations.any { it.lineId == l.id && it.itemId == place.anchor.item.id } }
            .map { if (ja) it.nameJa else it.nameRomaji ?: it.nameJa }
        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            item {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    SectorRing(place.things, 76.dp)
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(Places.name(place, ja), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text(
                            listOfNotNull(
                                stringResource(place.kind.one()),
                                lineNames.takeIf { it.isNotEmpty() }?.joinToString(if (ja) "・" else ", "),
                                distanceText(meters),
                                stringResource(R.string.place_of, place.collected, place.things.size),
                            ).joinToString(sep()),
                            style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            item { ThingsAndCard(place.things, open, onToggle = { t -> open = if (open == t) null else t }, vm, onOpenItem) }
            item { SectionTitle(stringResource(R.string.item_photos)) }
            item { PlacePhotos(photos.map { it.contentUri }) }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

/** The row of round tiles, one per thing, and the open thing's card just under it with a caret at its tile. */
@Composable
private fun ThingsAndCard(things: List<ItemRow>, open: String?, onToggle: (String) -> Unit, vm: MainViewModel, onOpenItem: (String) -> Unit) {
    val centres = remember { mutableStateMapOf<String, Float>() }
    var cardLeft by remember { mutableFloatStateOf(0f) }
    Column {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            things.forEach { t ->
                val selected = open == t.item.id
                Column(
                    Modifier.width(100.dp).clip(RoundedCornerShape(16.dp))
                        .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent)
                        .clickable { onToggle(t.item.id) }
                        .semantics(mergeDescendants = true) { this.selected = selected }
                        .onGloballyPositioned { c -> c.boundsInRoot().let { centres[t.item.id] = it.left + it.width / 2 } }
                        .padding(vertical = 8.dp, horizontal = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    SectorRing(listOf(t), 56.dp)
                    Spacer(Modifier.height(4.dp))
                    Text(kindName(t.item), style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        when {
                            t.collected -> t.lastAt?.let { dateText(it) } ?: stringResource(R.string.state_collected)
                            t.noneHere -> stringResource(noneHereLabel(t.item.type))
                            else -> stringResource(R.string.place_to_get)
                        },
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                    )
                }
            }
        }
        val t = things.firstOrNull { it.item.id == open } ?: return@Column
        val caret = MaterialTheme.colorScheme.surfaceContainerHigh
        val density = LocalDensity.current
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp).onGloballyPositioned { cardLeft = it.boundsInRoot().left }) {
            // The caret points up at the open tile
            val x = with(density) { ((centres[t.item.id] ?: cardLeft) - cardLeft).toDp() - 9.dp }
            Canvas(Modifier.padding(start = x.coerceAtLeast(12.dp)).size(18.dp, 9.dp)) {
                drawPath(Path().apply { moveTo(0f, size.height); lineTo(size.width / 2, 0f); lineTo(size.width, size.height); close() }, caret)
            }
            ThingCard(t, vm, onOpenItem)
        }
    }
}

/** One thing's card on the place page: its picture, name, state, where it is handed out, Directions and Collect. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ThingCard(t: ItemRow, vm: MainViewModel, onOpenItem: (String) -> Unit) {
    val ja = isJapanese()
    val context = LocalContext.current
    val confirm = rememberConfirm()
    val item = t.item
    val name = item.displayName(ja)
    val ekitagLabel = stringResource(R.string.type_ekitag)
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, shadowElevation = 2.dp) {
        Row(Modifier.padding(12.dp)) {
            // Your photo, else the card's own picture, else its type mark (as on its own page)
            Mark(t, 84.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                // The name opens the thing's own page: its finds, photos and details
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { onOpenItem(item.id) }, verticalAlignment = Alignment.CenterVertically) {
                    Text(item.cardTitle(ja).first, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.place_open_thing, name))
                }
                Text(
                    kindName(item) + sep() + stringResource(if (t.collected) R.string.state_collected else if (t.noneHere) noneHereLabel(item.type) else R.string.state_to_get),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val where = listOfNotNull(item.placeName?.takeIf { it.isNotBlank() && it != item.nameJa }, item.hoursText?.takeIf { it.isNotBlank() }).joinToString("\n")
                if (where.isNotEmpty()) {
                    Text(stringResource(placeLabel(item.type)), Modifier.padding(top = 6.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(where, style = MaterialTheme.typography.bodySmall)
                }
                FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (item.lat != null && item.lng != null) {
                        FilledTonalButton(onClick = { openDirections(context, item, vm.distanceTo(t)) }, modifier = Modifier.heightIn(min = 48.dp)) {
                            Icon(painterResource(R.drawable.ic_directions), contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.item_directions_plain))
                        }
                    }
                    // One verb everywhere (#143): Collect, then Collect again; an Ekitag is named with its kind
                    val label = stringResource(if (t.collected) R.string.got_it_again else R.string.got_it)
                    val collect = { confirm(); vm.gotIt(t, if (item.type == ItemType.EKITAG) "$name · $ekitagLabel" else name) }
                    val content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {
                        Icon(painterResource(R.drawable.ic_collect), contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(label, maxLines = 1)
                    }
                    if (t.collected) InkOutlinedButton(onClick = collect, content = content)
                    else Button(onClick = collect, modifier = Modifier.heightIn(min = 48.dp), content = content)
                }
            }
        }
    }
}

/** The photos of everything at the place, newest first; a tap shows one full screen. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlacePhotos(uris: List<String>) {
    if (uris.isEmpty()) {
        Text(stringResource(R.string.place_no_photos), Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    var full by remember { mutableStateOf<String?>(null) }
    full?.let { FullscreenPhoto(it, onClose = { full = null }) }
    // Three to a row, fitted to the screen
    val side = ((LocalConfiguration.current.screenWidthDp.coerceAtMost(640) - 32 - 16) / 3).dp
    val openLabel = stringResource(R.string.item_picture_open)
    FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        uris.forEach { u ->
            Box(Modifier.clickable(onClickLabel = openLabel) { full = u }) { PhotoThumb(u, side) }
        }
    }
}
