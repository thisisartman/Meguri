package io.github.thisisartman.collectibles.mockup

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import io.github.thisisartman.collectibles.R
import io.github.thisisartman.collectibles.data.Item
import io.github.thisisartman.collectibles.data.ItemRow
import io.github.thisisartman.collectibles.data.ItemType
import io.github.thisisartman.collectibles.data.Origin
import io.github.thisisartman.collectibles.ui.Mark
import io.github.thisisartman.collectibles.ui.SectorRing
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

/** One dummy tile: its name and the things on it; one thing is a single collectible, several a place. */
private class MockTile(val id: String, val name: String, val things: List<ItemRow>)

/** A quick button: what it collects, its label and icon (null: the tick). */
private class MockAction(val tile: MockTile, val row: ItemRow, val label: String, val icon: Int?)

private fun row(id: String, type: ItemType, nameJa: String, romaji: String, collected: Boolean = false, code: String? = null) = ItemRow(
    Item(id = id, type = type, nameJa = nameJa, nameRomaji = romaji, code = code, lat = 37.165, lng = 138.923, origin = Origin.SEED, createdAt = 0, updatedAt = 0),
    findCount = if (collected) 1 else 0, lastAt = null, photoUri = null,
)

private fun station(id: String, ja: String, en: String) = MockTile(id, en, listOf(
    row("$id-s", ItemType.EKI_STAMP, "${ja}駅", "$en Station"),
    row("$id-e", ItemType.EKITAG, "${ja}駅", "$en Station"),
    row("$id-c", ItemType.MANHOLE_CARD, "南魚沼市", "Minamiuonuma", code = "15-226-A001"),
))

private val ROW_ONE = listOf(
    station("urasa", "浦佐", "Urasa"),
    MockTile("tsubame", "Tsubame", listOf(row("m", ItemType.MANHOLE_CARD, "燕市", "Tsubame", code = "15-213-A001"))),
    MockTile("yukiguni", "Michi-no-eki Yukiguni", listOf(row("r", ItemType.ROADSIDE_STATION, "道の駅 雪国", "Michi-no-eki Yukiguni"))),
    MockTile("okutadami", "Okutadami Dam", listOf(row("d", ItemType.DAM_CARD, "奥只見ダム", "Okutadami Dam", collected = true))),
)

/** A tile at the top of the screen, whose fan opens downward. */
private val TOP = station("nagaoka", "長岡", "Nagaoka")

/** A tile's ring or picture, 64 dp. */
@Composable
private fun TileMark(t: MockTile) {
    if (t.things.size > 1) SectorRing(t.things, 64.dp) else Mark(t.things.first(), 64.dp)
}

/** One station per placement: left edge (fans right), middle (fans up), right edge (fans left). */
private val ROW_TWO = listOf(station("muika", "六日町", "Muikamachi"), station("echigo", "越後湯沢", "Echigo-Yuzawa"), station("koide", "小出", "Koide"))

private fun actionsOf(t: MockTile): List<MockAction> = t.things.map { r ->
    when (r.item.type) {
        ItemType.EKI_STAMP -> MockAction(t, r, "Stamp", R.drawable.ic_type_stamp)
        ItemType.EKITAG -> MockAction(t, r, "Ekitag", R.drawable.ic_type_ekitag)
        ItemType.MANHOLE_CARD -> MockAction(t, r, if (t.things.size == 1) "Collect" else "Card", if (t.things.size == 1) null else R.drawable.ic_type_manhole)
        else -> MockAction(t, r, "Collect", null)
    }
}

/**
 * #227 prototype, radial (owner, 2026-11-28): the buttons sit on an arc around the held tile, 45 degrees apart, on
 * the side with room. A) Hold, lift, tap: labels beside the icons, shrinking away after 1.5 s (Replay shows it
 * again). B) Hold and drag: the button under the finger grows and shows its label; lift on it to choose.
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
    // The ring (the tile's picture) of each tile: the fan's centre
    val ringBounds = remember { mutableStateMapOf<String, Rect>() }
    var rootSize by remember { mutableStateOf(IntSize.Zero) }
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val tileDp = 64.dp
    val buttonDp = 48.dp
    // One radius for every button, a true circle round the ring: the ring's own radius, 20 dp clear, then the button
    val radius = with(density) { (tileDp / 2 + 20.dp + buttonDp / 2).toPx() }
    val buttonPx = with(density) { buttonDp.toPx() }
    val band = with(density) { 44.dp.toPx() }
    // Each button's way out from the tile's centre, 0 at the centre and 1 on the arc
    val progress = remember { List(4) { Animatable(0f) } }
    val scope = rememberCoroutineScope()

    fun geometry(t: MockTile): Pair<Offset, List<Double>>? {
        val c = Radial.centreOf(ringBounds[t.id] ?: return null)
        val f = Radial.fan(c.x, c.y, rootSize.width.toFloat(), radius, buttonPx)
        return c to Radial.angles(f, actionsOf(t).size)
    }
    fun close() {
        scope.launch {
            // Back in, the last out first in
            progress.indices.reversed().forEach { i -> launch { delay((progress.lastIndex - i) * 30L); progress[i].animateTo(0f, tween(160)) } }
            delay(160L + progress.size * 30L)
            held = null; hovered = -1
        }
    }
    fun choose(a: MockAction) {
        chose = if (a.row.collected) "${a.label} at ${a.tile.name} is already collected" else "Chose: ${a.label} at ${a.tile.name}"
        close()
    }
    fun open(t: MockTile) {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        hovered = -1; held = t; wide = variant == "A"; replay++
        scope.launch {
            progress.forEachIndexed { i, p ->
                launch { p.snapTo(0f); delay(i * 30L); p.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow)) }
            }
        }
    }
    // A: labels beside the icons, then icons only after about 1.5 s
    LaunchedEffect(replay, variant) {
        if (variant == "A" && held != null) { wide = true; delay(1500); wide = false }
    }

    @Composable
    fun Tile(t: MockTile, modifier: Modifier) {
        Column(
            modifier
                .onGloballyPositioned { tileBounds[t.id] = it.boundsInRoot() }
                .pointerInput(variant) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { open(t) },
                        onDrag = { change, _ ->
                            if (variant != "B") return@detectDragGesturesAfterLongPress
                            val b = tileBounds[t.id] ?: return@detectDragGesturesAfterLongPress
                            val (c, angles) = geometry(t) ?: return@detectDragGesturesAfterLongPress
                            // Hit by angle and distance from the tile's centre: the nearest button within the ring band
                            val at = b.topLeft + change.position
                            val now = Radial.hit(at.x, at.y, c.x, c.y, angles, radius, band)
                            if (now != hovered) { hovered = now; if (now >= 0) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
                        },
                        onDragEnd = {
                            if (variant == "B") {
                                val a = actionsOf(t).getOrNull(hovered)
                                if (a != null) choose(a) else { chose = "Cancelled"; close() }
                            }
                        },
                        onDragCancel = { if (variant == "B") close() },
                    )
                }
                .padding(vertical = 6.dp, horizontal = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.onGloballyPositioned { ringBounds[t.id] = it.boundsInRoot() }) { TileMark(t) }
            Spacer(Modifier.height(4.dp))
            Text(t.name, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, minLines = 2, maxLines = 2)
        }
    }

    Box(Modifier.fillMaxSize().onSizeChanged { rootSize = it }) {
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            // A tile at the very top: its fan opens downward
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { Tile(TOP, Modifier.width(96.dp)) }
            Text("Hold a tile (#227 prototype, radial)", style = MaterialTheme.typography.titleLarge)
            Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = variant == "A", onClick = { variant = "A"; held = null }, label = { Text("A: tap") })
                FilterChip(selected = variant == "B", onClick = { variant = "B"; held = null }, label = { Text("B: drag") })
                if (variant == "A") OutlinedButton(onClick = { held?.let { replay++ } ?: open(ROW_ONE.first()) }) {
                    Icon(Icons.Filled.Replay, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp)); Text("Replay")
                }
            }
            Text(
                if (variant == "A") "Hold a tile and lift. The buttons fan out with their names, then shrink to icons. Tap one."
                else "Hold a tile and keep your finger down. Drag round the arc; lift on a button to choose it, elsewhere to cancel.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(96.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                ROW_ONE.forEach { t -> Tile(t, Modifier.weight(1f)) }
            }
            Spacer(Modifier.height(140.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                ROW_TWO.forEach { t -> Tile(t, Modifier.width(96.dp)) }
            }
            Spacer(Modifier.weight(1f))
            Text(chose, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
        }

        val t = held
        val g = t?.let { geometry(it) }
        if (t != null && g != null) {
            val (c, angles) = g
            // The rest of the screen dims while the fan is open (40 %); a tap on it closes (A). In B the finger is still
            // down, so lifting decides instead
            val dim = progress.first().value.coerceIn(0f, 1f)
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f * dim))
                    .then(if (variant == "A") Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { close() } else Modifier),
            )
            // The held tile's ring stays bright, over the dim
            Box(Modifier.placedAt(c.x, c.y, Offset.Zero)) { TileMark(t) }
            val screenW = rootSize.width.toFloat()
            val screenH = rootSize.height.toFloat()
            val spots = angles.map { Radial.position(c.x, c.y, radius, it, buttonPx / 2, screenW, screenH) }
            actionsOf(t).forEachIndexed { i, a ->
                val (tx, ty) = spots[i]
                val p = progress.getOrNull(i)?.value ?: 1f
                val others = spots.filterIndexed { j, _ -> j != i }.map { (x, y) -> Offset(x, y) }
                val showLabel = if (variant == "A") wide else hovered == i
                RadialButton(
                    a, c.x + (tx - c.x) * p, c.y + (ty - c.y) * p, angles[i], others, Size(screenW, screenH), p, showLabel, highlighted = hovered == i,
                    onClick = if (variant == "A") ({ choose(a) }) else null,
                )
            }
        }
    }
}

/** A round icon button centred on ([x], [y]), its label as a pill just outside the arc, pointing along [dir]. */
@Composable
private fun RadialButton(
    a: MockAction, x: Float, y: Float, deg: Double, others: List<Offset>, screen: Size, progress: Float,
    showLabel: Boolean, highlighted: Boolean, onClick: (() -> Unit)?,
) {
    val done = a.row.collected
    val scale by animateFloatAsState(if (highlighted) 1.25f else 1f, spring(stiffness = Spring.StiffnessMedium), label = "hover")
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
    val buttonR = with(LocalDensity.current) { (24.dp * scale).toPx() }
    val gap = with(LocalDensity.current) { 6.dp.toPx() }
    Box(Modifier.placedAt(x, y, Offset.Zero)) {
        Surface(
            shape = CircleShape, color = bg, contentColor = ink, shadowElevation = 4.dp,
            modifier = Modifier.size(48.dp)
                .graphicsLayer { val s = scale * (0.4f + 0.6f * progress.coerceIn(0f, 1.2f)); scaleX = s; scaleY = s; alpha = progress.coerceIn(0f, 1f) }
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (done || a.icon == null) Icon(Icons.Filled.Check, contentDescription = a.label, modifier = Modifier.size(24.dp))
                else Icon(painterResource(a.icon), contentDescription = a.label, modifier = Modifier.size(24.dp))
            }
        }
    }
    // The label: outward along the button's radius, else across it on the side away from the neighbours; always on
    // screen and never over another button
    Box(Modifier.labelAt(Offset(x, y), buttonR, deg, gap, others, screen)) {
        AnimatedVisibility(showLabel && progress > 0.5f, enter = fadeIn() + scaleIn(initialScale = 0.6f), exit = fadeOut() + scaleOut(targetScale = 0.6f)) {
            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.inverseSurface, contentColor = MaterialTheme.colorScheme.inverseOnSurface) {
                Text(if (done) "${a.label}: collected" else a.label, Modifier.padding(horizontal = 10.dp, vertical = 4.dp), style = MaterialTheme.typography.labelLarge, maxLines = 1)
            }
        }
    }
}

/** Takes the whole parent and places the label where [Radial.labelTopLeft] says, by its measured size. */
private fun Modifier.labelAt(button: Offset, buttonR: Float, deg: Double, gap: Float, others: List<Offset>, screen: Size) = this.layout { m, c ->
    val p = m.measure(c.copy(minWidth = 0, minHeight = 0))
    layout(c.maxWidth, c.maxHeight) {
        val tl = Radial.labelTopLeft(button, buttonR, deg, Size(p.width.toFloat(), p.height.toFloat()), gap, others, screen)
        p.place(tl.x.toInt(), tl.y.toInt())
    }
}

/**
 * Takes the whole parent and places the content so that its edge facing back along [dir] touches ([x], [y]); with
 * [dir] zero, the content's centre sits there. Only the content itself takes touches.
 */
private fun Modifier.placedAt(x: Float, y: Float, dir: Offset) = this.layout { m, c ->
    val p = m.measure(c.copy(minWidth = 0, minHeight = 0))
    layout(c.maxWidth, c.maxHeight) {
        val px = x - p.width / 2f + dir.x * p.width / 2f
        val py = y - p.height / 2f + dir.y * p.height / 2f
        p.place(px.toInt(), py.toInt())
    }
}
