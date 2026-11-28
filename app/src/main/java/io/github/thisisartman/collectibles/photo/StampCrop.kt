package io.github.thisisartman.collectibles.photo

import android.graphics.Bitmap
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * A stamp photo cut to its stamp by the stamp's shape (#188, #221; [StampShape]): the photo is looked at small, the
 * biggest stamp on it ([StampFind.blobs]) is cut from the full photo as a circle (a round stamp) or a square with a
 * little white around it (a square one), and cleaned ([StampFind.clean]) as the notebook saves it. A goshuin is cut
 * to its page, uncleaned. A photo that isn't mostly paper, with no stamp found, or of a shape it isn't sure of, is
 * left whole. The original is kept beside the cut (#208), so Edit can redo it.
 */
object StampCrop {
    /** The stamp's cut, no bigger than this: plenty for a stamp, as on a notebook page. A goshuin page gets more. */
    private const val STAMP_PX = 1200
    private const val PAGE_PX = 1600

    /** How [full] would be cut ([page]: a goshuin, cut to its page), or null to keep it whole. */
    fun plan(full: Bitmap, page: Boolean = false): StampShape.Plan? = runCatching {
        val s = 480f / max(full.width, full.height)
        val small = if (s < 1f) Bitmap.createScaledBitmap(full, max(1, (full.width * s).toInt()), max(1, (full.height * s).toInt()), true) else full
        val px = IntArray(small.width * small.height).also { small.getPixels(it, 0, small.width, 0, 0, small.width, small.height) }
        val w = small.width; val h = small.height
        if (small !== full) small.recycle()
        if (page) StampShape.pagePlan(px, w, h) else StampShape.stampPlan(px, w, h)
    }.getOrNull()

    /** [full]'s stamp (or page), cut by its shape, or null when it is kept whole. */
    fun cut(full: Bitmap, page: Boolean = false): Bitmap? = runCatching {
        val plan = plan(full, page) ?: return null
        val b = plan.square
        val left = b.left * full.width; val top = b.top * full.height
        val side = max(8f, (b.right - b.left) * full.width)
        // The part of the frame that is photo; the rest is white
        val rx = max(0, left.roundToInt()); val ry = max(0, top.roundToInt())
        val rx2 = min(full.width, (left + side).roundToInt()); val ry2 = min(full.height, (top + side).roundToInt())
        if (rx2 - rx < 8 || ry2 - ry < 8) return null
        val c = min(1f, (if (page) PAGE_PX else STAMP_PX) / side)
        val region = Bitmap.createBitmap(full, rx, ry, rx2 - rx, ry2 - ry)
        val scaled = if (c < 1f) Bitmap.createScaledBitmap(region, max(1, ((rx2 - rx) * c).roundToInt()), max(1, ((ry2 - ry) * c).roundToInt()), true) else region
        val rw = scaled.width; val rh = scaled.height
        val px = IntArray(rw * rh).also { scaled.getPixels(it, 0, rw, 0, 0, rw, rh) }
        if (scaled !== region) scaled.recycle()
        if (region !== full) region.recycle()
        val out = max(8, (side * c).roundToInt())
        val pixels = StampShape.render(px, rw, rh, out, ((rx - left) * c).roundToInt(), ((ry - top) * c).roundToInt(), plan)
        Bitmap.createBitmap(pixels, out, out, Bitmap.Config.ARGB_8888)
    }.getOrNull()
}
