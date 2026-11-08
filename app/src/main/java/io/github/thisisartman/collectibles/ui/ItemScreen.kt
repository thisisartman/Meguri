package io.github.thisisartman.collectibles.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Add
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.res.painterResource
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.thisisartman.collectibles.R
import io.github.thisisartman.collectibles.data.FindWithPhotos
import io.github.thisisartman.collectibles.data.Item
import io.github.thisisartman.collectibles.data.ItemType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemScreen(vm: MainViewModel, itemId: String, onBack: () -> Unit) {
    val ja = isJapanese()
    val row by remember(itemId) { vm.itemRow(itemId) }.collectAsStateWithLifecycle(null)
    val finds by remember(itemId) { vm.finds(itemId) }.collectAsStateWithLifecycle(emptyList())
    val lines by vm.lines.collectAsStateWithLifecycle()
    val stations by vm.lineStations.collectAsStateWithLifecycle()
    val photo = LocalPhoto.current
    val r = row
    var editing by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            windowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
            // Your own kinds by their own name ("Aquarium stamp"), not "My collection"
            title = {
                val custom = r?.item?.customTypeId?.let { LocalCustomTypes.current[it]?.name }
                Text(custom ?: r?.item?.type?.let { stringResource(it.label()) } ?: "")
            },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back)) } },
            // Edit its name, place or position (owner, 2026-11-08)
            actions = {
                if (r != null) IconButton(onClick = { editing = true }) {
                    Icon(androidx.compose.material.icons.Icons.Filled.Edit, contentDescription = stringResource(R.string.edit_title))
                }
            },
        )
        if (r == null) return@Column
        if (editing) ItemEditSheet(r.item, vm, onDismiss = { editing = false })
        val item = r.item
        val name = item.displayName(ja)
        // A station says which lines it is on (round 5, D2)
        val lineNames = lines.filter { l -> stations.any { it.lineId == l.id && it.itemId == item.id } }
            .map { if (ja) it.nameJa else it.nameRomaji ?: it.nameJa }
        val meters = vm.distanceTo(r)
        val context = LocalContext.current
        val tick = rememberTick()
        val confirm = rememberConfirm()
        // Photos go on the latest collection; with none yet, adding a photo collects the item first
        val target = finds.firstOrNull()?.find?.id
        val photoActions = remember(target, r.item.id) {
            FindPhotoActions(
                takePhoto = { kind ->
                    if (target != null) { vm.nextPhotoKind(target, kind); photo.capture(target) }
                    else vm.gotIt(r, name) { id -> vm.nextPhotoKind(id, kind); photo.capture(id) }
                },
                pickPhoto = { uri, kind ->
                    if (target != null) vm.addGalleryPhoto(context, target, uri, kind)
                    else vm.gotIt(r, name) { id -> vm.addGalleryPhoto(context, id, uri, kind) }
                },
                setKind = vm::setPhotoKind,
                remove = vm::removePhoto,
                setCover = { id -> vm.setCover(r.item.id, id) },
            )
        }
        // Owner, 2026-11-07: one layout for collected and uncollected, like a map app's place page:
        // who and where, actions, photos (a dotted square per kind), when you collected it, then the facts
        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            item {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    // The picture at the top opens full screen (owner, 2026-11-08)
                    var big by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
                    val cards by io.github.thisisartman.collectibles.photo.CardPictures.have.collectAsState()
                    val ctx = androidx.compose.ui.platform.LocalContext.current
                    val picture = r.photoUri ?: r.item.code?.takeIf { it in cards && r.item.type == ItemType.MANHOLE_CARD }
                        ?.let { io.github.thisisartman.collectibles.photo.CardPictures.uri(ctx, it) }
                    Mark(r, 64.dp, if (picture != null) Modifier.clickable { big = true } else Modifier)
                    if (big && picture != null) FullscreenPhoto(picture, onClose = { big = false })
                    Spacer(Modifier.width(14.dp))
                    Column {
                        NameText(item, ja, lineNames)
                        Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            ItemTypeIcon(item, 16.dp)
                            Spacer(Modifier.width(6.dp))
                            Text(
                                stringResource(if (r.collected) R.string.state_collected else if (r.noneHere) noneHereLabel(item.type) else R.string.state_to_get),
                                style = MaterialTheme.typography.labelLarge,
                                color = if (r.collected) LocalTypeColors.current.of(item.type) else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            item { Actions(item, meters) }
            // Found nothing at the place (owner, 2026-11-07, at Minami-Yoshida): mark it, so it stops being "to get"
            if (!r.collected) item { NoneHereRow(item, r.noneHere, onSet = { tick(); vm.setNoneHere(item.id, it) }) }
            // Where to get it right under the header: the one fact a collector needs (map-app study, 2026-11-07)
            if (hasFacts(context, item)) {
                item { SectionTitle(stringResource(R.string.item_where)) }
                item { Facts(item) }
            }
            item { SectionTitle(stringResource(R.string.item_photos)) }
            item { ItemPhotos(item.type, finds.flatMap { it.photos }, photoActions, coverId = item.coverPhotoId) }
            if (finds.isNotEmpty()) {
                item { SectionTitle(stringResource(R.string.item_your_record)) }
                items(finds, key = { it.find.id }) { f ->
                    // Cost only where something is paid: goshuin, and your own collections that note it (owner, 2026-11-07)
                    val custom = item.customTypeId?.let { LocalCustomTypes.current[it] }
                    CollectionRow(
                        f, ja, name, onRemoveBeforeApp = { vm.unmarkBeforeApp(item.id) }, onRemoveFind = { vm.removeFind(f.find.id) },
                        hasCost = item.type == ItemType.GOSHUIN || (item.type == ItemType.CUSTOM && custom?.hasCost == true),
                        onQuantity = { vm.setQuantity(f.find.id, it) }, onCost = { vm.setCost(f.find.id, it) },
                    )
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
        // Bottom: Photo opens a menu of the kinds for this type (owner, 2026-11-07); Collect is the main action
        var menu by remember { mutableStateOf(false) }
        var menuKind by remember { mutableStateOf<String?>(null) }
        var picking by remember { mutableStateOf(false) }
        val pick = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()) { uri ->
            val k = menuKind
            if (uri != null && k != null) photoActions.pickPhoto(uri, k)
            menuKind = null
        }
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.weight(1f)) {
                InkOutlinedButton(onClick = { menu = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Icon(painterResource(R.drawable.ic_add_photo), contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.photo))
                    Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
                }
                PhotoKindMenu(item.type, menu, onDismiss = { menu = false }, onPick = { menuKind = it })
            }
            val collectLabel = stringResource(if (r.collected) R.string.got_it_again else R.string.got_it)
            val collectContent: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {
                Icon(painterResource(R.drawable.ic_collect), contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(collectLabel)
            }
            if (r.collected) InkOutlinedButton(onClick = { confirm(); vm.gotIt(r, name) }, modifier = Modifier.weight(1f).heightIn(min = 48.dp), content = collectContent)
            else Button(onClick = { confirm(); vm.gotIt(r, name) }, modifier = Modifier.weight(1f).heightIn(min = 48.dp), content = collectContent)
        }
        // While the gallery is open the dialog stays hidden; the pick result clears menuKind
        menuKind?.let { k ->
            if (!picking) PhotoSourceDialog(
                title = stringResource(R.string.photo_add_kind, stringResource(kindLabel(item.type, k))),
                onCamera = { menuKind = null; photoActions.takePhoto(k) },
                onGallery = { picking = true; pick.launch(androidx.activity.result.PickVisualMediaRequest(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                onDismiss = { menuKind = null },
            )
        }
        LaunchedEffect(menuKind) { if (menuKind == null) picking = false }
    }
}

@Composable
private fun NameBlock(item: Item, ja: Boolean, lineNames: List<String>) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) { NameText(item, ja, lineNames) }
}

/** Round 5, D2: the two things you do with a place on a trip day, as icon buttons. */
@Composable
private fun Actions(item: Item, meters: Double?) {
    val context = LocalContext.current
    // Standing at the place (a goshuin added where it was logged, say), directions help nobody
    val hasPlace = item.lat != null && item.lng != null && (meters == null || meters >= HERE_M)
    if (!hasPlace) return
    // Call is a row in "Where to get it" now (tap the number), so Directions stands alone here
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        FilledTonalButton(modifier = Modifier.heightIn(min = 48.dp), onClick = { openDirections(context, item, meters) }) {
            // The same directions icon as the pin card (owner, 2026-11-07: consistent everywhere)
            Icon(painterResource(R.drawable.ic_directions), contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(distanceText(meters)?.let { stringResource(R.string.item_directions, it) } ?: stringResource(R.string.item_directions_plain))
        }
    }
}

@Composable
private fun NameText(item: Item, ja: Boolean, lineNames: List<String> = emptyList()) {
    // A manhole card: its design as the title, the place and code under it
    val (title, subtitle) = item.cardTitle(ja)
    Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    subtitle?.let { Text(it, style = MaterialTheme.typography.titleMedium) }
    item.secondaryName(ja)?.let { Text(it, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    // English mode: prefecture in romaji; the town has no romaji in the catalogue and is already in the name, so it is left out
    val sub = listOfNotNull(
        item.code,
        item.prefecture?.let { prefectureName(it, ja) },
        item.municipality?.takeIf { ja && it != item.nameJa },
    ).joinToString(sep())
    if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (item.series != null) {
        val date = item.releaseDate?.takeIf { it.isNotBlank() }?.let { if (ja) it else it.replace('/', '-') }
        Text(if (date != null) stringResource(R.string.item_wave, item.series, date) else stringResource(R.string.item_wave_only, item.series), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (lineNames.isNotEmpty()) {
        Text(stringResource(R.string.item_on_lines, lineNames.joinToString(if (ja) "・" else ", ")), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** The map for this item's area is not on the phone (outside the offline pack), so it needs internet there. */
private fun offlineMissing(context: android.content.Context, item: Item): Boolean =
    item.lat != null && item.lng != null && !OfflineMap.covers(context, item.lat, item.lng)

/** Whether "Where to get it" has anything to show; a goshuin added by hand often has nothing yet. */
private fun hasFacts(context: android.content.Context, item: Item): Boolean =
    listOfNotNull(item.hoursText, item.closedDays, item.placeName?.takeIf { it != item.nameJa }, item.address, item.notes, item.placePhone).any { it.isNotBlank() } ||
        offlineMissing(context, item)

/**
 * "Where to get it", right under the header as one grouped card of one-line icon rows (map-app study, 2026-11-07:
 * Google only mentions the manhole card inside a review; Organic Maps' rows are about 48 dp): hours, place, phone
 * (tap to call), notes, and a plain line when the map for this spot is not saved on the phone.
 */
@Composable
private fun Facts(item: Item) {
    val context = LocalContext.current
    val (placeLabel, hoursLabel) = when (item.type) {
        ItemType.MANHOLE_CARD -> R.string.item_handed_out_at to R.string.item_hours
        ItemType.GOSHUIN -> R.string.item_office to R.string.item_office_hours
        ItemType.EKI_STAMP, ItemType.EKITAG -> R.string.item_station to R.string.item_stamp_location
        ItemType.CUSTOM, ItemType.MUSEUM_STAMP, ItemType.ROADSIDE_STATION -> R.string.item_place to R.string.item_hours
        ItemType.DAM_CARD -> R.string.item_handed_out_at to R.string.item_hours
    }
    androidx.compose.material3.Surface(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.padding(vertical = 4.dp)) {
            // Hours first: on a trip day the question is whether it is open
            FactRow(painterResource(R.drawable.ic_schedule), stringResource(hoursLabel), listOfNotNull(item.hoursText, item.closedDays).joinToString("\n"))
            // A station's place is the station itself: repeating its Japanese name adds nothing (and breaks English mode)
            FactRow(androidx.compose.ui.graphics.vector.rememberVectorPainter(Icons.Filled.Place), stringResource(placeLabel), listOfNotNull(item.placeName?.takeIf { it != item.nameJa }, item.address).joinToString("\n"))
            item.placePhone?.takeIf { it.isNotBlank() }?.let { phone ->
                FactRow(androidx.compose.ui.graphics.vector.rememberVectorPainter(Icons.Filled.Call), stringResource(R.string.item_call), phone) {
                    // Opens the dialer with the number filled in; no call permission needed
                    runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + phone.filter { it.isDigit() || it == '+' }))) }
                }
            }
            item.notes?.let { FactRow(androidx.compose.ui.graphics.vector.rememberVectorPainter(Icons.Filled.Info), "", localNote(it, isJapanese())) }
            if (offlineMissing(LocalContext.current, item)) {
                FactRow(androidx.compose.ui.graphics.vector.rememberVectorPainter(Icons.Filled.Info), "", stringResource(R.string.item_map_not_saved))
            }
        }
    }
}

@Composable
private fun FactRow(icon: androidx.compose.ui.graphics.painter.Painter, label: String, value: String, onClick: (() -> Unit)? = null) {
    if (value.isBlank()) return
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = if (onClick != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            if (label.isNotEmpty()) Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyMedium, color = if (onClick != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
        }
    }
}

/** How long after a find its missing position counts as "still being found". */
private const val POSITION_WAIT_MS = 2 * 60 * 1000L

/**
 * One time you collected the item, as a single line (owner, 2026-11-07: no "had before the app", no "record"):
 * the date, or "Date not recorded"; Map when a position was saved; a delete icon that asks first.
 */
@Composable
private fun CollectionRow(
    f: FindWithPhotos, ja: Boolean, name: String, onRemoveBeforeApp: () -> Unit, onRemoveFind: () -> Unit,
    hasCost: Boolean = false, onQuantity: (Int) -> Unit = {}, onCost: (Int?) -> Unit = {},
) {
    val a = f.find
    // Tap the line to change how many (duplicates) and, for goshuin and your own collections that note it, what you paid
    var editing by remember { mutableStateOf(false) }
    if (editing) {
        var qty by remember { mutableStateOf(a.quantity) }
        var cost by remember { mutableStateOf(a.costYen?.toString().orEmpty()) }
        AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text(stringResource(R.string.find_edit_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.find_quantity), style = MaterialTheme.typography.labelLarge)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { qty = (qty - 1).coerceAtLeast(1) }, enabled = qty > 1) {
                            Icon(painterResource(R.drawable.ic_remove), contentDescription = stringResource(R.string.a11y_less))
                        }
                        Text("$qty", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(horizontal = 16.dp))
                        IconButton(onClick = { qty = (qty + 1).coerceAtMost(99) }) {
                            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.a11y_more))
                        }
                    }
                    if (hasCost) {
                        androidx.compose.material3.OutlinedTextField(
                            value = cost, onValueChange = { v -> cost = v.filter { it.isDigit() }.take(7) },
                            label = { Text(stringResource(R.string.find_cost)) }, singleLine = true, prefix = { Text("¥") },
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (qty != a.quantity) onQuantity(qty)
                    val yen = cost.toIntOrNull()
                    if (hasCost && yen != a.costYen) onCost(yen)
                    editing = false
                }) { Text(stringResource(R.string.photo_done)) }
            },
            dismissButton = { TextButton(onClick = { editing = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    // Owner, 2026-11-07: something you had before the app shows the day it was added; a dash if there is no date at all
    val whenText = when {
        !a.beforeApp && a.acquiredAt != null -> formatDateTime(a.acquiredAt, ja)
        a.createdAt > 0 -> stringResource(R.string.added_on, formatDate(a.createdAt, ja))
        else -> stringResource(R.string.no_date)
    }
    var confirmRemove by remember { mutableStateOf(false) }
    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text(stringResource(R.string.item_remove_find_title)) },
            text = { Text(stringResource(R.string.item_remove_find_text, whenText)) },
            confirmButton = {
                TextButton(onClick = { confirmRemove = false; if (a.beforeApp) onRemoveBeforeApp() else onRemoveFind() }) {
                    Text(stringResource(R.string.item_remove_find), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    // The position is filled in after the collection is saved; for a while it is pending, not missing
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(a.id, a.lat) {
        while (!a.beforeApp && a.lat == null && now - (a.acquiredAt ?: 0L) < POSITION_WAIT_MS) { delay(5_000); now = System.currentTimeMillis() }
    }
    val whereText = when {
        a.beforeApp -> null
        a.lat != null && a.lng != null -> stringResource(R.string.item_position_saved)
        now - (a.acquiredAt ?: 0L) < POSITION_WAIT_MS -> stringResource(R.string.item_finding_position)
        else -> null
    }
    Row(Modifier.fillMaxWidth().clickable { editing = true }.padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(R.drawable.ic_collect), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
            // The date, then "×2" for duplicates and "¥500" when what you paid is noted
            val extra = listOfNotNull(
                a.quantity.takeIf { it > 1 }?.let { stringResource(R.string.quantity_times, it) },
                a.costYen?.let { "¥%,d".format(it) },
            )
            Text((listOf(whenText) + extra).joinToString(sep()), style = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = "tnum"))
            whereText?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            a.notes?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
        if (a.lat != null && a.lng != null) {
            val context = LocalContext.current
            TextButton(onClick = {
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:${a.lat},${a.lng}?q=${a.lat},${a.lng}"))) }
            }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.item_find_map)) }
        }
        IconButton(onClick = { confirmRemove = true }) {
            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.a11y_remove_find, whenText), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}


/** "No stamp here" for stamps, "None here" for the rest. */
fun noneHereLabel(type: ItemType): Int =
    if (type == ItemType.EKI_STAMP || type == ItemType.MUSEUM_STAMP) R.string.none_here_stamp else R.string.none_here

@Composable
private fun NoneHereRow(item: Item, marked: Boolean, onSet: (Boolean) -> Unit) {
    val ja = isJapanese()
    if (marked) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.none_here_marked, stringResource(noneHereLabel(item.type)), formatDate(item.noneHereAt ?: 0L, ja)),
                Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = { onSet(false) }) { Text(stringResource(R.string.none_here_undo)) }
        }
    } else {
        TextButton(onClick = { onSet(true) }, modifier = Modifier.padding(horizontal = 8.dp)) {
            Text(stringResource(R.string.none_here_ask, stringResource(noneHereLabel(item.type))))
        }
    }
}
