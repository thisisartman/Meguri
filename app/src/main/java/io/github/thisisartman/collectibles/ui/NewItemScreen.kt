package io.github.thisisartman.collectibles.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.thisisartman.collectibles.R
import io.github.thisisartman.collectibles.data.ItemType
import io.github.thisisartman.collectibles.data.PlaceHit
import kotlinx.coroutines.launch

/** A kind offered in the form: one of Meguri's own, or one you made. */
private data class KindOption(val label: String, val category: Category, val type: ItemType, val customId: String? = null)

/** The kinds you can add by hand (Ekitags come from the station, so they are not offered). */
private val BUILT_IN = listOf(ItemType.MANHOLE_CARD, ItemType.DAM_CARD, ItemType.GOSHUIN, ItemType.EKI_STAMP, ItemType.MUSEUM_STAMP, ItemType.ROADSIDE_STATION)

/**
 * Add (owner, 2026-11-09): an empty form, like a new contact. A name, card or stamp, and its kind: pick one, or type
 * a new one and it becomes a kind of its own (a museum stamp, a kind of card Meguri doesn't know), with its own tile
 * in My book. Where and what you paid are optional.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NewItemScreen(vm: MainViewModel, onBack: () -> Unit, onSaved: (itemId: String, findId: String, withPhoto: Boolean) -> Unit) {
    val customTypes by vm.collectionTypes.collectAsStateWithLifecycle()
    val ja = isJapanese()
    var name by rememberSaveable { mutableStateOf("") }
    var otherName by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf<Category?>(null) }
    var kindText by rememberSaveable { mutableStateOf("") }
    var cost by rememberSaveable { mutableStateOf("") }
    var place by remember { mutableStateOf<PlaceHit?>(null) }
    var hits by remember { mutableStateOf<List<PlaceHit>?>(null) }
    var searching by remember { mutableStateOf(false) }
    var placeQuery by rememberSaveable { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val options = BUILT_IN.map { KindOption(stringResource(it.label()), it.category(), it) } +
        customTypes.map { KindOption(it.name, it.category(), ItemType.CUSTOM, it.id) }
    val typed = kindText.trim()
    val exact = options.firstOrNull { it.label.equals(typed, ignoreCase = true) }
    // The shared forgiving search, best match first ("manhol" or "マンホール" finds Manhole card)
    val shown = options.filter { o -> category == null || o.category == category }.let { opts ->
        if (typed.isEmpty()) opts else rankSearch(opts, typed, { fuzzyScore(typed, listOf(it.label)) }, { null })
    }
    val newKind = typed.isNotEmpty() && exact == null
    // A kind typed here gets "Note what I paid", on by default as in New category (finding 37)
    var newKindCost by rememberSaveable { mutableStateOf(true) }
    val hasCost = when {
        exact == null -> newKind && newKindCost
        exact.type == ItemType.GOSHUIN -> true
        exact.customId != null -> customTypes.firstOrNull { it.id == exact.customId }?.hasCost == true
        else -> false
    }
    val ok = name.isNotBlank() && (exact != null || (newKind && category != null)) && !saving

    fun save(withPhoto: Boolean) {
        saving = true
        val latin = name.all { it.code < 0x3000 }
        val nameJa = if (latin) otherName.trim().ifBlank { name.trim() } else name.trim()
        val romaji = if (latin) name.trim() else otherName.trim().ifBlank { null }
        val yen = cost.toIntOrNull()?.takeIf { hasCost }
        val done = { itemId: String, findId: String -> onSaved(itemId, findId, withPhoto) }
        if (exact != null) {
            vm.addNew(exact.type, nameJa, romaji, exact.customId, place, yen, done)
        } else {
            // A kind you typed: made once, then used like any other (a colour not yet taken, the card or stamp icon)
            val cat = category ?: Category.OTHER
            val color = CUSTOM_COLORS.firstOrNull { c -> customTypes.none { it.color == c } } ?: CUSTOM_COLORS[customTypes.size % CUSTOM_COLORS.size]
            vm.addCollectionType(typed, color, cat.icon(), hasCost = newKindCost) { id -> vm.addNew(ItemType.CUSTOM, nameJa, romaji, id, place, yen, done) }
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            windowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
            title = { Text(stringResource(R.string.new_title)) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
        )
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = name, onValueChange = { name = it.take(80) }, singleLine = true,
                label = { Text(stringResource(R.string.add_name)) }, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
            )
            OutlinedTextField(
                value = otherName, onValueChange = { otherName = it.take(80) }, singleLine = true,
                label = { Text(stringResource(R.string.add_other_name)) }, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
            )

            Text(stringResource(R.string.add_category), style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(Category.CARD to R.string.add_cat_card, Category.STAMP to R.string.add_cat_stamp, Category.OTHER to R.string.add_cat_other).forEach { (c, label) ->
                    InkFilterChip(
                        selected = category == c,
                        onClick = { category = if (category == c) null else c },
                        label = { Text(stringResource(label)) },
                        leadingIcon = { Icon(painterResource(customIconRes(c.icon())), contentDescription = null, modifier = Modifier.size(18.dp)) },
                    )
                }
            }

            Text(stringResource(R.string.add_kind), style = MaterialTheme.typography.labelLarge)
            OutlinedTextField(
                value = kindText, onValueChange = { kindText = it.take(40) }, singleLine = true,
                label = { Text(stringResource(R.string.add_kind_hint)) }, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                shown.forEach { o ->
                    InkFilterChip(
                        selected = exact == o,
                        onClick = { kindText = o.label; if (category == null) category = o.category },
                        label = { Text(o.label, maxLines = 1) },
                        leadingIcon = {
                            if (o.customId == null) TypeDot(o.type)
                            else customTypes.firstOrNull { it.id == o.customId }?.let { CustomDot(it) }
                        },
                    )
                }
                if (newKind) {
                    // A label, not a chip: it only shows what will be made, so it must not look tappable
                    androidx.compose.material3.Surface(
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    ) {
                        Row(Modifier.heightIn(min = 32.dp).padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(painterResource(customIconRes((category ?: Category.OTHER).icon())), contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.add_new_kind, typed), maxLines = 1, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
            if (newKind) {
                Text(
                    stringResource(if (category == null) R.string.add_new_kind_pick else R.string.add_new_kind_note),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // One switch for the whole row, read with its words (as in New category)
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        .toggleable(value = newKindCost, role = androidx.compose.ui.semantics.Role.Switch) { newKindCost = it },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.collection_has_cost), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    androidx.compose.material3.Switch(checked = newKindCost, onCheckedChange = null)
                }
            }

            // Where: your position by default, or a place found on OpenStreetMap (online)
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
                val runSearch = {
                    val q = placeQuery.ifBlank { name }
                    if (q.isNotBlank() && !searching) {
                        searching = true
                        scope.launch { hits = vm.lookUpPlace(q, ja); searching = false }
                    }
                }
                OutlinedTextField(
                    value = placeQuery, onValueChange = { placeQuery = it }, singleLine = true,
                    label = { Text(stringResource(R.string.new_find_place)) },
                    placeholder = { if (name.isNotBlank()) Text(name, maxLines = 1) },
                    trailingIcon = {
                        IconButton(onClick = { runSearch() }, enabled = !searching && (placeQuery.isNotBlank() || name.isNotBlank())) {
                            Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.new_find_place))
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { runSearch() }),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (searching) Text(stringResource(R.string.new_finding_place), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                hits?.let { list ->
                    if (list.isEmpty()) Text(stringResource(R.string.new_no_place), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    list.forEach { h ->
                        Column(
                            Modifier.fillMaxWidth().clickable {
                                place = h; hits = null
                                // Fill only an empty name, so nothing you typed is overwritten
                                if (name.isBlank()) name = (if (ja) h.nameJa else h.nameEn) ?: h.name
                            }.heightIn(min = 48.dp).padding(vertical = 6.dp),
                        ) {
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
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
            if (name.isBlank()) {
                Text(stringResource(R.string.new_needs_name), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.heightIn(min = 8.dp))
        }
        // Saving collects it today, here; "Save and add photo" opens the camera straight after
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            InkOutlinedButton(onClick = { save(true) }, enabled = ok, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                Icon(painterResource(R.drawable.ic_add_photo), contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.add_save_photo), maxLines = 1)
            }
            Button(onClick = { save(false) }, enabled = ok, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                Text(stringResource(R.string.new_save), maxLines = 1)
            }
        }
    }
}
