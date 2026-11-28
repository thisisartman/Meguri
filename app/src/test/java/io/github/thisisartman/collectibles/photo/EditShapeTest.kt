package io.github.thisisartman.collectibles.photo

import io.github.thisisartman.collectibles.photo.EditGeometry.Nudge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Circle and Square frames of the photo editor: a fixed frame with the photo moved under it. */
class EditShapeTest {
    private val bounds = EditRect(0f, 0f, 400f, 300f)

    private fun assertInside(r: EditRect, b: EditRect = bounds) =
        assertTrue("$r inside $b", r.left >= b.left - 1e-3f && r.top >= b.top - 1e-3f && r.right <= b.right + 1e-3f && r.bottom <= b.bottom + 1e-3f)

    private fun assertSquare(r: EditRect) = assertEquals(r.width, r.height, 1e-3f)

    @Test fun aFreeBoxBecomesASquareInside() {
        val s = EditGeometry.toShape(EditRect(0f, 0f, 400f, 300f), bounds, 32f)
        assertSquare(s); assertInside(s)
        assertEquals(300f, s.width, 1e-3f)
        assertEquals(200f, s.cx, 1e-3f)
    }

    @Test fun draggingMovesTheBoxButNeverPastThePhoto() {
        val r = EditRect(100f, 100f, 200f, 200f)
        val moved = EditGeometry.panZoom(r, 50f, -20f, 1f, bounds, 32f)
        assertEquals(EditRect(150f, 80f, 250f, 180f), moved)
        val far = EditGeometry.panZoom(r, 1000f, 1000f, 1f, bounds, 32f)
        assertSquare(far); assertInside(far)
        assertEquals(400f, far.right, 1e-3f); assertEquals(300f, far.bottom, 1e-3f)
    }

    @Test fun pinchingOutZoomsInAboutTheCentre() {
        val r = EditRect(100f, 100f, 200f, 200f)
        val z = EditGeometry.panZoom(r, 0f, 0f, 2f, bounds, 32f)
        assertEquals(50f, z.width, 1e-3f)
        assertEquals(150f, z.cx, 1e-3f); assertEquals(150f, z.cy, 1e-3f)
    }

    @Test fun zoomIsClampedSoTheFrameStaysCovered() {
        val r = EditRect(100f, 100f, 200f, 200f)
        // Out: no bigger than the photo's short side, moved back inside
        val out = EditGeometry.panZoom(r, 0f, 0f, 0.1f, bounds, 32f)
        assertEquals(300f, out.width, 1e-3f); assertInside(out); assertSquare(out)
        // In: no smaller than the smallest box
        val tight = EditGeometry.panZoom(r, 0f, 0f, 100f, bounds, 32f)
        assertEquals(32f, tight.width, 1e-3f)
        // A bad pinch reading changes nothing
        assertEquals(r, EditGeometry.panZoom(r, 0f, 0f, Float.NaN, bounds, 32f))
        assertEquals(r, EditGeometry.panZoom(r, 0f, 0f, 0f, bounds, 32f))
    }

    @Test fun aStraightenedPhotosSmallerBoundsAreKept() {
        val a = EditGeometry.allowed(400f, 300f, 10f)
        val r = EditGeometry.panZoom(EditRect(0f, 0f, 300f, 300f), -50f, -50f, 1f, a, 32f)
        assertInside(r, a); assertSquare(r)
    }

    @Test fun nudgesForAScreenReader() {
        val r = EditRect(100f, 100f, 200f, 200f)
        assertEquals(90f, EditGeometry.nudge(r, Nudge.LEFT, bounds, 32f).left, 1e-3f)
        assertEquals(110f, EditGeometry.nudge(r, Nudge.RIGHT, bounds, 32f).left, 1e-3f)
        assertEquals(90f, EditGeometry.nudge(r, Nudge.UP, bounds, 32f).top, 1e-3f)
        assertEquals(110f, EditGeometry.nudge(r, Nudge.DOWN, bounds, 32f).top, 1e-3f)
        assertEquals(80f, EditGeometry.nudge(r, Nudge.ZOOM_IN, bounds, 32f).width, 1e-3f)
        assertEquals(125f, EditGeometry.nudge(r, Nudge.ZOOM_OUT, bounds, 32f).width, 1e-3f)
        // At the photo's edge a nudge stops there
        val edge = EditRect(0f, 0f, 100f, 100f)
        assertEquals(edge, EditGeometry.nudge(edge, Nudge.LEFT, bounds, 32f))
    }

    @Test fun theBoxFillsTheFixedFrameOnScreen() {
        val r = EditRect(100f, 50f, 300f, 250f)
        val (s, ox, oy) = EditGeometry.under(r, 1000f, 800f, 600f)
        assertEquals(3f, s, 1e-4f)
        // The box's corners land on the frame's, centred in the view
        assertEquals(200f, r.left * s + ox, 1e-3f); assertEquals(800f, r.right * s + ox, 1e-3f)
        assertEquals(100f, r.top * s + oy, 1e-3f); assertEquals(700f, r.bottom * s + oy, 1e-3f)
    }
}
