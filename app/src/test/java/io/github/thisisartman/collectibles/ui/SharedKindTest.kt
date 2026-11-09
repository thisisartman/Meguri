package io.github.thisisartman.collectibles.ui

import io.github.thisisartman.collectibles.data.ItemType
import io.github.thisisartman.collectibles.data.PhotoKind
import org.junit.Assert.assertEquals
import org.junit.Test

class SharedKindTest {
    @Test fun aScannedCardPickedByHandIsFiledAsTheCard() {
        assertEquals(PhotoKind.STAMP, MainViewModel.sharedKind(ItemType.MANHOLE_CARD, PhotoKind.STAMP))
        assertEquals(PhotoKind.MANHOLE, MainViewModel.sharedKind(ItemType.MANHOLE_CARD, PhotoKind.MANHOLE))
    }

    @Test fun anythingElseStaysOther() {
        assertEquals(PhotoKind.OTHER, MainViewModel.sharedKind(ItemType.EKI_STAMP, PhotoKind.STAMP))
        assertEquals(PhotoKind.OTHER, MainViewModel.sharedKind(null, PhotoKind.STAMP))
        assertEquals(PhotoKind.OTHER, MainViewModel.sharedKind(ItemType.MANHOLE_CARD, PhotoKind.OTHER))
    }
}
