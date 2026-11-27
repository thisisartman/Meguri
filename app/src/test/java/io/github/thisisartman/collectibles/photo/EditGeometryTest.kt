package io.github.thisisartman.collectibles.photo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

/** #208: the sums behind the photo edit screen. */
class EditGeometryTest {
    private fun near(a: Float, b: Float, msg: String = "") = assertEquals(msg, a.toDouble(), b.toDouble(), 0.01)

    private fun near(a: EditRect, b: EditRect) {
        near(a.left, b.left, "left"); near(a.top, b.top, "top"); near(a.right, b.right, "right"); near(a.bottom, b.bottom, "bottom")
    }

    @Test fun aQuarterTurnSwapsTheSides() {
        assertEquals(400f to 300f, EditGeometry.turnedSize(400f, 300f, 0))
        assertEquals(300f to 400f, EditGeometry.turnedSize(400f, 300f, 1))
        assertEquals(400f to 300f, EditGeometry.turnedSize(400f, 300f, 2))
        assertEquals(300f to 400f, EditGeometry.turnedSize(400f, 300f, -1))
    }

    @Test fun theAffineTurnsTheCornersClockwiseIntoTheFrame() {
        // 400 x 300, one turn: the frame is 300 x 400, the top left lands top right
        val m = EditGeometry.affine(400f, 300f, 1, 0f)
        val (x0, y0) = EditGeometry.map(m, 0f, 0f)
        near(300f, x0); near(0f, y0)
        val (x1, y1) = EditGeometry.map(m, 400f, 300f)
        near(0f, x1); near(400f, y1)
        // No turn: nothing moves
        val id = EditGeometry.affine(400f, 300f, 0, 0f)
        val (a, b) = EditGeometry.map(id, 123f, 45f)
        near(123f, a); near(45f, b)
    }

    @Test fun turningTheBoxMatchesTurningThePicture() {
        val r = EditRect(10f, 20f, 110f, 70f)
        val m = EditGeometry.affine(400f, 300f, 1, 0f)
        val turned = EditGeometry.turnRect(r, 400f, 300f)
        // The box's corners, sent through the picture's turn, are the turned box's corners
        val (ax, ay) = EditGeometry.map(m, r.left, r.top)
        val (bx, by) = EditGeometry.map(m, r.right, r.bottom)
        near(EditRect(minOf(ax, bx), minOf(ay, by), maxOf(ax, bx), maxOf(ay, by)), turned)
    }

    @Test fun fourTurnsGiveTheSameBoxBack() {
        val r = EditRect(10f, 20f, 110f, 70f)
        var w = 400f; var h = 300f; var x = r
        repeat(4) { x = EditGeometry.turnRect(x, w, h); val t = w; w = h; h = t }
        near(r, x)
    }

    @Test fun unstraightenedKeepsTheWholePicture() {
        near(EditRect(0f, 0f, 400f, 300f), EditGeometry.allowed(400f, 300f, 0f))
    }

    @Test fun straightenedKeepsOnlyWhatIsInsideTheTurnedPicture() {
        for (deg in listOf(-15f, -7.5f, 3f, 15f)) {
            val a = EditGeometry.allowed(400f, 300f, deg)
            assertTrue(a.width < 400f && a.height < 300f)
            near(400f / 300f, a.width / a.height, "same shape")
            near(200f, a.cx); near(150f, a.cy)
            // Every corner of the allowed box, turned back by the angle, is inside the picture
            val t = Math.toRadians(-deg.toDouble())
            for ((x, y) in listOf(a.left to a.top, a.right to a.top, a.left to a.bottom, a.right to a.bottom)) {
                val dx = x - 200.0; val dy = y - 150.0
                val px = dx * cos(t) - dy * sin(t) + 200; val py = dx * sin(t) + dy * cos(t) + 150
                assertTrue("$deg: $px,$py", px >= -0.01 && px <= 400.01 && py >= -0.01 && py <= 300.01)
            }
        }
        // As big as it can be: at 15 degrees a corner of it touches the picture's edge
        val a = EditGeometry.allowed(400f, 300f, 15f)
        val t = Math.toRadians(-15.0)
        val touches = listOf(a.left to a.top, a.right to a.top, a.left to a.bottom, a.right to a.bottom).any { (x, y) ->
            val dx = x - 200.0; val dy = y - 150.0
            val px = dx * cos(t) - dy * sin(t) + 200; val py = dx * sin(t) + dy * cos(t) + 150
            listOf(px, 400 - px, py, 300 - py).any { kotlin.math.abs(it) < 0.05 }
        }
        assertTrue(touches)
    }

    @Test fun clampMovesABoxBackInsideAndKeepsAMinimum() {
        val bounds = EditRect(0f, 0f, 400f, 300f)
        near(EditRect(300f, 0f, 400f, 50f), EditGeometry.clamp(EditRect(350f, -20f, 450f, 30f), bounds, 10f))
        // Too small grows to the minimum, too big shrinks to the bounds
        near(EditRect(10f, 10f, 42f, 42f), EditGeometry.clamp(EditRect(10f, 10f, 12f, 12f), bounds, 32f))
        near(bounds, EditGeometry.clamp(EditRect(-50f, -50f, 900f, 900f), bounds, 32f))
    }

    @Test fun draggingACornerMovesOnlyItsSides() {
        val bounds = EditRect(0f, 0f, 400f, 300f)
        val r = EditRect(100f, 100f, 200f, 200f)
        near(EditRect(90f, 80f, 200f, 200f), EditGeometry.drag(r, Handle.TOP_LEFT, -10f, -20f, bounds, 32f))
        near(EditRect(100f, 100f, 250f, 200f), EditGeometry.drag(r, Handle.RIGHT, 50f, 99f, bounds, 32f))
        // Not past the picture's edge
        near(EditRect(100f, 100f, 400f, 300f), EditGeometry.drag(r, Handle.BOTTOM_RIGHT, 999f, 999f, bounds, 32f))
        // Not smaller than the minimum, and never turned inside out
        near(EditRect(168f, 100f, 200f, 200f), EditGeometry.drag(r, Handle.LEFT, 500f, 0f, bounds, 32f))
    }

    @Test fun draggingInsideMovesTheBoxAndStopsAtTheEdge() {
        val bounds = EditRect(0f, 0f, 400f, 300f)
        val r = EditRect(100f, 100f, 200f, 200f)
        near(EditRect(130f, 90f, 230f, 190f), EditGeometry.drag(r, Handle.MOVE, 30f, -10f, bounds, 32f))
        near(EditRect(300f, 0f, 400f, 100f), EditGeometry.drag(r, Handle.MOVE, 999f, -999f, bounds, 32f))
    }

    @Test fun aCircleBoxStaysSquare() {
        val bounds = EditRect(0f, 0f, 400f, 300f)
        val r = EditRect(100f, 100f, 200f, 200f)
        val grown = EditGeometry.drag(r, Handle.BOTTOM_RIGHT, 40f, 10f, bounds, 32f, square = true)
        near(EditRect(100f, 100f, 240f, 240f), grown)
        // Held by the edge's middle: grows evenly about it, and stops where the picture ends
        val edge = EditGeometry.drag(r, Handle.RIGHT, 500f, 0f, bounds, 32f, square = true)
        near(edge.width, edge.height)
        assertTrue(edge.top >= 0f && edge.bottom <= 300f && edge.right <= 400f)
        near(EditRect(150f, 100f, 250f, 200f), EditGeometry.square(EditRect(100f, 100f, 300f, 200f)))
    }

    @Test fun touchesFindTheRightHandle() {
        val r = EditRect(100f, 100f, 200f, 200f)
        assertEquals(Handle.TOP_LEFT, EditGeometry.handleAt(r, 95f, 104f, 10f))
        assertEquals(Handle.BOTTOM_RIGHT, EditGeometry.handleAt(r, 205f, 198f, 10f))
        assertEquals(Handle.TOP, EditGeometry.handleAt(r, 150f, 92f, 10f))
        assertEquals(Handle.LEFT, EditGeometry.handleAt(r, 100f, 150f, 10f))
        assertEquals(Handle.MOVE, EditGeometry.handleAt(r, 150f, 150f, 10f))
        assertNull(EditGeometry.handleAt(r, 300f, 150f, 10f))
    }

    @Test fun thePreviewIsFittedAndCentred() {
        // 400 x 300 in a 200 x 300 view: half size, centred top to bottom
        val (s, ox, oy) = EditGeometry.fit(400f, 300f, 200f, 300f)
        near(0.5f, s); near(0f, ox); near(75f, oy)
    }

    @Test fun sharesTravelFromThePreviewToTheFullPicture() {
        // A box placed on a 1600 px preview cuts the same part of the 3000 px picture
        val onPreview = EditRect(160f, 120f, 800f, 600f)
        val shares = EditGeometry.toShares(onPreview, 1600f, 1200f)
        near(EditRect(300f, 225f, 1500f, 1125f), EditGeometry.fromShares(shares, 3000f, 2250f))
    }

    @Test fun straighteningShrinksTheBoxOnlyAsNeeded() {
        val whole = EditRect(0f, 0f, 400f, 300f)
        val fitted = EditGeometry.fitAfterStraighten(whole, 400f, 300f, 10f, 32f)
        near(EditGeometry.allowed(400f, 300f, 10f), fitted)
        // A small box in the middle is left as it is
        val small = EditRect(180f, 130f, 220f, 170f)
        near(small, EditGeometry.fitAfterStraighten(small, 400f, 300f, 10f, 32f))
    }
}
