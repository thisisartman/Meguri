package io.github.thisisartman.collectibles.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoCoverTest {
    @Test fun eachTypesMainPhotoBecomesTheDisplayPicture() {
        assertTrue(AutoCover.makesCover(ItemType.EKI_STAMP, PhotoKind.EKITAG))
        assertTrue(AutoCover.makesCover(ItemType.EKITAG, PhotoKind.EKITAG))
        assertTrue(AutoCover.makesCover(ItemType.GOSHUIN, PhotoKind.STAMP))
    }

    // 2026-11-27: a card shows the card itself, not its cover
    @Test fun aCardsDisplayPictureIsTheCard() {
        assertTrue(AutoCover.makesCover(ItemType.MANHOLE_CARD, PhotoKind.STAMP))
        assertTrue(AutoCover.makesCover(ItemType.MANHOLE_CARD, PhotoKind.PROOF)) // 0.3's card photo
        assertTrue(AutoCover.makesCover(ItemType.DAM_CARD, PhotoKind.STAMP))
        assertFalse(AutoCover.makesCover(ItemType.MANHOLE_CARD, PhotoKind.MANHOLE))
    }

    @Test fun otherPhotosDoNot() {
        assertFalse(AutoCover.makesCover(ItemType.MANHOLE_CARD, PhotoKind.OTHER))
        assertFalse(AutoCover.makesCover(ItemType.MANHOLE_CARD, PhotoKind.PLACE))
        assertFalse(AutoCover.makesCover(ItemType.DAM_CARD, PhotoKind.PLACE))
        assertFalse(AutoCover.makesCover(ItemType.EKI_STAMP, PhotoKind.STAMP))
        assertFalse(AutoCover.makesCover(ItemType.GOSHUIN, PhotoKind.PLACE))
        assertFalse(AutoCover.makesCover(ItemType.DAM_CARD, PhotoKind.MANHOLE))
    }
}
