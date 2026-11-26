package io.github.thisisartman.collectibles.ui

import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Add
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.res.painterResource
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.thisisartman.collectibles.R
import io.github.thisisartman.collectibles.data.FindWithPhotos
import io.github.thisisartman.collectibles.data.Item
import io.github.thisisartman.collectibles.data.ItemRow
import io.github.thisisartman.collectibles.data.ItemType

/**
 * A thing's lines under its title in its card on the place page (the old item page's header, #192): a card's place
 * and code, the other-language name, code, prefecture and town, and the wave with its release date.
 */
@Composable
internal fun ThingNameLines(item: Item, ja: Boolean) {
    // A manhole card: its design as the title, the place and code under it
    val (title, subtitle) = item.cardTitle(ja)
    subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
    // A card's Japanese name without the code that is already under the title (#149)
    item.secondaryName(ja)?.let { n -> item.code?.substringAfterLast('-')?.takeIf { item.type == ItemType.MANHOLE_CARD }?.let { n.removeSuffix(" $it").trim() } ?: n }
        ?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    // English mode: prefecture in romaji; the town has no romaji in the catalogue and is already in the name, so it is left out
    val sub = listOfNotNull(
        // The code is already in the title or subtitle for a card; showing it again read as a mistake (2026-11-13)
        item.code?.takeIf { c -> title.contains(c) != true && subtitle?.contains(c) != true },
        item.prefecture?.let { prefectureName(it, ja) },
        item.municipality?.takeIf { ja && it != item.nameJa },
    ).joinToString(sep())
    if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (item.series != null) {
        val date = item.releaseDate?.takeIf { it.isNotBlank() }?.let { if (ja) it else it.replace('/', '-') }
        Text(if (date != null) stringResource(R.string.item_wave, item.series, date) else stringResource(R.string.item_wave_only, item.series), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** The map for this item's area is not on the phone (outside the offline pack), so it needs internet there. */
private fun offlineMissing(context: android.content.Context, item: Item): Boolean =
    item.lat != null && item.lng != null && !OfflineMap.covers(context, item.lat, item.lng)

/** Whether "Where to get it" has anything to show; a goshuin added by hand often has nothing yet. */
internal fun hasFacts(context: android.content.Context, item: Item): Boolean =
    listOfNotNull(item.hoursText, item.closedDays, item.placeName?.takeIf { it != item.nameJa }, item.address, item.notes, item.placePhone).any { it.isNotBlank() } ||
        offlineMissing(context, item)

/**
 * "Where to get it" as one grouped block of one-line icon rows (map-app study, 2026-11-07: Organic Maps' rows are
 * about 48 dp), in the thing's card on the place page (#192): hours, place and address, phone (tap to call), notes,
 * and a plain line when the map for this spot is not saved on the phone.
 */
@Composable
internal fun Facts(item: Item, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val placeLabel = placeLabel(item.type)
    val hoursLabel = when (item.type) {
        ItemType.GOSHUIN -> R.string.item_office_hours
        ItemType.EKI_STAMP, ItemType.EKITAG -> R.string.item_stamp_location
        else -> R.string.item_hours
    }
    androidx.compose.material3.Surface(
        modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.padding(vertical = 4.dp)) {
            // Hours first: on a trip day the question is whether it is open
            FactRow(painterResource(R.drawable.ic_schedule), stringResource(hoursLabel), listOfNotNull(item.hoursText, item.closedDays).joinToString("\n"), status = openLine(item))
            // A station's place is the station itself: repeating its Japanese name adds nothing (and breaks English mode)
            FactRow(androidx.compose.ui.graphics.vector.rememberVectorPainter(Icons.Filled.Place), stringResource(placeLabel), listOfNotNull(item.placeName?.takeIf { it != item.nameJa }, item.address).joinToString("\n"))
            item.placePhone?.takeIf { it.isNotBlank() }?.let { phone ->
                FactRow(androidx.compose.ui.graphics.vector.rememberVectorPainter(Icons.Filled.Call), stringResource(R.string.item_call), phone) {
                    // Opens the dialer with the number filled in; no call permission needed
                    runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + phone.filter { it.isDigit() || it == '+' }))) }
                }
            }
            item.notes?.let { FactRow(androidx.compose.ui.graphics.vector.rememberVectorPainter(Icons.Filled.Info), "", localNote(it, isJapanese())) }
            if (offlineMissing(LocalContext.current, item)) {
                FactRow(androidx.compose.ui.graphics.vector.rememberVectorPainter(Icons.Filled.Info), "", stringResource(R.string.item_map_not_saved))
            }
        }
    }
}

/** The name of the place an item is handed out at, the same on its page and in Edit (#144). */
fun placeLabel(type: ItemType): Int = when (type) {
    ItemType.MANHOLE_CARD, ItemType.DAM_CARD -> R.string.item_handed_out_at
    ItemType.GOSHUIN -> R.string.item_office
    ItemType.EKI_STAMP, ItemType.EKITAG -> R.string.item_station
    ItemType.CUSTOM, ItemType.MUSEUM_STAMP, ItemType.ROADSIDE_STATION, ItemType.PLACE -> R.string.item_place
}

@Composable
private fun FactRow(icon: androidx.compose.ui.graphics.painter.Painter, label: String, value: String, status: OpenLine? = null, onClick: (() -> Unit)? = null) {
    if (value.isBlank()) return
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = if (onClick != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            if (label.isNotEmpty()) Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            // Open now or closed, read from the hours text (0.5); left out when the text can't be read with confidence
            status?.let { Text(it.text, style = MaterialTheme.typography.labelLarge, color = if (it.closed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary) }
            Text(value, style = MaterialTheme.typography.bodyMedium, color = if (onClick != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
        }
    }
}

/** How long after a find its missing position counts as "still being found". */
private const val POSITION_WAIT_MS = 2 * 60 * 1000L

/**
 * One time you collected the item, as a single line (owner, 2026-11-07: no "had before the app", no "record"):
 * the date; Map when a position was saved; a delete icon that asks first.
 */
@Composable
internal fun CollectionRow(
    f: FindWithPhotos, ja: Boolean, name: String, onRemoveFind: () -> Unit,
    hasCost: Boolean = false, onQuantity: (Int) -> Unit = {}, onCost: (Int?) -> Unit = {},
) {
    val a = f.find
    // Tap the line to change how many (duplicates) and, for goshuin and your own collections that note it, what you paid
    var editing by remember { mutableStateOf(false) }
    if (editing) {
        var qty by remember { mutableStateOf(a.quantity) }
        var cost by remember { mutableStateOf(a.costYen?.toString().orEmpty()) }
        AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text(stringResource(R.string.find_edit_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.find_quantity), style = MaterialTheme.typography.labelLarge)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { qty = (qty - 1).coerceAtLeast(1) }, enabled = qty > 1) {
                            Icon(painterResource(R.drawable.ic_remove), contentDescription = stringResource(R.string.a11y_less))
                        }
                        Text("$qty", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(horizontal = 16.dp))
                        IconButton(onClick = { qty = (qty + 1).coerceAtMost(99) }) {
                            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.a11y_more))
                        }
                    }
                    if (hasCost) {
                        androidx.compose.material3.OutlinedTextField(
                            value = cost, onValueChange = { v -> cost = v.filter { it.isDigit() }.take(7) },
                            label = { Text(stringResource(R.string.find_cost)) }, singleLine = true, prefix = { Text("¥") },
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (qty != a.quantity) onQuantity(qty)
                    val yen = cost.toIntOrNull()
                    if (hasCost && yen != a.costYen) onCost(yen)
                    editing = false
                }) { Text(stringResource(R.string.photo_done)) }
            },
            dismissButton = { TextButton(onClick = { editing = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    // Every find has a date (#194); one marked without a place (Already went, ticked off, a notebook page) is a day,
    // with no time
    val whenText = (a.acquiredAt ?: a.createdAt).let { if (a.via == io.github.thisisartman.collectibles.data.Via.BULK) dateText(it) else dateTimeText(it) }
    var confirmRemove by remember { mutableStateOf(false) }
    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text(stringResource(R.string.item_remove_find_title)) },
            text = { Text(stringResource(R.string.item_remove_find_text, whenText)) },
            confirmButton = {
                TextButton(onClick = { confirmRemove = false; onRemoveFind() }) {
                    Text(stringResource(R.string.item_remove_find), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    // The position is filled in after the collection is saved; for a while it is pending, not missing
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(a.id, a.lat) {
        while (a.via != io.github.thisisartman.collectibles.data.Via.BULK && a.lat == null && now - (a.acquiredAt ?: 0L) < POSITION_WAIT_MS) { delay(5_000); now = System.currentTimeMillis() }
    }
    val whereText = when {
        // Marked without a place: no position is looked for
        a.via == io.github.thisisartman.collectibles.data.Via.BULK -> null
        a.lat != null && a.lng != null -> stringResource(R.string.item_position_saved)
        now - (a.acquiredAt ?: 0L) < POSITION_WAIT_MS -> stringResource(R.string.item_finding_position)
        else -> null
    }
    Row(Modifier.fillMaxWidth().clickable { editing = true }.padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(R.drawable.ic_history), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
            // The date, then "×2" for duplicates and "¥500" when what you paid is noted
            val extra = listOfNotNull(
                a.quantity.takeIf { it > 1 }?.let { stringResource(R.string.quantity_times, it) },
                a.costYen?.let { "¥%,d".format(it) },
            )
            Text((listOf(whenText) + extra).joinToString(sep()), style = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = "tnum"))
            whereText?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            a.notes?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
        if (a.lat != null && a.lng != null) {
            val context = LocalContext.current
            TextButton(onClick = {
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:${a.lat},${a.lng}?q=${a.lat},${a.lng}"))) }
            }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.item_find_map)) }
        }
        IconButton(onClick = { confirmRemove = true }) {
            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.a11y_remove_find, whenText), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}


/** "No stamp here" for stamps, "None here" for the rest. */
fun noneHereLabel(type: ItemType): Int =
    if (type == ItemType.EKI_STAMP || type == ItemType.MUSEUM_STAMP) R.string.none_here_stamp else R.string.none_here

@Composable
internal fun NoneHereRow(item: Item, marked: Boolean, onSet: (Boolean) -> Unit) {
    val ja = isJapanese()
    if (marked) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.none_here_marked, stringResource(noneHereLabel(item.type)), formatDate(item.noneHereAt ?: 0L, ja)),
                Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = { onSet(false) }) { Text(stringResource(R.string.none_here_undo)) }
        }
    } else {
        TextButton(onClick = { onSet(true) }, modifier = Modifier.padding(horizontal = 8.dp)) {
            Text(stringResource(R.string.none_here_ask, stringResource(noneHereLabel(item.type))))
        }
    }
}
