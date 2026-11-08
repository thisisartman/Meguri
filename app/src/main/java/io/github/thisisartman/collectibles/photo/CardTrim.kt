package io.github.thisisartman.collectibles.photo

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Trims a scanned manhole card to the top card (owner, 2026-11-09: the scanner took in the cards under it, or the
 * box's edge). Every manhole card has a deep blue frame inside a thin white border, so: mark the blue pixels, take
 * the largest blue patch, its four outer corners, push them out by the white border, and straighten that shape to
 * a card. The border sizes come from the official card picture; the card is 63 by 88 mm. Pure Kotlin, no library.
 */
object CardTrim {
    private const val WORK = 320
    /** White border, as a share of the blue frame's width (sides) and height (top and bottom). */
    const val SIDE = 0.063f
    const val TOP = 0.050f
    const val CARD_ASPECT = 63f / 88f

    /** Why a page was or wasn't trimmed, for the logs and tests. */
    enum class Result { OK, NO_FRAME, FRAME_CUT_OFF, ODD_SHAPE }

    /** A deep, clearly blue pixel: the card's frame, not a pale blue box or white. */
    fun isFrameBlue(argb: Int): Boolean {
        val r = (argb shr 16) and 0xFF; val g = (argb shr 8) and 0xFF; val b = argb and 0xFF
        return b - g > 45 && b - r > 70 && b > 100
    }

    /**
     * The card's corners (top left, top right, bottom right, bottom left; x,y pairs) in [argb]'s pixels, border
     * included, or why not. [argb] is row-major, [w] by [h]. Pure, for tests.
     */
    fun find(argb: IntArray, w: Int, h: Int): Pair<FloatArray?, Result> {
        val mask = BooleanArray(w * h) { isFrameBlue(argb[it]) }
        // Largest 4-connected blue patch
        val label = IntArray(w * h); val queue = IntArray(w * h)
        var best = 0; var bestN = 0; var cur = 0
        for (start in 0 until w * h) {
            if (!mask[start] || label[start] != 0) continue
            cur++; var head = 0; var tail = 0; queue[tail++] = start; label[start] = cur
            while (head < tail) {
                val i = queue[head++]; val x = i % w; val y = i / w
                if (x > 0 && mask[i - 1] && label[i - 1] == 0) { label[i - 1] = cur; queue[tail++] = i - 1 }
                if (x < w - 1 && mask[i + 1] && label[i + 1] == 0) { label[i + 1] = cur; queue[tail++] = i + 1 }
                if (y > 0 && mask[i - w] && label[i - w] == 0) { label[i - w] = cur; queue[tail++] = i - w }
                if (y < h - 1 && mask[i + w] && label[i + w] == 0) { label[i + w] = cur; queue[tail++] = i + w }
            }
            if (tail > bestN) { bestN = tail; best = cur }
        }
        if (bestN < 0.05f * w * h) return null to Result.NO_FRAME
        // Outer corners: the extremes of x+y and x-y
        var tl = 0; var tr = 0; var br = 0; var bl = 0
        var sMin = Int.MAX_VALUE; var sMax = Int.MIN_VALUE; var dMin = Int.MAX_VALUE; var dMax = Int.MIN_VALUE
        for (i in 0 until w * h) {
            if (label[i] != best) continue
            val x = i % w; val y = i / w; val s = x + y; val d = x - y
            if (s < sMin) { sMin = s; tl = i }; if (s > sMax) { sMax = s; br = i }
            if (d > dMax) { dMax = d; tr = i }; if (d < dMin) { dMin = d; bl = i }
        }
        val q = floatArrayOf(
            (tl % w).toFloat(), (tl / w).toFloat(), (tr % w).toFloat(), (tr / w).toFloat(),
            (br % w).toFloat(), (br / w).toFloat(), (bl % w).toFloat(), (bl / w).toFloat(),
        )
        // The frame should look like a card: about 0.71 wide for its height
        val fw = (hypot(q[2] - q[0], q[3] - q[1]) + hypot(q[4] - q[6], q[5] - q[7])) / 2
        val fh = (hypot(q[6] - q[0], q[7] - q[1]) + hypot(q[4] - q[2], q[5] - q[3])) / 2
        if (fh <= 0f || fw / fh !in 0.55f..0.9f) return null to Result.ODD_SHAPE
        val e = expand(q)
        val slackX = 0.02f * w; val slackY = 0.02f * h
        for (k in 0 until 4) {
            val x = e[2 * k]; val y = e[2 * k + 1]
            if (x < -slackX || x > w + slackX || y < -slackY || y > h + slackY) return null to Result.FRAME_CUT_OFF
        }
        return e to Result.OK
    }

    /** Pushes the frame's corners out by the white border, along the frame's own edges (so it follows any tilt). */
    fun expand(q: FloatArray): FloatArray {
        val rx = ((q[2] - q[0]) + (q[4] - q[6])) / 2; val ry = ((q[3] - q[1]) + (q[5] - q[7])) / 2
        val dx = ((q[6] - q[0]) + (q[4] - q[2])) / 2; val dy = ((q[7] - q[1]) + (q[5] - q[3])) / 2
        val sx = rx * SIDE; val sy = ry * SIDE; val tx = dx * TOP; val ty = dy * TOP
        return floatArrayOf(
            q[0] - sx - tx, q[1] - sy - ty,
            q[2] + sx - tx, q[3] + sy - ty,
            q[4] + sx + tx, q[5] + sy + ty,
            q[6] - sx + tx, q[7] - sy + ty,
        )
    }

    /** [page] trimmed to the top card and straightened, or null (with why) to keep the scanner's own crop. */
    fun trim(page: Bitmap): Pair<Bitmap?, Result> {
        val scale = min(1f, WORK.toFloat() / max(page.width, page.height))
        val sw = max(1, (page.width * scale).roundToInt()); val sh = max(1, (page.height * scale).roundToInt())
        val small = Bitmap.createScaledBitmap(page, sw, sh, true)
        val px = IntArray(sw * sh).also { small.getPixels(it, 0, sw, 0, 0, sw, sh) }
        if (small !== page) small.recycle()
        val (corners, why) = find(px, sw, sh)
        if (corners == null) return null to why
        val src = FloatArray(8) { corners[it] / scale }
        val cardH = ((hypot(src[6] - src[0], src[7] - src[1]) + hypot(src[4] - src[2], src[5] - src[3])) / 2)
            .roundToInt().coerceIn(400, PhotoStore.LONG_SIDE)
        val cardW = (cardH * CARD_ASPECT).roundToInt()
        val dst = floatArrayOf(0f, 0f, cardW.toFloat(), 0f, cardW.toFloat(), cardH.toFloat(), 0f, cardH.toFloat())
        val m = Matrix()
        if (!m.setPolyToPoly(src, 0, dst, 0, 4)) return null to Result.ODD_SHAPE
        val out = Bitmap.createBitmap(cardW, cardH, Bitmap.Config.ARGB_8888)
        Canvas(out).drawBitmap(page, m, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        return out to Result.OK
    }
}
