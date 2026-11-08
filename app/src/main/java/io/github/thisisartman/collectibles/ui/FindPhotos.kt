package io.github.thisisartman.collectibles.ui

import androidx.compose.material.icons.filled.Add
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.background
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.thisisartman.collectibles.R
import io.github.thisisartman.collectibles.data.ItemType
import io.github.thisisartman.collectibles.data.Photo
import io.github.thisisartman.collectibles.data.PhotoKind

/** What the item page's photos can do: add by camera or from the gallery (of a kind), change a photo's kind, remove it. */
class FindPhotoActions(
    val takePhoto: (kind: String) -> Unit,
    val pickPhoto: (uri: Uri, kind: String) -> Unit,
    val setKind: (photoId: String, kind: String) -> Unit,
    val remove: (photoId: String) -> Unit,
    // Choose the photo that stands for the item (owner, 2026-11-07); null goes back to the latest
    val setCover: (photoId: String?) -> Unit = {},
)

/**
 * The photo kinds for a type, in order (owner, 2026-11-07: like Google Maps' "Add menu photos", one dotted square
 * per kind). The first is the collectible itself.
 */
fun photoKinds(type: ItemType): List<String> = when (type) {
    ItemType.EKI_STAMP, ItemType.EKITAG -> listOf(PhotoKind.STAMP, PhotoKind.EKITAG, PhotoKind.PLACE)
    ItemType.MANHOLE_CARD -> listOf(PhotoKind.STAMP, PhotoKind.MANHOLE, PhotoKind.PLACE)
    ItemType.GOSHUIN -> listOf(PhotoKind.STAMP, PhotoKind.PLACE)
    ItemType.CUSTOM, ItemType.DAM_CARD, ItemType.MUSEUM_STAMP, ItemType.ROADSIDE_STATION -> listOf(PhotoKind.STAMP, PhotoKind.PLACE)
}

@androidx.annotation.StringRes
fun kindLabel(type: ItemType, kind: String): Int = when (kind) {
    PhotoKind.STAMP -> when (type) {
        ItemType.EKI_STAMP, ItemType.EKITAG -> R.string.kind_station_stamp
        ItemType.MANHOLE_CARD -> R.string.kind_card
        ItemType.GOSHUIN -> R.string.kind_goshuin
        ItemType.CUSTOM -> R.string.kind_item
        ItemType.DAM_CARD -> R.string.kind_card
        ItemType.MUSEUM_STAMP, ItemType.ROADSIDE_STATION -> R.string.photo_kind_stamp
    }
    PhotoKind.EKITAG -> R.string.kind_ekitag
    PhotoKind.MANHOLE -> R.string.kind_manhole
    PhotoKind.PLACE -> when (type) {
        ItemType.EKI_STAMP, ItemType.EKITAG -> R.string.kind_station
        ItemType.GOSHUIN -> R.string.kind_shrine
        ItemType.MANHOLE_CARD, ItemType.CUSTOM, ItemType.DAM_CARD, ItemType.MUSEUM_STAMP, ItemType.ROADSIDE_STATION -> R.string.kind_place
    }
    // 0.3's "proof" was an Ekitag screenshot or a card photo
    PhotoKind.PROOF -> if (type == ItemType.MANHOLE_CARD) R.string.kind_card else R.string.kind_ekitag
    else -> R.string.photo_kind_other
}

private fun sameKind(a: String, b: String, type: ItemType): Boolean =
    a == b || (a == PhotoKind.PROOF && b == (if (type == ItemType.MANHOLE_CARD) PhotoKind.STAMP else PhotoKind.EKITAG))

// Three to a row on a phone (3 x 96 + 2 x 10 + 2 x 16 = 340 dp, inside a 360 dp screen)
/** The photo square's side: fitted to the screen so a row of up to four squares fits on one line (owner, 2026-11-08). */
private val LocalSlot = androidx.compose.runtime.compositionLocalOf { 96.dp }

/**
 * The item's photos as squares: each kind for the type has a square, filled with your photo or dotted with a
 * camera and a caption to add one; extra photos follow, then "Other photo". Tap an empty square to add, a photo
 * to change its kind or remove it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ItemPhotos(type: ItemType, photos: List<Photo>, actions: FindPhotoActions, coverId: String? = null) {
    var adding by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<Photo?>(null) }
    var fullscreen by remember { mutableStateOf<String?>(null) }
    fullscreen?.let { FullscreenPhoto(it, onClose = { fullscreen = null }) }
    var pendingKind by remember { mutableStateOf(PhotoKind.STAMP) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) actions.pickPhoto(uri, pendingKind)
    }
    val kinds = photoKinds(type)
    val sorted = photos.sortedBy { it.createdAt }
    // One square per kind (the first photo of that kind, or empty), then the rest of the photos, then "Other"
    val slots: List<Pair<String, Photo?>> = kinds.map { k -> k to sorted.firstOrNull { sameKind(it.role, k, type) } }
    val shown = slots.mapNotNull { it.second }.map { it.id }.toSet()
    val extra = sorted.filter { it.id !in shown }

    // A grid that wraps to the screen, not a sideways strip: every kind is visible without scrolling
    // (map-app study, 2026-11-07: the strip cut off "Other photo")
    // Up to four squares per line, sized to the screen (owner, 2026-11-08: "smaller to fit on the same line")
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
    val count = (slots.size + extra.size + 1).coerceAtMost(5)
    val gap = 8.dp
    val slot = ((maxWidth - gap * (count - 1)) / count).coerceIn(64.dp, 96.dp)
    androidx.compose.runtime.CompositionLocalProvider(LocalSlot provides slot) {
    FlowRow(
        Modifier,
        horizontalArrangement = Arrangement.spacedBy(gap),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        slots.forEach { (kind, p) ->
            val label = stringResource(kindLabel(type, kind))
            if (p != null) PhotoSquare(p, if (p.id == coverId) label + " ★" else label) { editing = p }
            else EmptySquare(label) { adding = kind }
        }
        extra.forEach { p -> PhotoSquare(p, stringResource(kindLabel(type, p.role)) + if (p.id == coverId) " ★" else "") { editing = p } }
        EmptySquare(stringResource(R.string.photo_kind_other)) { adding = PhotoKind.OTHER }
    }
    }
    }

    adding?.let { kind ->
        PhotoSourceDialog(
            title = stringResource(R.string.photo_add_kind, stringResource(kindLabel(type, kind))),
            onCamera = { adding = null; actions.takePhoto(kind) },
            onGallery = {
                adding = null
                pendingKind = kind
                pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            onDismiss = { adding = null },
        )
    }

    editing?.let { p ->
        var kind by remember(p.id) { mutableStateOf(if (p.role in kinds || p.role == PhotoKind.OTHER) p.role else kinds.firstOrNull { sameKind(p.role, it, type) } ?: PhotoKind.OTHER) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(stringResource(R.string.photo_edit_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Card size, whole, with fullscreen a tap away (owner, 2026-11-08)
                    LargePhoto(p.contentUri, onFullscreen = { fullscreen = p.contentUri })
                    KindChips(type, kind) { kind = it }
                    // The photo shown for this item on tiles, in lists and on its page
                    val isCover = p.id == coverId
                    if (isCover) {
                        InkOutlinedButton(onClick = { actions.setCover(null); editing = null }, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.photo_cover_clear))
                        }
                    } else {
                        androidx.compose.material3.FilledTonalButton(onClick = { actions.setCover(p.id); editing = null }, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.photo_cover_set))
                        }
                    }
                    Text(stringResource(R.string.photo_remove_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {
                TextButton(onClick = { if (kind != p.role) actions.setKind(p.id, kind); editing = null }) { Text(stringResource(R.string.photo_done)) }
            },
            dismissButton = {
                TextButton(onClick = { actions.remove(p.id); editing = null }) {
                    Text(stringResource(R.string.photo_remove), color = MaterialTheme.colorScheme.error)
                }
            },
        )
    }
}

/** Camera or gallery, for one kind of photo: full-width buttons, the camera first (owner, 2026-11-07). */
@Composable
fun PhotoSourceDialog(title: String, onCamera: () -> Unit, onGallery: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                androidx.compose.material3.Button(onClick = onCamera, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Icon(painterResource(R.drawable.ic_add_photo), contentDescription = null, modifier = Modifier.size(18.dp))
                    androidx.compose.foundation.layout.Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.photo_take))
                }
                androidx.compose.material3.FilledTonalButton(onClick = onGallery, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.photo_pick))
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/**
 * The Photo button's menu (owner, 2026-11-07): a vertical list of the kinds for this type; picking one asks camera
 * or gallery. [anchor] is the button that opens it.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun PhotoKindMenu(type: ItemType, expanded: Boolean, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    // A Material bottom sheet with a list, not a floating menu (owner, 2026-11-07)
    if (!expanded) return
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            stringResource(R.string.photo_add_title),
            Modifier.padding(start = 24.dp, end = 24.dp, bottom = 8.dp),
            style = MaterialTheme.typography.titleMedium,
        )
        (photoKinds(type) + PhotoKind.OTHER).forEach { k ->
            androidx.compose.material3.ListItem(
                headlineContent = { Text(stringResource(kindLabel(type, k))) },
                leadingContent = { KindAddIcon(type, k) },
                modifier = Modifier.clickable { onDismiss(); onPick(k) }.padding(horizontal = 8.dp),
                colors = androidx.compose.material3.ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
            )
        }
        androidx.compose.foundation.layout.Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun PhotoSquare(p: Photo, label: String, onClick: () -> Unit) {
    val SLOT = LocalSlot.current
    Column(
        Modifier.width(SLOT).clickable(onClick = onClick).spoken(stringResource(R.string.a11y_photo_kind, label)),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PhotoThumb(p.contentUri, SLOT)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
    }
}

/** A dotted square with a camera and the kind's name: tap to add that photo. */
@Composable
private fun EmptySquare(label: String, onClick: () -> Unit) {
    val dash = LocalTypeColors.current.dash
    val SLOT = LocalSlot.current
    val add = stringResource(R.string.photo_add_kind, label)
    Column(
        Modifier.width(SLOT).clickable(onClick = onClick).spoken(add),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(SLOT), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val w = 2.dp.toPx()
                drawRoundRect(
                    color = dash,
                    topLeft = androidx.compose.ui.geometry.Offset(w / 2, w / 2),
                    size = androidx.compose.ui.geometry.Size(size.width - w, size.height - w),
                    cornerRadius = CornerRadius(SLOT.toPx() * 0.16f),
                    style = Stroke(width = w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx()))),
                )
            }
            Icon(painterResource(R.drawable.ic_add_photo), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(28.dp))
        }
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun KindChips(type: ItemType, selected: String, onSelect: (String) -> Unit) {
    // A compact dropdown, not a row of chips (owner, 2026-11-07)
    var open by remember { mutableStateOf(false) }
    androidx.compose.material3.ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it }) {
        androidx.compose.material3.OutlinedTextField(
            value = stringResource(kindLabel(type, selected)),
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.photo_kind_label)) },
            leadingIcon = { Icon(painterResource(kindIcon(type, selected)), contentDescription = null, modifier = Modifier.size(20.dp)) },
            trailingIcon = { androidx.compose.material3.ExposedDropdownMenuDefaults.TrailingIcon(expanded = open) },
            modifier = Modifier.fillMaxWidth().menuAnchor(androidx.compose.material3.MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            (photoKinds(type) + PhotoKind.OTHER).forEach { k ->
                DropdownMenuItem(
                    text = { Text(stringResource(kindLabel(type, k))) },
                    leadingIcon = { Icon(painterResource(kindIcon(type, k)), contentDescription = null, modifier = Modifier.size(20.dp)) },
                    onClick = { onSelect(k); open = false },
                )
            }
        }
    }
}

/** The icon for a kind of photo: the collectible's own symbol, the station, the Ekitag, or a plain photo. */
fun kindIcon(type: ItemType, kind: String): Int = when (kind) {
    PhotoKind.STAMP -> when (type) {
        ItemType.EKI_STAMP, ItemType.EKITAG -> R.drawable.ic_type_station
        ItemType.MANHOLE_CARD -> R.drawable.ic_type_manhole
        ItemType.GOSHUIN -> R.drawable.ic_type_goshuin
        ItemType.DAM_CARD -> R.drawable.ic_type_dam
        ItemType.MUSEUM_STAMP -> R.drawable.ic_type_museum
        ItemType.ROADSIDE_STATION -> R.drawable.ic_type_roadside
        ItemType.CUSTOM -> R.drawable.ic_add_photo
    }
    PhotoKind.EKITAG -> R.drawable.ic_type_ekitag
    PhotoKind.MANHOLE -> R.drawable.ic_type_manhole
    PhotoKind.PLACE -> when (type) {
        ItemType.EKI_STAMP, ItemType.EKITAG -> R.drawable.ic_type_station
        ItemType.GOSHUIN -> R.drawable.ic_type_shrine
        ItemType.DAM_CARD -> R.drawable.ic_type_dam
        ItemType.MUSEUM_STAMP -> R.drawable.ic_type_museum
        ItemType.ROADSIDE_STATION -> R.drawable.ic_type_roadside
        else -> R.drawable.ic_add_photo
    }
    PhotoKind.PROOF -> if (type == ItemType.EKI_STAMP || type == ItemType.EKITAG) R.drawable.ic_type_ekitag else R.drawable.ic_add_photo
    else -> R.drawable.ic_add_photo
}

/** A kind's icon with a small plus: "add a photo of this". */
@Composable
fun KindAddIcon(type: ItemType, kind: String) {
    Box(Modifier.size(28.dp)) {
        Icon(painterResource(kindIcon(type, kind)), contentDescription = null, modifier = Modifier.size(24.dp).align(Alignment.Center))
        if (kindIcon(type, kind) != R.drawable.ic_add_photo) {
            Box(
                Modifier.align(Alignment.BottomEnd).size(13.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh, androidx.compose.foundation.shape.CircleShape),
                contentAlignment = Alignment.Center,
            ) { Icon(androidx.compose.material.icons.Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(11.dp)) }
        }
    }
}
