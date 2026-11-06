package io.github.thisisartman.collectibles.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
    STAMPS(setOf(ItemType.EKI_STAMP, ItemType.EKITAG, ItemType.GOSHUIN)),
    CARDS(setOf(ItemType.MANHOLE_CARD)),
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
    var picked by rememberSaveable { mutableStateOf<String?>(null) }
    val row = shown.firstOrNull { it.item.id == picked }

    Box(Modifier.fillMaxSize()) {
        MeguriMap(
            pins, here.fix?.let { it.lat to it.lng }, Modifier.fillMaxSize(),
            onPinTap = { picked = it }, onMapTap = { picked = null },
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
                    Text(row.item.displayName(ja), style = MaterialTheme.typography.titleMedium)
                    val sub = listOfNotNull(
                        stringResource(if (row.findCount > 0) R.string.map_collected else R.string.map_not_collected),
                        distanceText(vm.distanceTo(row)),
                        row.item.placeName?.takeIf { it != row.item.nameJa },
                    ).joinToString(" · ")
                    Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(12.dp))
                    Row {
                        Button(onClick = { nav.openItem(row.item.id) }) { Text(stringResource(R.string.map_details)) }
                        Spacer(Modifier.width(8.dp))
                        InkOutlinedButton(onClick = {
                            val i = row.item
                            val uri = Uri.parse("geo:${i.lat},${i.lng}?q=${i.lat},${i.lng}(${Uri.encode(i.placeName ?: i.nameJa)})")
                            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
                        }) { Text(stringResource(R.string.item_directions_plain)) }
                    }
                }
            }
        }
    }
}
