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

    /**
     * A card frame's blue, not the pale blue of a card box or white: Niigata frames are about (20,70,170), Tokyo's
     * (63,99,125) with a steel blue top band (100,140,170), the owner's box (130,200,230). The red channel is what
     * sets the box apart (owner's 27 photos: red under 100 missed Tokyo's top band on 13 cards).
     */
    fun isFrameBlue(argb: Int): Boolean {
        val r = (argb shr 16) and 0xFF; val g = (argb shr 8) and 0xFF; val b = argb and 0xFF
        return r < 120 && b - r > 40 && b > 100 && b >= g
    }

    /** Share of points just inside each edge of [q] that are frame: a real frame is blue on all four sides. */
    private fun edgesBlue(inFrame: (Int, Int) -> Boolean, q: FloatArray): FloatArray {
        val out = FloatArray(4)
        for (e in 0 until 4) {
            val a = e; val b = (e + 1) % 4; val opp = (e + 3) % 4
            val ax = q[2 * a]; val ay = q[2 * a + 1]; val bx = q[2 * b]; val by = q[2 * b + 1]
            val ix = (q[2 * opp] - ax) * 0.004f; val iy = (q[2 * opp + 1] - ay) * 0.004f
            var hits = 0; val n = 40
            for (k in 0 until n) {
                val t = 0.15f + 0.7f * k / (n - 1)
                val x = (ax + (bx - ax) * t + ix).roundToInt(); val y = (ay + (by - ay) * t + iy).roundToInt()
                var hit = false
                for (dy in -2..2) for (dx in -2..2) if (inFrame(x + dx, y + dy)) hit = true
                if (hit) hits++
            }
            out[e] = hits.toFloat() / n
        }
        return out
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
        // Blue along all four sides, or it isn't a whole frame (a band and a side of another card, say)
        val sides = edgesBlue({ x, y -> x in 0 until w && y in 0 until h && label[y * w + x] == best }, q)
        if (sides.any { it < 0.6f }) return null to Result.ODD_SHAPE
        val e = expand(q)
        // A border a little past the scanner's page is filled white, like the card's own border
        val slackX = 0.06f * w; val slackY = 0.06f * h
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

    /**
     * [page] shrunk to [w] by [h] in halving steps. One bilinear step from a phone page to 320 px reads only a few
     * pixels per output pixel and skipped the card's thin side bands (13 of the owner's Tokyo pages failed); each
     * halving averages every pixel, like the prototype's thumbnail (22 of 31 pages trimmed on the laptop probe).
     */
    private fun shrink(page: Bitmap, w: Int, h: Int): Bitmap {
        var cur = page
        while (cur.width / 2 >= w && cur.height / 2 >= h) {
            val next = Bitmap.createScaledBitmap(cur, cur.width / 2, cur.height / 2, true)
            if (cur !== page) cur.recycle()
            cur = next
        }
        if (cur.width == w && cur.height == h) return cur
        val out = Bitmap.createScaledBitmap(cur, w, h, true)
        if (cur !== page && cur !== out) cur.recycle()
        return out
    }

    /**
     * Whether [page] shows a manhole card at all (a large patch of frame blue), even when it can't be trimmed:
     * a card whose code wasn't read must not be taken for a manhole cover (owner's batch, 2026-11-09: 3 were).
     */
    fun looksLikeCard(page: Bitmap): Boolean = detect(page) != Result.NO_FRAME

    /** What [find] makes of [page] looked at small: OK for a whole card, NO_FRAME for no card frame at all. */
    fun detect(page: Bitmap): Result {
        val scale = min(1f, WORK.toFloat() / max(page.width, page.height))
        val sw = max(1, (page.width * scale).roundToInt()); val sh = max(1, (page.height * scale).roundToInt())
        val small = shrink(page, sw, sh)
        val px = IntArray(sw * sh).also { small.getPixels(it, 0, sw, 0, 0, sw, sh) }
        if (small !== page) small.recycle()
        return find(px, sw, sh).second
    }

    /** [page] trimmed to the top card and straightened, or null (with why) to keep the scanner's own crop. */
    fun trim(page: Bitmap): Pair<Bitmap?, Result> {
        val scale = min(1f, WORK.toFloat() / max(page.width, page.height))
        val sw = max(1, (page.width * scale).roundToInt()); val sh = max(1, (page.height * scale).roundToInt())
        val small = shrink(page, sw, sh)
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
        Canvas(out).apply {
            drawColor(android.graphics.Color.WHITE)
            drawBitmap(page, m, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        }
        return out to Result.OK
    }
}
