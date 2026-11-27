package io.github.thisisartman.collectibles.photo

import org.junit.Test
import java.awt.Color
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Runs [CardTrim.find] over real card photos and writes each crop and a contact sheet, to look at by eye. Only runs
 * when CARDTRIM_PHOTOS names a folder of upright pictures (PNG or JPEG, EXIF already applied, as the app decodes them);
 * the crops go to CARDTRIM_OUT (default: an "out" folder beside it). Not a pass/fail test: the eye is the check.
 */
class CardTrimPhotosTest {
    private fun argb(b: BufferedImage) = b.getRGB(0, 0, b.width, b.height, null, 0, b.width)

    private fun scaled(src: BufferedImage, w: Int, h: Int): BufferedImage {
        val o = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
        o.createGraphics().apply {
            setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            drawImage(src, 0, 0, w, h, null); dispose()
        }
        return o
    }

    /** As [CardTrim]'s shrink: halving steps, then one last scale. */
    private fun shrink(page: BufferedImage, w: Int, h: Int): BufferedImage {
        var cur = page
        while (cur.width / 2 >= w && cur.height / 2 >= h) cur = scaled(cur, cur.width / 2, cur.height / 2)
        return if (cur.width == w && cur.height == h) cur else scaled(cur, w, h)
    }

    /** The homography taking the unit rectangle (0,0)-(cw,ch) to quad [q]: 8 unknowns by elimination. */
    private fun homography(q: FloatArray, cw: Int, ch: Int): DoubleArray {
        val src = doubleArrayOf(0.0, 0.0, cw.toDouble(), 0.0, cw.toDouble(), ch.toDouble(), 0.0, ch.toDouble())
        val a = Array(8) { DoubleArray(9) }
        for (k in 0 until 4) {
            val x = src[2 * k]; val y = src[2 * k + 1]; val u = q[2 * k].toDouble(); val v = q[2 * k + 1].toDouble()
            a[2 * k] = doubleArrayOf(x, y, 1.0, 0.0, 0.0, 0.0, -u * x, -u * y, u)
            a[2 * k + 1] = doubleArrayOf(0.0, 0.0, 0.0, x, y, 1.0, -v * x, -v * y, v)
        }
        for (c in 0 until 8) {
            val p = (c until 8).maxByOrNull { kotlin.math.abs(a[it][c]) }!!
            val t = a[c]; a[c] = a[p]; a[p] = t
            for (r in 0 until 8) if (r != c) {
                val f = a[r][c] / a[c][c]
                for (k in c until 9) a[r][k] -= f * a[c][k]
            }
        }
        return DoubleArray(8) { a[it][8] / a[it][it] }
    }

    private fun warp(img: BufferedImage, q: FloatArray, cw: Int, ch: Int): BufferedImage {
        val m = homography(q, cw, ch)
        val out = BufferedImage(cw, ch, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until ch) for (x in 0 until cw) {
            val d = m[6] * x + m[7] * y + 1
            val u = ((m[0] * x + m[1] * y + m[2]) / d).roundToInt(); val v = ((m[3] * x + m[4] * y + m[5]) / d).roundToInt()
            out.setRGB(x, y, if (u in 0 until img.width && v in 0 until img.height) img.getRGB(u, v) else 0xFFFFFF)
        }
        return out
    }

    @Test fun cropsOfRealPhotos() {
        val dir = System.getenv("CARDTRIM_PHOTOS")?.let(::File)?.takeIf { it.isDirectory } ?: return
        val out = System.getenv("CARDTRIM_OUT")?.let(::File) ?: File(dir.parentFile, "out")
        out.mkdirs() // files are overwritten, nothing is deleted
        val files = dir.listFiles { f -> f.extension.lowercase() in setOf("png", "jpg", "jpeg") }!!.sortedBy { it.name }
        val tw = 240; val th = 336; val cols = 6; val rows = (files.size + cols - 1) / cols
        val sheet = BufferedImage(cols * tw * 2, rows * (th + 18), BufferedImage.TYPE_INT_RGB)
        val g = sheet.createGraphics().apply { color = Color.WHITE; fillRect(0, 0, sheet.width, sheet.height) }
        val report = StringBuilder("photo,result\n")
        files.forEachIndexed { i, f ->
            val img = ImageIO.read(f)
            val s = min(1f, 320f / max(img.width, img.height))
            val sw = max(1, (img.width * s).roundToInt()); val sh = max(1, (img.height * s).roundToInt())
            val small = shrink(img, sw, sh)
            val started = System.nanoTime()
            val (q, why) = CardTrim.find(argb(small), sw, sh)
            val ms = (System.nanoTime() - started) / 1_000_000
            val byBorder = CardTrim.findByBorder(argb(small), sw, sh) != null
            report.append("${f.nameWithoutExtension},$why${if (q != null && !byBorder) " (patch)" else ""},${ms}ms\n")
            val x0 = (i % cols) * tw * 2; val y0 = (i / cols) * (th + 18)
            val k = min(tw.toFloat() / sw, th.toFloat() / sh)
            g.drawImage(scaled(small, (sw * k).roundToInt(), (sh * k).roundToInt()), x0, y0, null)
            if (q != null) {
                g.color = Color.MAGENTA
                val xs = IntArray(4) { (q[2 * it] * k).roundToInt() + x0 }; val ys = IntArray(4) { (q[2 * it + 1] * k).roundToInt() + y0 }
                g.drawPolygon(xs, ys, 4)
                val big = FloatArray(8) { q[it] / s }
                val crop = warp(img, big, 630, 880)
                ImageIO.write(crop, "jpg", File(out, f.nameWithoutExtension + ".jpg"))
                g.drawImage(scaled(crop, tw, th), x0 + tw, y0, null)
            }
            g.color = Color.BLACK
            g.drawString("${f.nameWithoutExtension.take(34)} $why", x0 + 2, y0 + th + 13)
        }
        g.dispose()
        ImageIO.write(sheet, "jpg", File(out, "contact-sheet.jpg"))
        File(out, "report.csv").writeText(report.toString())
    }
}
