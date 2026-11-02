package io.github.thisisartman.collectibles.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.thisisartman.collectibles.R
import io.github.thisisartman.collectibles.data.ItemType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogSheet(vm: MainViewModel, onDismiss: () -> Unit, onOpenItem: (String) -> Unit) {
    val ja = isJapanese()
    val rows by vm.rows.collectAsStateWithLifecycle()
    val nearest by vm.nearest.collectAsStateWithLifecycle()
    val here by vm.here.collectAsStateWithLifecycle()
    val photo = LocalPhoto.current
    var query by rememberSaveable { mutableStateOf("") }
    var newOpen by rememberSaveable { mutableStateOf(false) }
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val results: List<Ranked> = if (query.isBlank()) {
        nearest.take(12)
    } else {
        rows.filter { it.item.type != ItemType.EKITAG && it.item.matches(query) }
            .map { Ranked(it, vm.distanceTo(it)) }
            .sortedWith(compareBy<Ranked> { it.row.collected }.thenBy { it.meters ?: Double.MAX_VALUE })
            .take(40)
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            Text(stringResource(R.string.log_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 16.dp))
            OutlinedTextField(
                value = query, onValueChange = { query = it }, singleLine = true,
                placeholder = { Text(stringResource(R.string.log_search)) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            if (query.isBlank()) {
                Text(
                    stringResource(if (here.fix != null) R.string.log_near_you else R.string.log_recent),
                    Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                items(results, key = { it.row.item.id }) { r ->
                    val name = r.row.item.displayName(ja)
                    Row(
                        Modifier.fillMaxWidth().clickable { onOpenItem(r.row.item.id) }.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Mark(r.row, 30.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            TypeChip(r.row.item.type, listOfNotNull(r.row.item.code, distanceText(r.meters)).joinToString(" · ").ifEmpty { null })
                        }
                        Button(onClick = { vm.gotIt(r.row, name); onDismiss() }, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)) {
                            Text(stringResource(R.string.got_it))
                        }
                        Spacer(Modifier.width(6.dp))
                        OutlinedButton(onClick = { vm.gotIt(r.row, name) { id -> photo.capture(id) }; onDismiss() }, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)) {
                            Text(stringResource(R.string.photo))
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
                item {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.log_not_in_list), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                            Text(stringResource(R.string.log_not_in_list_sub), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        OutlinedButton(onClick = { newOpen = true }) { Text(stringResource(R.string.log_new)) }
                    }
                }
            }
        }
    }

    if (newOpen) {
        NewItemDialog(
            initialName = query,
            onDismiss = { newOpen = false },
            onSave = { type, nameJa, romaji, withPhoto ->
                vm.addNew(type, nameJa, romaji) { _, findId -> if (withPhoto) photo.capture(findId) }
                newOpen = false
                onDismiss()
            },
        )
    }
}

@Composable
fun NewItemDialog(initialName: String, onDismiss: () -> Unit, onSave: (ItemType, String, String?, Boolean) -> Unit) {
    var type by rememberSaveable { mutableStateOf(ItemType.GOSHUIN) }
    val looksLatin = initialName.isNotBlank() && initialName.all { it.code < 0x3000 }
    var nameJa by rememberSaveable { mutableStateOf(if (looksLatin) "" else initialName) }
    var romaji by rememberSaveable { mutableStateOf(if (looksLatin) initialName else "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.new_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.new_type), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(ItemType.GOSHUIN, ItemType.EKI_STAMP, ItemType.MANHOLE_CARD).forEach { t ->
                        FilterChip(selected = type == t, onClick = { type = t }, label = { Text(stringResource(t.label()), maxLines = 1) })
                    }
                }
                OutlinedTextField(value = nameJa, onValueChange = { nameJa = it }, label = { Text(stringResource(R.string.new_name_ja)) }, singleLine = true)
                OutlinedTextField(value = romaji, onValueChange = { romaji = it }, label = { Text(stringResource(R.string.new_name_romaji)) }, singleLine = true)
            }
        },
        confirmButton = {
            Row {
                TextButton(enabled = nameJa.isNotBlank() || romaji.isNotBlank(), onClick = { onSave(type, nameJa.ifBlank { romaji }, romaji.ifBlank { null }, true) }) {
                    Text(stringResource(R.string.photo))
                }
                TextButton(enabled = nameJa.isNotBlank() || romaji.isNotBlank(), onClick = { onSave(type, nameJa.ifBlank { romaji }, romaji.ifBlank { null }, false) }) {
                    Text(stringResource(R.string.new_save))
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
