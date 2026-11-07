package io.github.thisisartman.collectibles.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.filled.Info
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import io.github.thisisartman.collectibles.R
import io.github.thisisartman.collectibles.data.ItemRow
import io.github.thisisartman.collectibles.data.ItemType

/** Which items a map shows: everything, or one Nearby section. */
enum class MapSection(val types: Set<ItemType>) {
    ALL(ItemType.entries.toSet()),
    STAMPS(setOf(ItemType.EKI_STAMP, ItemType.EKITAG, ItemType.GOSHUIN, ItemType.MUSEUM_STAMP)),
    CARDS(setOf(ItemType.MANHOLE_CARD, ItemType.DAM_CARD)),
    // Your own collections (#64); the tab shows only when you have any
    MINE(setOf(ItemType.CUSTOM)),
}

fun ItemRow.toPin(): MapPin? {
    val lat = item.lat ?: return null
    val lng = item.lng ?: return null
    return MapPin(item.id, lat, lng, item.type, findCount > 0)
}

/** Full-screen map (#47, #51): tap a pin for a card with Details and Directions (#52). */
@Composable
fun MapScreen(vm: MainViewModel, nav: NavHostController, section: MapSection) {
    val ja = isJapanese()
    val context = LocalContext.current
    val rows by vm.rows.collectAsStateWithLifecycle()
    val here by vm.here.collectAsStateWithLifecycle()
    val shown = remember(rows, section) { rows.filter { it.item.type in section.types } }
    val pins = remember(shown) { shown.mapNotNull { it.toPin() } }
    val tick = rememberTick()
    var picked by rememberSaveable { mutableStateOf<String?>(null) }
    val row = shown.firstOrNull { it.item.id == picked }

    Box(Modifier.fillMaxSize()) {
        MeguriMap(
            pins, here.fix?.let { it.lat to it.lng }, Modifier.fillMaxSize(),
            onPinTap = { tick(); picked = it }, onMapTap = { picked = null },
        )
        Surface(
            Modifier.statusBarsPadding().padding(12.dp),
            shape = RoundedCornerShape(24.dp), shadowElevation = 3.dp,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { nav.popBackStack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                }
                Text(
                    stringResource(
                        when (section) {
                            MapSection.ALL -> R.string.map_title_all
                            MapSection.STAMPS -> R.string.map_title_stamps
                            MapSection.CARDS -> R.string.map_title_cards
                            MapSection.MINE -> R.string.more_my_collections
                        },
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(end = 16.dp),
                )
            }
        }
        if (row != null) {
            Surface(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(12.dp),
                shape = RoundedCornerShape(20.dp), shadowElevation = 6.dp,
            ) {
                Column(Modifier.padding(16.dp)) {
                    PinHeader(row, vm.distanceTo(row), here.fix?.let { it.lat to it.lng })
                    Spacer(Modifier.height(12.dp))
                    PinActions(row, vm, nav, onCollected = { picked = null })
                }
            }
        }
    }
}

/**
 * A tapped pin's buttons, the same on Nearby and the full-screen map. Owner, 2026-11-06: collect straight from
 * the map. Still to get: Directions, Details, and Collected as the main action on the right, as everywhere else.
 * Collected already: Directions and Details.
 */
@Composable
fun PinActions(row: ItemRow, vm: MainViewModel, nav: NavHostController, onCollected: () -> Unit) {
    val context = LocalContext.current
    val name = row.item.displayName(isJapanese())
    // Owner, 2026-11-07: icon plus word, so a later compact card can drop the words and keep the icons;
    // "Collect" (a verb) with a plus-on-stack icon, since "Collected" read as already collected
    val pad = androidx.compose.foundation.layout.PaddingValues(start = 12.dp, end = 14.dp)
    @Composable fun Lead(res: Int) { Icon(painterResource(res), contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        InkOutlinedButton(onClick = { openDirections(context, row.item, vm.distanceTo(row)) }, modifier = Modifier.heightIn(min = 48.dp), contentPadding = pad) {
            Lead(R.drawable.ic_directions); Text(stringResource(R.string.item_directions_plain), maxLines = 1)
        }
        val details: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {
            Icon(Icons.Filled.Info, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.map_details), maxLines = 1)
        }
        if (row.findCount > 0) {
            Button(onClick = { nav.openItem(row.item.id) }, modifier = Modifier.heightIn(min = 48.dp), contentPadding = pad, content = details)
        } else {
            InkOutlinedButton(onClick = { nav.openItem(row.item.id) }, modifier = Modifier.heightIn(min = 48.dp), contentPadding = pad, content = details)
            Button(
                onClick = { vm.gotIt(row, name); onCollected() },
                modifier = Modifier.heightIn(min = 48.dp).spoken(stringResource(R.string.a11y_got_it, name)),
                contentPadding = pad,
            ) { Lead(R.drawable.ic_collect); Text(stringResource(R.string.got_it), maxLines = 1) }
        }
    }
}

/**
 * A tapped pin's title and status: type icon, name, the Japanese name, then the state as a word on the left and the
 * distance with an arrow pointing at the place on the right (map-app study, 2026-11-07).
 */
@Composable
fun PinHeader(row: ItemRow, meters: Double?, here: Pair<Double, Double>?) {
    val ja = isJapanese()
    Row(verticalAlignment = Alignment.CenterVertically) {
        ItemTypeIcon(row.item, 22.dp)
        Spacer(Modifier.width(8.dp))
        Column {
            Text(row.item.displayName(ja), style = MaterialTheme.typography.titleMedium, maxLines = 2)
            // The Japanese name lives here and on the item page, not on the map labels (owner, 2026-11-07)
            if (!ja && row.item.nameJa != row.item.displayName(ja)) {
                Text(row.item.nameJa, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
    }
    Row(Modifier.fillMaxWidth().padding(start = 30.dp, top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        val collected = row.findCount > 0
        Text(
            stringResource(if (collected) R.string.map_collected else R.string.map_not_collected),
            style = MaterialTheme.typography.labelLarge,
            color = if (collected) LocalTypeColors.current.of(row.item.type) else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        distanceText(meters)?.let { d ->
            DistanceArrow(d, here, row.item.lat?.let { lat -> row.item.lng?.let { lat to it } })
        }
    }
}
