package io.github.thisisartman.collectibles.mockup

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** Which way a held tile's buttons fan out (owner, 2026-11-28: radial, not a column). */
enum class Fan { RIGHT, LEFT, UP, DOWN }

/**
 * The radial quick buttons' sums (#227 prototype). Angles in degrees on the screen: 0 points right, 90 down.
 * Pure, so it is unit tested.
 */
object Radial {
    const val STEP_DEG = 45.0

    /**
     * A tile at the left edge (centre in the left quarter) fans right, at the right edge left; in the middle, up,
     * away from the hand below, unless the arc would leave the top of the screen, then down.
     */
    fun fan(cx: Float, cy: Float, screenW: Float, radius: Float, button: Float): Fan = when {
        cx < screenW * 0.25f -> Fan.RIGHT
        cx > screenW * 0.75f -> Fan.LEFT
        cy - radius - button / 2 < 0f -> Fan.DOWN
        else -> Fan.UP
    }

    private fun centre(f: Fan) = when (f) { Fan.RIGHT -> 0.0; Fan.LEFT -> 180.0; Fan.UP -> -90.0; Fan.DOWN -> 90.0 }

    /** [n] buttons [STEP_DEG] apart, centred on the fan's direction: three to the right are -45, 0 and 45. */
    fun angles(f: Fan, n: Int): List<Double> = List(n) { i -> centre(f) + (i - (n - 1) / 2.0) * STEP_DEG }

    /** A button's centre on the arc, kept [half] inside the screen on every side. */
    fun position(cx: Float, cy: Float, radius: Float, deg: Double, half: Float, screenW: Float, screenH: Float): Pair<Float, Float> {
        val r = Math.toRadians(deg)
        val x = (cx + radius * cos(r)).toFloat().coerceIn(half, (screenW - half).coerceAtLeast(half))
        val y = (cy + radius * sin(r)).toFloat().coerceIn(half, (screenH - half).coerceAtLeast(half))
        return x to y
    }

    /**
     * Which button the finger is on, by angle and distance from the tile's centre: the nearest angle within half a
     * step, while the finger is in the ring band ([radius] ± [band]). -1 for none (on the tile, or too far out).
     */
    fun hit(fx: Float, fy: Float, cx: Float, cy: Float, angles: List<Double>, radius: Float, band: Float): Int {
        val d = hypot((fx - cx).toDouble(), (fy - cy).toDouble())
        if (d < radius - band || d > radius + band) return -1
        val a = Math.toDegrees(atan2((fy - cy).toDouble(), (fx - cx).toDouble()))
        var best = -1
        var bestDiff = Double.MAX_VALUE
        angles.forEachIndexed { i, g ->
            val diff = abs(((a - g) % 360 + 540) % 360 - 180)
            if (diff < bestDiff) { best = i; bestDiff = diff }
        }
        return if (bestDiff <= STEP_DEG / 2) best else -1
    }

    /**
     * The arc's centre in the overlay's own coordinates: the centre of the tile's ring (its picture), never the tile
     * with its name under it. Both rectangles are in root (window) coordinates; the overlay sits inside the safe-area
     * padding (edge to edge, the status bar is above it), so its origin is taken off.
     */
    fun centreOf(ring: Rect, overlayOrigin: Offset = Offset.Zero): Offset = ring.center - overlayOrigin

    /**
     * Where a button's label pill goes, its top-left corner: outward along the button's own radius; if that would
     * leave the screen or cover a button, across the radius on the side away from the neighbours. Always pulled
     * inside the screen, and never over the button itself or another one. Pure, so it is unit tested.
     */
    fun labelTopLeft(button: Offset, buttonR: Float, deg: Double, label: Size, gap: Float, others: List<Offset>, screen: Size): Offset {
        val r = Math.toRadians(deg)
        val out = Offset(cos(r).toFloat(), sin(r).toFloat())
        // Placed so the edge facing the button sits [gap] past the button's rim, along [d], then pulled on screen
        fun placed(d: Offset): Rect {
            val a = button + d * (buttonR + gap)
            val x = (a.x - label.width / 2 + d.x * label.width / 2).coerceIn(0f, (screen.width - label.width).coerceAtLeast(0f))
            val y = (a.y - label.height / 2 + d.y * label.height / 2).coerceIn(0f, (screen.height - label.height).coerceAtLeast(0f))
            return Rect(Offset(x, y), label)
        }
        val buttons = (others + button).map { Rect(it, buttonR + gap / 2) }
        fun ok(rc: Rect): Boolean = buttons.none { rc.overlaps(it) }
        val first = placed(out)
        if (ok(first)) return first.topLeft
        // Across the radius, on the side away from the neighbours first
        val tangent = Offset(-out.y, out.x)
        val lean = others.sumOf { ((it - button).x * tangent.x + (it - button).y * tangent.y).toDouble() }
        val away = if (lean > 0) tangent * -1f else tangent
        for (d in listOf(away, away * -1f)) placed(d).let { if (ok(it)) return it.topLeft }
        return first.topLeft
    }
}
