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
import androidx.compose.runtime.LaunchedEffect
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
import io.github.thisisartman.collectibles.data.PhotoKind

/**
 * The place page (#192, owner approved round 3, 2026-11-26), opened from a Places tile with more than one thing:
 * the place's ring, name, kind, line and distance at the top; a row of round tiles, one per thing; a tap on one
 * opens its card just under the row (picture, name, where it is handed out, Directions and Collect), a second tap
 * closes it. The card adds photos too, and each thing's photos sit below under its name with the item page's
 * viewer (cover, kind, remove), so nothing day to day needs the thing's own page, still one tap from its card.
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
        // Each thing's finds, by its id: its photos, and the latest find that a new photo goes on
        val findsById by remember(ids) {
            kotlinx.coroutines.flow.combine(ids.map { vm.finds(it) }) { all -> ids.zip(all.toList()).toMap() }
        }.collectAsStateWithLifecycle(emptyMap())
        // A station's stamp and its Ekitag share one set of photos, as on the station page
        val sections = remember(place.things, rows) { photoSections(place.things, rows) }
        val photo = LocalPhoto.current
        val context = LocalContext.current
        val ekitagLabel = stringResource(R.string.type_ekitag)
        // The item page's photo actions (ItemScreen), per section: a photo goes on the latest find, and with none
        // yet, adding a photo collects the thing first. On a station an Ekitag screenshot goes on the Ekitag
        fun actionsFor(s: PhotoSection): FindPhotoActions {
            val target = findsById[s.main.item.id]?.firstOrNull()?.find?.id
            val tagTarget = s.ekitag?.let { findsById[it.item.id]?.firstOrNull()?.find?.id }
            fun owner(kind: String): Pair<ItemRow, String?> =
                if (s.ekitag != null && kind == PhotoKind.EKITAG) s.ekitag to tagTarget else s.main to target
            fun named(who: ItemRow) = who.item.displayName(ja).let { if (who.item.type == ItemType.EKITAG) "$it · $ekitagLabel" else it }
            return FindPhotoActions(
                takePhoto = { kind ->
                    val (who, t) = owner(kind)
                    if (t != null) { vm.nextPhotoKind(t, kind); photo.capture(t) }
                    else vm.gotIt(who, named(who)) { id -> vm.nextPhotoKind(id, kind); photo.capture(id) }
                },
                pickPhoto = { uri, kind ->
                    val (who, t) = owner(kind)
                    if (t != null) vm.addGalleryPhoto(context, t, uri, kind)
                    else vm.gotIt(who, named(who)) { id -> vm.addGalleryPhoto(context, id, uri, kind) }
                },
                setKind = vm::setPhotoKind,
                remove = vm::removePhoto,
                setCover = { id -> vm.setCover(s.main.item.id, id) },
            )
        }
        fun sectionOf(thingId: String?) = sections.firstOrNull { it.main.item.id == thingId || it.ekitag?.item?.id == thingId }
        // The card's Add photo: the kinds for the thing, then camera or gallery (the item page's bottom bar). Held
        // here, not in the card, so the gallery's answer still arrives if the card closes meanwhile
        var addFor by rememberSaveable { mutableStateOf<String?>(null) }
        var kindMenu by remember { mutableStateOf(false) }
        var menuKind by rememberSaveable { mutableStateOf<String?>(null) }
        var picking by remember { mutableStateOf(false) }
        val pick = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()) { uri ->
            val k = menuKind
            val s = sectionOf(addFor)
            if (uri != null && k != null && s != null) actionsFor(s).pickPhoto(uri, k)
            menuKind = null
        }
        sectionOf(addFor)?.let { s ->
            PhotoKindMenu(s.main.item.type, kindMenu, onDismiss = { kindMenu = false }, onPick = { menuKind = it })
            menuKind?.let { k ->
                if (!picking) PhotoSourceDialog(
                    title = stringResource(R.string.photo_add_kind, stringResource(kindLabel(s.main.item.type, k))),
                    onCamera = { menuKind = null; actionsFor(s).takePhoto(k) },
                    onGallery = { picking = true; pick.launch(androidx.activity.result.PickVisualMediaRequest(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    onDismiss = { menuKind = null },
                )
            }
        }
        LaunchedEffect(menuKind) { if (menuKind == null) picking = false }
        val meters = here.fix?.let { Places.nearestM(place, it.lat, it.lng) }
        val lineNames = lines.filter { l -> stations.any { it.lineId == l.id && it.itemId == place.anchor.item.id } }
            .map { if (ja) it.nameJa else it.nameRomaji ?: it.nameJa }
        // Each photo section's heading: the thing's kind, or Station for a stamp with its Ekitag
        val stationLabel = stringResource(R.string.station_title)
        val labels = sections.map { s -> if (s.ekitag != null) stationLabel else kindName(s.main.item) }
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
            item {
                ThingsAndCard(
                    place.things, open, onToggle = { t -> open = if (open == t) null else t }, vm, onOpenItem,
                    onAddPhoto = { t -> addFor = t; kindMenu = true },
                )
            }
            item { SectionTitle(stringResource(R.string.item_photos)) }
            // Each thing's photos under its name, as on its own page: tap a photo to see it, change its kind, make
            // it the cover or remove it; tap a dotted square to add one (owner, 2026-11-26: no second page needed)
            sections.forEachIndexed { i, s ->
                item(key = "photos-" + s.main.item.id) {
                    val label = labels[i].let { l -> if (labels.count { it == l } > 1) l + sep() + s.main.item.displayName(ja) else l }
                    Text(
                        label, Modifier.padding(start = 16.dp, end = 16.dp, top = if (i == 0) 0.dp else 12.dp, bottom = 6.dp),
                        style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    val photos = findsById[s.main.item.id].orEmpty().flatMap { it.photos } +
                        s.ekitag?.let { e -> findsById[e.item.id].orEmpty().flatMap { it.photos } }.orEmpty()
                    ItemPhotos(
                        s.main.item.type, photos, actionsFor(s), coverId = s.main.item.coverPhotoId, shownUri = s.main.photoUri,
                        // A station without an Ekitag here has no Ekitag screenshot square, as on the station page
                        hasEkitag = s.main.item.type != ItemType.EKI_STAMP || s.ekitag != null,
                    )
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

/** The row of round tiles, one per thing, and the open thing's card just under it with a caret at its tile. */
@Composable
private fun ThingsAndCard(things: List<ItemRow>, open: String?, onToggle: (String) -> Unit, vm: MainViewModel, onOpenItem: (String) -> Unit, onAddPhoto: (String) -> Unit) {
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
            ThingCard(t, vm, onOpenItem, onAddPhoto = { onAddPhoto(t.item.id) })
        }
    }
}

/** One thing's card on the place page: its picture, name, state, where it is handed out, Directions, Add photo and Collect. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ThingCard(t: ItemRow, vm: MainViewModel, onOpenItem: (String) -> Unit, onAddPhoto: () -> Unit) {
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
                    // Add photo here, so the place page needs no second page (owner, 2026-11-26)
                    InkOutlinedButton(onClick = onAddPhoto, modifier = Modifier.heightIn(min = 48.dp)) {
                        Icon(painterResource(R.drawable.ic_add_photo), contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.place_add_photo), maxLines = 1)
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

/** One set of photos on the place page: a thing, or a station's stamp with its Ekitag (as on the station page). */
private class PhotoSection(val main: ItemRow, val ekitag: ItemRow?)

/** The place's things as photo sections: a station's Ekitag folds into its stamp when both are at the place. */
private fun photoSections(things: List<ItemRow>, rows: List<ItemRow>): List<PhotoSection> {
    val ids = things.map { it.item.id }.toSet()
    return things.mapNotNull { t ->
        val pair = stationPair(rows, t.item.id)
        when {
            t.item.type == ItemType.EKITAG && pair != null && pair.first.item.id in ids -> null
            t.item.type == ItemType.EKI_STAMP -> PhotoSection(t, pair?.second?.takeIf { it.item.id in ids })
            else -> PhotoSection(t, null)
        }
    }
}
