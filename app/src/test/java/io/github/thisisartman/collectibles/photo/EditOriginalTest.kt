package io.github.thisisartman.collectibles.photo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** #208, owner's device test 2026-11-28: the first edit of a photo never cut keeps the photo as its original. */
class EditOriginalTest {
    private val photo = "app:photos/p1.webp"
    private val orig = "app:photos/p1.orig.webp"

    @Test fun aPhotoWithNoOriginalIsStoredAsItsOwnOriginalBeforeTheEdit() {
        val plan = EditOriginal.plan(photo, null, originalExists = false, inApp = true)
        assertEquals(photo, plan.source)
        assertEquals(photo, plan.storeOriginal)
    }

    @Test fun aKeptOriginalIsTheSourceAndStaysAsItIs() {
        val plan = EditOriginal.plan("app:photos/p1-e5.webp", orig, originalExists = true, inApp = true)
        assertEquals(orig, plan.source)
        assertNull(plan.storeOriginal)
    }

    @Test fun anOriginalWhoseFileIsGoneIsReplacedByThePhotoAsItIs() {
        val plan = EditOriginal.plan(photo, orig, originalExists = false, inApp = true)
        assertEquals(photo, plan.source)
        assertEquals(photo, plan.storeOriginal)
    }

    @Test fun aPictureOnlyInTheGalleryIsNotTakenAsAnOriginal() {
        val gallery = "content://media/external/images/media/7"
        assertNull(EditOriginal.plan(gallery, null, originalExists = false, inApp = false).storeOriginal)
    }

    @Test fun theFullPhotoIsSaidLostOnlyForAnOlderCutPhotoWithNoOriginal() {
        val before = EditOriginal.ORIGINALS_SINCE - 1
        val after = EditOriginal.ORIGINALS_SINCE + 1
        assertTrue(EditOriginal.fullPhotoLost(hasOriginal = false, role = "stamp", createdAt = before))
        assertTrue(EditOriginal.fullPhotoLost(hasOriginal = false, role = "manhole", createdAt = before))
        assertTrue(EditOriginal.fullPhotoLost(hasOriginal = false, role = "ekitag", createdAt = before))
        // The photo is itself the full photo: added since, never cut, or a kind the app never cuts
        assertFalse(EditOriginal.fullPhotoLost(hasOriginal = false, role = "stamp", createdAt = after))
        assertFalse(EditOriginal.fullPhotoLost(hasOriginal = false, role = "place", createdAt = before))
        assertFalse(EditOriginal.fullPhotoLost(hasOriginal = false, role = "other", createdAt = before))
        // It has its original
        assertFalse(EditOriginal.fullPhotoLost(hasOriginal = true, role = "stamp", createdAt = before))
    }

    @Test fun originalsCameInOnTheTwentyEighthOfNovemberJapanTime() {
        // 2026-11-28 00:00 +09:00
        assertEquals(java.time.OffsetDateTime.parse("2026-11-28T00:00+09:00").toInstant().toEpochMilli(), EditOriginal.ORIGINALS_SINCE)
    }
}
