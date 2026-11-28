package io.github.thisisartman.collectibles.photo

import io.github.thisisartman.collectibles.data.ItemType
import io.github.thisisartman.collectibles.data.PhotoKind
import io.github.thisisartman.collectibles.photo.CardTrim.Result
import io.github.thisisartman.collectibles.photo.PhotoSort.Crop
import io.github.thisisartman.collectibles.photo.PhotoSort.Decision
import io.github.thisisartman.collectibles.photo.PhotoSort.Facts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoSortTest {
    private val card = ItemType.MANHOLE_CARD
    private val wholeCard = Facts(card = Result.OK)
    private val oddCard = Facts(card = Result.ODD_SHAPE)
    private val noCard = Facts(card = Result.NO_FRAME)
    private val shot = Facts(screenshot = true)

    // The owner's photo file, 2026-11-27: card photos filed as `manhole` became circle-cropped covers
    @Test fun aCardFiledAsTheCoverGoesToTheCardSlotTrimmedNeverACircle() {
        assertEquals(Decision(PhotoKind.STAMP, Crop.CARD), PhotoSort.decide(card, PhotoKind.MANHOLE, wholeCard))
    }

    @Test fun aCardThatCantBeTrimmedIsStillTheCardKeptWhole() {
        assertEquals(Decision(PhotoKind.STAMP, Crop.NONE), PhotoSort.decide(card, PhotoKind.MANHOLE, oddCard))
        assertEquals(Decision(PhotoKind.STAMP, Crop.NONE), PhotoSort.decide(card, PhotoKind.MANHOLE, Facts(card = Result.FRAME_CUT_OFF)))
    }

    @Test fun onlyARealCoverPhotoIsCutToACircle() {
        assertEquals(Decision(PhotoKind.MANHOLE, Crop.CIRCLE), PhotoSort.decide(card, PhotoKind.MANHOLE, noCard))
        // Not looked at (it can't be read small): the cover as filed
        assertEquals(Decision(PhotoKind.MANHOLE, Crop.CIRCLE), PhotoSort.decide(card, PhotoKind.MANHOLE, Facts()))
        assertEquals(Crop.NONE, PhotoSort.decide(card, PhotoKind.STAMP, noCard).crop)
        assertEquals(Crop.NONE, PhotoSort.decide(card, null, noCard).crop)
    }

    @Test fun cardKindGoesToTheCardSlot() {
        assertEquals(Decision(PhotoKind.STAMP, Crop.CARD), PhotoSort.decide(card, "card", wholeCard))
        assertEquals(Decision(PhotoKind.STAMP, Crop.NONE), PhotoSort.decide(card, "Card", noCard))
        assertEquals(Decision(PhotoKind.STAMP, Crop.NONE), PhotoSort.decide(ItemType.DAM_CARD, "card", Facts()))
        // 0.3's proof on a card is the card
        assertEquals(PhotoKind.STAMP, PhotoSort.decide(card, PhotoKind.PROOF, noCard).kind)
    }

    @Test fun aCardOnAnythingElseIsOther() {
        assertEquals(Decision(PhotoKind.OTHER, Crop.NONE), PhotoSort.decide(ItemType.EKI_STAMP, "card", Facts()))
        assertEquals(Decision(PhotoKind.OTHER, Crop.NONE), PhotoSort.decide(ItemType.CUSTOM, "card", Facts()))
    }

    @Test fun withNoKindGivenAWholeCardIsTheCard() {
        assertEquals(Decision(PhotoKind.STAMP, Crop.CARD), PhotoSort.decide(card, null, wholeCard))
        assertEquals(Decision(PhotoKind.STAMP, Crop.CARD), PhotoSort.decide(card, PhotoKind.OTHER, wholeCard))
        assertEquals(Decision(PhotoKind.STAMP, Crop.CARD), PhotoSort.decide(card, "", wholeCard))
        // Some blue that isn't a whole card (sky in a photo, say) is not enough to call it a card
        assertEquals(Decision(PhotoKind.OTHER, Crop.NONE), PhotoSort.decide(card, null, oddCard))
        assertEquals(Decision(PhotoKind.OTHER, Crop.NONE), PhotoSort.decide(card, null, noCard))
    }

    @Test fun aPlacePhotoStaysThePlaceAndIsNotLookedAtForACard() {
        assertEquals(Decision(PhotoKind.PLACE, Crop.NONE), PhotoSort.decide(card, PhotoKind.PLACE, wholeCard))
        assertFalse(PhotoSort.checksCard(card, PhotoKind.PLACE))
        assertTrue(PhotoSort.checksCard(card, PhotoKind.MANHOLE))
        assertTrue(PhotoSort.checksCard(card, null))
        assertTrue(PhotoSort.checksCard(ItemType.DAM_CARD, "card"))
        assertFalse(PhotoSort.checksCard(ItemType.EKI_STAMP, PhotoKind.STAMP))
    }

    @Test fun aScreenshotOfAStationIsItsEkitag() {
        for (t in listOf(ItemType.EKI_STAMP, ItemType.EKITAG)) {
            assertEquals(Decision(PhotoKind.EKITAG, Crop.EKITAG), PhotoSort.decide(t, null, shot))
            assertEquals(Decision(PhotoKind.EKITAG, Crop.EKITAG), PhotoSort.decide(t, PhotoKind.OTHER, shot))
            // A station stamp is never a screenshot
            assertEquals(Decision(PhotoKind.EKITAG, Crop.EKITAG), PhotoSort.decide(t, PhotoKind.STAMP, shot, cutStamps = true))
            assertEquals(Decision(PhotoKind.EKITAG, Crop.EKITAG), PhotoSort.decide(t, PhotoKind.EKITAG, shot))
        }
    }

    @Test fun aScreenshotFiledAsThePlaceStaysThePlace() {
        assertEquals(Decision(PhotoKind.PLACE, Crop.NONE), PhotoSort.decide(ItemType.EKI_STAMP, PhotoKind.PLACE, shot))
    }

    @Test fun aScreenshotOfAnythingElseKeepsItsKind() {
        assertEquals(Decision(PhotoKind.OTHER, Crop.NONE), PhotoSort.decide(ItemType.GOSHUIN, null, shot))
        // A goshuin's page is cut to the page however it came
        assertEquals(Decision(PhotoKind.STAMP, Crop.PAGE), PhotoSort.decide(ItemType.GOSHUIN, PhotoKind.STAMP, shot))
        assertEquals(Decision(PhotoKind.OTHER, Crop.NONE), PhotoSort.decide(card, null, Facts(card = Result.NO_FRAME, screenshot = true)))
    }

    @Test fun aStationPhotoThatIsntAScreenshotKeepsItsKind() {
        assertEquals(Decision(PhotoKind.STAMP, Crop.STAMP), PhotoSort.decide(ItemType.EKI_STAMP, PhotoKind.STAMP, Facts()))
        assertEquals(Decision(PhotoKind.OTHER, Crop.NONE), PhotoSort.decide(ItemType.EKI_STAMP, null, Facts()))
        assertEquals(Decision(PhotoKind.EKITAG, Crop.EKITAG), PhotoSort.decide(ItemType.EKITAG, PhotoKind.EKITAG, Facts()))
    }

    @Test fun stampsAreCutOnEveryWayInForStampKindOnStampTypes() {
        for (t in listOf(ItemType.EKI_STAMP, ItemType.EKITAG, ItemType.ROADSIDE_STATION, ItemType.MUSEUM_STAMP, ItemType.PLACE, ItemType.CUSTOM)) {
            assertEquals(Decision(PhotoKind.STAMP, Crop.STAMP), PhotoSort.decide(t, PhotoKind.STAMP, Facts()))
            // A stamp the caller cut already (a notebook stamp) isn't cut again
            assertEquals(Decision(PhotoKind.STAMP, Crop.NONE), PhotoSort.decide(t, PhotoKind.STAMP, Facts(), cutStamps = false))
            // Scenery stays whole
            assertEquals(Crop.NONE, PhotoSort.decide(t, PhotoKind.PLACE, Facts()).crop)
            assertEquals(Crop.NONE, PhotoSort.decide(t, PhotoKind.OTHER, Facts()).crop)
        }
        // A goshuin is cut to its page, a card has its own cut
        assertEquals(Crop.PAGE, PhotoSort.decide(ItemType.GOSHUIN, PhotoKind.STAMP, Facts()).crop)
        assertEquals(Crop.NONE, PhotoSort.decide(card, PhotoKind.STAMP, noCard).crop)
        assertEquals(Crop.CARD, PhotoSort.decide(card, PhotoKind.STAMP, wholeCard).crop)
    }

    @Test fun aStampItemsPhotoWithNoKindIsTheStampWhenAClearStampIsOnIt() {
        assertTrue(PhotoSort.checksStamp(ItemType.EKI_STAMP, null))
        assertTrue(PhotoSort.checksStamp(ItemType.MUSEUM_STAMP, PhotoKind.OTHER))
        assertFalse(PhotoSort.checksStamp(ItemType.EKI_STAMP, PhotoKind.PLACE))
        assertFalse(PhotoSort.checksStamp(card, null))
        assertFalse(PhotoSort.checksStamp(ItemType.GOSHUIN, null))
        assertEquals(Decision(PhotoKind.STAMP, Crop.STAMP), PhotoSort.decide(ItemType.ROADSIDE_STATION, null, Facts(stamp = true)))
        assertEquals(Decision(PhotoKind.OTHER, Crop.NONE), PhotoSort.decide(ItemType.ROADSIDE_STATION, null, Facts()))
        // Filed as the place, it stays the place whatever is on it
        assertEquals(Decision(PhotoKind.PLACE, Crop.NONE), PhotoSort.decide(ItemType.ROADSIDE_STATION, PhotoKind.PLACE, Facts(stamp = true)))
    }

    @Test fun otherKindsPassThrough() {
        assertEquals(Decision(PhotoKind.GOSHUIN, Crop.PAGE), PhotoSort.decide(ItemType.PLACE, PhotoKind.GOSHUIN, Facts()))
        assertEquals(Decision(PhotoKind.GOSHUIN, Crop.NONE), PhotoSort.decide(ItemType.PLACE, PhotoKind.GOSHUIN, Facts(), cutStamps = false))
        assertEquals(Decision(PhotoKind.PLACE, Crop.NONE), PhotoSort.decide(ItemType.GOSHUIN, PhotoKind.PLACE, Facts()))
        assertEquals(Decision(PhotoKind.OTHER, Crop.NONE), PhotoSort.decide(null, null, Facts()))
    }

    @Test fun screenshotsByName() {
        assertTrue(PhotoSort.isScreenshot("Screenshot_20261127_101500_JR East.jpg", null, 1080, 1080, hasCameraExif = false))
        assertTrue(PhotoSort.isScreenshot("スクリーンショット 2026-11-27.png", null, 800, 600, hasCameraExif = false))
        assertTrue(PhotoSort.isScreenshot("IMG_1.jpg", "Pictures/Screenshots/", 1080, 1080, hasCameraExif = false))
        assertTrue(PhotoSort.isScreenshot("screen_shot.png", null, 10, 10, hasCameraExif = true))
    }

    @Test fun screenshotsByShape() {
        // S23 (1080 x 2340), Pixel (1080 x 2400), iPhone (1170 x 2532), an 18:9 phone (1080 x 2160)
        for ((w, h) in listOf(1080 to 2340, 1080 to 2400, 1170 to 2532, 1080 to 2160)) {
            assertTrue("$w x $h", PhotoSort.isScreenshot("PXL_1.png", null, w, h, hasCameraExif = false))
        }
    }

    @Test fun cameraPhotosAreNotScreenshots() {
        // A camera's own shapes, and a tall picture that says which camera took it
        assertFalse(PhotoSort.isScreenshot("IMG_1.jpg", "DCIM/Camera/", 3000, 4000, hasCameraExif = true))
        assertFalse(PhotoSort.isScreenshot("IMG_1.jpg", null, 2250, 4000, hasCameraExif = false))
        assertFalse(PhotoSort.isScreenshot("IMG_1.jpg", null, 1080, 2340, hasCameraExif = true))
        // Sideways is not a phone screen held upright
        assertFalse(PhotoSort.isScreenshot("x.png", null, 2340, 1080, hasCameraExif = false))
        assertFalse(PhotoSort.isScreenshot(null, null, 0, 0, hasCameraExif = false))
    }
}
