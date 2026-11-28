package io.github.thisisartman.collectibles.photo

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * How a stamp photo is cut, by the stamp's shape (#221; owner, 2026-11-28: "if the stamp is a circle, cut a circle
 * that fills the picture; if it's a square, keep a square with a little white around it"; unsure, keep it whole).
 * A goshuin is cut to its page, a rectangle, as a square with a little white around it. Pure Kotlin on row-major
 * ARGB, so it is tested on a laptop and run over the owner's photos there; [StampCrop] does the phone's side.
 */
object StampShape {
    enum class Shape { ROUND, SQUARE, UNSURE }

    /**
     * What to cut: [square] is the picture's frame, as shares of the photo's width and height (it may reach past
     * the photo, where it is white); [shape] ROUND cuts a circle filling it, SQUARE keeps the square; [clean]
     * repaints the stamp in its ink's colour on white paper, as the notebook does (a goshuin keeps its colours).
     */
    data class Plan(val shape: Shape, val square: StampFind.Box, val clean: Boolean)

    /** The white around a square stamp or a page, as a share of its longer side, each way. */
    const val MARGIN = 0.06f
    /** A round stamp's circle sits just outside its rim. */
    const val ROUND_PAD = 0.02f

    /**
     * A patch's shape from its facts: a disk fills about 0.785 of its box and leaves the box's corners empty; a
     * square or rectangle, even with rounded corners, fills 0.88 or more. Anything else (an oval, writing joined
     * to the stamp, a torn print, a neighbour's edge) is unsure (owner, 2026-11-28: unsure, keep it whole).
     */
    fun shapeOf(width: Int, height: Int, area: Int, corners: Float): Shape {
        if (width <= 0 || height <= 0) return Shape.UNSURE
        val fill = area.toFloat() / (width.toFloat() * height)
        val aspect = width.toFloat() / height
        return when {
            fill >= 0.88f -> Shape.SQUARE
            // Corners all but empty: on the owner's photos real round stamps leave at most 0.01 there, and a
            // squarish one with a round design (Okachimachi) 0.26
            fill in 0.70f..0.86f && corners < 0.15f && aspect in 0.88f..1.14f -> Shape.ROUND
            else -> Shape.UNSURE
        }
    }

    fun shapeOf(b: StampFind.Blob): Shape = shapeOf(b.width, b.height, b.area, b.corners)

    /** The frame around a patch at [x0]..[x1], [y0]..[y1] (pixels, inclusive) of a [w] by [h] photo, as shares. */
    fun frame(shape: Shape, x0: Int, y0: Int, x1: Int, y1: Int, w: Int, h: Int): StampFind.Box {
        val cx = (x0 + x1 + 1) / 2f; val cy = (y0 + y1 + 1) / 2f
        val long = max(x1 - x0 + 1, y1 - y0 + 1).toFloat()
        val side = if (shape == Shape.ROUND) long * (1 + 2 * ROUND_PAD) else long * (1 + 2 * MARGIN)
        return StampFind.Box((cx - side / 2) / w, (cy - side / 2) / h, (cx + side / 2) / w, (cy + side / 2) / h)
    }

    /** How a stamp photo ([argb], [w] by [h], small) is cut; null when it isn't a page with a stamp of a clear shape. */
    fun stampPlan(argb: IntArray, w: Int, h: Int): Plan? {
        if (!StampFind.isPage(argb)) return null
        val b = StampFind.blobs(argb, w, h).firstOrNull() ?: return null
        val shape = shapeOf(b)
        if (shape == Shape.UNSURE) return null
        return Plan(shape, frame(shape, b.x0, b.y0, b.x1, b.y1, w, h), clean = true)
    }

    /**
     * How a goshuin photo is cut: to its page, the biggest bright, plain, rectangular patch, at least a fifth of
     * the photo. Null when there is no clear page.
     */
    fun pagePlan(argb: IntArray, w: Int, h: Int): Plan? {
        fun lum(c: Int) = (((c shr 16) and 0xFF) + ((c shr 8) and 0xFF) + (c and 0xFF)) / 3f
        var m = BooleanArray(w * h) { i ->
            val c = argb[i]
            val r = (c shr 16) and 0xFF; val g = (c shr 8) and 0xFF; val b = c and 0xFF
            val mx = max(r, max(g, b)); val mn = min(r, min(g, b))
            lum(c) > 140f && mx > 0 && (mx - mn).toFloat() / mx < 0.25f
        }
        // Ink on the page leaves holes: bridge the strokes, then fill what the paper encloses
        val k = max(2, (min(w, h) * 0.015f).roundToInt())
        m = StampFind.boxFilter(StampFind.boxFilter(m, w, h, k) { it > 0 }, w, h, k) { it == (2 * k + 1) * (2 * k + 1) }
        m = StampFind.fillHoles(m, w, h)
        val p = StampFind.patches(m, w, h).firstOrNull() ?: return null
        if (p.area < w * h * 0.2f || p.area.toFloat() / (p.width.toFloat() * p.height) < 0.85f) return null
        return Plan(Shape.SQUARE, frame(Shape.SQUARE, p.x0, p.y0, p.x1, p.y1, w, h), clean = false)
    }

    /**
     * The cut picture, [side] by [side]: [region] ([rw] by [rh]) is the part of the photo inside the frame, at the
     * frame's scale, with its top left at [ox], [oy] in the frame; the rest of the frame is white. Cleaned first if
     * the plan says so; a ROUND plan is a circle filling the frame, transparent outside with a soft edge.
     */
    fun render(region: IntArray, rw: Int, rh: Int, side: Int, ox: Int, oy: Int, plan: Plan): IntArray {
        val src = if (plan.clean && rw > 2 && rh > 2) StampFind.clean(region, rw, rh) else region
        val out = IntArray(side * side) { WHITE }
        for (y in 0 until rh) {
            val ty = y + oy
            if (ty !in 0 until side) continue
            for (x in 0 until rw) {
                val tx = x + ox
                if (tx in 0 until side) out[ty * side + tx] = src[y * rw + x] or (0xFF shl 24)
            }
        }
        if (plan.shape == Shape.ROUND) {
            val c = side / 2f; val r = side / 2f
            for (y in 0 until side) for (x in 0 until side) {
                val dx = x + 0.5f - c; val dy = y + 0.5f - c
                // A one-pixel soft edge, so the rim isn't jagged
                val a = (r - sqrt(dx * dx + dy * dy) + 0.5f).coerceIn(0f, 1f)
                val i = y * side + x
                out[i] = ((a * 255).roundToInt() shl 24) or (out[i] and 0xFFFFFF)
            }
        }
        return out
    }

    private const val WHITE = 0xFFFFFFFF.toInt()
}
