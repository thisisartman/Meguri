package io.github.thisisartman.collectibles.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.remember
import kotlinx.coroutines.launch
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
import androidx.compose.ui.platform.LocalDensity
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
    val largeText = LocalDensity.current.fontScale > 1.3f

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
            if (query.isBlank() && results.isEmpty()) {
                Text(
                    stringResource(R.string.log_nothing_yet),
                    Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (query.isBlank()) {
                Text(
                    stringResource(if (here.fix != null) R.string.log_near_you else R.string.log_recent),
                    Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                items(results, key = { it.row.item.id }) { r ->
                    val name = r.row.item.displayName(ja)
                    // Same order as the item page: Photo, then the main action on the right
                    val buttons: @Composable () -> Unit = {
                        InkOutlinedButton(onClick = { vm.gotIt(r.row, name) { id -> photo.capture(id) }; onDismiss() }, modifier = Modifier.heightIn(min = 48.dp).spoken(stringResource(R.string.a11y_photo, name)), contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)) {
                            Text(stringResource(R.string.photo))
                        }
                        Spacer(Modifier.width(6.dp))
                        Button(onClick = { vm.gotIt(r.row, name); onDismiss() }, modifier = Modifier.heightIn(min = 48.dp).spoken(stringResource(R.string.a11y_got_it, name)), contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)) {
                            Text(stringResource(R.string.got_it))
                        }
                    }
                    Column(Modifier.fillMaxWidth().clickable { onOpenItem(r.row.item.id) }.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Mark(r.row, 30.dp)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                // Distance first: it is the part that must survive when the line is cut
                                TypeChip(r.row.item.type, listOfNotNull(distanceText(r.meters), r.row.item.code).joinToString(sep()).ifEmpty { null })
                            }
                            if (!largeText) buttons()
                        }
                        // Large text: the buttons get their own line instead of squeezing the name
                        if (largeText) Row(Modifier.padding(start = 40.dp, top = 6.dp)) { buttons() }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
            // Pinned under the list: at a shrine that isn't in the catalogue, this is the way in
            HorizontalDivider(thickness = 2.dp, color = MaterialTheme.colorScheme.outlineVariant)
            Row(Modifier.fillMaxWidth().clickable { newOpen = true }.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.log_not_in_list), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.log_not_in_list_sub), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.width(8.dp))
                InkOutlinedButton(onClick = { newOpen = true }) { Text(stringResource(R.string.log_new)) }
            }
        }
    }

    if (newOpen) {
        val customTypes by vm.collectionTypes.collectAsStateWithLifecycle()
        val jaMode = isJapanese()
        NewItemDialog(
            initialName = query,
            customTypes = customTypes,
            lookUp = { q -> vm.lookUpPlace(q, jaMode) },
            onDismiss = { newOpen = false },
            onSave = { n ->
                vm.addNew(n.type, n.nameJa, n.romaji, n.customTypeId, n.place, n.costYen) { _, findId -> if (n.withPhoto) photo.capture(findId) }
                newOpen = false
                onDismiss()
            },
        )
    }
}

/** What the new-item dialog saves: the kind (or your own collection), names, a looked-up place, and what you paid. */
data class NewItem(
    val type: ItemType,
    val customTypeId: String?,
    val nameJa: String,
    val romaji: String?,
    val place: io.github.thisisartman.collectibles.data.PlaceHit?,
    val costYen: Int?,
    val withPhoto: Boolean,
)

/**
 * "Not in the list?": a new item and its collection in one step. Pick the kind or one of your own collections (#64),
 * the names, optionally look the place up on OpenStreetMap (online; owner, 2026-11-07), and, for goshuin and your
 * own collections that record it, what you paid.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NewItemDialog(
    initialName: String,
    customTypes: List<io.github.thisisartman.collectibles.data.CollectionType>,
    lookUp: suspend (String) -> List<io.github.thisisartman.collectibles.data.PlaceHit>,
    onDismiss: () -> Unit,
    onSave: (NewItem) -> Unit,
) {
    var type by rememberSaveable { mutableStateOf(ItemType.GOSHUIN) }
    var customId by rememberSaveable { mutableStateOf<String?>(null) }
    val looksLatin = initialName.isNotBlank() && initialName.all { it.code < 0x3000 }
    var nameJa by rememberSaveable { mutableStateOf(if (looksLatin) "" else initialName) }
    var romaji by rememberSaveable { mutableStateOf(if (looksLatin) initialName else "") }
    var cost by rememberSaveable { mutableStateOf("") }
    var place by remember { mutableStateOf<io.github.thisisartman.collectibles.data.PlaceHit?>(null) }
    var hits by remember { mutableStateOf<List<io.github.thisisartman.collectibles.data.PlaceHit>?>(null) }
    var searching by remember { mutableStateOf(false) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val custom = customTypes.firstOrNull { it.id == customId }
    val hasCost = if (type == ItemType.CUSTOM) custom?.hasCost == true else type == ItemType.GOSHUIN
    val name = nameJa.ifBlank { romaji }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.new_title)) },
        text = {
            Column(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.new_type), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(ItemType.GOSHUIN, ItemType.EKI_STAMP, ItemType.MANHOLE_CARD, ItemType.DAM_CARD, ItemType.MUSEUM_STAMP).forEach { t ->
                        InkFilterChip(selected = type == t, onClick = { type = t; customId = null }, label = { Text(stringResource(t.label()), maxLines = 1) }, leadingIcon = { TypeDot(t) })
                    }
                    customTypes.forEach { c ->
                        InkFilterChip(
                            selected = type == ItemType.CUSTOM && customId == c.id,
                            onClick = { type = ItemType.CUSTOM; customId = c.id },
                            label = { Text(c.name, maxLines = 1) },
                            leadingIcon = { CustomDot(c) },
                        )
                    }
                }
                OutlinedTextField(value = nameJa, onValueChange = { nameJa = it }, label = { Text(stringResource(R.string.new_name_ja)) }, singleLine = true)
                OutlinedTextField(value = romaji, onValueChange = { romaji = it }, label = { Text(stringResource(R.string.new_name_romaji)) }, singleLine = true)
                if (name.isBlank()) {
                    Text(stringResource(R.string.new_needs_name), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                // Where: your position by default, or a place found on OpenStreetMap
                Text(stringResource(R.string.new_where), style = MaterialTheme.typography.labelLarge)
                val p = place
                if (p != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(p.name, style = MaterialTheme.typography.bodyMedium)
                            p.address?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2) }
                        }
                        TextButton(onClick = { place = null }) { Text(stringResource(R.string.new_use_position)) }
                    }
                } else {
                    Text(stringResource(R.string.new_where_position), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(
                        enabled = name.isNotBlank() && !searching,
                        // OpenStreetMap often has only the Japanese name (燕市産業史料館 found, "Tsubame Industrial
                        // Museum" not): search the Japanese name first, then the romaji one
                        onClick = {
                            searching = true
                            scope.launch {
                                val first = nameJa.takeIf { it.isNotBlank() }?.let { lookUp(it) }.orEmpty()
                                hits = first.ifEmpty { romaji.takeIf { it.isNotBlank() }?.let { lookUp(it) }.orEmpty() }
                                searching = false
                            }
                        },
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) { Text(stringResource(if (searching) R.string.new_finding_place else R.string.new_find_place)) }
                    hits?.let { list ->
                        if (list.isEmpty()) Text(stringResource(R.string.new_no_place), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        list.forEach { h ->
                            Column(Modifier.fillMaxWidth().clickable { place = h; hits = null }.heightIn(min = 48.dp).padding(vertical = 6.dp)) {
                                Text(h.name, style = MaterialTheme.typography.bodyMedium)
                                h.address?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1) }
                            }
                        }
                        if (list.isNotEmpty()) Text(stringResource(R.string.osm_credit), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (hasCost) {
                    OutlinedTextField(
                        value = cost, onValueChange = { v -> cost = v.filter { it.isDigit() }.take(7) },
                        label = { Text(stringResource(R.string.new_cost)) }, singleLine = true, prefix = { Text("¥") },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                    )
                }
            }
        },
        confirmButton = {
            Row {
                val save = { photo: Boolean ->
                    onSave(NewItem(type, if (type == ItemType.CUSTOM) customId else null, name, romaji.ifBlank { null }, place, cost.toIntOrNull()?.takeIf { hasCost }, photo))
                }
                val ok = name.isNotBlank() && (type != ItemType.CUSTOM || customId != null)
                TextButton(enabled = ok, onClick = { save(true) }) { Text(stringResource(R.string.photo)) }
                TextButton(enabled = ok, onClick = { save(false) }) { Text(stringResource(R.string.new_save)) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
