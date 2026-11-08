package io.github.thisisartman.collectibles.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.thisisartman.collectibles.R
import io.github.thisisartman.collectibles.data.CollectionType
import io.github.thisisartman.collectibles.data.Item
import io.github.thisisartman.collectibles.data.ItemType

/** Your own collections by id, so any screen can draw an item in its collection's colour and icon. */
val LocalCustomTypes = compositionLocalOf<Map<String, CollectionType>> { emptyMap() }

/** Colours offered for a new collection: readable on both Meguri backgrounds, apart from the built-in kinds. */
val CUSTOM_COLORS = listOf(0xFF7A5AA8, 0xFF2F6F8F, 0xFFA8611F, 0xFF4F7A2E, 0xFFA83A6E, 0xFF5A6470)

/** Icons offered for a new collection (CC0: Mapbox Maki and Temaki; Material for the generic one). */
val CUSTOM_ICONS = listOf("collect", "card", "stamp", "museum", "dam", "station", "goshuin", "manhole", "ekitag")

/**
 * Card, stamp or something else (owner, 2026-11-09: a new item is first a card or a stamp, then its kind). Built-in
 * kinds have a fixed one; your own kinds take it from their icon, so no database change is needed.
 */
enum class Category { CARD, STAMP, OTHER }

fun ItemType.category(): Category = when (this) {
    ItemType.MANHOLE_CARD, ItemType.DAM_CARD -> Category.CARD
    ItemType.EKI_STAMP, ItemType.GOSHUIN, ItemType.MUSEUM_STAMP, ItemType.ROADSIDE_STATION, ItemType.EKITAG -> Category.STAMP
    ItemType.CUSTOM -> Category.OTHER
}

fun CollectionType.category(): Category = when (icon) {
    "card", "manhole", "dam" -> Category.CARD
    "stamp", "museum", "station", "goshuin", "ekitag" -> Category.STAMP
    else -> Category.OTHER
}

/** The icon a kind you type in gets: a card, a stamp, or the general one. */
fun Category.icon(): String = when (this) { Category.CARD -> "card"; Category.STAMP -> "stamp"; Category.OTHER -> "collect" }

@androidx.annotation.DrawableRes
fun customIconRes(key: String): Int = when (key) {
    "card" -> R.drawable.ic_type_card
    "stamp" -> R.drawable.ic_type_stamp
    "museum" -> R.drawable.ic_type_museum
    "dam" -> R.drawable.ic_type_dam
    "station" -> R.drawable.ic_type_station
    "goshuin" -> R.drawable.ic_type_goshuin
    "manhole" -> R.drawable.ic_type_manhole
    "ekitag" -> R.drawable.ic_type_ekitag
    else -> R.drawable.ic_collect
}

/** What a screen reader says for each icon choice. */
@androidx.annotation.StringRes
private fun iconLabel(key: String): Int = when (key) {
    "card" -> R.string.add_cat_card
    "stamp" -> R.string.add_cat_stamp
    "museum" -> R.string.type_museum
    "dam" -> R.string.type_dam
    "station" -> R.string.type_eki
    "goshuin" -> R.string.type_goshuin
    "manhole" -> R.string.type_manhole
    "ekitag" -> R.string.type_ekitag
    else -> R.string.type_custom
}

/** The colour an item is drawn in: its kind's, or its own collection's. */
@Composable
fun colorOf(item: Item): Color {
    val custom = item.customTypeId?.let { LocalCustomTypes.current[it] }
    return if (item.type == ItemType.CUSTOM && custom != null) Color(custom.color) else LocalTypeColors.current.of(item.type)
}

/** The icon an item is drawn with: its kind's, or its own collection's. */
@Composable
fun iconOf(item: Item): Int {
    val custom = item.customTypeId?.let { LocalCustomTypes.current[it] }
    return when {
        item.type == ItemType.CUSTOM && custom != null -> customIconRes(custom.icon)
        item.type == ItemType.GOSHUIN -> goshuinIcon(item.nameJa)
        else -> typeIcon(item.type)
    }
}

/** Whether a goshuin's place is a shrine or a temple, from its name; null when the name does not say. */
fun shrineOrTemple(nameJa: String): Boolean? = when {
    listOf("神社", "大社", "神宮", "八幡宮", "天満宮", "稲荷", "宮").any { it in nameJa } -> true
    listOf("寺", "院", "不動", "観音", "大師", "坊").any { it in nameJa } -> false
    else -> null
}

/**
 * The GSI map symbols for goshuin (#53, owner 2026-11-07: official icons): the torii for a shrine, and for a temple
 * what [TEMPLE_SYMBOL] says (the official 卍 can be misread outside Japan, so the owner is asked).
 */
@androidx.annotation.DrawableRes
fun goshuinIcon(nameJa: String): Int = when (shrineOrTemple(nameJa)) {
    true -> R.drawable.ic_type_shrine
    false -> if (TEMPLE_SYMBOL) R.drawable.ic_type_temple else R.drawable.ic_type_goshuin
    null -> R.drawable.ic_type_goshuin
}

/** The official 卍 for temples; false keeps the goshuin icon (Telegram meguri-53-temple). */
const val TEMPLE_SYMBOL = true

/** The name of an item's kind: the built-in kind, or its own collection's name. */
@Composable
fun kindName(item: Item): String {
    val custom = item.customTypeId?.let { LocalCustomTypes.current[it] }
    return if (item.type == ItemType.CUSTOM && custom != null) custom.name else stringResource(item.type.label())
}

@Composable
fun CustomDot(t: CollectionType, size: Dp = 8.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(Color(t.color)))
}

/** A small icon in an item's own colour (its kind, or its own collection). */
@Composable
fun ItemTypeIcon(item: Item, size: Dp = 20.dp, modifier: Modifier = Modifier) {
    Icon(painterResource(iconOf(item)), contentDescription = null, tint = colorOf(item), modifier = modifier.size(size))
}

/** Make a collection of your own (#64): a name, a colour, an icon, and whether you note what you paid. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NewCollectionDialog(
    onDismiss: () -> Unit,
    existingNames: Set<String> = emptySet(),
    initial: CollectionType? = null,
    itemCount: Int = 0,
    onRemove: (() -> Unit)? = null,
    onSave: (name: String, color: Long, icon: String, hasCost: Boolean) -> Unit,
) {
    // With [initial], the same dialog edits a category (Settings > Categories, owner 2026-11-09)
    var name by rememberSaveable { mutableStateOf(initial?.name ?: "") }
    var color by rememberSaveable { mutableStateOf(initial?.color ?: CUSTOM_COLORS.first()) }
    var icon by rememberSaveable { mutableStateOf(initial?.icon ?: CUSTOM_ICONS.first()) }
    var hasCost by rememberSaveable { mutableStateOf(initial?.hasCost ?: true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (initial == null) R.string.collection_new_title else R.string.cat_edit_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val taken = name.trim().lowercase() in existingNames.map { it.trim().lowercase() }
                OutlinedTextField(
                    value = name, onValueChange = { name = it.take(40) }, label = { Text(stringResource(R.string.collection_name)) }, singleLine = true,
                    isError = taken,
                    supportingText = if (taken) ({ Text(stringResource(R.string.collection_name_taken)) }) else null,
                )
                Text(stringResource(R.string.collection_color), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    CUSTOM_COLORS.forEachIndexed { i, c ->
                        val label = stringResource(R.string.a11y_color, i + 1)
                        // 48 dp to tap, the 40 dp swatch inside; read out as "Colour 1, selected"
                        Box(
                            Modifier.size(48.dp).selectable(selected = c == color, role = Role.RadioButton) { color = c }
                                .semantics { contentDescription = label },
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(Modifier.size(40.dp).clip(CircleShape).background(Color(c))
                                .border(3.dp, if (c == color) MaterialTheme.colorScheme.onSurface else Color.Transparent, CircleShape))
                        }
                    }
                }
                Text(stringResource(R.string.collection_icon), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    CUSTOM_ICONS.forEach { k ->
                        val label = stringResource(iconLabel(k))
                        Box(
                            Modifier.size(48.dp).clip(CircleShape)
                                .background(if (k == icon) Color(color).copy(alpha = 0.18f) else Color.Transparent)
                                .selectable(selected = k == icon, role = Role.RadioButton) { icon = k }
                                .semantics { contentDescription = label },
                            contentAlignment = Alignment.Center,
                        ) { Icon(painterResource(customIconRes(k)), contentDescription = null, tint = Color(color), modifier = Modifier.size(26.dp)) }
                    }
                }
                // One switch for the whole row, read with its words
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(value = hasCost, role = Role.Switch) { hasCost = it },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.collection_has_cost), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = hasCost, onCheckedChange = null)
                }
                if (initial != null && itemCount > 0) Text(
                    androidx.compose.ui.res.pluralStringResource(R.plurals.cat_remove_blocked, itemCount, itemCount),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            val taken = name.trim().lowercase() in existingNames.map { it.trim().lowercase() }
            TextButton(enabled = name.isNotBlank() && !taken, onClick = { onSave(name, color, icon, hasCost) }) { Text(stringResource(R.string.cat_save)) }
        },
        dismissButton = {
            Row {
                if (onRemove != null && itemCount == 0) TextButton(onClick = onRemove) { Text(stringResource(R.string.cat_remove), color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        },
    )
}

/** Card, stamp or other, as a word (Settings > Categories). */
@androidx.annotation.StringRes
fun Category.label(): Int = when (this) { Category.CARD -> R.string.add_cat_card; Category.STAMP -> R.string.add_cat_stamp; Category.OTHER -> R.string.add_cat_other }

/** Settings: your own collections, and making a new one. */
@Composable
fun MyCollectionsSection(types: List<CollectionType>, itemCounts: Map<String, Int>, onNew: () -> Unit) {
    types.forEach { t ->
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(customIconRes(t.icon)), contentDescription = null, tint = Color(t.color), modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(14.dp))
            Text(t.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Text(
                androidx.compose.ui.res.pluralStringResource(R.plurals.items, itemCounts[t.id] ?: 0, itemCounts[t.id] ?: 0),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    TextButton(onClick = onNew, modifier = Modifier.padding(horizontal = 8.dp).heightIn(min = 48.dp)) {
        Icon(painterResource(R.drawable.ic_collect), contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.collection_new))
    }
}
