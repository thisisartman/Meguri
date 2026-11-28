package io.github.thisisartman.collectibles.photo

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Stamps on a notebook page (owner, 2026-11-09: station, museum and ferry stamps in one notebook, some faint).
 * Ink is whatever is darker or more coloured than the paper around it; a stamp is a large compact patch of ink.
 * Writing next to a stamp can join its box: the person fixes that in the review step, by design (owner: "halfway").
 * Pure Kotlin on row-major ARGB, so it is tested on a laptop; prototyped on the owner's 43 photos.
 */
object StampFind {
    /** A box as shares of the page's width and height (0..1), so it fits the full-size photo too. */
    data class Box(val left: Float, val top: Float, val right: Float, val bottom: Float)

    private fun r(c: Int) = (c shr 16) and 0xFF
    private fun g(c: Int) = (c shr 8) and 0xFF
    private fun b(c: Int) = c and 0xFF
    private fun lum(c: Int) = (r(c) + g(c) + b(c)) / 3f

    /** The paper behind every pixel: the bright end of its block, spread smoothly over the page. */
    class Paper(val rr: FloatArray, val gg: FloatArray, val bb: FloatArray) {
        fun l(i: Int) = (rr[i] + gg[i] + bb[i]) / 3f
    }

    fun paper(argb: IntArray, w: Int, h: Int, pct: Float = 0.92f, blocks: Int = 20): Paper {
        val bs = max(6, min(w, h) / blocks)
        val nx = (w + bs - 1) / bs; val ny = (h + bs - 1) / bs
        val pr = FloatArray(nx * ny); val pg = FloatArray(nx * ny); val pb = FloatArray(nx * ny)
        for (by in 0 until ny) for (bx in 0 until nx) {
            val ls = ArrayList<Int>(bs * bs)
            for (y in by * bs until min(h, (by + 1) * bs)) for (x in bx * bs until min(w, (bx + 1) * bs)) ls += y * w + x
            ls.sortBy { lum(argb[it]) }
            // The paper's colour: the brightest pixels of the block (ink is darker)
            val from = (ls.size * pct).toInt().coerceIn(0, ls.size - 1)
            var sr = 0f; var sg = 0f; var sb = 0f; var n = 0
            for (k in from until ls.size) { val c = argb[ls[k]]; sr += r(c); sg += g(c); sb += b(c); n++ }
            val j = by * nx + bx; pr[j] = sr / n; pg[j] = sg / n; pb[j] = sb / n
        }
        val rr = FloatArray(w * h); val gg = FloatArray(w * h); val bb = FloatArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            // Bilinear between block centres, so block edges don't show as steps
            val fx = ((x + 0.5f) / bs - 0.5f).coerceIn(0f, (nx - 1).toFloat()); val fy = ((y + 0.5f) / bs - 0.5f).coerceIn(0f, (ny - 1).toFloat())
            val x0 = fx.toInt(); val y0 = fy.toInt(); val x1 = min(nx - 1, x0 + 1); val y1 = min(ny - 1, y0 + 1)
            val ax = fx - x0; val ay = fy - y0
            fun mix(a: FloatArray) = (a[y0 * nx + x0] * (1 - ax) + a[y0 * nx + x1] * ax) * (1 - ay) + (a[y1 * nx + x0] * (1 - ax) + a[y1 * nx + x1] * ax) * ay
            val i = y * w + x; rr[i] = mix(pr); gg[i] = mix(pg); bb[i] = mix(pb)
        }
        return Paper(rr, gg, bb)
    }

    /** How much each pixel is ink: darker than its paper, or coloured where the paper isn't (faint red, blue, green). */
    fun ink(argb: IntArray, p: Paper): FloatArray = FloatArray(argb.size) { i ->
        val c = argb[i]; val pl = max(1f, p.l(i))
        val dark = ((pl - lum(c)) / pl).coerceIn(0f, 1f)
        val m = lum(c); val pm = p.l(i)
        val chroma = (abs((r(c) - m) - (p.rr[i] - pm)) + abs((g(c) - m) - (p.gg[i] - pm)) + abs((b(c) - m) - (p.bb[i] - pm))) / 255f
        max(dark * 1.4f, chroma * 1.1f)
    }

    /** Whether the picture is mostly paper: bright and plain over a large part (a notebook page, not a card or a lid). */
    fun isPage(argb: IntArray): Boolean {
        var paper = 0
        for (c in argb) {
            val mx = max(r(c), max(g(c), b(c))); val mn = min(r(c), min(g(c), b(c)))
            if (lum(c) > 165 && mx > 0 && (mx - mn).toFloat() / mx < 0.16f) paper++
        }
        return paper > argb.size * 0.35f
    }

    /**
     * A stamp as found: its padded [box], and the patch of ink itself for telling its shape ([StampShape]): its
     * bounds in pixels ([x0]..[x1], [y0]..[y1], inclusive), the area inside its outline taken row by row ([area]),
     * and how much of its four corner squares that fills ([corners], 0..1: a round stamp leaves them empty, a
     * square one fills them).
     */
    data class Blob(val box: Box, val x0: Int, val y0: Int, val x1: Int, val y1: Int, val area: Int, val corners: Float) {
        val width get() = x1 - x0 + 1
        val height get() = y1 - y0 + 1
    }

    /** The stamps on a page, biggest first. */
    fun find(argb: IntArray, w: Int, h: Int): List<Box> = blobs(argb, w, h).map { it.box }

    /** The stamps on a page with their shapes' facts, biggest first. */
    fun blobs(argb: IntArray, w: Int, h: Int): List<Blob> {
        val p = paper(argb, w, h)
        val ink = ink(argb, p)
        val paperish = BooleanArray(w * h) { p.l(it) > 150f }
        val onPaper = ink.indices.filter { paperish[it] }.map { ink[it] }.sorted()
        if (onPaper.isEmpty()) return emptyList()
        val thr = max(0.08f, onPaper[(onPaper.size * 0.92f).toInt().coerceAtMost(onPaper.size - 1)])
        var m = BooleanArray(w * h) { paperish[it] && ink[it] > thr }
        // Close the small breaks in a faint print, then fill what each frame encloses
        val k = max(3, (min(w, h) * 0.02f).roundToInt())
        m = boxFilter(boxFilter(m, w, h, k) { it > 0 }, w, h, k) { it == (2 * k + 1) * (2 * k + 1) }
        m = fillHoles(m, w, h)
        return patches(m, w, h)
    }

    /** The large, compact patches of [m]: writing is long and thin, specks are small. Biggest first. */
    internal fun patches(m: BooleanArray, w: Int, h: Int): List<Blob> {
        val out = ArrayList<Pair<Int, Blob>>()
        val label = IntArray(w * h); val queue = IntArray(w * h); var cur = 0
        for (s in 0 until w * h) {
            if (!m[s] || label[s] != 0) continue
            cur++; var head = 0; var tail = 0; queue[tail++] = s; label[s] = cur
            var x0 = w; var y0 = h; var x1 = -1; var y1 = -1
            while (head < tail) {
                val i = queue[head++]; val x = i % w; val y = i / w
                if (x < x0) x0 = x; if (x > x1) x1 = x; if (y < y0) y0 = y; if (y > y1) y1 = y
                if (x > 0 && m[i - 1] && label[i - 1] == 0) { label[i - 1] = cur; queue[tail++] = i - 1 }
                if (x < w - 1 && m[i + 1] && label[i + 1] == 0) { label[i + 1] = cur; queue[tail++] = i + 1 }
                if (y > 0 && m[i - w] && label[i - w] == 0) { label[i - w] = cur; queue[tail++] = i - w }
                if (y < h - 1 && m[i + w] && label[i + w] == 0) { label[i + w] = cur; queue[tail++] = i + w }
            }
            val bw = x1 - x0 + 1; val bh = y1 - y0 + 1
            if (bw < w * 0.15f || bh < w * 0.15f) continue
            val fill = tail.toFloat() / (bw * bh)
            if (fill < 0.45f || bw.toFloat() / bh !in 0.4f..2.5f) continue
            // A little margin, so the stamp's edge isn't cut
            val pad = 0.03f * max(bw, bh)
            val box = Box(
                ((x0 - pad) / w).coerceIn(0f, 1f), ((y0 - pad) / h).coerceIn(0f, 1f),
                ((x1 + 1 + pad) / w).coerceIn(0f, 1f), ((y1 + 1 + pad) / h).coerceIn(0f, 1f),
            )
            // Its outline, filled row by row: a faint print's gaps inside don't count, its outer shape does
            val from = IntArray(bh); val to = IntArray(bh)
            var spans = 0
            for (r in 0 until bh) {
                val row = (y0 + r) * w
                var a = -1; var b = -1
                for (x in x0..x1) if (label[row + x] == cur) { if (a < 0) a = x; b = x }
                from[r] = a; to[r] = b
                if (a >= 0) spans += b - a + 1
            }
            // The four corner squares, an eighth of the shorter side each
            val c = max(1, min(bw, bh) / 8)
            var inCorners = 0
            fun inside(x: Int, r: Int) = from[r] >= 0 && x in from[r]..to[r]
            for (dy in 0 until c) for (dx in 0 until c) {
                if (inside(x0 + dx, dy)) inCorners++
                if (inside(x1 - dx, dy)) inCorners++
                if (inside(x0 + dx, bh - 1 - dy)) inCorners++
                if (inside(x1 - dx, bh - 1 - dy)) inCorners++
            }
            out += tail to Blob(box, x0, y0, x1, y1, spans, inCorners / (4f * c * c))
        }
        return out.sortedByDescending { it.first }.map { it.second }
    }

    /** Square window of radius [k] over [m]; [keep] decides from how many set pixels it holds. Summed-area table. */
    internal fun boxFilter(m: BooleanArray, w: Int, h: Int, k: Int, keep: (Int) -> Boolean): BooleanArray {
        val sat = IntArray((w + 1) * (h + 1))
        for (y in 0 until h) {
            var row = 0
            for (x in 0 until w) { if (m[y * w + x]) row++; sat[(y + 1) * (w + 1) + x + 1] = sat[y * (w + 1) + x + 1] + row }
        }
        return BooleanArray(w * h) { i ->
            val x = i % w; val y = i / w
            val xa = max(0, x - k); val ya = max(0, y - k); val xb = min(w, x + k + 1); val yb = min(h, y + k + 1)
            val n = sat[yb * (w + 1) + xb] - sat[ya * (w + 1) + xb] - sat[yb * (w + 1) + xa] + sat[ya * (w + 1) + xa]
            // At the page's edge the window is cut: count the missing part as set, so edges don't erode away
            val full = (2 * k + 1) * (2 * k + 1); val inside = (xb - xa) * (yb - ya)
            keep(n + (full - inside))
        }
    }

    internal fun fillHoles(m: BooleanArray, w: Int, h: Int): BooleanArray {
        val outside = BooleanArray(w * h); val queue = IntArray(w * h); var head = 0; var tail = 0
        fun seed(i: Int) { if (!m[i] && !outside[i]) { outside[i] = true; queue[tail++] = i } }
        for (x in 0 until w) { seed(x); seed((h - 1) * w + x) }
        for (y in 0 until h) { seed(y * w); seed(y * w + w - 1) }
        while (head < tail) {
            val i = queue[head++]; val x = i % w; val y = i / w
            if (x > 0) seed(i - 1); if (x < w - 1) seed(i + 1); if (y > 0) seed(i - w); if (y < h - 1) seed(i + w)
        }
        return BooleanArray(w * h) { !outside[it] }
    }

    /**
     * A stamp cleaned (owner, 2026-11-09: cleaned by default, "too contrasty is fine, too faint is the problem"):
     * the paper goes white, the ink goes to full strength in its own colour made vivid; grey or black ink goes black.
     * [argb] is the stamp's box only. Returns new pixels, same size.
     */
    fun clean(argb: IntArray, w: Int, h: Int): IntArray {
        // Big blocks: a stamp's crop is mostly stamp, and a small block could sit wholly on a thick stroke
        val p = paper(argb, w, h, pct = 0.95f, blocks = 5)
        val ink = ink(argb, p)
        val sorted = ink.sortedArray()
        fun pctl(q: Float) = sorted[(sorted.size * q).toInt().coerceIn(0, sorted.size - 1)]
        val lo = pctl(0.55f); val hi = pctl(0.97f); val span = max(1e-3f, hi - lo)
        // The ink's colour from its strongest pixels (faint ones are mostly paper)
        val strong = pctl(0.93f)
        val rs = ArrayList<Int>(); val gs = ArrayList<Int>(); val bs = ArrayList<Int>()
        for (i in argb.indices) if (ink[i] >= strong) { rs += r(argb[i]); gs += g(argb[i]); bs += b(argb[i]) }
        fun med(l: ArrayList<Int>) = if (l.isEmpty()) 60f else l.sorted()[l.size / 2].toFloat()
        var cr = med(rs); var cg = med(gs); var cb = med(bs)
        val grey = (cr + cg + cb) / 3f
        cr = grey + (cr - grey) * 2.4f; cg = grey + (cg - grey) * 2.4f; cb = grey + (cb - grey) * 2.4f
        val top = max(cr, max(cg, cb))
        if (top > 95f) { val k = 95f / top; cr *= k; cg *= k; cb *= k }
        cr = cr.coerceIn(0f, 255f); cg = cg.coerceIn(0f, 255f); cb = cb.coerceIn(0f, 255f)
        if (max(cr, max(cg, cb)) - min(cr, min(cg, cb)) < 25f) { cr = 35f; cg = 35f; cb = 35f }
        return IntArray(argb.size) { i ->
            val s = ((ink[i] - lo) / span).coerceIn(0f, 1f).pow(0.7f)
            val outR = (255 - (255 - cr) * s).roundToInt(); val outG = (255 - (255 - cg) * s).roundToInt(); val outB = (255 - (255 - cb) * s).roundToInt()
            (0xFF shl 24) or (outR shl 16) or (outG shl 8) or outB
        }
    }

    /** For tests: how far apart two colours are. */
    internal fun distance(a: Int, b2: Int) = sqrt(((r(a) - r(b2)) * (r(a) - r(b2)) + (g(a) - g(b2)) * (g(a) - g(b2)) + (b(a) - b(b2)) * (b(a) - b(b2))).toFloat())
}
