package io.github.thisisartman.collectibles.mockup

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import io.github.thisisartman.collectibles.R
import io.github.thisisartman.collectibles.data.Item
import io.github.thisisartman.collectibles.data.ItemRow
import io.github.thisisartman.collectibles.data.ItemType
import io.github.thisisartman.collectibles.data.Origin
import io.github.thisisartman.collectibles.ui.Mark
import io.github.thisisartman.collectibles.ui.QuickPlacement
import io.github.thisisartman.collectibles.ui.SectorRing

/** One dummy tile: its name and the things on it; one thing is a single collectible, several a place. */
private class MockTile(val name: String, val things: List<ItemRow>)

/** A quick button: what it collects, its label and icon (null: the tick). */
private class MockAction(val tile: MockTile, val row: ItemRow, val label: String, val icon: Int?)

private fun row(id: String, type: ItemType, nameJa: String, romaji: String, collected: Boolean = false, code: String? = null) = ItemRow(
    Item(id = id, type = type, nameJa = nameJa, nameRomaji = romaji, code = code, lat = 37.165, lng = 138.923, origin = Origin.SEED, createdAt = 0, updatedAt = 0),
    findCount = if (collected) 1 else 0, lastAt = null, photoUri = null,
)

private val TILES = listOf(
    MockTile("Urasa", listOf(
        row("s", ItemType.EKI_STAMP, "浦佐駅", "Urasa Station"),
        row("e", ItemType.EKITAG, "浦佐駅", "Urasa Station"),
        row("c", ItemType.MANHOLE_CARD, "南魚沼市", "Minamiuonuma", code = "15-226-A001"),
    )),
    MockTile("Tsubame", listOf(row("m", ItemType.MANHOLE_CARD, "燕市", "Tsubame", code = "15-213-A001"))),
    MockTile("Michi-no-eki Yukiguni", listOf(row("r", ItemType.ROADSIDE_STATION, "道の駅 雪国", "Michi-no-eki Yukiguni"))),
    MockTile("Okutadami Dam", listOf(row("d", ItemType.DAM_CARD, "奥只見ダム", "Okutadami Dam", collected = true))),
)

/** B: which button the finger is over, by the buttons' bounds in root coordinates; -1 for none. Pure, so it is unit tested. */
internal fun hoveredIndex(finger: Offset, bounds: Map<Int, Rect>): Int =
    bounds.entries.filter { it.value.contains(finger) }.minOfOrNull { it.key } ?: -1

private fun actionsOf(t: MockTile): List<MockAction> = t.things.map { r ->
    when (r.item.type) {
        ItemType.EKI_STAMP -> MockAction(t, r, "Stamp", R.drawable.ic_type_stamp)
        ItemType.EKITAG -> MockAction(t, r, "Ekitag", R.drawable.ic_type_ekitag)
        ItemType.MANHOLE_CARD -> MockAction(t, r, if (t.things.size == 1) "Collect" else "Card", if (t.things.size == 1) null else R.drawable.ic_type_manhole)
        else -> MockAction(t, r, "Collect", null)
    }
}

/**
 * #227 prototype: a row of four tiles and two ways to choose.
 * A) Hold, lift, tap: the buttons open wide with labels and shrink to icons after 1.5 s (Replay shows it again).
 * B) Hold and drag: the buttons open as icons; the one under your finger widens with its label and a tick of haptic;
 *    lift on a button to choose it, anywhere else to cancel.
 */
@Composable
fun HoldTileMockup() {
    var variant by remember { mutableStateOf("A") }
    var held by remember { mutableStateOf<MockTile?>(null) }
    var wide by remember { mutableStateOf(false) }
    var replay by remember { mutableIntStateOf(0) }
    var hovered by remember { mutableIntStateOf(-1) }
    var chose by remember { mutableStateOf("Nothing chosen yet") }
    val tileBounds = remember { mutableStateMapOf<String, Rect>() }
    val buttonBounds = remember { mutableStateMapOf<Int, Rect>() }
    var rootSize by remember { mutableStateOf(IntSize.Zero) }
    var panelSize by remember { mutableStateOf(IntSize.Zero) }
    val haptic = LocalHapticFeedback.current

    fun choose(a: MockAction) {
        chose = if (a.row.collected) "${a.label} at ${a.tile.name} is already collected" else "Chose: ${a.label} at ${a.tile.name}"
        held = null; hovered = -1
    }
    fun open(t: MockTile) {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        buttonBounds.clear(); panelSize = IntSize.Zero; hovered = -1
        held = t; wide = variant == "A"; replay++
    }
    // A: wide with labels, then icons after about 1.5 s
    LaunchedEffect(replay, variant) {
        if (variant == "A" && held != null) { wide = true; kotlinx.coroutines.delay(1500); wide = false }
    }

    Box(Modifier.fillMaxSize().onSizeChanged { rootSize = it }) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Text("Hold a tile (#227 prototype)", style = MaterialTheme.typography.titleLarge)
            Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = variant == "A", onClick = { variant = "A"; held = null }, label = { Text("A: tap") })
                FilterChip(selected = variant == "B", onClick = { variant = "B"; held = null }, label = { Text("B: drag") })
                if (variant == "A") OutlinedButton(onClick = { held?.let { replay++ } ?: TILES.first().let { open(it) } }) {
                    Icon(Icons.Filled.Replay, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp)); Text("Replay")
                }
            }
            Text(
                if (variant == "A") "Hold a tile and lift. The buttons show their names, then shrink to icons. Tap one."
                else "Hold a tile and keep your finger down. Drag over the icons; lift on one to choose it, elsewhere to cancel.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TILES.forEach { t ->
                    var origin by remember { mutableStateOf(Offset.Zero) }
                    Column(
                        Modifier.weight(1f)
                            .onGloballyPositioned { c -> c.boundsInRoot().let { origin = it.topLeft; tileBounds[t.name] = it } }
                            .pointerInput(variant) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { open(t) },
                                    onDrag = { change, _ ->
                                        if (variant != "B") return@detectDragGesturesAfterLongPress
                                        val at = origin + change.position
                                        val now = hoveredIndex(at, buttonBounds)
                                        if (now != hovered) { hovered = now; if (now >= 0) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
                                    },
                                    onDragEnd = {
                                        if (variant == "B") {
                                            val a = actionsOf(t).getOrNull(hovered)
                                            if (a != null) choose(a) else { held = null; hovered = -1; chose = "Cancelled" }
                                        }
                                    },
                                    onDragCancel = { if (variant == "B") { held = null; hovered = -1 } },
                                )
                            }
                            .padding(vertical = 6.dp, horizontal = 2.dp)
                            .alpha(if (held == t) 0.6f else 1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        if (t.things.size > 1) SectorRing(t.things, 64.dp) else Mark(t.things.first(), 64.dp)
                        Spacer(Modifier.height(4.dp))
                        Text(t.name, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, minLines = 2, maxLines = 2)
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            Text(chose, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
        }

        val t = held
        if (t != null) {
            // A tap outside closes (A); in B the finger is still down, so lifting decides instead
            if (variant == "A") Box(
                Modifier.fillMaxSize().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { held = null },
            )
            val tile = tileBounds[t.name]
            val place = if (tile != null && panelSize != IntSize.Zero) {
                val r = IntRect(tile.left.toInt(), tile.top.toInt(), tile.right.toInt(), tile.bottom.toInt())
                QuickPlacement.offset(QuickPlacement.side(r, rootSize, panelSize), r, rootSize, panelSize, 16)
            } else IntOffset.Zero
            Surface(
                shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, shadowElevation = 6.dp,
                modifier = Modifier.offset { place }.onSizeChanged { panelSize = it }.alpha(if (panelSize == IntSize.Zero) 0f else 1f),
            ) {
                Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    actionsOf(t).forEachIndexed { i, a ->
                        val showLabel = if (variant == "A") wide else hovered == i
                        MockButton(a, showLabel, highlighted = hovered == i,
                            modifier = Modifier.onGloballyPositioned { buttonBounds[i] = it.boundsInRoot() },
                            onClick = if (variant == "A") ({ choose(a) }) else null)
                    }
                }
            }
        }
    }
}

@Composable
private fun MockButton(a: MockAction, showLabel: Boolean, highlighted: Boolean, modifier: Modifier, onClick: (() -> Unit)?) {
    val done = a.row.collected
    val bg = when {
        done -> MaterialTheme.colorScheme.surfaceVariant
        highlighted -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.secondaryContainer
    }
    val ink = when {
        done -> MaterialTheme.colorScheme.onSurfaceVariant
        highlighted -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onSecondaryContainer
    }
    Surface(
        shape = RoundedCornerShape(24.dp), color = bg, contentColor = ink,
        modifier = modifier.heightIn(min = 48.dp).widthIn(min = 48.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (done || a.icon == null) Icon(Icons.Filled.Check, contentDescription = a.label, modifier = Modifier.size(24.dp))
            else Icon(painterResource(a.icon), contentDescription = a.label, modifier = Modifier.size(24.dp))
            val spec = tween<IntSize>(300, easing = FastOutSlowInEasing)
            AnimatedVisibility(showLabel, enter = expandHorizontally(spec) + fadeIn(), exit = shrinkHorizontally(spec) + fadeOut()) {
                Text(if (done) "${a.label}: collected" else a.label, Modifier.padding(start = 8.dp), maxLines = 1)
            }
        }
    }
}
