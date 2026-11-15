package io.github.thisisartman.collectibles.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.thisisartman.collectibles.R
import io.github.thisisartman.collectibles.data.Item
import io.github.thisisartman.collectibles.data.PlaceHit
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Edit an item's details (owner, 2026-11-08): its names, place, address, hours, notes and position. The position can
 * come from where you are, a shared Google Maps link, or a place search. Saved edits survive catalogue updates.
 */
/** Types an item you added can be changed to. */
private val RETYPE = listOf(
    io.github.thisisartman.collectibles.data.ItemType.PLACE, io.github.thisisartman.collectibles.data.ItemType.EKI_STAMP,
    io.github.thisisartman.collectibles.data.ItemType.GOSHUIN, io.github.thisisartman.collectibles.data.ItemType.MUSEUM_STAMP,
    io.github.thisisartman.collectibles.data.ItemType.MANHOLE_CARD, io.github.thisisartman.collectibles.data.ItemType.DAM_CARD,
    io.github.thisisartman.collectibles.data.ItemType.ROADSIDE_STATION,
)

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ItemEditSheet(item: Item, vm: MainViewModel, onDismiss: () -> Unit) {
    val ja = isJapanese()
    val scope = rememberCoroutineScope()
    var nameJa by remember { mutableStateOf(item.nameJa) }
    var nameEn by remember { mutableStateOf(item.nameRomaji.orEmpty()) }
    var place by remember { mutableStateOf(item.placeName.orEmpty()) }
    var address by remember { mutableStateOf(item.address.orEmpty()) }
    var hours by remember { mutableStateOf(item.hoursText.orEmpty()) }
    var notes by remember { mutableStateOf(item.notes.orEmpty()) }
    var lat by remember { mutableStateOf(item.lat) }
    var lng by remember { mutableStateOf(item.lng) }
    var link by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    var hits by remember { mutableStateOf<List<PlaceHit>>(emptyList()) }
    var message by remember { mutableStateOf<String?>(null) }
    // Things you added yourself can change type: a stamp added as a station stamp can become a Place, which also takes
    // a goshuin (owner, 2026-11-15, Hakkaisan). Catalogue items keep theirs.
    var type by remember { mutableStateOf(item.type) }
    val canRetype = item.origin == io.github.thisisartman.collectibles.data.Origin.USER && item.type != io.github.thisisartman.collectibles.data.ItemType.CUSTOM
    val noLink = stringResource(R.string.edit_link_failed)
    val noHere = stringResource(R.string.edit_no_position)
    val nothing = stringResource(R.string.edit_search_none)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding()) {
            Text(stringResource(R.string.edit_title), style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(12.dp))
            if (canRetype) {
                Text(stringResource(R.string.edit_type), Modifier.padding(top = 4.dp), style = MaterialTheme.typography.titleSmall)
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    RETYPE.forEach { t ->
                        androidx.compose.material3.FilterChip(selected = type == t, onClick = { type = t }, label = { Text(stringResource(t.label())) })
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
            Field(stringResource(R.string.edit_name_ja), nameJa) { nameJa = it }
            Field(stringResource(R.string.edit_name_en), nameEn) { nameEn = it }
            Field(stringResource(R.string.edit_place), place) { place = it }
            Field(stringResource(R.string.edit_address), address) { address = it }
            Field(stringResource(R.string.edit_hours), hours, single = false) { hours = it }
            Field(stringResource(R.string.edit_notes), notes, single = false) { notes = it }

            Text(stringResource(R.string.edit_position), Modifier.padding(top = 12.dp), style = MaterialTheme.typography.titleSmall)
            Text(
                if (lat != null && lng != null) String.format(Locale.US, "%.5f, %.5f", lat, lng) else stringResource(R.string.edit_position_none),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            Spacer(Modifier.height(6.dp))
            OutlinedButton(
                onClick = { vm.here.value.fix?.let { lat = it.lat; lng = it.lng; message = null } ?: run { message = noHere } },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) { Text(stringResource(R.string.edit_use_here)) }
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                OutlinedTextField(link, { link = it }, Modifier.weight(1f), singleLine = true, label = { Text(stringResource(R.string.edit_link)) })
                TextButton(
                    enabled = link.isNotBlank(),
                    onClick = { scope.launch { vm.positionFromLink(link, ja)?.let { lat = it.first; lng = it.second; message = null } ?: run { message = noLink } } },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) { Text(stringResource(R.string.edit_read)) }
            }
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                OutlinedTextField(query, { query = it }, Modifier.weight(1f), singleLine = true, label = { Text(stringResource(R.string.edit_search)) })
                TextButton(
                    enabled = query.isNotBlank(),
                    onClick = { scope.launch { hits = runCatching { vm.lookUpPlace(query, ja) }.getOrDefault(emptyList()); message = if (hits.isEmpty()) nothing else null } },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) { Text(stringResource(R.string.edit_find)) }
            }
            hits.take(5).forEach { h ->
                Column(Modifier.fillMaxWidth().clickable { lat = h.lat; lng = h.lng; if (place.isBlank()) place = h.name; hits = emptyList() }.padding(vertical = 8.dp)) {
                    Text(h.name, style = MaterialTheme.typography.bodyMedium)
                    h.address?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }

            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text(stringResource(R.string.cancel)) }
                Button(
                    enabled = nameJa.isNotBlank(),
                    onClick = {
                        fun opt(s: String) = s.trim().ifEmpty { null }
                        vm.editItem(
                            item.copy(
                                type = type, nameJa = nameJa.trim(), nameRomaji = opt(nameEn), placeName = opt(place), address = opt(address),
                                hoursText = opt(hours), notes = opt(notes), lat = lat, lng = lng,
                            ),
                        )
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                ) { Text(stringResource(R.string.edit_save)) }
            }
            Text(stringResource(R.string.edit_kept_note), Modifier.padding(vertical = 12.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Field(label: String, value: String, single: Boolean = true, onChange: (String) -> Unit) {
    OutlinedTextField(value, onChange, Modifier.fillMaxWidth().padding(vertical = 4.dp), singleLine = single, label = { Text(label) })
}
