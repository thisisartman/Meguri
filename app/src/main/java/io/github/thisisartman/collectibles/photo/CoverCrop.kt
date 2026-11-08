package io.github.thisisartman.collectibles.photo

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** A circle in image pixels, and how sure the detector is (the share of its rim that has an edge, 0..1). */
data class Circle(val cx: Float, val cy: Float, val r: Float, val score: Float)

/**
 * Finds a manhole cover in a photo and crops it to a circle (owner, 2026-11-08: "auto detecting and adding as a
 * circle"). A small gradient-guided Hough transform on a downscaled grey copy: every strong edge votes for centres
 * along its gradient, for radii a fifth to a half of the picture; the circle whose rim is most covered by edges wins.
 * No library: a few hundred milliseconds on a phone.
 */
object CoverCrop {
    private const val WORK = 240
    /** Below this share of the rim on an edge, it is not a cover (tuned on the owner's photos when they arrive). */
    const val MIN_SCORE = 0.35f

    /** [gray] is row-major, values 0..255, [w] by [h]. Pure, for tests. */
    fun detect(gray: IntArray, w: Int, h: Int): Circle? = raw(gray, w, h)?.takeIf { it.third }?.first

    /** The best circle and the share of the picture that is edge, before any threshold (for tuning). */
    fun raw(gray: IntArray, w: Int, h: Int): Triple<Circle, Float, Boolean>? {
        val mag = FloatArray(w * h); val gx = FloatArray(w * h); val gy = FloatArray(w * h)
        var maxMag = 0f
        for (y in 1 until h - 1) for (x in 1 until w - 1) {
            fun p(dx: Int, dy: Int) = gray[(y + dy) * w + x + dx].toFloat()
            val sx = (p(1, -1) + 2 * p(1, 0) + p(1, 1)) - (p(-1, -1) + 2 * p(-1, 0) + p(-1, 1))
            val sy = (p(-1, 1) + 2 * p(0, 1) + p(1, 1)) - (p(-1, -1) + 2 * p(0, -1) + p(1, -1))
            val i = y * w + x
            gx[i] = sx; gy[i] = sy; mag[i] = hypot(sx, sy); if (mag[i] > maxMag) maxMag = mag[i]
        }
        if (maxMag == 0f) return null
        val edge = maxMag * 0.25f
        val rMin = (min(w, h) * 0.2f).roundToInt(); val rMax = (min(w, h) * 0.5f).roundToInt()
        val step = 2
        val nr = (rMax - rMin) / step + 1
        val acc = IntArray(w * h * nr)
        for (y in 1 until h - 1) for (x in 1 until w - 1) {
            val i = y * w + x
            if (mag[i] < edge) continue
            val ux = gx[i] / mag[i]; val uy = gy[i] / mag[i]
            for (k in 0 until nr) {
                val r = rMin + k * step
                for (sgn in intArrayOf(1, -1)) {
                    val cx = (x + sgn * ux * r).roundToInt(); val cy = (y + sgn * uy * r).roundToInt()
                    if (cx in 0 until w && cy in 0 until h) acc[(k * h + cy) * w + cx]++
                }
            }
        }
        // The best few centres by votes, then each is scored by how much of its rim sits on an edge
        val top = (0 until acc.size).sortedByDescending { acc[it] }.take(40)
        var best: Circle?
        val cands = mutableListOf<Circle>()
        for (a in top) {
            val k = a / (w * h); val rem = a % (w * h); val cy = rem / w; val cx = rem % w
            val r = (rMin + k * step).toFloat()
            var hit = 0; val n = 90
            for (t in 0 until n) {
                val ang = 2 * Math.PI * t / n
                val px = (cx + r * cos(ang)).roundToInt(); val py = (cy + r * sin(ang)).roundToInt()
                if (px !in 1 until w - 1 || py !in 1 until h - 1) continue
                var on = false
                for (dy in -1..1) for (dx in -1..1) if (mag[(py + dy) * w + px + dx] >= edge) on = true
                if (on) hit++
            }
            cands += Circle(cx.toFloat(), cy.toFloat(), r, hit.toFloat() / n)
        }
        // The outer rim of a lid beats a ring inside its design: among circles nearly as good as the best, the largest
        val top1 = cands.maxOfOrNull { it.score } ?: return null
        best = cands.filter { it.score >= top1 * 0.9f }.maxByOrNull { it.r }
        // A rim on an edge only counts when edges are rare elsewhere: in noise or texture every circle looks covered
        var edges = 0
        for (v in mag) if (v >= edge) edges++
        val density = edges.toFloat() / mag.size
        // The check is on the first circle; growing only finds the frame's size
        val passed = best?.let { it.score >= MIN_SCORE && it.score >= density * 2.5f } == true
        // A lid's metal frame is a second ring just outside the coloured design: grow to the outermost clear rim
        best?.takeIf { it.score >= MIN_SCORE && it.score >= density * 2.5f }?.let { b ->
            var grown: Circle = b
            var r = b.r + 2
            while (r <= min(b.r * 1.35f, rMax.toFloat() * 1.1f)) {
                for (dy in -2..2) for (dx in -2..2) {
                    val sc = rim(mag, w, h, edge, b.cx + dx, b.cy + dy, r)
                    if (sc >= b.score * 0.7f && sc >= MIN_SCORE) grown = Circle(b.cx + dx, b.cy + dy, r, sc)
                }
                r += 2
            }
            best = grown
        }
        return best?.let { Triple(it, density, passed) }
    }

    /** The share of the circle's rim that sits on an edge. */
    private fun rim(mag: FloatArray, w: Int, h: Int, edge: Float, cx: Float, cy: Float, r: Float): Float {
        var hit = 0; var seen = 0; val n = 90
        for (t in 0 until n) {
            val ang = 2 * Math.PI * t / n
            val px = (cx + r * cos(ang)).roundToInt(); val py = (cy + r * sin(ang)).roundToInt()
            if (px !in 1 until w - 1 || py !in 1 until h - 1) continue
            seen++
            var on = false
            for (dy in -1..1) for (dx in -1..1) if (mag[(py + dy) * w + px + dx] >= edge) on = true
            if (on) hit++
        }
        return if (seen < n / 2) 0f else hit.toFloat() / n
    }

    /** The cover in [bmp], or null when no clear circle is found. */
    fun find(bmp: Bitmap): Circle? {
        val s = WORK.toFloat() / max(bmp.width, bmp.height)
        val small = Bitmap.createScaledBitmap(bmp, max(1, (bmp.width * s).roundToInt()), max(1, (bmp.height * s).roundToInt()), true)
        val px = IntArray(small.width * small.height)
        small.getPixels(px, 0, small.width, 0, 0, small.width, small.height)
        val gray = IntArray(px.size) { val c = px[it]; ((c shr 16 and 255) * 299 + (c shr 8 and 255) * 587 + (c and 255) * 114) / 1000 }
        val c = detect(gray, small.width, small.height) ?: return null
        if (small !== bmp) small.recycle()
        return Circle(c.cx / s, c.cy / s, c.r / s, c.score)
    }

    /** The cover cut out as a circle, transparent outside; null when no clear circle is found. */
    fun crop(bmp: Bitmap): Bitmap? {
        val c = find(bmp) ?: return null
        val r = (c.r * 1.03f).roundToInt()
        val side = 2 * r
        val out = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        canvas.drawCircle(r.toFloat(), r.toFloat(), r.toFloat(), paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(bmp, (r - c.cx), (r - c.cy), paint)
        return out
    }
}
