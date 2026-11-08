package io.github.thisisartman.collectibles.photo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.math.hypot
import kotlin.random.Random

class CoverCropTest {
    /** A dark disc (a cover) on lighter ground with noise, like asphalt around a lid. */
    private fun disc(w: Int, h: Int, cx: Int, cy: Int, r: Int, noise: Int = 20, seed: Int = 1): IntArray {
        val rnd = Random(seed)
        return IntArray(w * h) { i ->
            val x = i % w; val y = i / w
            val base = if (hypot((x - cx).toDouble(), (y - cy).toDouble()) <= r) 70 else 170
            (base + rnd.nextInt(-noise, noise + 1)).coerceIn(0, 255)
        }
    }

    @Test fun findsACentredCover() {
        val c = CoverCrop.detect(disc(200, 160, 100, 80, 60), 200, 160)
        assertNotNull(c); c!!
        assertEquals(100f, c.cx, 4f); assertEquals(80f, c.cy, 4f); assertEquals(60f, c.r, 4f)
    }

    @Test fun findsAnOffCentreCover() {
        val c = CoverCrop.detect(disc(240, 180, 140, 95, 50, seed = 3), 240, 180)!!
        assertEquals(140f, c.cx, 4f); assertEquals(95f, c.cy, 4f); assertEquals(50f, c.r, 4f)
    }

    @Test fun noCircleInPlainNoiseOrARectangle() {
        val rnd = Random(7)
        assertNull(CoverCrop.detect(IntArray(200 * 160) { 128 + rnd.nextInt(-20, 21) }, 200, 160))
        // A card-like rectangle: straight edges should not pass as a circle
        val rect = IntArray(200 * 160) { i -> val x = i % 200; val y = i / 200; if (x in 40..160 && y in 20..140) 60 else 180 }
        assertNull(CoverCrop.detect(rect, 200, 160))
    }
}
