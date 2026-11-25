package io.github.thisisartman.collectibles.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.thisisartman.collectibles.R
import io.github.thisisartman.collectibles.data.ItemRow
import io.github.thisisartman.collectibles.data.ItemType
import kotlin.math.cos
import kotlin.math.sin

/** The most parts a ring is split into; a place with more shows its first three (#192). */
const val RING_PARTS = 3

/**
 * The sector ring (#192, owner approved round 3, 2026-11-26), on a Places tile, a station's tile under Collectibles
 * and the place page. One part per thing at the place, from 12 o'clock, with a small gap between parts: a part is
 * in its thing's colour once collected, grey until then, and the thing's icon sits in a small circle on its part
 * (filled once collected). When everything is collected the middle takes the collected tint and a tick sits at the
 * lower right. One thing: a plain ring, dashed grey until collected, then in its colour with the tick.
 * The middle is your photo or the card's picture when there is one, else the type icon. Sizes follow the round 3
 * drawing, which is 64 units across.
 */
@Composable
fun SectorRing(things: List<ItemRow>, size: Dp, modifier: Modifier = Modifier) {
    if (things.isEmpty()) return
    val shown = things.take(RING_PARTS)
    val n = shown.size
    val done = things.all { it.collected }
    val dash = LocalTypeColors.current.dash
    val colours = shown.map { colorOf(it.item) }
    val arcs = shown.mapIndexed { i, t -> if (t.collected) colours[i] else dash }
    val primary = MaterialTheme.colorScheme.primary
    val surface = MaterialTheme.colorScheme.surface
    val fill = if (done) primary.copy(alpha = 0.16f).compositeOver(surface) else surface
    val u = size / 64
    val r = size / 2 - u * 7
    val described = stringResource(R.string.ring_a11y, things.count { it.collected }, things.size)
    Box(modifier.size(size).semantics { contentDescription = described }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val unit = this.size.minDimension / 64
            val c = Offset(this.size.width / 2, this.size.height / 2)
            val rad = this.size.minDimension / 2 - unit * 7
            drawCircle(fill, radius = rad - unit * 4, center = c)
            if (n == 1) {
                val have = shown[0].collected
                drawCircle(
                    arcs[0], radius = rad, center = c,
                    style = if (have) Stroke(unit * 5) else Stroke(unit * 3, pathEffect = PathEffect.dashPathEffect(floatArrayOf(unit * 4, unit * 4))),
                )
            } else {
                val span = 360f / n
                val gap = 16f
                arcs.forEachIndexed { i, col ->
                    drawArc(
                        col, startAngle = -90f + i * span + gap / 2, sweepAngle = span - gap, useCenter = false,
                        topLeft = Offset(c.x - rad, c.y - rad), size = Size(rad * 2, rad * 2),
                        style = Stroke(unit * 5, cap = StrokeCap.Round),
                    )
                }
            }
        }
        RingMiddle(things, size * 0.5f, anyCollected = things.any { it.collected })
        // Each thing's icon on its own part (not on a one-thing ring)
        if (n > 1) shown.forEachIndexed { i, t ->
            val a = Math.toRadians(-90.0 + (i + 0.5) * 360.0 / n)
            val badge = (u * 18).coerceAtLeast(14.dp)
            Box(
                Modifier.offset(r * cos(a).toFloat(), r * sin(a).toFloat()).size(badge)
                    .clip(CircleShape).background(if (t.collected) colours[i] else surface)
                    .border(1.5.dp, colours[i], CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(iconOf(t.item)), contentDescription = null,
                    tint = if (t.collected) LocalTypeColors.current.onSeal else colours[i], modifier = Modifier.size(badge * 0.62f),
                )
            }
        }
        if (done) Tick(Modifier.offset(r * 0.78f, r * 0.9f), (u * 18).coerceAtLeast(14.dp))
    }
}

/** The tick on a ring whose every thing is collected. */
@Composable
fun Tick(modifier: Modifier, size: Dp) {
    Box(
        modifier.size(size).clip(CircleShape).background(MaterialTheme.colorScheme.primary)
            .border(1.5.dp, MaterialTheme.colorScheme.surface, CircleShape),
        contentAlignment = Alignment.Center,
    ) { Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(size * 0.7f)) }
}

/** The middle of a ring: the first photo at the place, else the card's own picture, else the type icon. */
@Composable
private fun RingMiddle(things: List<ItemRow>, size: Dp, anyCollected: Boolean) {
    val first = things.first()
    val cards by io.github.thisisartman.collectibles.photo.CardPictures.have.collectAsState()
    val photo = things.firstOrNull { it.photoUri != null }
    val card = things.firstOrNull { it.item.type == ItemType.MANHOLE_CARD && it.item.code != null && it.item.code in cards }
    val tint = if (anyCollected) colorOf(first.item) else LocalTypeColors.current.dash
    @Composable fun icon() = Icon(painterResource(iconOf(first.item)), contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.84f))
    when {
        photo?.photoUri != null -> PhotoThumb(photo.photoUri, size, Modifier.clip(CircleShape), fallback = { icon() })
        card != null -> CardPictureThumb(card.item.code!!, card.collected, size, Modifier.clip(CircleShape)) { icon() }
        else -> icon()
    }
}
