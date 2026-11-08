package io.github.thisisartman.collectibles.photo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.math.abs

class CardTrimTest {
    private val desk = rgb(200, 180, 150)
    private val white = rgb(250, 250, 250)
    private val blue = rgb(20, 70, 170)
    private val grey = rgb(120, 120, 120)
    private val paleBlue = rgb(130, 200, 230)

    private fun rgb(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r shl 16) or (g shl 8) or b

    private class Canvas(val w: Int, val h: Int, fill: Int) {
        val px = IntArray(w * h) { fill }
        fun rect(x0: Int, y0: Int, x1: Int, y1: Int, c: Int) { for (y in y0 until y1) for (x in x0 until x1) if (x in 0 until w && y in 0 until h) px[y * w + x] = c }
    }

    /** A card whose outer (white) edge is [x0,y0]-[x0+cw, y0+ch], frame drawn at the official insets. */
    private fun card(c: Canvas, x0: Int, y0: Int, cw: Int, ch: Int) {
        c.rect(x0, y0, x0 + cw, y0 + ch, white)
        // Outer card = frame * (1 + 2 * border share), with the shares measured on the official card picture
        // (written out here, not taken from CardTrim, so a wrong constant there fails these tests)
        val fw = cw / (1 + 2 * 0.063f); val fh = ch / (1 + 2 * 0.050f)
        val fx = x0 + ((cw - fw) / 2).toInt(); val fy = y0 + ((ch - fh) / 2).toInt()
        c.rect(fx, fy, fx + fw.toInt(), fy + fh.toInt(), blue)
        // The picture inside the frame: top band and bottom band stay blue
        c.rect(fx + 4, fy + (fh * 0.15f).toInt(), fx + fw.toInt() - 4, fy + (fh * 0.8f).toInt(), grey)
    }

    private fun assertNear(expected: Float, actual: Float, tol: Float, what: String) =
        assertEquals(what, expected, actual, tol)

    @Test fun findsTheCardOnADesk() {
        val c = Canvas(240, 300, desk)
        card(c, 40, 30, 160, 224)
        val (q, why) = CardTrim.find(c.px, c.w, c.h)
        assertEquals(CardTrim.Result.OK, why)
        assertNotNull(q)
        assertNear(40f, q!![0], 4f, "left"); assertNear(30f, q[1], 4f, "top")
        assertNear(200f, q[4], 4f, "right"); assertNear(254f, q[5], 4f, "bottom")
    }

    @Test fun findsOnlyTheTopCardOfAStackInABox() {
        val c = Canvas(240, 300, paleBlue)
        c.rect(30, 20, 220, 290, white)          // cards underneath, showing at the right and bottom
        card(c, 30, 20, 170, 238)                 // the top card
        val (q, why) = CardTrim.find(c.px, c.w, c.h)
        assertEquals(CardTrim.Result.OK, why)
        assertNear(30f, q!![0], 4f, "left"); assertNear(20f, q[1], 4f, "top")
        assertNear(200f, q[4], 4f, "right: not the stack's edge at 220"); assertNear(258f, q[5], 4f, "bottom: not 290")
    }

    @Test fun noBlueFrameIsLeftAlone() {
        val c = Canvas(200, 280, desk)
        c.rect(30, 30, 170, 250, white)
        assertEquals(CardTrim.Result.NO_FRAME, CardTrim.find(c.px, c.w, c.h).second)
    }

    @Test fun aFrameCutByThePageEdgeIsLeftAlone() {
        val c = Canvas(200, 280, desk)
        card(c, 60, 20, 170, 238)                 // runs off the right side
        val (q, why) = CardTrim.find(c.px, c.w, c.h)
        // Turned down either way: its right band is off the page, and so is its border
        assert(why == CardTrim.Result.FRAME_CUT_OFF || why == CardTrim.Result.ODD_SHAPE) { "was $why" }
        assertNull(q)
    }

    @Test fun aBluePatchThatIsNotCardShapedIsLeftAlone() {
        val c = Canvas(300, 200, desk)
        c.rect(20, 60, 280, 140, blue)            // a wide blue band
        assertEquals(CardTrim.Result.ODD_SHAPE, CardTrim.find(c.px, c.w, c.h).second)
    }

    @Test fun aFrameMissingASideIsLeftAlone() {
        val c = Canvas(240, 300, desk)
        card(c, 40, 30, 160, 224)
        c.rect(40, 30, 200, 75, white)           // the whole top band hidden (a glare, another card over it)
        assertEquals(CardTrim.Result.ODD_SHAPE, CardTrim.find(c.px, c.w, c.h).second)
    }

    @Test fun tokyosDullerBlueIsFrame() {
        assert(CardTrim.isFrameBlue(rgb(63, 99, 125)))
        // Tokyo's steel blue top band, measured on the owner's photos
        assert(CardTrim.isFrameBlue(rgb(106, 147, 177)))
        assert(CardTrim.isFrameBlue(rgb(97, 133, 159)))
    }

    @Test fun paleBlueAndWhiteAreNotFrame() {
        assert(!CardTrim.isFrameBlue(paleBlue)); assert(!CardTrim.isFrameBlue(white)); assert(CardTrim.isFrameBlue(blue))
        assert(abs(CardTrim.CARD_ASPECT - 0.716f) < 0.01f)
    }
}
