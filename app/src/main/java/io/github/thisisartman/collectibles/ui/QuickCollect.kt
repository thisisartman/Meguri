package io.github.thisisartman.collectibles.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import io.github.thisisartman.collectibles.R
import io.github.thisisartman.collectibles.data.ItemRow
import io.github.thisisartman.collectibles.data.ItemType

/** Where a tile's quick Collect buttons open (#227). */
enum class QuickSide { RIGHT, LEFT, BELOW, ABOVE }

/** The quick buttons' placement beside a held tile (#227). Pure, so it is unit tested. */
object QuickPlacement {
    /**
     * A tile at the left edge opens its buttons to the right, one at the right edge to the left; in the middle, the
     * side with more room, if the buttons fit there; if they fit on neither side, below, or above when there is more
     * room above. "At an edge" is within half a tile of the window's side. Sizes in pixels, the tile's in the window.
     */
    fun side(tile: IntRect, window: IntSize, popup: IntSize): QuickSide {
        val roomLeft = tile.left
        val roomRight = window.width - tile.right
        val edge = tile.width / 2
        val fitsRight = roomRight >= popup.width
        val fitsLeft = roomLeft >= popup.width
        return when {
            roomLeft <= edge && fitsRight -> QuickSide.RIGHT
            roomRight <= edge && fitsLeft -> QuickSide.LEFT
            fitsRight && (roomRight >= roomLeft || !fitsLeft) -> QuickSide.RIGHT
            fitsLeft -> QuickSide.LEFT
            window.height - tile.bottom >= popup.height || window.height - tile.bottom >= tile.top -> QuickSide.BELOW
            else -> QuickSide.ABOVE
        }
    }

    /** The buttons' top-left corner for [side], [gap] from the tile, centred on it and kept inside the window. */
    fun offset(side: QuickSide, tile: IntRect, window: IntSize, popup: IntSize, gap: Int): IntOffset {
        val x = when (side) {
            QuickSide.RIGHT -> tile.right + gap
            QuickSide.LEFT -> tile.left - gap - popup.width
            else -> tile.center.x - popup.width / 2
        }
        val y = when (side) {
            QuickSide.BELOW -> tile.bottom + gap
            QuickSide.ABOVE -> tile.top - gap - popup.height
            else -> tile.center.y - popup.height / 2
        }
        return IntOffset(
            x.coerceIn(0, (window.width - popup.width).coerceAtLeast(0)),
            y.coerceIn(0, (window.height - popup.height).coerceAtLeast(0)),
        )
    }
}

/** One quick button: the thing it collects, its label and icon. */
data class QuickItem(val row: ItemRow, val label: Int, val icon: Int?, val name: String)

/**
 * What a held tile offers (#227): a station's Stamp and Ekitag, each on its own; one Collect for anything else.
 * [things] are the tile's things in order (a station's stamp first); [name] is how the snackbar names them.
 */
fun quickItems(things: List<ItemRow>, name: (ItemRow) -> String): List<QuickItem> {
    val station = things.any { it.item.type == ItemType.EKI_STAMP || it.item.type == ItemType.EKITAG }
    return things.map { r ->
        when {
            station && r.item.type == ItemType.EKI_STAMP -> QuickItem(r, R.string.quick_stamp, R.drawable.ic_type_stamp, name(r))
            station && r.item.type == ItemType.EKITAG -> QuickItem(r, R.string.type_ekitag, R.drawable.ic_type_ekitag, name(r))
            things.size == 1 -> QuickItem(r, R.string.quick_collect, null, name(r))
            // A place with several things (a shrine's stamp and goshuin): one button per thing, by its type
            else -> QuickItem(r, r.item.type.label(), null, name(r))
        }
    }
}

/** A held tile's Collect: the usual find, tick and "Collected · Undo"; an Ekitag is named as on the place page. */
@Composable
fun rememberQuickCollect(vm: MainViewModel): (QuickItem) -> Unit {
    val tick = rememberTick()
    val ekitag = sep() + stringResource(R.string.type_ekitag)
    return androidx.compose.runtime.remember(vm, ekitag, tick) {
        { q: QuickItem -> tick(); vm.gotIt(q.row, if (q.row.item.type == ItemType.EKITAG) q.name + ekitag else q.name) }
    }
}

/** The floating buttons beside a held tile: a button for each thing still to get, a quiet "Collected" for the rest. */
@Composable
fun QuickCollectPopup(items: List<QuickItem>, onCollect: (QuickItem) -> Unit, onDismiss: () -> Unit) {
    val gap = with(LocalDensity.current) { 8.dp.roundToPx() }
    val positioner = object : PopupPositionProvider {
        override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
            val side = QuickPlacement.side(anchorBounds, windowSize, popupContentSize)
            return QuickPlacement.offset(side, anchorBounds, windowSize, popupContentSize, gap)
        }
    }
    Popup(popupPositionProvider = positioner, onDismissRequest = onDismiss, properties = PopupProperties(focusable = true)) {
        Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, shadowElevation = 6.dp) {
            Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items.forEach { q ->
                    if (q.row.collected) {
                        // Already collected: said, not offered
                        Row(Modifier.heightIn(min = 40.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                stringResource(R.string.quick_collected, stringResource(q.label)),
                                style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else FilledTonalButton(onClick = { onCollect(q) }, modifier = Modifier.heightIn(min = 48.dp)) {
                        if (q.icon != null) Icon(painterResource(q.icon), contentDescription = null, modifier = Modifier.size(18.dp))
                        else Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(q.label), maxLines = 1)
                    }
                }
            }
        }
    }
}
