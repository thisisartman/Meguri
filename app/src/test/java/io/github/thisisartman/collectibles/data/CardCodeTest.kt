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

    @Test fun anIMayBeAnL() = assertEquals(listOf("13-100-L101"), CardCode.lookalikes("13-100-I101", setOf("13-100-I101", "13-100-L101", "13-100-J101")))

    @Test fun noLookalikeWhenNoneIsKnown() = assertEquals(emptyList<String>(), CardCode.lookalikes("15-202-A001", setOf("15-202-A001", "15-202-B001")))

    @Test fun anOMayBeAQOrD() = assertEquals(listOf("13-100-Q001", "13-100-D001"), CardCode.lookalikes("13-100-O001", setOf("13-100-O001", "13-100-Q001", "13-100-D001")))

    // A 600 x 840 card page; the code is printed top right when it is upright
    @Test fun anUprightCardNeedsNoTurn() = assertEquals(0, CardCode.turnToTopRight(500f, 60f, 600f, 840f))

    @Test fun anUpsideDownCardTurnsHalfway() = assertEquals(180, CardCode.turnToTopRight(100f, 780f, 600f, 840f))

    // Lying on its side (840 x 600): code at the bottom right means the card's top is on the right, so a quarter turn back
    @Test fun aCardOnItsRightSideTurnsBack() = assertEquals(270, CardCode.turnToTopRight(780f, 500f, 840f, 600f))

    @Test fun aCardOnItsLeftSideTurnsForward() = assertEquals(90, CardCode.turnToTopRight(60f, 100f, 840f, 600f))
}
