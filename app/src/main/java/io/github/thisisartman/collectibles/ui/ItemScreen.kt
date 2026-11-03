package io.github.thisisartman.collectibles.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material.icons.filled.Call
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
import io.github.thisisartman.collectibles.data.ItemType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemScreen(vm: MainViewModel, itemId: String, onBack: () -> Unit) {
    val ja = isJapanese()
    val row by remember(itemId) { vm.itemRow(itemId) }.collectAsStateWithLifecycle(null)
    val finds by remember(itemId) { vm.finds(itemId) }.collectAsStateWithLifecycle(emptyList())
    val lines by vm.lines.collectAsStateWithLifecycle()
    val stations by vm.lineStations.collectAsStateWithLifecycle()
    val photo = LocalPhoto.current
    val r = row

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            windowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
            title = { Text(r?.item?.type?.let { stringResource(it.label()) } ?: "") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back)) } },
        )
        if (r == null) return@Column
        val item = r.item
        val name = item.displayName(ja)
        // A station says which lines it is on (round 5, D2)
        val lineNames = lines.filter { l -> stations.any { it.lineId == l.id && it.itemId == item.id } }
            .map { if (ja) it.nameJa else it.nameRomaji ?: it.nameJa }
        val meters = vm.distanceTo(r)
        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            if (r.collected) {
                // Have it: your mark large, your record, then the facts
                item {
                    Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Mark(r, 140.dp)
                    }
                }
                item { NameBlock(item, ja, lineNames) }
                item { Actions(item, meters) }
                item { SectionTitle(stringResource(R.string.item_your_record)) }
                items(finds, key = { it.find.id }) { f ->
                    FindCard(f, ja, name, onRemoveBeforeApp = { vm.unmarkBeforeApp(item.id) }, onRemoveFind = { vm.removeFind(f.find.id) })
                }
                item { SectionTitle(stringResource(R.string.item_where)) }
                item { Facts(item) }
            } else {
                // Still to get: how to get there first, then the facts
                item {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Mark(r, 56.dp)
                        Spacer(Modifier.width(14.dp))
                        Column { NameText(item, ja, lineNames) }
                    }
                }
                item { Actions(item, meters) }
                item { SectionTitle(stringResource(R.string.item_where)) }
                item { Facts(item) }
                item {
                    Text(
                        stringResource(R.string.item_no_finds),
                        Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = {
                    val latest = finds.firstOrNull { !it.find.beforeApp }?.find?.id
                    if (latest != null) photo.capture(latest) else vm.gotIt(r, name) { id -> photo.capture(id) }
                },
                modifier = Modifier.weight(1f),
            ) { Text(stringResource(R.string.photo)) }
            if (r.collected) {
                OutlinedButton(onClick = { vm.gotIt(r, name) }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.got_it_again)) }
            } else {
                Button(onClick = { vm.gotIt(r, name) }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.got_it)) }
            }
        }
    }
}

@Composable
private fun NameBlock(item: Item, ja: Boolean, lineNames: List<String>) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) { NameText(item, ja, lineNames) }
}

/** Round 5, D2: the two things you do with a place on a trip day, as icon buttons. */
@Composable
private fun Actions(item: Item, meters: Double?) {
    val context = LocalContext.current
    val hasPlace = item.lat != null && item.lng != null
    val phone = item.placePhone?.takeIf { it.isNotBlank() }
    if (!hasPlace && phone == null) return
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (hasPlace) {
            FilledTonalButton(onClick = {
                val uri = Uri.parse("geo:${item.lat},${item.lng}?q=${item.lat},${item.lng}(${Uri.encode(item.placeName ?: item.nameJa)})")
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
            }) {
                Icon(Icons.Filled.Place, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(distanceText(meters)?.let { stringResource(R.string.item_directions, it) } ?: stringResource(R.string.item_directions_plain))
            }
        }
        if (phone != null) {
            OutlinedButton(onClick = {
                // Opens the dialer with the number filled in; no call permission needed
                runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + phone.filter { it.isDigit() || it == '+' }))) }
            }) {
                Icon(Icons.Filled.Call, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.item_call))
            }
        }
    }
}

@Composable
private fun NameText(item: Item, ja: Boolean, lineNames: List<String> = emptyList()) {
    Text(item.displayName(ja), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    item.secondaryName(ja)?.let { Text(it, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    // English mode: prefecture in romaji; the town has no romaji in the catalogue and is already in the name, so it is left out
    val sub = listOfNotNull(
        item.code,
        item.prefecture?.let { prefectureName(it, ja) },
        item.municipality?.takeIf { ja && it != item.nameJa },
    ).joinToString(" · ")
    if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (item.series != null) {
        val date = item.releaseDate.orEmpty().let { if (ja) it else it.replace('/', '-') }
        Text(stringResource(R.string.item_wave, item.series, date), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (lineNames.isNotEmpty()) {
        Text(stringResource(R.string.item_on_lines, lineNames.joinToString(if (ja) "・" else ", ")), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Facts(item: Item) {
    val (placeLabel, hoursLabel) = when (item.type) {
        ItemType.MANHOLE_CARD -> R.string.item_handed_out_at to R.string.item_hours
        ItemType.GOSHUIN -> R.string.item_office to R.string.item_office_hours
        ItemType.EKI_STAMP, ItemType.EKITAG -> R.string.item_station to R.string.item_stamp_location
    }
    Column(Modifier.fillMaxWidth()) {
        // Hours first: on a trip day the question is whether it is open. Distance and phone are in the buttons above.
        Fact(stringResource(hoursLabel), listOfNotNull(item.hoursText, item.closedDays).joinToString("\n"))
        // A station's place is the station itself: repeating its Japanese name adds nothing (and breaks English mode)
        Fact(stringResource(placeLabel), listOfNotNull(item.placeName?.takeIf { it != item.nameJa }, item.address).joinToString("\n"))
        item.notes?.let { Fact("", it) }
    }
}

@Composable
private fun Fact(label: String, value: String) {
    if (value.isBlank()) return
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp)) {
        if (label.isNotEmpty()) Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
    HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
}

/** How long after a find its missing position counts as "still being found". */
private const val POSITION_WAIT_MS = 2 * 60 * 1000L

@Composable
private fun FindCard(f: FindWithPhotos, ja: Boolean, name: String, onRemoveBeforeApp: () -> Unit, onRemoveFind: () -> Unit) {
    // Removing a "had before the app" mark or a find has no Undo, so both ask first
    var confirmRemove by remember { mutableStateOf(false) }
    if (confirmRemove) {
        val a = f.find
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text(stringResource(if (a.beforeApp) R.string.item_remove_confirm_title else R.string.item_remove_find_title)) },
            text = {
                Text(
                    if (a.beforeApp) stringResource(R.string.item_remove_confirm_text, name)
                    else stringResource(R.string.item_remove_find_text, a.acquiredAt?.let { formatDateTime(it, ja) }.orEmpty())
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmRemove = false; if (a.beforeApp) onRemoveBeforeApp() else onRemoveFind() }) {
                    Text(stringResource(R.string.item_remove_confirm), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.padding(12.dp)) {
            val a = f.find
            if (a.beforeApp) {
                Text(stringResource(R.string.had_before_app), style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = { confirmRemove = true }, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                    Text(stringResource(R.string.item_remove_before_app))
                }
            } else {
                val whenText = a.acquiredAt?.let { formatDateTime(it, ja) } ?: ""
                // The position is filled in after the find is saved; for a while it is pending, not missing
                var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
                LaunchedEffect(a.id, a.lat) {
                    while (a.lat == null && now - (a.acquiredAt ?: 0L) < POSITION_WAIT_MS) { delay(5_000); now = System.currentTimeMillis() }
                }
                val whereText = when {
                    a.lat != null && a.lng != null -> stringResource(R.string.item_position_saved)
                    now - (a.acquiredAt ?: 0L) < POSITION_WAIT_MS -> stringResource(R.string.item_finding_position)
                    else -> stringResource(R.string.item_no_position)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.item_found_at, whenText, whereText), style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"), modifier = Modifier.weight(1f))
                    if (a.lat != null && a.lng != null) {
                        val context = LocalContext.current
                        TextButton(onClick = {
                            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:${a.lat},${a.lng}?q=${a.lat},${a.lng}"))) }
                        }) { Text(stringResource(R.string.item_find_map)) }
                    }
                }
                a.notes?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                TextButton(onClick = { confirmRemove = true }, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                    Text(stringResource(R.string.item_remove_find))
                }
            }
            if (f.photos.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(f.photos.sortedBy { it.createdAt }, key = { it.id }) { p -> PhotoThumb(p.contentUri, 88.dp) }
                }
            }
        }
    }
}
