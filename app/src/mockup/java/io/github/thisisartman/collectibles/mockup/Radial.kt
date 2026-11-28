package io.github.thisisartman.collectibles.mockup

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
}
