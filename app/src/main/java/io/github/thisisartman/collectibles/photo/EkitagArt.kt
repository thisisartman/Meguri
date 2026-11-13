package io.github.thisisartman.collectibles.photo

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * An Ekitag screenshot turned into a display picture (owner, 2026-11-09): the stamp cut out by its own shape and set
 * on a rounded square tinted with its ink colour, with the station's name at the bottom.
 *
 * Finding the stamp: the Ekitag app draws every stamp on a pure white outline, over a light grey page or a dimmed
 * collection, so the stamp is the largest pure white shape that doesn't touch the screen's sides (the header bar and
 * the bottom sheet do). Collaboration stamps have no outline; for those it is the largest compact shape unlike the
 * page, above the station's details. Tested on 68 of the owner's screenshots in all three of the app's views.
 */
object EkitagArt {
    private const val WORK = 540

    /** Where the stamp is in a [w] by [h] picture: its box, and which pixels of the box are inside its shape. */
    class Found(val x: Int, val y: Int, val w: Int, val h: Int, val inside: BooleanArray)

    /** [argb] row-major, [w] by [h]. Pure, for tests. */
    fun find(argb: IntArray, w: Int, h: Int): Found? {
        plain(argb, w, h)?.let { return it }
        val white = BooleanArray(w * h) { i -> i / w >= (h * 0.04f).toInt() && isWhite(argb[i]) }
        best(white, w, h)?.let { return it }
        // No white outline: the largest compact shape unlike the page's own colour, above the details
        val bg = commonColour(argb, w, h)
        var diff = BooleanArray(w * h) { i ->
            val y = i / w
            y >= (h * 0.04f).toInt() && y < (h * 0.62f).toInt() && distance(argb[i], bg) > 60
        }
        diff = dilate(dilate(erode(erode(diff, w, h), w, h), w, h), w, h)
        return best(diff, w, h)
    }

    /**
     * A picture that is only the stamp (a saved stamp image, 2026-11-13: Yahiko's and Yoshida's came out as a corner and
     * a blank blob): its border is plain white or transparent all round. The stamp is then its inked area, holes filled.
     */
    private fun plain(argb: IntArray, w: Int, h: Int): Found? {
        // Near white, not pure: a saved JPEG's white edge reads 242 to 249 (Yahiko's, 2026-11-13)
        fun blank(c: Int) = (c ushr 24) < 128 || ((c shr 16 and 0xFF) >= 230 && (c shr 8 and 0xFF) >= 230 && (c and 0xFF) >= 230)
        val border = (0 until w).flatMap { listOf(it, (h - 1) * w + it) } + (0 until h).flatMap { listOf(it * w, it * w + w - 1) }
        if (border.count { blank(argb[it]) } < border.size * 0.9f) return null
        var ink = BooleanArray(w * h) { !blank(argb[it]) && distance(argb[it], rgb(255, 255, 255)) > 60 }
        repeat(3) { ink = dilate(ink, w, h) }
        var x0 = w; var y0 = h; var x1 = -1; var y1 = -1
        for (i in 0 until w * h) if (ink[i]) { val x = i % w; val y = i / w; if (x < x0) x0 = x; if (x > x1) x1 = x; if (y < y0) y0 = y; if (y > y1) y1 = y }
        if (x1 < 0 || x1 - x0 < w * 0.3f || y1 - y0 < h * 0.3f) return null
        val bw = x1 - x0 + 1; val bh = y1 - y0 + 1
        val outside = BooleanArray(bw * bh); val queue = IntArray(bw * bh); var head = 0; var tail = 0
        fun seed(x: Int, y: Int) {
            val j = y * bw + x
            if (!outside[j] && !ink[(y0 + y) * w + x0 + x]) { outside[j] = true; queue[tail++] = j }
        }
        for (x in 0 until bw) { seed(x, 0); seed(x, bh - 1) }
        for (y in 0 until bh) { seed(0, y); seed(bw - 1, y) }
        while (head < tail) {
            val j = queue[head++]; val x = j % bw; val y = j / bw
            if (x > 0) seed(x - 1, y); if (x < bw - 1) seed(x + 1, y); if (y > 0) seed(x, y - 1); if (y < bh - 1) seed(x, y + 1)
        }
        return Found(x0, y0, bw, bh, BooleanArray(bw * bh) { !outside[it] })
    }

    /** An opaque colour, without android.graphics.Color, so the finding stays testable on a laptop. */
    private fun rgb(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r shl 16) or (g shl 8) or b

    private fun isWhite(c: Int) = (c shr 16 and 0xFF) >= 250 && (c shr 8 and 0xFF) >= 250 && (c and 0xFF) >= 250

    private fun distance(a: Int, b: Int) =
        abs((a shr 16 and 0xFF) - (b shr 16 and 0xFF)) + abs((a shr 8 and 0xFF) - (b shr 8 and 0xFF)) + abs((a and 0xFF) - (b and 0xFF))

    private fun commonColour(argb: IntArray, w: Int, h: Int): Int {
        val counts = HashMap<Int, Int>()
        for (y in 0 until h step 8) for (x in 0 until w step 8) {
            val c = argb[y * w + x]; val k = ((c shr 16 and 0xFF) / 8 shl 10) or ((c shr 8 and 0xFF) / 8 shl 5) or ((c and 0xFF) / 8)
            counts[k] = (counts[k] ?: 0) + 1
        }
        val k = counts.maxByOrNull { it.value }?.key ?: return rgb(255, 255, 255)
        return rgb((k shr 10 and 31) * 8 + 4, (k shr 5 and 31) * 8 + 4, (k and 31) * 8 + 4)
    }

    private fun erode(m: BooleanArray, w: Int, h: Int) = BooleanArray(w * h) { i ->
        val x = i % w; val y = i / w
        m[i] && x > 0 && x < w - 1 && y > 0 && y < h - 1 && m[i - 1] && m[i + 1] && m[i - w] && m[i + w]
    }

    private fun dilate(m: BooleanArray, w: Int, h: Int) = BooleanArray(w * h) { i ->
        val x = i % w; val y = i / w
        m[i] || (x > 0 && m[i - 1]) || (x < w - 1 && m[i + 1]) || (y > 0 && m[i - w]) || (y < h - 1 && m[i + w])
    }

    /** The biggest 4-connected patch that is stamp-sized, roughly square and clear of the sides, filled in. */
    private fun best(mask: BooleanArray, w: Int, h: Int): Found? {
        val label = IntArray(w * h); val queue = IntArray(w * h)
        var cur = 0; var bestArea = 0
        var bx0 = 0; var by0 = 0; var bx1 = 0; var by1 = 0; var bestLabel = 0
        for (s in 0 until w * h) {
            if (!mask[s] || label[s] != 0) continue
            cur++; var head = 0; var tail = 0; queue[tail++] = s; label[s] = cur
            var x0 = w; var y0 = h; var x1 = -1; var y1 = -1
            while (head < tail) {
                val i = queue[head++]; val x = i % w; val y = i / w
                if (x < x0) x0 = x; if (x > x1) x1 = x; if (y < y0) y0 = y; if (y > y1) y1 = y
                if (x > 0 && mask[i - 1] && label[i - 1] == 0) { label[i - 1] = cur; queue[tail++] = i - 1 }
                if (x < w - 1 && mask[i + 1] && label[i + 1] == 0) { label[i + 1] = cur; queue[tail++] = i + 1 }
                if (y > 0 && mask[i - w] && label[i - w] == 0) { label[i - w] = cur; queue[tail++] = i - w }
                if (y < h - 1 && mask[i + w] && label[i + w] == 0) { label[i + w] = cur; queue[tail++] = i + w }
            }
            if (x0 <= 2 || x1 >= w - 3) continue
            val bw = x1 - x0 + 1; val bh = y1 - y0 + 1
            if (bw < w * 0.3f || bh < w * 0.3f || bw.toFloat() / bh !in 0.6f..1.6f) continue
            if (bw * bh > bestArea) { bestArea = bw * bh; bestLabel = cur; bx0 = x0; by0 = y0; bx1 = x1; by1 = y1 }
        }
        if (bestLabel == 0) return null
        val bw = bx1 - bx0 + 1; val bh = by1 - by0 + 1
        // Fill the holes: whatever the outside can't reach from the box's edge is inside the stamp
        val outside = BooleanArray(bw * bh); var head = 0; var tail = 0
        fun seed(x: Int, y: Int) {
            val j = y * bw + x
            if (!outside[j] && label[(by0 + y) * w + bx0 + x] != bestLabel) { outside[j] = true; queue[tail++] = j }
        }
        for (x in 0 until bw) { seed(x, 0); seed(x, bh - 1) }
        for (y in 0 until bh) { seed(0, y); seed(bw - 1, y) }
        while (head < tail) {
            val j = queue[head++]; val x = j % bw; val y = j / bw
            if (x > 0) seed(x - 1, y); if (x < bw - 1) seed(x + 1, y); if (y > 0) seed(x, y - 1); if (y < bh - 1) seed(x, y + 1)
        }
        return Found(bx0, by0, bw, bh, BooleanArray(bw * bh) { !outside[it] })
    }

    /** The stamp's ink: the middle of its coloured pixels, or of all its ink when it is black or grey. */
    fun ink(argb: IntArray, w: Int, f: Found): Int {
        val rs = ArrayList<Int>(); val gs = ArrayList<Int>(); val bs = ArrayList<Int>()
        val crs = ArrayList<Int>(); val cgs = ArrayList<Int>(); val cbs = ArrayList<Int>()
        for (y in 0 until f.h) for (x in 0 until f.w) {
            if (!f.inside[y * f.w + x]) continue
            val c = argb[(f.y + y) * w + f.x + x]
            val r = c shr 16 and 0xFF; val g = c shr 8 and 0xFF; val b = c and 0xFF
            val mx = max(r, max(g, b)); val mn = min(r, min(g, b))
            if (mx >= 245) continue
            rs += r; gs += g; bs += b
            if (mx > 0 && (mx - mn).toFloat() / mx > 0.35f) { crs += r; cgs += g; cbs += b }
        }
        if (rs.isEmpty()) return rgb(60, 60, 60)
        val useColour = crs.size > 0.15f * rs.size
        fun med(l: ArrayList<Int>) = l.sorted()[l.size / 2]
        return if (useColour) rgb(med(crs), med(cgs), med(cbs)) else rgb(med(rs), med(gs), med(bs))
    }

    /**
     * The display picture for [screenshot], titled [first] over [second] (the app's language first; [second] may be
     * empty); null when no stamp is found, and the screenshot is kept. Layout from the owner's notes (Telegram 491,
     * 2026-11-09): little empty space, the stamp near the top with a little room above, the name large on two lines.
     */
    fun render(screenshot: Bitmap, first: String, second: String = "", size: Int = 1200, bold: Typeface = Typeface.DEFAULT_BOLD, regular: Typeface = Typeface.DEFAULT): Bitmap? {
        val scale = min(1f, WORK.toFloat() / screenshot.width)
        val sw = max(1, (screenshot.width * scale).roundToInt()); val sh = max(1, (screenshot.height * scale).roundToInt())
        val small = Bitmap.createScaledBitmap(screenshot, sw, sh, true)
        val px = IntArray(sw * sh).also { small.getPixels(it, 0, sw, 0, 0, sw, sh) }
        if (small !== screenshot) small.recycle()
        val f = find(px, sw, sh) ?: return null
        val inkColour = ink(px, sw, f)

        // The stamp at full size, with its shape as soft-edged alpha
        val src = Rect((f.x / scale).toInt(), (f.y / scale).toInt(), ((f.x + f.w) / scale).toInt().coerceAtMost(screenshot.width), ((f.y + f.h) / scale).toInt().coerceAtMost(screenshot.height))
        val stamp = Bitmap.createBitmap(src.width(), src.height(), Bitmap.Config.ARGB_8888)
        Canvas(stamp).apply {
            drawBitmap(screenshot, src, Rect(0, 0, src.width(), src.height()), null)
            val maskBmp = Bitmap.createBitmap(f.w, f.h, Bitmap.Config.ARGB_8888)
            maskBmp.setPixels(IntArray(f.w * f.h) { if (f.inside[it]) Color.BLACK else Color.TRANSPARENT }, 0, f.w, 0, 0, f.w, f.h)
            drawBitmap(maskBmp, null, Rect(0, 0, src.width(), src.height()), Paint(Paint.FILTER_BITMAP_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) })
            maskBmp.recycle()
        }

        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        // A pale wash of the ink colour, on a rounded square
        val tint = Color.rgb(255 - ((255 - Color.red(inkColour)) * 0.14f).toInt(), 255 - ((255 - Color.green(inkColour)) * 0.14f).toInt(), 255 - ((255 - Color.blue(inkColour)) * 0.14f).toInt())
        val r = size * 0.12f
        c.drawRoundRect(RectF(0f, 0f, size.toFloat(), size.toFloat()), r, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = tint })
        val topRoom = size * 0.045f
        val textH = size * 0.215f
        val boxH = size - topRoom - textH - size * 0.02f
        val boxW = size * 0.90f
        val k = min(boxW / stamp.width, boxH / stamp.height)
        val dw = stamp.width * k; val dh = stamp.height * k
        val left = (size - dw) / 2; val top = topRoom + (boxH - dh) / 2
        val dst = RectF(left, top, left + dw, top + dh)
        // A soft shadow under the stamp
        val shadow = stamp.extractAlpha(Paint().apply { maskFilter = BlurMaskFilter(14f / k, BlurMaskFilter.Blur.NORMAL) }, IntArray(2))
        c.drawBitmap(shadow, null, RectF(dst.left, dst.top + size * 0.009f, dst.right, dst.bottom + size * 0.009f), Paint(Paint.FILTER_BITMAP_FLAG).apply { color = Color.argb(56, 0, 0, 0) })
        shadow.recycle()
        c.drawBitmap(stamp, null, dst, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        stamp.recycle()
        // The station's name on two lines, in the ink colour; a light ink (Asaka's sky blue) is darkened until it reads
        val lum = (0.2126f * Color.red(inkColour) + 0.7152f * Color.green(inkColour) + 0.0722f * Color.blue(inkColour)) / 255f
        val textColour = if (lum > 0.42f) Color.rgb((Color.red(inkColour) * 0.42f / lum).toInt(), (Color.green(inkColour) * 0.42f / lum).toInt(), (Color.blue(inkColour) * 0.42f / lum).toInt()) else inkColour
        val maxW = size * 0.88f
        fun line(text: String, sizeShare: Float, isBold: Boolean, baseline: Float) {
            if (text.isBlank()) return
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = textColour; typeface = if (isBold) bold else regular
                textSize = size * sizeShare; textAlign = Paint.Align.CENTER
            }
            if (p.measureText(text) > maxW) p.textSize *= maxW / p.measureText(text)
            c.drawText(text, size / 2f, baseline, p)
        }
        val firstBase = size - textH + size * 0.012f + size * 0.082f
        line(first, 0.082f, true, firstBase)
        line(second, 0.056f, false, firstBase + size * 0.075f)
        return out
    }
}
