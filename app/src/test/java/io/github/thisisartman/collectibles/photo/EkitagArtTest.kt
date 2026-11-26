package io.github.thisisartman.collectibles.photo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EkitagArtTest {
    private fun rgb(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    private val page = rgb(242, 242, 242)
    private val white = rgb(255, 255, 255)
    private val red = rgb(230, 70, 30)
    private val yellow = rgb(250, 210, 20)

    private class Shot(val w: Int, val h: Int, fill: Int) {
        val px = IntArray(w * h) { fill }
        fun rect(x0: Int, y0: Int, x1: Int, y1: Int, c: Int) { for (y in y0 until y1) for (x in x0 until x1) px[y * w + x] = c }
        fun disc(cx: Int, cy: Int, r: Int, c: Int) {
            for (y in cy - r..cy + r) for (x in cx - r..cx + r) if ((x - cx) * (x - cx) + (y - cy) * (y - cy) <= r * r) px[y * w + x] = c
        }
    }

    @Test fun findsARingedStampAndSkipsTheWhiteHeader() {
        val s = Shot(300, 650, page)
        s.rect(0, 30, 300, 70, white)          // the app's white header bar, edge to edge
        s.disc(150, 220, 100, white)            // the stamp's white outline
        s.disc(150, 220, 90, red)               // the stamp itself
        s.disc(150, 220, 40, white)             // white inside the stamp's design
        val f = EkitagArt.find(s.px, s.w, s.h)
        assertNotNull(f)
        assertEquals(50, f!!.x, 2); assertEquals(120, f.y, 2); assertEquals(201, f.w, 3); assertEquals(201, f.h, 3)
        // The middle of the stamp is inside its shape even though it is white too; a box corner is outside
        assertTrue(f.inside[(f.h / 2) * f.w + f.w / 2])
        assertTrue(!f.inside[0])
        assertEquals(red, EkitagArt.ink(s.px, s.w, f))
    }

    @Test fun findsACollaborationStampWithNoOutline() {
        val s = Shot(300, 650, page)
        s.rect(60, 120, 240, 300, yellow)
        val f = EkitagArt.find(s.px, s.w, s.h)
        assertNotNull(f)
        assertEquals(60, f!!.x, 4); assertEquals(180, f.w, 6)
    }

    @Test fun anEmptyPageHasNoStamp() {
        val s = Shot(300, 650, page)
        s.rect(0, 30, 300, 70, white)
        assertNull(EkitagArt.find(s.px, s.w, s.h))
    }

    private fun assertEquals(expected: Int, actual: Int, tol: Int) =
        assertTrue("expected $expected ± $tol, was $actual", kotlin.math.abs(expected - actual) <= tol)

    @Test fun aScreenshotsStatusBarAndCloseButtonAreNotPartOfTheStamp() {
        // The owner's 56 detail and full-screen screenshots (2026-11-25): a pale page edge to edge reads like a saved
        // image's white edge, and the status bar icons and the close button stretched "the stamp" to the whole screen
        val s = Shot(300, 650, page)
        for (x in listOf(20, 40, 60, 240, 260, 280)) s.rect(x, 6, x + 8, 16, rgb(40, 40, 40)) // status bar icons
        s.disc(150, 620, 12, rgb(40, 40, 40))  // the close button
        s.disc(150, 300, 100, white)            // the stamp's white outline
        s.disc(150, 300, 90, red)
        val f = EkitagArt.find(s.px, s.w, s.h)
        assertNotNull(f)
        assertEquals(201, f!!.w, 3); assertEquals(201, f.h, 3); assertEquals(200, f.y, 2)
    }

    @Test fun aSavedStampImageIsTheStampItself() {
        // Yahiko's and Yoshida's saved stamps (2026-11-13): white edge to edge, a red ring with white inside
        val s = Shot(400, 400, white)
        s.disc(200, 200, 190, red)
        s.disc(200, 200, 175, white)
        s.rect(150, 300, 250, 340, red)         // the station's name band
        val f = EkitagArt.find(s.px, s.w, s.h)
        assertNotNull(f)
        assertTrue(f!!.w > 370 && f.h > 370)
        assertTrue(f.inside[(f.h / 2) * f.w + f.w / 2])
        assertTrue(!f.inside[0])
        // A JPEG's off-white edge (242) reads as white too
        val j = Shot(400, 400, rgb(244, 243, 242))
        j.disc(200, 200, 190, red); j.disc(200, 200, 175, rgb(244, 243, 242))
        assertTrue(EkitagArt.find(j.px, j.w, j.h)!!.w > 370)
        // A transparent page reads the same as white
        val t = Shot(400, 400, 0)
        t.disc(200, 200, 190, red); t.disc(200, 200, 175, white)
        assertTrue(EkitagArt.find(t.px, t.w, t.h)!!.w > 370)
    }
}
