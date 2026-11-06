package io.github.thisisartman.collectibles.ui

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
)

/**
 * The photo kinds for a type, in order (owner, 2026-11-07: like Google Maps' "Add menu photos", one dotted square
 * per kind). The first is the collectible itself.
 */
fun photoKinds(type: ItemType): List<String> = when (type) {
    ItemType.EKI_STAMP, ItemType.EKITAG -> listOf(PhotoKind.STAMP, PhotoKind.EKITAG, PhotoKind.PLACE)
    ItemType.MANHOLE_CARD -> listOf(PhotoKind.STAMP, PhotoKind.MANHOLE, PhotoKind.PLACE)
    ItemType.GOSHUIN -> listOf(PhotoKind.STAMP, PhotoKind.PLACE)
}

@androidx.annotation.StringRes
fun kindLabel(type: ItemType, kind: String): Int = when (kind) {
    PhotoKind.STAMP -> when (type) {
        ItemType.EKI_STAMP, ItemType.EKITAG -> R.string.kind_station_stamp
        ItemType.MANHOLE_CARD -> R.string.kind_card
        ItemType.GOSHUIN -> R.string.kind_goshuin
    }
    PhotoKind.EKITAG -> R.string.kind_ekitag
    PhotoKind.MANHOLE -> R.string.kind_manhole
    PhotoKind.PLACE -> when (type) {
        ItemType.EKI_STAMP, ItemType.EKITAG -> R.string.kind_station
        ItemType.GOSHUIN -> R.string.kind_shrine
        ItemType.MANHOLE_CARD -> R.string.kind_place
    }
    // 0.3's "proof" was an Ekitag screenshot or a card photo
    PhotoKind.PROOF -> if (type == ItemType.MANHOLE_CARD) R.string.kind_card else R.string.kind_ekitag
    else -> R.string.photo_kind_other
}

private fun sameKind(a: String, b: String, type: ItemType): Boolean =
    a == b || (a == PhotoKind.PROOF && b == (if (type == ItemType.MANHOLE_CARD) PhotoKind.STAMP else PhotoKind.EKITAG))

private val SLOT = 96.dp

/**
 * The item's photos as squares: each kind for the type has a square, filled with your photo or dotted with a
 * camera and a caption to add one; extra photos follow, then "Other photo". Tap an empty square to add, a photo
 * to change its kind or remove it.
 */
@Composable
fun ItemPhotos(type: ItemType, photos: List<Photo>, actions: FindPhotoActions) {
    var adding by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<Photo?>(null) }
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

    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
    ) {
        items(slots, key = { "slot-" + it.first }) { (kind, p) ->
            val label = stringResource(kindLabel(type, kind))
            if (p != null) PhotoSquare(p, label) { editing = p }
            else EmptySquare(label) { adding = kind }
        }
        items(extra, key = { it.id }) { p -> PhotoSquare(p, stringResource(kindLabel(type, p.role))) { editing = p } }
        item(key = "slot-other") { EmptySquare(stringResource(R.string.photo_kind_other)) { adding = PhotoKind.OTHER } }
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
                    PhotoThumb(p.contentUri, 120.dp)
                    KindChips(type, kind) { kind = it }
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

/** Camera or gallery, for one kind of photo. */
@Composable
fun PhotoSourceDialog(title: String, onCamera: () -> Unit, onGallery: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        confirmButton = {
            Column(horizontalAlignment = Alignment.End) {
                TextButton(onClick = onCamera) { Text(stringResource(R.string.photo_take)) }
                TextButton(onClick = onGallery) { Text(stringResource(R.string.photo_pick)) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/**
 * The Photo button's menu (owner, 2026-11-07): a vertical list of the kinds for this type; picking one asks camera
 * or gallery. [anchor] is the button that opens it.
 */
@Composable
fun PhotoKindMenu(type: ItemType, expanded: Boolean, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        (photoKinds(type) + PhotoKind.OTHER).forEach { k ->
            DropdownMenuItem(
                text = { Text(stringResource(kindLabel(type, k))) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_add_photo), contentDescription = null) },
                onClick = { onDismiss(); onPick(k) },
            )
        }
    }
}

@Composable
private fun PhotoSquare(p: Photo, label: String, onClick: () -> Unit) {
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun KindChips(type: ItemType, selected: String, onSelect: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        (photoKinds(type) + PhotoKind.OTHER).forEach { k ->
            InkFilterChip(selected = selected == k, onClick = { onSelect(k) }, label = { Text(stringResource(kindLabel(type, k))) })
        }
    }
}
