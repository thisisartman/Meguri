package io.github.thisisartman.collectibles.ui

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.thisisartman.collectibles.R
import io.github.thisisartman.collectibles.data.BackupMerge
import io.github.thisisartman.collectibles.data.Item
import io.github.thisisartman.collectibles.data.MergeConflict
import io.github.thisisartman.collectibles.data.MergeField
import io.github.thisisartman.collectibles.data.MergePick
import io.github.thisisartman.collectibles.data.Photo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Add from a backup (#209), first step: what the backup adds, and whether some items differ. With differences,
 * [onNext] opens the screen where you pick; without, [onAdd] adds straight away.
 */
@Composable
fun MergeDialog(plan: BackupMerge.Plan, onCancel: () -> Unit, onNext: () -> Unit, onAdd: () -> Unit) {
    val res = LocalContext.current.resources
    val differ = plan.conflicts.size
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.merge_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (plan.nothingNew) Text(stringResource(R.string.merge_nothing))
                else {
                    Text(stringResource(R.string.merge_new_line, countsText(res, plan.newFinds, plan.newItems, plan.newPhotos)))
                    if (differ > 0) Text(pluralStringResource(R.plurals.merge_differ, differ, differ))
                    Text(stringResource(R.string.merge_copy_note))
                }
            }
        },
        confirmButton = {
            when {
                plan.nothingNew -> TextButton(onClick = onCancel) { Text(stringResource(android.R.string.ok)) }
                differ > 0 -> TextButton(onClick = onNext) { Text(stringResource(R.string.merge_next)) }
                else -> TextButton(onClick = onAdd) { Text(stringResource(R.string.merge_add)) }
            }
        },
        dismissButton = if (plan.nothingNew) null else ({ TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) } }),
    )
}

/**
 * Add from a backup (#209), the items where this phone and the backup differ (owner, 2026-11-28): each item shows
 * both sides next to each other, pictures as pictures, and you tap the side to keep. This phone is kept until you
 * pick. "Keep all from this phone" and "Keep all from the backup" set every item at once; Add stays at the bottom.
 * [backupPicture] finds the picture the backup carries for one of its photos.
 */
@Composable
fun MergeConflictScreen(
    conflicts: List<MergeConflict>,
    backupPicture: (String) -> File?,
    onCancel: () -> Unit,
    onAdd: (Map<String, MergePick>) -> Unit,
) {
    val picks = remember { mutableStateMapOf<String, MergePick>() }
    Dialog(onDismissRequest = onCancel, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onCancel) { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.cancel)) }
                    Text(
                        stringResource(R.string.merge_pick_title), style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f).semantics { heading() },
                    )
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { conflicts.forEach { picks[it.phone.id] = MergePick.PHONE } }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(8.dp)) {
                        Text(stringResource(R.string.merge_keep_all_phone), maxLines = 2)
                    }
                    OutlinedButton(onClick = { conflicts.forEach { picks[it.phone.id] = MergePick.BACKUP } }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(8.dp)) {
                        Text(stringResource(R.string.merge_keep_all_backup), maxLines = 2)
                    }
                }
                LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    items(conflicts, key = { it.phone.id }) { c ->
                        ConflictCard(c, picks[c.phone.id] ?: MergePick.PHONE, backupPicture) { picks[c.phone.id] = it }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                // The action stays on screen however long the list is (owner's rule for important buttons)
                Button(onClick = { onAdd(picks.toMap()) }, modifier = Modifier.fillMaxWidth().padding(16.dp).heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.merge_add))
                }
            }
        }
    }
}

@Composable
private fun ConflictCard(c: MergeConflict, pick: MergePick, backupPicture: (String) -> File?, onPick: (MergePick) -> Unit) {
    val ja = isJapanese()
    val (title, sub) = c.phone.cardTitle(ja)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.semantics(mergeDescendants = true) {}) {
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(sub, stringResource(c.phone.type.label())).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Side(
                MergePick.PHONE, pick == MergePick.PHONE, c, c.phone, Modifier.weight(1f),
                picture = { c.phoneCover?.let { p -> PhoneThumb(p) } },
            ) { onPick(MergePick.PHONE) }
            Side(
                MergePick.BACKUP, pick == MergePick.BACKUP, c, c.backup, Modifier.weight(1f),
                picture = { c.backupCover?.let { p -> BackupThumb(p, backupPicture) } },
            ) { onPick(MergePick.BACKUP) }
        }
    }
}

/** One side of an item: its values for each field that differs, tapped as a whole to keep it. */
@Composable
private fun Side(
    side: MergePick, selected: Boolean, c: MergeConflict, item: Item, modifier: Modifier,
    picture: @Composable () -> Unit, onClick: () -> Unit,
) {
    val label = stringResource(if (side == MergePick.PHONE) R.string.merge_this_phone else R.string.merge_the_backup)
    val color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    Surface(
        modifier = modifier.selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(if (selected) 2.dp else 1.dp, color),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(
                    if (side == MergePick.PHONE) Icons.Outlined.PhoneAndroid else Icons.Outlined.Backup,
                    contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                if (selected) Icon(
                    Icons.Filled.CheckCircle, contentDescription = stringResource(R.string.merge_kept),
                    modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary,
                )
            }
            for (f in c.fields) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(stringResource(fieldLabel(f)), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    when (f) {
                        MergeField.COVER_PHOTO -> picture()
                        MergeField.COVER_POSITION -> Text(position(item), style = MaterialTheme.typography.bodyMedium)
                        MergeField.NOTES -> Text(item.notes.orEmpty(), style = MaterialTheme.typography.bodyMedium, maxLines = 6, overflow = TextOverflow.Ellipsis)
                        MergeField.HOURS -> Text(
                            listOfNotNull(item.hoursText?.takeIf { it.isNotBlank() }, item.closedDays?.takeIf { it.isNotBlank() }).joinToString("\n"),
                            style = MaterialTheme.typography.bodyMedium, maxLines = 6, overflow = TextOverflow.Ellipsis,
                        )
                        MergeField.NONE_HERE -> Text(
                            if (side == MergePick.PHONE) stringResource(R.string.none_here_stamp)
                            else c.backupFirstFind?.let { stringResource(R.string.merge_found_on, dateText(it.acquiredAt ?: it.createdAt)) }.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
    }
}

private fun fieldLabel(f: MergeField) = when (f) {
    MergeField.COVER_PHOTO -> R.string.merge_field_cover
    MergeField.COVER_POSITION -> R.string.merge_field_position
    MergeField.NOTES -> R.string.merge_field_notes
    MergeField.HOURS -> R.string.merge_field_hours
    MergeField.NONE_HERE -> R.string.merge_field_found
}

private fun position(i: Item): String =
    if (i.coverLat == null || i.coverLng == null) "" else String.format(java.util.Locale.US, "%.5f, %.5f", i.coverLat, i.coverLng)

@Composable
private fun PhoneThumb(p: Photo) {
    PhotoThumb(p.contentUri, THUMB)
}

/** The backup's own picture of a photo, read from the backup being added; a note when the backup has none. */
@Composable
private fun BackupThumb(p: Photo, backupPicture: (String) -> File?) {
    var bmp by remember(p.id) { mutableStateOf<Bitmap?>(null) }
    var done by remember(p.id) { mutableStateOf(false) }
    LaunchedEffect(p.id) {
        bmp = withContext(Dispatchers.IO) { backupPicture(p.id)?.let { io.github.thisisartman.collectibles.photo.PhotoStore.thumbnail(it, 256) } }
        done = true
    }
    val b = bmp
    val shape = RoundedCornerShape(THUMB * 0.22f)
    when {
        b != null -> Image(b.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(THUMB).clip(shape))
        done -> Text(stringResource(R.string.merge_no_picture), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        else -> Box(Modifier.size(THUMB).clip(shape).background(MaterialTheme.colorScheme.surfaceVariant))
    }
}

private val THUMB = 96.dp
