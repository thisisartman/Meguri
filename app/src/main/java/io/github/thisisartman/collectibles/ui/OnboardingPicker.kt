package io.github.thisisartman.collectibles.ui

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.github.thisisartman.collectibles.R
import io.github.thisisartman.collectibles.data.ItemRow

/**
 * "What do you already have?" (#81, owner 2026-11-07): search, a 4-column grid of places, and a card beside the tapped
 * place with a checkbox for each thing there. After each pick the search text is selected in full, so one backspace
 * clears it for the next name (as in WhatsApp's forward picker). Added things stack as circles above Done.
 */
@Composable
fun OnboardingPicker(
    state: OnboardingState,
    rows: List<ItemRow>,
    distance: (ItemRow) -> Double?,
    onDone: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
    origin: Any? = null,
) {
    val ja = isJapanese()
    val tick = rememberTick()
    var query by remember { mutableStateOf(TextFieldValue("")) }
    var nearest by remember { mutableStateOf(false) }
    var open by remember { mutableStateOf<String?>(null) }
    val tiles = remember { mutableStateMapOf<String, Rect>() }
    val places = remember(rows) { pickerPlaces(rows.filter { !it.collected }) }
    val shown = remember(places, query.text, nearest, if (nearest) origin else null) {
        val q = query.text.trim()
        // The shared search; while typing, the best match comes first (ties keep A to Z or nearest)
        fun score(p: PickerPlace) = p.rows.maxOf { r -> r.item.searchScore(q) }
        val hits = if (q.isEmpty()) places else places.filter { score(it) > 0 }
        val sorted = sortPlaces(hits, ja, nearest, distance)
        if (q.isEmpty()) sorted else sorted.sortedByDescending { score(it) }
    }
    // Back to the top whenever the order or the search changes. The grid otherwise keeps the old first tile in view:
    // switching to Nearest kept Aburumagawa Dam (first A to Z) on screen, now 30 km down the list, and Urasa (2.5 km,
    // first) sat above it out of sight (owner's S23 run, 2026-11-10; emulator log showed the list itself was right)
    val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
    androidx.compose.runtime.LaunchedEffect(nearest, query.text, if (nearest) origin else null) { gridState.scrollToItem(0) }
    val byId = remember(rows) { rows.associateBy { it.item.id } }
    androidx.compose.runtime.LaunchedEffect(shown) { state.pickerShown = shown.map { it.first } }
    // Back closes an open card; with none open, Nearby's handler keeps you in the picker (picks aren't lost)
    androidx.activity.compose.BackHandler(enabled = open != null) { open = null }
    // After a pick the keyboard stays up, so one backspace clears the selected text for the next name (owner)
    val searchFocus = remember { androidx.compose.ui.focus.FocusRequester() }
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val focus = androidx.compose.ui.platform.LocalFocusManager.current

    // Above the keyboard while typing, so Done stays in reach (owner's S23 run, 2026-11-10)
    Column(modifier.fillMaxWidth().imePadding()) {
        Text(stringResource(R.string.onb_have_title), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.onb_have_sub), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = query, onValueChange = { query = it; open = null },
                modifier = Modifier.weight(1f).focusRequester(searchFocus), singleLine = true,
                placeholder = { Text(stringResource(R.string.onb_have_search)) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                shape = RoundedCornerShape(24.dp),
            )
            Spacer(Modifier.width(8.dp))
            FilterChip(
                selected = nearest, onClick = { nearest = !nearest },
                label = { Text(stringResource(if (nearest) R.string.nearby_sort_near else R.string.nearby_sort_az)) },
                leadingIcon = { Icon(painterResource(if (nearest) R.drawable.ic_near_me else R.drawable.ic_sort_by_alpha), contentDescription = null, modifier = Modifier.size(18.dp)) },
            )
        }
        Spacer(Modifier.height(8.dp))
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val boxW = with(LocalDensity.current) { maxWidth.toPx() }
            LazyVerticalGrid(GridCells.Fixed(4), Modifier.fillMaxSize(), state = gridState) {
                items(shown, key = { it.key }) { p ->
                    val name = p.first.item.displayName(ja)
                    val done = p.rows.all { it.item.id in state.added }
                    Column(
                        Modifier.onGloballyPositioned { tiles[p.key] = it.boundsInParent() }
                            // Tapping a place puts the keyboard away, so its card and the grid show in full
                            .clickable { tick(); focus.clearFocus(); keyboard?.hide(); open = if (open == p.key) null else p.key }
                            .padding(vertical = 6.dp, horizontal = 2.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box {
                            Mark(p.first, 56.dp)
                            if (done) Surface(Modifier.align(Alignment.BottomEnd).size(20.dp), shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.primary) {
                                Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.padding(2.dp))
                            }
                        }
                        Text(
                            tileName(name), style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center,
                            maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = if (open == p.key) FontWeight.Bold else null,
                        )
                    }
                }
            }
            // The card next to the tapped place (owner: above, below, left or right, by where it sits): the outer
            // columns open beside the tile, toward the middle; the middle columns open below a tile in the upper half
            // and above one in the lower half
            val place = shown.firstOrNull { it.key == open }
            val at = open?.let { tiles[it] }
            val boxH = with(LocalDensity.current) { maxHeight.toPx() }
            var cardH by remember { mutableStateOf(0) }
            if (place != null && at != null) {
                val cardW = with(LocalDensity.current) { 220.dp.toPx() }
                val col = (at.center.x / (boxW / 4)).toInt().coerceIn(0, 3)
                val (x, y) = when (col) {
                    0 -> at.right to at.top
                    3 -> at.left - cardW to at.top
                    else -> (at.center.x - cardW / 2) to (if (at.center.y < boxH / 2) at.bottom else at.top - cardH)
                }
                PlaceCard(
                    place, state.added, ja,
                    modifier = Modifier
                        .offset { IntOffset(x.toInt().coerceIn(0, (boxW - cardW).toInt().coerceAtLeast(0)), y.toInt().coerceIn(0, (boxH - cardH).toInt().coerceAtLeast(0))) }
                        .widthIn(max = 220.dp)
                        .onSizeChanged { cardH = it.height },
                    onOk = { ids ->
                        state.added = state.added.filter { it !in place.rows.map { r -> r.item.id } } + ids
                        open = null
                        tick()
                        // Clear the search and put the keyboard away, so the grid and Done are in view (owner's S23
                        // run, 2026-11-10: the keyboard covered half the grid and Done, and the old text stayed;
                        // replaces the earlier keep-the-keyboard-up rule)
                        query = TextFieldValue("")
                        focus.clearFocus()
                        keyboard?.hide()
                    },
                )
            }
        }
        // Added so far: circles stacked over each other, newest on top (owner: a visual count, not a list)
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            LazyRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy((-14).dp)) {
                // Filled in the kind's colour: they are only marked on Done, so Mark would still draw an empty ring
                items(state.added.takeLast(14)) { id ->
                    byId[id]?.let { r ->
                        Surface(Modifier.size(34.dp), shape = RoundedCornerShape(17.dp), color = LocalTypeColors.current.of(r.item.type), shadowElevation = 2.dp) {
                            Box(contentAlignment = Alignment.Center) { Icon(painterResource(iconOf(r.item)), contentDescription = null, tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(18.dp)) }
                        }
                    }
                }
            }
            // Only the count: the last item's name beside it changed length and cut-off with every pick (owner's S23
            // run, 2026-11-10); the circles already say what kinds were added
            Text(pluralStringResource(R.plurals.onb_added, state.added.size, state.added.size), style = MaterialTheme.typography.labelLarge)
        }
        Button(onClick = { onDone(state.added) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text(stringResource(if (state.added.isEmpty()) R.string.onb_have_skip else R.string.onb_have_done))
        }
    }
}

@Composable
private fun PlaceCard(place: PickerPlace, added: List<String>, ja: Boolean, modifier: Modifier, onOk: (List<String>) -> Unit) {
    val checked = remember(place.key) { mutableStateMapOf<String, Boolean>().apply { place.rows.forEach { put(it.item.id, it.item.id in added || place.rows.size == 1) } } }
    Surface(modifier, shape = RoundedCornerShape(16.dp), shadowElevation = 8.dp, tonalElevation = 2.dp) {
        Column(Modifier.padding(12.dp)) {
            Text(place.first.item.displayName(ja), style = MaterialTheme.typography.titleSmall, maxLines = 2)
            place.rows.forEach { r ->
                Row(Modifier.fillMaxWidth().clickable { checked[r.item.id] = checked[r.item.id] != true }, verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = checked[r.item.id] == true, onCheckedChange = { checked[r.item.id] = it })
                    ItemTypeIcon(r.item, 18.dp)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(r.item.type.label()), style = MaterialTheme.typography.bodyMedium)
                }
            }
            TextButton(onClick = { onOk(checked.filterValues { it }.keys.toList()) }, modifier = Modifier.align(Alignment.End).heightIn(min = 48.dp)) {
                Text(stringResource(R.string.onb_ok))
            }
        }
    }
}

/** The last sheet: how many were added, and where to add photos (#81). */
@Composable
fun OnboardingFinished(count: Int, onProfile: () -> Unit, onDone: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
        Text(pluralStringResource(R.plurals.onb_finished_title, count, count), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.onb_finished_how), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(12.dp))
            // A picture of the My book tab, bottom right
            Icon(Icons.AutoMirrored.Filled.List, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp))
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onDone, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text(stringResource(R.string.onb_finished_done)) }
            Button(onClick = onProfile, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text(stringResource(R.string.onb_finished_profile)) }
        }
    }
}

/** A vibration was given by the caller; this shows the toast the owner asked for. */
fun toastAdded(context: android.content.Context, count: Int) {
    Toast.makeText(context, context.resources.getQuantityString(R.plurals.onb_finished_title, count, count), Toast.LENGTH_SHORT).show()
}
