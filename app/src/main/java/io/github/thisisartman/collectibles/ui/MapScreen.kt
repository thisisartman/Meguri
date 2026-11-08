package io.github.thisisartman.collectibles.ui

import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.clickable
import androidx.compose.runtime.LaunchedEffect
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
    STAMPS(setOf(ItemType.EKI_STAMP, ItemType.EKITAG, ItemType.GOSHUIN, ItemType.MUSEUM_STAMP, ItemType.ROADSIDE_STATION)),
    CARDS(setOf(ItemType.MANHOLE_CARD, ItemType.DAM_CARD)),
    // Your own kinds that are neither a card nor a stamp; the tab, "Other", shows only when you have any
    MINE(setOf(ItemType.CUSTOM)),
    // Everything you have collected, from the profile page (#72)
    COLLECTED(ItemType.entries.toSet()),
    ;

    /**
     * Whether [item] belongs here. Your own kinds go where their category says (owner, 2026-11-09: "what is Mine?"):
     * a kind of card under Cards, a kind of stamp under Stamps, anything else under Other.
     */
    fun has(item: io.github.thisisartman.collectibles.data.Item, custom: Map<String, io.github.thisisartman.collectibles.data.CollectionType>): Boolean {
        if (item.type != ItemType.CUSTOM || this == ALL || this == COLLECTED) return item.type in types
        val cat = item.customTypeId?.let { custom[it] }?.category() ?: Category.OTHER
        return when (this) {
            STAMPS -> cat == Category.STAMP
            CARDS -> cat == Category.CARD
            else -> cat == Category.OTHER
        }
    }
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
    val custom = LocalCustomTypes.current
    val shown = remember(rows, section, custom) { rows.filter { section.has(it.item, custom) && (section != MapSection.COLLECTED || it.collected) } }
    val pins = remember(shown) { shown.mapNotNull { it.toPin() } }
    val tick = rememberTick()
    var picked by rememberSaveable { mutableStateOf<String?>(null) }
    val row = shown.firstOrNull { it.item.id == picked }

    var pinAt by remember { mutableStateOf<androidx.compose.ui.geometry.Offset?>(null) }
    Box(Modifier.fillMaxSize()) {
        MeguriMap(
            pins, here.fix?.let { it.lat to it.lng }, Modifier.fillMaxSize(),
            onPinTap = { tick(); picked = it }, onMapTap = { picked = null },
            anchor = row?.item?.lat?.let { lat -> row.item.lng?.let { lat to it } }, onAnchorScreen = { pinAt = it },
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
                            MapSection.COLLECTED -> R.string.map_title_collected
                        },
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(end = 16.dp),
                )
            }
        }
        if (row != null) {
            PinPopup(row, pinAt, vm, nav, here.fix?.let { it.lat to it.lng }, topLimit = 72.dp, onCollected = { picked = null })
        }
    }
}

/**
 * A tapped pin's card, compact on one line (owner's pick C, Telegram 408): type icon, name, distance with an arrow,
 * then Directions and, while still to get, Collect as icons. Tapping the text opens the item. It grows out of the
 * tapped pin and stays on it while the map moves (#76): above the pin, or below it when there is no room above.
 */
@Composable
fun PinPopup(
    row: ItemRow, at: androidx.compose.ui.geometry.Offset?, vm: MainViewModel, nav: NavHostController,
    here: Pair<Double, Double>?, topLimit: androidx.compose.ui.unit.Dp, onCollected: () -> Unit,
) {
    val context = LocalContext.current
    val ja = isJapanese()
    val name = row.item.displayName(ja)
    val density = androidx.compose.ui.platform.LocalDensity.current
    var size by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    val grow = remember(row.item.id) { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(row.item.id) {
        grow.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = 0.72f, stiffness = 520f))
    }
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
        val width = with(density) { maxWidth.toPx() }
        val margin = with(density) { 12.dp.toPx() }
        val gap = with(density) { 14.dp.toPx() }
        val top = with(density) { topLimit.toPx() }
        val pin = at ?: return@BoxWithConstraints
        val left = (pin.x - size.width / 2f).coerceIn(margin, (width - size.width - margin).coerceAtLeast(margin))
        val above = pin.y - size.height - gap >= top
        // A pin under the search bar puts its card just below the bar, never over it
        val y = (if (above) pin.y - size.height - gap else pin.y + gap).coerceAtLeast(top)
        val pivot = androidx.compose.ui.graphics.TransformOrigin(
            if (size.width > 0) ((pin.x - left) / size.width).coerceIn(0f, 1f) else 0.5f, if (above) 1f else 0f,
        )
        Surface(
            Modifier
                .offset { androidx.compose.ui.unit.IntOffset(left.toInt(), y.toInt()) }
                .widthIn(max = 360.dp)
                .onSizeChanged { size = it }
                .graphicsLayer {
                    val g = grow.value
                    scaleX = 0.2f + 0.8f * g; scaleY = 0.2f + 0.8f * g
                    alpha = if (size == androidx.compose.ui.unit.IntSize.Zero) 0f else g.coerceIn(0f, 1f)
                    transformOrigin = pivot
                },
            shape = RoundedCornerShape(24.dp), shadowElevation = 6.dp,
        ) {
            Row(Modifier.heightIn(min = 56.dp).padding(start = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier.weight(1f, fill = false).heightIn(min = 48.dp).clickable { nav.openItem(row.item.id) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ItemTypeIcon(row.item, PIN_ICON)
                    Spacer(Modifier.width(8.dp))
                    Text(name, Modifier.weight(1f, fill = false), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    distanceText(vm.distanceTo(row))?.let { d ->
                        Spacer(Modifier.width(8.dp))
                        DistanceArrow(d, here, row.item.lat?.let { lat -> row.item.lng?.let { lat to it } }, MaterialTheme.typography.labelMedium)
                    }
                }
                IconButton(onClick = { openDirections(context, row.item, vm.distanceTo(row)) }) {
                    Icon(painterResource(R.drawable.ic_directions), contentDescription = stringResource(R.string.item_directions_plain), modifier = Modifier.size(PIN_ICON))
                }
                if (row.findCount == 0) {
                    androidx.compose.material3.FilledIconButton(
                        onClick = { vm.gotIt(row, name); onCollected() },
                        modifier = Modifier.spoken(stringResource(R.string.a11y_got_it, name)),
                    ) { Icon(painterResource(R.drawable.ic_collect), contentDescription = stringResource(R.string.got_it), modifier = Modifier.size(PIN_ICON)) }
                }
            }
        }
    }
}

/** One size for every icon on the pin card (owner, 2026-11-08: they looked different sizes). */
private val PIN_ICON = 22.dp
