package io.github.thisisartman.collectibles.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.thisisartman.collectibles.R
import io.github.thisisartman.collectibles.data.Photo
import io.github.thisisartman.collectibles.data.PhotoKind

/** What a find's photo strip can do (#54): add by camera or from the gallery, change a photo's kind, remove it. */
class FindPhotoActions(
    val takePhoto: (kind: String) -> Unit,
    val pickPhoto: (uri: Uri, kind: String) -> Unit,
    val setKind: (photoId: String, kind: String) -> Unit,
    val remove: (photoId: String) -> Unit,
)

@androidx.annotation.StringRes
private fun kindLabel(kind: String): Int = when (kind) {
    PhotoKind.STAMP -> R.string.photo_kind_stamp
    PhotoKind.PROOF -> R.string.photo_kind_proof
    else -> R.string.photo_kind_other
}

/** A find's photos, each labelled with its kind, and "Add a photo" (#54). Several photos per find, any kinds. */
@Composable
fun FindPhotos(photos: List<Photo>, actions: FindPhotoActions) {
    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Photo?>(null) }
    var pendingKind by remember { mutableStateOf(PhotoKind.STAMP) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) actions.pickPhoto(uri, pendingKind)
    }

    if (photos.isNotEmpty()) {
        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(photos.sortedBy { it.createdAt }, key = { it.id }) { p ->
                val label = stringResource(kindLabel(p.role))
                Column(
                    Modifier.width(88.dp).clickable { editing = p }.spoken(stringResource(R.string.a11y_photo_kind, label)),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    PhotoThumb(p.contentUri, 88.dp)
                    Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, textAlign = TextAlign.Center)
                }
            }
        }
    }
    TextButton(onClick = { adding = true }, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
        Text(stringResource(R.string.item_add_photo))
    }

    if (adding) {
        var kind by remember { mutableStateOf(PhotoKind.STAMP) }
        AlertDialog(
            onDismissRequest = { adding = false },
            title = { Text(stringResource(R.string.photo_add_title)) },
            text = { KindChips(kind) { kind = it } },
            confirmButton = {
                Column(horizontalAlignment = Alignment.End) {
                    TextButton(onClick = { adding = false; actions.takePhoto(kind) }) { Text(stringResource(R.string.photo_take)) }
                    TextButton(onClick = {
                        adding = false
                        pendingKind = kind
                        pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }) { Text(stringResource(R.string.photo_pick)) }
                }
            },
            dismissButton = { TextButton(onClick = { adding = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    editing?.let { p ->
        var kind by remember(p.id) { mutableStateOf(p.role) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(stringResource(R.string.photo_edit_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PhotoThumb(p.contentUri, 120.dp)
                    KindChips(kind) { kind = it }
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun KindChips(selected: String, onSelect: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(PhotoKind.STAMP, PhotoKind.PROOF, PhotoKind.OTHER).forEach { k ->
            InkFilterChip(selected = selected == k, onClick = { onSelect(k) }, label = { Text(stringResource(kindLabel(k))) })
        }
    }
}
