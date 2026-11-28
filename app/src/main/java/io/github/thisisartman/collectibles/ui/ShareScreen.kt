package io.github.thisisartman.collectibles.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.thisisartman.collectibles.R
import io.github.thisisartman.collectibles.data.ItemRow
import io.github.thisisartman.collectibles.data.ItemType
import io.github.thisisartman.collectibles.data.PhotoGroup
import io.github.thisisartman.collectibles.data.PhotoSuggest
import io.github.thisisartman.collectibles.data.ShareGrouping
import io.github.thisisartman.collectibles.data.SharedPhoto
import io.github.thisisartman.collectibles.location.distanceM
import kotlinx.coroutines.launch

/**
 * Photos shared into Meguri (#80, owner's pick V1 "groups, then a sheet", 2026-11-08): the photos grouped by where
 * they were taken, each group with the item it most likely shows. "Change" opens a sheet to pick another; "Add all"
 * puts each group's photos on its item, collecting it on the day they were taken if it wasn't already.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareScreen(vm: MainViewModel, onClose: () -> Unit, onAdded: (photos: Int, items: Int) -> Unit) {
    val ja = isJapanese()
    val scope = rememberCoroutineScope()
    val photos by vm.shared.collectAsState()
    val shareCount by vm.shareCount.collectAsState()
    val rows by vm.rows.collectAsState()
    val here = vm.here.collectAsState().value.fix?.let { it.lat to it.lng }

    LaunchedEffect(Unit) { vm.readShare() }
    // A gallery hides where its photos were taken unless Meguri may see the originals; offered, never asked unprompted
    val allowPlaces = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { vm.readShare() }
    val hidden = photos?.count { it.placeHidden } ?: 0

    // Many photos at once (#206): the text on each is read on the phone and suggests where it goes. A suggestion is
    // never chosen for you: one tap on its check confirms it. Photos with nothing to go on stay in the groups below
    val suggested by vm.suggested.collectAsState()
    val textRead by vm.textRead.collectAsState()
    val readsText = vm.readsShareText
    LaunchedEffect(rows.isNotEmpty()) { if (rows.isNotEmpty()) vm.readShareText() }
    val reading = readsText && suggested == null
    val textGroups = remember(photos, suggested, rows) { textGroups(photos.orEmpty(), suggested.orEmpty(), rows) }
    val confirmed = remember(textGroups) { mutableStateMapOf<Int, Boolean>() }
    // "Change" on a suggestion: the item picked instead, by group
    val instead = remember(textGroups) { mutableStateMapOf<Int, String>() }
    var changingText by remember { mutableStateOf<Int?>(null) }
    val rest = remember(photos, textGroups) { photos?.let { p -> val read = textGroups.flatMap { it.photos }.toSet(); p.filter { it !in read } } }
    val groups = remember(rest) { rest?.let { ShareGrouping.byPlace(it) }.orEmpty() }
    // The item chosen for each group, by index; seeded with the nearest suggestion once rows and groups are in
    val chosen = remember(groups) { mutableStateMapOf<Int, String?>() }
    LaunchedEffect(groups, rows.isNotEmpty()) {
        groups.forEachIndexed { i, g ->
            // Only photos that say where they were taken get a guess; where you are now is no evidence for the rest
            if (i !in chosen && g.centre != null) ShareGrouping.suggestions(rows, g.centre, limit = 1).firstOrNull()?.let { chosen[i] = it.first.item.id }
        }
    }
    var changing by remember { mutableStateOf<Int?>(null) }
    var adding by remember { mutableStateOf(false) }
    val picked = groups.indices.mapNotNull { i -> chosen[i]?.let { it to groups[i] } }
    val pickedText = textGroups.indices.filter { confirmed[it] == true }.map { i ->
        val g = textGroups[i]
        val s = instead[i]?.let { id -> g.suggestion.copy(target = PhotoSuggest.Target.Existing(id), kind = io.github.thisisartman.collectibles.data.PhotoKind.OTHER, cover = null) } ?: g.suggestion
        s to g.photos
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            windowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
            title = { Text(pluralStringResource(R.plurals.share_title, shareCount, shareCount)) },
            navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Default.Close, stringResource(R.string.cancel)) } },
        )
        if (photos == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Column
        }
        Text(
            stringResource(R.string.share_grouped), Modifier.padding(horizontal = 16.dp),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (hidden > 0) item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(pluralStringResource(R.plurals.share_hidden, hidden, hidden), style = MaterialTheme.typography.bodyMedium)
                        TextButton(onClick = { allowPlaces.launch(io.github.thisisartman.collectibles.data.SharedPhotoReader.placePermissions()) }) {
                            Text(stringResource(R.string.share_allow_places))
                        }
                    }
                }
            }
            if (reading) item {
                Column(Modifier.fillMaxWidth().padding(4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.share_reading, textRead, shareCount), style = MaterialTheme.typography.bodyMedium)
                    androidx.compose.material3.LinearProgressIndicator(
                        progress = { if (shareCount > 0) textRead.toFloat() / shareCount else 0f }, modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            if (textGroups.isNotEmpty()) item {
                Text(
                    stringResource(R.string.share_from_text), Modifier.padding(horizontal = 4.dp, vertical = 4.dp).semantics { heading() },
                    style = MaterialTheme.typography.titleSmall,
                )
            }
            itemsIndexed(textGroups) { i, g ->
                val row = (instead[i] ?: (g.suggestion.target as? PhotoSuggest.Target.Existing)?.id)?.let { id -> rows.firstOrNull { it.item.id == id } }
                SuggestionCard(
                    g, row, if (instead[i] != null) null else (g.suggestion.target as? PhotoSuggest.Target.New), ja, confirmed[i] == true,
                    onConfirm = { confirmed[i] = it }, onChange = { changingText = i },
                )
            }
            if (textGroups.isNotEmpty() && groups.isNotEmpty()) item {
                Text(
                    stringResource(R.string.share_unsorted), Modifier.padding(horizontal = 4.dp, vertical = 4.dp).semantics { heading() },
                    style = MaterialTheme.typography.titleSmall,
                )
            }
            itemsIndexed(groups) { i, g ->
                val row = chosen[i]?.let { id -> rows.firstOrNull { it.item.id == id } }
                GroupCard(g, row, ja, onChange = { changing = i })
            }
        }
        val count = picked.size + pickedText.size
        Button(
            enabled = count > 0 && !adding,
            onClick = {
                adding = true
                scope.launch {
                    val (n1, items1) = vm.addSuggested(pickedText)
                    val (n, items) = vm.addShared(picked)
                    // Leave first; the message is shown by the app, so it outlives this page
                    onClose()
                    onAdded(n + n1, items + items1)
                }
            },
            modifier = Modifier.fillMaxWidth().padding(12.dp).navigationBarsPadding().heightIn(min = 52.dp),
        ) {
            // Copying full-size photos takes a few seconds; show it is working
            if (adding) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            else Text(pluralStringResource(R.plurals.share_add_all, count, count))
        }
    }

    changingText?.let { i ->
        PickSheet(
            group = PhotoGroup(textGroups[i].photos, null), rows = rows, here = here, ja = ja,
            onPick = { id -> instead[i] = id; confirmed[i] = true; changingText = null },
            onLeaveOut = { instead.remove(i); confirmed[i] = false; changingText = null },
            onDismiss = { changingText = null },
        )
    }

    changing?.let { i ->
        PickSheet(
            group = groups[i], rows = rows, here = here, ja = ja,
            onPick = { id -> chosen[i] = id; changing = null },
            onLeaveOut = { chosen[i] = null; changing = null },
            onDismiss = { changing = null },
        )
    }
}

@Composable
private fun GroupCard(g: PhotoGroup, row: ItemRow?, ja: Boolean, onChange: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(
                groupTitle(g, row, ja) + " · " + pluralStringResource(R.plurals.share_photos, g.photos.size, g.photos.size),
                style = MaterialTheme.typography.titleSmall,
            )
            LazyRow(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(g.photos) { PhotoThumb(it.uri, 56.dp) }
            }
            if (row == null) {
                OutlinedButton(onClick = onChange, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.share_choose)) }
            } else {
                Row(
                    Modifier.fillMaxWidth().clickable(onClick = onChange).heightIn(min = 48.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Mark(row, 32.dp)
                    Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                        Text(row.item.displayName(ja), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val d = g.centre?.let { c -> row.item.lat?.let { distanceM(c.first, c.second, it, row.item.lng!!) } }
                        Text(
                            listOfNotNull(stringResource(row.item.type.label()), distanceText(d)).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = onChange) { Text(stringResource(R.string.share_change)) }
                }
            }
        }
    }
}

@Composable
private fun groupTitle(g: PhotoGroup, row: ItemRow?, ja: Boolean): String = when {
    g.centre == null -> stringResource(R.string.share_no_place)
    // The place field is Japanese; in English the item's own name reads better ("Near Yahiko Station")
    row != null -> stringResource(R.string.share_near, if (ja) row.item.placeName ?: row.item.nameJa else row.item.displayName(false))
    else -> stringResource(R.string.share_somewhere)
}

/** B inside A: the group's photos, a search, kinds, and what is near where they were taken. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PickSheet(
    group: PhotoGroup, rows: List<ItemRow>, here: Pair<Double, Double>?, ja: Boolean,
    onPick: (String) -> Unit, onLeaveOut: () -> Unit, onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf<ItemType?>(null) }
    val kinds = remember(rows) { rows.map { it.item.type }.distinct().sortedBy { it.ordinal } }
    val at = group.centre ?: here
    val shown: List<ItemRow> = remember(query, kind, rows, at) {
        if (query.isBlank()) {
            ShareGrouping.suggestions(rows, group.centre, here, types = kind?.let { setOf(it) }, limit = 12).map { it.first }
        } else {
            // The shared search: best match first, then nearest to where the photos were taken
            rankSearch(
                rows.filter { kind == null || it.item.type == kind }, query, { it.item.searchScore(query) },
                { r -> if (at == null || r.item.lat == null) null else distanceM(at.first, at.second, r.item.lat, r.item.lng!!) },
            ).take(24)
        }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 16.dp).navigationBarsPadding()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { items(group.photos) { PhotoThumb(it.uri, 64.dp) } }
            OutlinedTextField(
                query, { query = it }, Modifier.fillMaxWidth().padding(top = 10.dp), singleLine = true,
                label = { Text(stringResource(R.string.share_search)) },
            )
            LazyRow(Modifier.padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(kinds) { t ->
                    FilterChip(selected = kind == t, onClick = { kind = if (kind == t) null else t }, label = { Text(stringResource(t.label())) })
                }
            }
            Text(
                stringResource(if (query.isBlank()) (if (group.centre != null) R.string.share_near_taken else R.string.share_near_you) else R.string.share_results),
                style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (shown.isEmpty()) {
                Text(stringResource(R.string.share_nothing), Modifier.padding(vertical = 16.dp), style = MaterialTheme.typography.bodyMedium)
            } else {
                LazyVerticalGrid(GridCells.Adaptive(84.dp), Modifier.heightIn(max = 340.dp).padding(top = 6.dp)) {
                    items(shown, key = { it.item.id }) { r ->
                        Column(
                            Modifier.clickable { onPick(r.item.id) }.padding(4.dp).width(84.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Mark(r, 52.dp)
                            Text(r.item.tileLabel(ja), style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        }
                    }
                }
            }
            TextButton(onClick = onLeaveOut, modifier = Modifier.padding(vertical = 8.dp)) { Text(stringResource(R.string.share_leave_out)) }
            Spacer(Modifier.size(8.dp))
        }
    }
}

/** Shared photos whose text points at one thing (#206), and that suggestion. */
internal data class TextGroup(val suggestion: PhotoSuggest.Suggestion, val photos: List<SharedPhoto>)

/**
 * The read photos grouped by where their text points, in place order: a station stamp sits beside its Ekitag, as
 * both carry the station's name.
 */
internal fun textGroups(photos: List<SharedPhoto>, suggested: Map<String, PhotoSuggest.Suggestion>, rows: List<ItemRow>): List<TextGroup> {
    val byId = rows.associateBy { it.item.id }
    fun place(s: PhotoSuggest.Suggestion): String = when (val t = s.target) {
        is PhotoSuggest.Target.Existing -> byId[t.id]?.item?.nameJa ?: t.id
        is PhotoSuggest.Target.New -> t.name
    }.removeSuffix("駅")
    return photos.mapNotNull { p -> suggested[p.uri]?.let { it to p } }
        .groupBy({ it.first.target }, { it })
        .map { (_, list) -> TextGroup(list.first().first, list.map { it.second }) }
        .sortedWith(compareBy({ place(it.suggestion) }, { it.suggestion.type.ordinal }))
}

/**
 * One suggestion read off the photos: what it is and its photos, unconfirmed until its check is tapped (#206).
 * [new] is set when it would make a new item of your own. "Change" picks another item.
 */
@Composable
private fun SuggestionCard(
    g: TextGroup, row: ItemRow?, new: PhotoSuggest.Target.New?, ja: Boolean, confirmed: Boolean,
    onConfirm: (Boolean) -> Unit, onChange: () -> Unit,
) {
    val name = row?.item?.displayName(ja) ?: new?.name.orEmpty()
    val type = stringResource((row?.item?.type ?: g.suggestion.type).label())
    val use = stringResource(R.string.share_use, name)
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            LazyRow(Modifier.padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(g.photos) { PhotoThumb(it.uri, 56.dp) }
            }
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                if (row != null) Mark(row, 32.dp) else TypeIcon(g.suggestion.type, 32.dp)
                Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                    Text(name, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOfNotNull(type, if (new != null) stringResource(R.string.share_new) else null, pluralStringResource(R.plurals.share_photos, g.photos.size, g.photos.size)).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = onChange) { Text(stringResource(R.string.share_change)) }
                androidx.compose.material3.FilledTonalIconToggleButton(
                    checked = confirmed, onCheckedChange = onConfirm,
                    modifier = Modifier.semantics { contentDescription = use },
                ) { Icon(Icons.Default.Check, contentDescription = null) }
            }
        }
    }
}
