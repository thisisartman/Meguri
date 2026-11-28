package io.github.thisisartman.collectibles.photo

import io.github.thisisartman.collectibles.photo.StampShape.Shape
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Test
import java.io.File
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** The shape decision of the stamp crop (#221), on drawn shapes; and, when asked, a run over real photos. */
class StampShapeTest {
    private fun rgb(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    private val paper = rgb(236, 232, 222)
    private val red = rgb(200, 90, 80)
    private val pen = rgb(60, 70, 140)

    private class Page(val w: Int, val h: Int, fill: Int) {
        val px = IntArray(w * h) { fill }
        fun ring(cx: Int, cy: Int, r0: Int, r1: Int, c: Int) {
            for (y in 0 until h) for (x in 0 until w) {
                val d2 = (x - cx) * (x - cx) + (y - cy) * (y - cy)
                if (d2 in r0 * r0..r1 * r1) px[y * w + x] = c
            }
        }
        fun rect(x0: Int, y0: Int, x1: Int, y1: Int, c: Int) { for (y in y0 until y1) for (x in x0 until x1) px[y * w + x] = c }
        fun frame(x0: Int, y0: Int, x1: Int, y1: Int, t: Int, c: Int) {
            rect(x0, y0, x1, y0 + t, c); rect(x0, y1 - t, x1, y1, c); rect(x0, y0, x0 + t, y1, c); rect(x1 - t, y0, x1, y1, c)
        }
    }

    // Facts of plain shapes: a disk fills pi/4 of its box with empty corners, a square fills it
    @Test fun aDiskIsRound() = assertEquals(Shape.ROUND, StampShape.shapeOf(100, 100, (PI * 50 * 50).roundToInt(), 0f))

    @Test fun aSquareIsSquare() = assertEquals(Shape.SQUARE, StampShape.shapeOf(100, 100, 10_000, 1f))

    @Test fun aSquareWithRoundedCornersIsSquare() = assertEquals(Shape.SQUARE, StampShape.shapeOf(100, 100, 9_650, 0.4f))

    @Test fun aWideRectangleIsSquare() = assertEquals(Shape.SQUARE, StampShape.shapeOf(200, 100, 19_500, 1f))

    @Test fun anOvalIsUnsure() = assertEquals(Shape.UNSURE, StampShape.shapeOf(140, 100, (PI * 70 * 50).roundToInt(), 0.05f))

    @Test fun writingJoinedToAStampIsUnsure() {
        // A disk with a line of writing hanging off it: the box grows, the fill falls
        assertEquals(Shape.UNSURE, StampShape.shapeOf(100, 160, (PI * 50 * 50).roundToInt() + 600, 0.1f))
        // Patchy, half-filled: unsure
        assertEquals(Shape.UNSURE, StampShape.shapeOf(100, 100, 6_000, 0.5f))
        assertEquals(Shape.UNSURE, StampShape.shapeOf(0, 100, 0, 0f))
        // Round-ish but its corners part filled (a square stamp with a round design): not a circle
        assertEquals(Shape.UNSURE, StampShape.shapeOf(100, 100, 7_900, 0.26f))
    }

    @Test fun aRoundStampOnAPageIsCutAsACircle() {
        val p = Page(300, 400, paper)
        p.ring(150, 160, 70, 78, red)
        p.rect(110, 150, 190, 160, red)
        val plan = StampShape.stampPlan(p.px, p.w, p.h)
        assertNotNull(plan); plan!!
        assertEquals(Shape.ROUND, plan.shape)
        // The circle sits on the stamp: centred near (150, 160), just bigger than its 156 px rim
        val side = (plan.square.right - plan.square.left) * p.w
        assertTrue("side $side", side in 150f..180f)
        assertEquals(150f, (plan.square.left + plan.square.right) / 2 * p.w, 4f)
        assertEquals(160f, (plan.square.top + plan.square.bottom) / 2 * p.h, 4f)
    }

    @Test fun aSquareStampOnAPageIsCutAsASquareWithWhiteAround() {
        val p = Page(300, 400, paper)
        p.frame(70, 90, 230, 250, 8, red)
        p.rect(100, 160, 200, 170, red)
        val plan = StampShape.stampPlan(p.px, p.w, p.h)
        assertNotNull(plan); plan!!
        assertEquals(Shape.SQUARE, plan.shape)
        val side = (plan.square.right - plan.square.left) * p.w
        assertTrue("side $side, a margin round 160", side > 165f && side < 200f)
    }

    @Test fun aPageWithOnlyWritingIsKeptWhole() {
        val p = Page(300, 400, paper)
        p.rect(40, 100, 260, 104, pen); p.rect(40, 140, 250, 144, pen); p.rect(40, 180, 240, 184, pen)
        assertNull(StampShape.stampPlan(p.px, p.w, p.h))
    }

    @Test fun aRoundCutIsTransparentOutsideItsCircle() {
        val plan = StampShape.Plan(Shape.ROUND, StampFind.Box(0f, 0f, 1f, 1f), clean = false)
        val out = StampShape.render(IntArray(100 * 100) { red }, 100, 100, 100, 0, 0, plan)
        assertEquals(0, out[0] ushr 24)
        assertEquals(255, out[50 * 100 + 50] ushr 24)
        assertEquals(red, out[50 * 100 + 50])
    }

    @Test fun aSquareCutIsWhiteWhereThePhotoEnds() {
        val plan = StampShape.Plan(Shape.SQUARE, StampFind.Box(0f, 0f, 1f, 1f), clean = false)
        // The photo covers only the right half of the frame
        val out = StampShape.render(IntArray(50 * 100) { red }, 50, 100, 100, 50, 0, plan)
        assertEquals(0xFFFFFFFF.toInt(), out[50 * 100 + 10])
        assertEquals(red, out[50 * 100 + 70])
    }

    @Test fun aGoshuinPageOnADeskIsCutToThePage() {
        val desk = rgb(70, 60, 55)
        val p = Page(300, 400, desk)
        p.rect(60, 50, 240, 350, rgb(245, 243, 238))
        // Brush writing and a red seal on it
        p.rect(140, 80, 160, 320, rgb(20, 20, 20)); p.rect(90, 200, 130, 240, red)
        val plan = StampShape.pagePlan(p.px, p.w, p.h)
        assertNotNull(plan); plan!!
        assertEquals(Shape.SQUARE, plan.shape)
        assertTrue(!plan.clean)
        val side = (plan.square.right - plan.square.left) * p.w
        assertTrue("side $side: the page's 300 px height and a margin", side in 300f..345f)
    }

    /**
     * Not a check: with STAMPCROP_DIR set to a folder of PNGs (photos made upright and small), writes each cut
     * as the phone would make it to STAMPCROP_DIR/out, for a contact sheet. Goshuin photos are the ones whose
     * name starts with "g_".
     */
    @Test fun cutRealPhotos() {
        val dir = System.getenv("STAMPCROP_DIR")
        Assume.assumeTrue(dir != null)
        val out = File(dir, "out").apply { mkdirs() }
        val report = StringBuilder()
        for (f in File(dir).listFiles { x -> x.name.endsWith(".png") }!!.sorted()) {
            val img = javax.imageio.ImageIO.read(f)
            val w = img.width; val h = img.height
            val px = IntArray(w * h).also { img.getRGB(0, 0, w, h, it, 0, w) }
            val page = f.name.startsWith("g_")
            val blob = if (page) null else StampFind.blobs(px, w, h).firstOrNull()
            val plan = if (page) StampShape.pagePlan(px, w, h) else StampShape.stampPlan(px, w, h)
            report.append(f.name).append(',').append(plan?.shape ?: "WHOLE")
            blob?.let { report.append(",fill=%.2f,corners=%.2f,aspect=%.2f".format(it.area.toFloat() / (it.width * it.height), it.corners, it.width.toFloat() / it.height)) }
            report.append('\n')
            if (plan == null) { javax.imageio.ImageIO.write(img, "png", File(out, f.name)); continue }
            val b = plan.square
            val left = b.left * w; val top = b.top * h; val side = (b.right - b.left) * w
            val rx = max(0, left.roundToInt()); val ry = max(0, top.roundToInt())
            val rx2 = min(w, (left + side).roundToInt()); val ry2 = min(h, (top + side).roundToInt())
            val region = IntArray((rx2 - rx) * (ry2 - ry)).also { img.getRGB(rx, ry, rx2 - rx, ry2 - ry, it, 0, rx2 - rx) }
            val s = side.roundToInt()
            val cut = StampShape.render(region, rx2 - rx, ry2 - ry, s, rx - left.roundToInt(), ry - top.roundToInt(), plan)
            val o = java.awt.image.BufferedImage(s, s, java.awt.image.BufferedImage.TYPE_INT_ARGB)
            o.setRGB(0, 0, s, s, cut, 0, s)
            javax.imageio.ImageIO.write(o, "png", File(out, f.name))
        }
        File(out, "shapes.csv").writeText(report.toString())
    }
}
