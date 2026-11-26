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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.thisisartman.collectibles.R
import io.github.thisisartman.collectibles.data.CatalogueReport
import io.github.thisisartman.collectibles.data.FindWithPhotos
import io.github.thisisartman.collectibles.data.ItemRow
import io.github.thisisartman.collectibles.data.ItemType
import io.github.thisisartman.collectibles.data.Origin
import io.github.thisisartman.collectibles.data.PhotoKind

/**
 * The place page (#192, owner approved round 3, 2026-11-26), the one page for every thing (owner, 2026-11-26: one
 * page per place, no inner pages; it replaced the item and station pages): the place's ring, name, kind, line and
 * distance at the top; a row of round tiles, one per thing; a tap on one opens its card just under the row, a
 * second tap closes it. The card holds all the item page had: its names, state, None here, where to get it,
 * Directions, its finds, and Edit, reports and Remove in its ⋮ menu; Add photo and Collect for the open card are
 * pinned in a bottom bar so they never scroll away. Each thing's photos sit
 * below under its name with the photo viewer (cover, kind, remove). [openArg] is the thing whose card opens first.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaceScreen(vm: MainViewModel, id: String, openArg: String?, onBack: () -> Unit) {
    val ja = isJapanese()
    val rows by vm.rows.collectAsStateWithLifecycle()
    val here by vm.here.collectAsStateWithLifecycle()
    val lines by vm.lines.collectAsStateWithLifecycle()
    val stations by vm.lineStations.collectAsStateWithLifecycle()
    // Switching cards is state on this page, not a new page, so Back goes straight to where the page was opened from
    var open by rememberSaveable { mutableStateOf(openArg) }
    // The place's things as last seen: removing the thing the page was opened for still finds the place by the rest
    var known by remember { mutableStateOf(listOf<String>()) }
    // Grouped as on Nearby, from the same list, so the page shows the same place and the same count; by the open
    // thing first, so the page follows it when its position is edited
    val place = remember(rows, id, open, known) { Places.placeOf(rows, listOfNotNull(open, id) + known) }
    LaunchedEffect(place) { place?.let { p -> known = p.things.map { it.item.id } } }
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
                    place.things, open, onToggle = { t -> open = if (open == t) null else t }, vm, findsById,
                    // Your own thing removed: the page closes when it was the only one, else its card does
                    onRemoved = { t -> if (place.things.size == 1) onBack() else if (open == t) open = null },
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
        // Pinned at the bottom, as on the old item page: Add photo and Collect never scroll away (owner rule). They act
        // on the open card's thing; with no card open, on the first thing still to get, whose card then opens so the
        // result shows. With no card open and nothing left to get, there is no bar
        val barThing = place.things.firstOrNull { it.item.id == open } ?: place.things.firstOrNull { !it.collected && !it.noneHere }
        if (barThing != null) PlaceActions(
            barThing, kindMenu,
            onAddPhoto = { open = barThing.item.id; addFor = barThing.item.id; kindMenu = true },
            onCollect = { open = barThing.item.id },
            kindMenuContent = {
                sectionOf(addFor)?.let { s -> PhotoKindMenu(s.main.item.type, kindMenu, onDismiss = { kindMenu = false }, onPick = { menuKind = it }) }
            },
            vm = vm,
        )
    }
}

/**
 * The place page's bottom bar, the old item page's: Add photo (outlined, opens the kinds for the thing) and Collect,
 * or Collect again once collected (filled until then). A flow row: at large text sizes the buttons take a row each
 * instead of wrapping their labels (#165). It sits under the list, so the list's end is never hidden behind it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlaceActions(
    t: ItemRow, kindMenu: Boolean, onAddPhoto: () -> Unit, onCollect: () -> Unit,
    kindMenuContent: @Composable () -> Unit, vm: MainViewModel,
) {
    val ja = isJapanese()
    val confirm = rememberConfirm()
    val ekitagLabel = stringResource(R.string.type_ekitag)
    val name = t.item.displayName(ja)
    FlowRow(
        Modifier.fillMaxWidth().padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.weight(1f)) {
            InkOutlinedButton(onClick = onAddPhoto, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Icon(painterResource(R.drawable.ic_add_photo), contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.place_add_photo), maxLines = 1, softWrap = false)
            }
            // Anchored here, so the kinds open over the button
            if (kindMenu) kindMenuContent()
        }
        // One verb everywhere (#143): Collect, then Collect again; an Ekitag is named with its kind
        val label = stringResource(if (t.collected) R.string.got_it_again else R.string.got_it)
        val collect = {
            confirm()
            onCollect()
            vm.gotIt(t, if (t.item.type == ItemType.EKITAG) "$name · $ekitagLabel" else name)
        }
        val content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {
            Icon(painterResource(R.drawable.ic_collect), contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, maxLines = 1, softWrap = false)
        }
        if (t.collected) InkOutlinedButton(onClick = collect, modifier = Modifier.weight(1f).heightIn(min = 48.dp), content = content)
        else Button(onClick = collect, modifier = Modifier.weight(1f).heightIn(min = 48.dp), content = content)
    }
}

/** The row of round tiles, one per thing, and the open thing's card just under it with a caret at its tile. */
@Composable
private fun ThingsAndCard(
    things: List<ItemRow>, open: String?, onToggle: (String) -> Unit, vm: MainViewModel, findsById: Map<String, List<FindWithPhotos>>,
    onRemoved: (String) -> Unit,
) {
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
            ThingCard(t, vm, findsById[t.item.id].orEmpty(), onRemoved = { onRemoved(t.item.id) })
        }
    }
}

/**
 * One thing's card on the place page, all the old item page had (owner, 2026-11-26: no inner pages): its picture
 * (tap for full screen), name lines and state; None here; where to get it (hours, place, phone, notes); Directions,
 * Add photo and Collect in the page's pinned bottom bar; its finds (tap one for how many and what you paid, Map, remove). Edit, the catalogue reports
 * and Remove are in the card's ⋮ menu.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ThingCard(t: ItemRow, vm: MainViewModel, finds: List<FindWithPhotos>, onRemoved: () -> Unit) {
    val ja = isJapanese()
    val context = LocalContext.current
    val tick = rememberTick()
    val item = t.item
    val name = item.displayName(ja)
    val own = item.origin == Origin.USER
    var editing by rememberSaveable(item.id) { mutableStateOf(false) }
    var confirmRemove by remember(item.id) { mutableStateOf(false) }
    if (editing) ItemEditSheet(item, vm, onDismiss = { editing = false })
    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text(stringResource(if (own) R.string.item_remove_title else R.string.item_remove_collection_title)) },
            text = { Text(stringResource(if (own) R.string.item_remove_text else R.string.item_remove_collection_text)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmRemove = false
                    vm.removeItem(item.id, alsoItem = own) { if (own) onRemoved() }
                }) { Text(stringResource(R.string.remove), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, shadowElevation = 2.dp) {
        Column(Modifier.padding(start = 12.dp, end = 4.dp, top = 12.dp, bottom = 8.dp)) {
            Row {
                // Your photo, else the card's own picture, else its type mark; a picture opens full screen
                var big by remember { mutableStateOf(false) }
                val cards by io.github.thisisartman.collectibles.photo.CardPictures.have.collectAsState()
                val picture = t.photoUri ?: item.code?.takeIf { it in cards && item.type == ItemType.MANHOLE_CARD }
                    ?.let { io.github.thisisartman.collectibles.photo.CardPictures.uri(context, it) }
                val openPicture = stringResource(R.string.item_picture_open)
                Mark(
                    t, 84.dp,
                    if (picture != null) Modifier.clickable(onClickLabel = openPicture) { big = true }
                        .semantics { contentDescription = openPicture } else Modifier,
                )
                if (big && picture != null) FullscreenPhoto(picture, onClose = { big = false })
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f).padding(top = 4.dp)) {
                    Text(item.cardTitle(ja).first, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    ThingNameLines(item, ja)
                    Text(
                        kindName(item) + sep() + stringResource(if (t.collected) R.string.state_collected else if (t.noneHere) noneHereLabel(item.type) else R.string.state_to_get),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (t.collected) LocalTypeColors.current.of(item.type) else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                ThingMenu(t, onEdit = { editing = true }, onRemove = { confirmRemove = true }, vm)
            }
            Column(Modifier.padding(end = 8.dp)) {
                // Found nothing at the place (owner, 2026-11-07, at Minami-Yoshida): mark it, so it stops being "to get"
                if (!t.collected) NoneHereRow(item, t.noneHere, onSet = { tick(); vm.setNoneHere(item.id, it) })
                if (hasFacts(context, item)) Facts(item, Modifier.fillMaxWidth().padding(top = 8.dp))
                // Standing at the place (a goshuin added where it was logged, say), directions help nobody. Add photo
                // and Collect are in the page's bottom bar, so they never scroll away (owner rule)
                val meters = vm.distanceTo(t)
                val toPlace = item.lat != null && item.lng != null && (meters == null || meters >= HERE_M)
                // A card's cover, where the card says it is (#203, owner 2026-11-26: "directions to the cover should be
                // accessible too"). Shown at the hand-out place too: that is where you set off for the cover
                val cLat = item.coverLat
                val cLng = item.coverLng
                val coverMeters = vm.distanceToCover(item)
                val toCover = (item.type == ItemType.MANHOLE_CARD || item.type == ItemType.DAM_CARD) && cLat != null && cLng != null &&
                    (coverMeters == null || coverMeters >= HERE_M)
                if (toPlace || toCover) FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (toPlace) FilledTonalButton(onClick = { openDirections(context, item, meters) }, modifier = Modifier.heightIn(min = 48.dp)) {
                        Icon(painterResource(R.drawable.ic_directions), contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(distanceText(meters)?.let { stringResource(R.string.item_directions, it) } ?: stringResource(R.string.item_directions_plain), maxLines = 1)
                    }
                    if (toCover && cLat != null && cLng != null) OutlinedButton(onClick = { openCoverDirections(context, cLat, cLng, coverMeters) }, modifier = Modifier.heightIn(min = 48.dp)) {
                        Icon(painterResource(if (item.type == ItemType.DAM_CARD) R.drawable.ic_type_dam else R.drawable.ic_type_manhole), contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        val dam = item.type == ItemType.DAM_CARD
                        Text(
                            distanceText(coverMeters)?.let { stringResource(if (dam) R.string.item_dam_directions else R.string.item_cover_directions, it) }
                                ?: stringResource(if (dam) R.string.item_dam_directions_plain else R.string.item_cover_directions_plain),
                            maxLines = 1,
                        )
                    }
                }
            }
            // Each time you collected it, newest first: tap one for how many and what you paid; Map; remove
            if (finds.isNotEmpty()) {
                Text(
                    stringResource(R.string.item_your_record), Modifier.padding(top = 12.dp, bottom = 2.dp),
                    style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // Cost only where something is paid: goshuin, and your own collections that note it (owner, 2026-11-07)
                val custom = item.customTypeId?.let { LocalCustomTypes.current[it] }
                val hasCost = item.type == ItemType.GOSHUIN || (item.type == ItemType.CUSTOM && custom?.hasCost == true)
                finds.forEach { f ->
                    androidx.compose.runtime.key(f.find.id) {
                        CollectionRow(
                            f, ja, name, onRemoveBeforeApp = { vm.unmarkBeforeApp(item.id) }, onRemoveFind = { vm.removeFind(f.find.id) },
                            hasCost = hasCost, onQuantity = { vm.setQuantity(f.find.id, it) }, onCost = { vm.setCost(f.find.id, it) },
                        )
                    }
                }
            }
        }
    }
}

/** The card's ⋮ menu: Edit, the three catalogue reports, and Remove (your own thing) or Remove from collection. */
@Composable
private fun ThingMenu(t: ItemRow, onEdit: () -> Unit, onRemove: () -> Unit, vm: MainViewModel) {
    val context = LocalContext.current
    val own = t.item.origin == Origin.USER
    var more by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { more = true }) {
            Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.more_options))
        }
        DropdownMenu(expanded = more, onDismissRequest = { more = false }) {
            // Edit its name, place or position (owner, 2026-11-08)
            DropdownMenuItem(text = { Text(stringResource(R.string.edit_title)) }, onClick = { more = false; onEdit() })
            // Catalogue reports (0.6, #1): the form on GitHub opens with this thing filled in
            listOf(
                CatalogueReport.Kind.AVAILABILITY to R.string.report_availability,
                CatalogueReport.Kind.CORRECTION to R.string.report_correction,
                CatalogueReport.Kind.ADD to R.string.report_add,
            ).forEach { (kind, label) ->
                DropdownMenuItem(
                    text = { Text(stringResource(label)) },
                    onClick = {
                        more = false
                        vm.log.i("Report", "Opened the ${kind.name.lowercase()} form")
                        runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(CatalogueReport.url(kind, t.item)))) }
                    },
                )
            }
            // Remove (owner, 2026-11-09): a thing you added goes entirely; a catalogue thing leaves your collection
            if (own || t.collected) DropdownMenuItem(
                text = { Text(stringResource(if (own) R.string.item_remove else R.string.item_remove_collection)) },
                onClick = { more = false; onRemove() },
            )
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
