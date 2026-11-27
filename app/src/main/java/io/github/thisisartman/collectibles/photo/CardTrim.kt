package io.github.thisisartman.collectibles.photo

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Trims a scanned manhole card to the top card (owner, 2026-11-09: the scanner took in the cards under it, or the
 * box's edge). Every manhole card has a deep blue frame inside a thin white border, so: mark the blue pixels, take
 * the largest blue patch, its four outer corners, push them out by the white border, and straighten that shape to
 * a card. The border sizes come from the official card picture; the card is 63 by 88 mm. A photo of a card among
 * others (a binder page, 2026-11-28) is found first by the white border of the card in the middle ([findByBorder]).
 * Pure Kotlin, no library.
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
     *
     * First the card at the middle of the picture by its white border ([findByBorder]), which copes with the
     * neighbouring cards of a binder page; then, for a scanned page, the largest blue patch ([findByPatch]).
     */
    fun find(argb: IntArray, w: Int, h: Int): Pair<FloatArray?, Result> {
        findByBorder(argb, w, h)?.let { return it to Result.OK }
        return findByPatch(argb, w, h)
    }

    /** A card frame's outer size, wide for high: 63 by 88 mm less the white border. */
    private val FRAME_ASPECT = (63f / (1 + 2 * SIDE)) / (88f / (1 + 2 * TOP))
    private const val TILT = 0.3f

    /**
     * The card nearest the middle of the picture, found by where its narrow white border starts outside the frame
     * (owner, 2026-11-28: cards photographed in a binder page, with the neighbours' edges and a plastic sleeve, were
     * never trimmed, as the largest blue patch ran into the other cards or broke up in the sleeve's glare).
     *
     * Every pixel where a short white run starts just past a cool (blue, teal or grey) pixel is an edge point, kept
     * by which way it faces. For each side, the straight lines through many of those points on that side of the
     * middle are candidates; of every set of four, the one shaped like a card frame (about 0.70 wide for its height),
     * covering a fair share of the picture and best backed by edge points all round wins, and only if its border is
     * brighter than its frame on all four sides. The corners returned include the border. Null when unsure: no crop
     * beats a wrong one.
     */
    fun findByBorder(argb: IntArray, w: Int, h: Int): FloatArray? {
        val n = w * h
        if (w < 40 || h < 40) return null
        val white = BooleanArray(n); val cool = BooleanArray(n); val lum = IntArray(n)
        for (i in 0 until n) {
            val p = argb[i]; val r = (p shr 16) and 0xFF; val g = (p shr 8) and 0xFF; val b = p and 0xFF
            val mn = min(r, min(g, b)); val mx = max(r, max(g, b))
            white[i] = mn > 135 && mx - mn < 50
            cool[i] = !white[i] && b >= r - 8 && mx > 60
            lum[i] = (r + g + b) / 3
        }
        val cx = w / 2f; val cy = h / 2f
        val maxRun = 0.09f * max(w, h)
        // dx, dy: the way out from the frame to the border, for the right, left, bottom and top sides
        val dirs = arrayOf(intArrayOf(1, 0), intArrayOf(-1, 0), intArrayOf(0, 1), intArrayOf(0, -1))
        val cands = arrayOfNulls<List<FloatArray>>(4)
        val points = arrayOfNulls<FloatArray>(4)
        fun at(m: BooleanArray, x: Int, y: Int) = x in 0 until w && y in 0 until h && m[y * w + x]
        val run = IntArray(n)
        for (s in 0 until 4) {
            val dx = dirs[s][0]; val dy = dirs[s][1]
            // White run length from each pixel going outward
            if (dx != 0) for (y in 0 until h) {
                var acc = 0
                val xs = if (dx > 0) (w - 1 downTo 0) else (0 until w)
                for (x in xs) { val i = y * w + x; acc = if (white[i]) acc + 1 else 0; run[i] = acc }
            } else for (x in 0 until w) {
                var acc = 0
                val ys = if (dy > 0) (h - 1 downTo 0) else (0 until h)
                for (y in ys) { val i = y * w + x; acc = if (white[i]) acc + 1 else 0; run[i] = acc }
            }
            val pts = ArrayList<Float>()
            for (y in 0 until h) for (x in 0 until w) {
                val i = y * w + x
                if (!white[i] || run[i] < 2 || run[i] > maxRun) continue
                if (!at(cool, x - dx, y - dy)) continue
                if (!at(cool, x - 2 * dx, y - 2 * dy) && !at(cool, x - 3 * dx, y - 3 * dy)) continue
                pts.add(x.toFloat()); pts.add(y.toFloat())
            }
            points[s] = pts.toFloatArray()
            val vertical = s < 2
            val mid = if (vertical) cx else cy
            val us = ArrayList<Float>(); val vs = ArrayList<Float>()
            for (k in 0 until pts.size / 2) {
                val x = pts[2 * k]; val y = pts[2 * k + 1]
                val u = if (vertical) x else y
                // Only this side of the middle: the card nearest the middle, not a neighbour's same side
                if ((s == 0 || s == 2) && u <= mid) continue
                if ((s == 1 || s == 3) && u >= mid) continue
                us.add(u); vs.add(if (vertical) y - cy else x - cx)
            }
            val c = lines(us, vs, 0.12f * (if (vertical) h else w), max(w, h))
            if (c.isEmpty()) return null
            cands[s] = c
        }
        val rC = cands[0]!!; val lC = cands[1]!!; val bC = cands[2]!!; val tC = cands[3]!!
        val combos = ArrayList<Pair<FloatArray, Float>>()
        for (l in lC) for (r in rC) for (t in tC) for (b in bC) {
            val q = FloatArray(8)
            if (!meet(l, t, cx, cy, q, 0) || !meet(r, t, cx, cy, q, 2) || !meet(r, b, cx, cy, q, 4) || !meet(l, b, cx, cy, q, 6)) continue
            val top = hypot(q[2] - q[0], q[3] - q[1]); val bottom = hypot(q[4] - q[6], q[5] - q[7])
            val left = hypot(q[6] - q[0], q[7] - q[1]); val right = hypot(q[4] - q[2], q[5] - q[3])
            if (min(top, bottom) <= 0f || min(left, right) <= 0f) continue
            // Opposite sides about as long as each other: a mild slant at most
            if (min(top, bottom) / max(top, bottom) < 0.8f || min(left, right) / max(left, right) < 0.8f) continue
            val fw = (top + bottom) / 2; val fh = (left + right) / 2
            val off = abs(fw / fh / FRAME_ASPECT - 1)
            if (off > 0.12f) continue
            val area = fw * fh / (w * h)
            if (area < 0.2f) continue
            // Edge points along each side: top, right, bottom, left
            val cov = floatArrayOf(
                coverage(points[3]!!, q, 0, 1), coverage(points[0]!!, q, 1, 2),
                coverage(points[2]!!, q, 2, 3), coverage(points[1]!!, q, 3, 0),
            )
            val least = cov.min()
            if (least < 0.2f) continue
            combos.add(q to least + cov.average().toFloat() + area - 2 * off)
        }
        if (combos.isEmpty()) return null
        combos.sortByDescending { it.second }
        val best = combos[0].second
        val slackX = 0.06f * w; val slackY = 0.06f * h
        for ((q, score) in combos) {
            // Only a near rival of the best: a much weaker set of lines is a guess
            if (score < best - 0.1f) break
            if (!borderBrighter(lum, w, h, q)) continue
            val e = expand(q)
            val inside = (0 until 4).all { k ->
                e[2 * k] in -slackX..w + slackX && e[2 * k + 1] in -slackY..h + slackY
            }
            if (inside) return e
        }
        return null
    }

    /**
     * Lines u = u0 + t * v through the points (u, v) backed by at least [need] of them, at most six, the best
     * backed first, as (u0, t, support): a small Hough count over tilts up to [TILT], three pixels wide.
     */
    private fun lines(us: List<Float>, vs: List<Float>, need: Float, size: Int): List<FloatArray> {
        if (us.isEmpty()) return emptyList()
        val pad = (size * TILT).toInt() + 4; val bins = size + 2 * pad
        val best = HashMap<Int, FloatArray>()
        val hist = IntArray(bins)
        for (ti in -15..15) {
            val t = ti * 0.02f
            java.util.Arrays.fill(hist, 0)
            for (k in us.indices) {
                val b = (us[k] - t * vs[k]).roundToInt() + pad
                if (b in 0 until bins) hist[b]++
            }
            for (b in 1 until bins - 1) {
                val sum = hist[b - 1] + hist[b] + hist[b + 1]
                if (sum < need) continue
                val u0 = b - pad
                val old = best[u0]
                if (old == null || sum > old[2]) best[u0] = floatArrayOf(u0.toFloat(), t, sum.toFloat())
            }
        }
        val kept = ArrayList<FloatArray>()
        for (c in best.values.sortedByDescending { it[2] }) {
            if (kept.all { abs(it[0] - c[0]) > 4f }) kept.add(c)
            if (kept.size == 6) break
        }
        return kept
    }

    /** Where vertical line [v] (x = a + t (y - cy)) meets horizontal line [hz] (y = b + s (x - cx)), into [out] at [at]. */
    private fun meet(v: FloatArray, hz: FloatArray, cx: Float, cy: Float, out: FloatArray, at: Int): Boolean {
        val a = v[0]; val t = v[1]; val b = hz[0]; val s = hz[1]
        val d = 1 - t * s
        if (abs(d) < 1e-3f) return false
        val x = (a + t * (b - cy) - t * s * cx) / d
        out[at] = x; out[at + 1] = b + s * (x - cx)
        return true
    }

    /** Share of the side from corner [i] to corner [j] of [q], pixel by pixel, with an edge point within 1.5 px. */
    private fun coverage(pts: FloatArray, q: FloatArray, i: Int, j: Int): Float {
        val ax = q[2 * i]; val ay = q[2 * i + 1]; val dx = q[2 * j] - ax; val dy = q[2 * j + 1] - ay
        val len = hypot(dx, dy)
        if (len < 1f) return 0f
        val seen = BooleanArray(len.toInt() + 1)
        for (k in 0 until pts.size / 2) {
            val px = pts[2 * k] - ax; val py = pts[2 * k + 1] - ay
            val t = (px * dx + py * dy) / (len * len)
            if (t < 0f || t > 1f) continue
            if (abs(py * dx - px * dy) / len > 1.5f) continue
            seen[(t * len).toInt()] = true
        }
        return seen.count { it }.toFloat() / len
    }

    /** Whether, along at least half of every side of frame [q], just outside is clearly brighter than just inside. */
    private fun borderBrighter(lum: IntArray, w: Int, h: Int, q: FloatArray): Boolean {
        val mx = (q[0] + q[2] + q[4] + q[6]) / 4; val my = (q[1] + q[3] + q[5] + q[7]) / 4
        for (e in 0 until 4) {
            val ax = q[2 * e]; val ay = q[2 * e + 1]; val bx = q[2 * ((e + 1) % 4)]; val by = q[2 * ((e + 1) % 4) + 1]
            var nx = by - ay; var ny = ax - bx
            val nl = hypot(nx, ny); if (nl <= 0f) return false
            nx /= nl; ny /= nl
            if (((ax + bx) / 2 - mx) * nx + ((ay + by) / 2 - my) * ny < 0) { nx = -nx; ny = -ny }
            var hits = 0; val count = 30
            for (k in 0 until count) {
                val f = 0.1f + 0.8f * k / (count - 1)
                val px = ax + (bx - ax) * f; val py = ay + (by - ay) * f
                val ix = (px - 2 * nx).roundToInt(); val iy = (py - 2 * ny).roundToInt()
                val ox = (px + 2 * nx).roundToInt(); val oy = (py + 2 * ny).roundToInt()
                if (ix !in 0 until w || iy !in 0 until h || ox !in 0 until w || oy !in 0 until h) continue
                val inside = lum[iy * w + ix]; val outside = lum[oy * w + ox]
                if (outside - inside > 30 && outside > 120) hits++
            }
            if (hits < count / 2) return false
        }
        return true
    }

    /** The largest blue patch's outer corners, as a scanned page shows a card ([find]'s fallback). */
    fun findByPatch(argb: IntArray, w: Int, h: Int): Pair<FloatArray?, Result> {
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
