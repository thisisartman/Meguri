package io.github.thisisartman.collectibles.ui

import androidx.compose.ui.text.font.FontWeight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DirectionsTest {
    @Test fun walkingWhenCloseTransitWhenFar() {
        assertEquals("walking", travelMode(800.0))
        assertEquals("walking", travelMode(1_999.0))
        assertEquals("transit", travelMode(2_000.0))
        assertEquals("transit", travelMode(12_000.0))
        assertEquals("transit", travelMode(null))
    }

    @Test fun boldMatchMarksTheTypedPart() {
        val s = boldMatch("Minami-Yoshida Station", "yoshida")
        val span = s.spanStyles.single()
        assertEquals(7, span.start)
        assertEquals(14, span.end)
        assertEquals(FontWeight.Bold, span.item.fontWeight)
        assertTrue(boldMatch("Minami-Yoshida Station", "yshda").spanStyles.isEmpty())
    }
}
