package io.github.thisisartman.collectibles.photo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StampFindTest {
    private fun rgb(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    private val paper = rgb(236, 232, 222)
    private val faintRed = rgb(222, 175, 165)
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
    }

    @Test fun aFaintRoundStampOnAPageIsFound() {
        val p = Page(300, 400, paper)
        p.ring(150, 150, 70, 78, faintRed)           // the stamp's rim
        p.rect(110, 140, 190, 150, faintRed)         // a line of its lettering
        p.rect(60, 330, 240, 334, pen)               // handwriting under it: long and thin
        val boxes = StampFind.find(p.px, p.w, p.h)
        assertEquals(1, boxes.size)
        val b = boxes[0]
        assertTrue("around x 72..228, was ${b.left * 300}..${b.right * 300}", b.left * 300 in 55f..80f && b.right * 300 in 220f..245f)
        assertTrue("above the writing, was bottom ${b.bottom * 400}", b.bottom * 400 < 330f)
    }

    @Test fun twoStampsApartAreTwo() {
        val p = Page(300, 600, paper)
        p.ring(150, 150, 60, 68, faintRed)
        p.ring(150, 440, 60, 68, faintRed)
        assertEquals(2, StampFind.find(p.px, p.w, p.h).size)
    }

    @Test fun aBlankPageHasNoStamps() = assertEquals(0, StampFind.find(Page(200, 300, paper).px, 200, 300).size)

    @Test fun aPageIsPaperAndACardIsNot() {
        assertTrue(StampFind.isPage(Page(100, 100, paper).px))
        assertFalse(StampFind.isPage(Page(100, 100, rgb(20, 70, 170)).px))
    }

    @Test fun cleaningMakesFaintInkStrongAndPaperWhite() {
        val p = Page(120, 120, paper)
        p.ring(60, 60, 30, 40, faintRed)
        val out = StampFind.clean(p.px, p.w, p.h)
        val inkPx = out[60 * 120 + 25]                // on the ring
        val paperPx = out[5 * 120 + 5]                // corner, paper
        assertTrue("paper should be near white", StampFind.distance(paperPx, rgb(255, 255, 255)) < 30f)
        assertTrue("ink should be much stronger than the faint print", StampFind.distance(inkPx, rgb(255, 255, 255)) > StampFind.distance(faintRed, rgb(255, 255, 255)) * 1.5f)
        assertTrue("and still red", ((inkPx shr 16) and 0xFF) > (inkPx and 0xFF) + 20)
    }
}
