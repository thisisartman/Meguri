package io.github.thisisartman.collectibles.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CardCodeTest {
    @Test fun plainCode() = assertEquals(listOf("15-202-A001"), CardCode.findAll("新潟県 長岡市 15-202-A001"))

    @Test fun fullWidthAndOtherDashes() {
        assertEquals(listOf("15-202-A001"), CardCode.findAll("１５－２０２－Ａ００１"))
        assertEquals(listOf("13-100-C001"), CardCode.findAll("13ー100ーC001"))
        assertEquals(listOf("11-208-D001"), CardCode.findAll("11 – 208 − D 001"))
    }

    @Test fun lettersAndDigitsMixedUpByOcr() {
        // O for 0 in the number slots, 0 for O in the letter slot
        assertEquals(listOf("15-2O2-A001".replace('O', '0')), CardCode.findAll("15-2O2-A0O1"))
        assertEquals(listOf("13-100-O001"), CardCode.findAll("13-100-0001"))
        assertEquals(listOf("15-100-B001"), CardCode.findAll("I5-I00-B00I"))
    }

    @Test fun coordinatesAndNoiseAreNotCodes() {
        assertEquals(emptyList<String>(), CardCode.findAll("37°26'50.1\"N 138°51'04.0\"E 2026"))
        assertNull(CardCode.best("no code here", emptySet()))
    }

    @Test fun prefersAKnownCode() {
        val text = "15-202-A001 ... printed 15-202-A099"
        assertEquals("15-202-A001", CardCode.best(text, setOf("15-202-A001")))
        assertEquals("15-202-A099", CardCode.best(text, setOf("15-202-A099")))
    }
}
