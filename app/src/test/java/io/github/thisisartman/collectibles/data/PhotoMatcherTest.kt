package io.github.thisisartman.collectibles.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PhotoMatcherTest {
    private fun photo(
        id: Long? = 10, name: String? = "CT_1.jpg", path: String? = "DCIM/CollectiblesTracker/", size: Long? = 100, taken: Long? = 500,
    ) = Photo(
        id = "p", acquisitionId = "a", contentUri = "content://old", mediastoreId = id, displayName = name,
        relativePath = path, sizeBytes = size, takenAt = taken, createdAt = 1,
    )

    private fun media(id: Long, name: String? = "CT_1.jpg", path: String? = "DCIM/CollectiblesTracker/", size: Long? = 100, taken: Long? = 500) =
        MediaEntry(id, name, path, size, taken)

    @Test fun sameIdAndNameWins() {
        val m = PhotoMatcher.match(photo(), listOf(media(99), media(10)))
        assertEquals(10L, m?.id)
    }

    @Test fun reusedIdWithAnotherNameIsNotAMatch() {
        val m = PhotoMatcher.match(photo(), listOf(media(10, name = "other.jpg", size = 7, taken = 7)))
        assertNull(m)
    }

    @Test fun newPhoneMatchesByNamePathAndSize() {
        val m = PhotoMatcher.match(photo(), listOf(media(77, name = "other.jpg", size = 7, taken = 7), media(78)))
        assertEquals(78L, m?.id)
    }

    @Test fun twoEqualCandidatesFallThroughAndStayUnmatched() {
        assertNull(PhotoMatcher.match(photo(id = null), listOf(media(1), media(2))))
    }

    @Test fun renamedFileMatchesByDateAndSize() {
        val m = PhotoMatcher.match(photo(id = null), listOf(media(5, name = "renamed.jpg", path = "Pictures/")))
        assertEquals(5L, m?.id)
    }

    @Test fun ambiguousDateAndSizeIsNoMatch() {
        val found = listOf(media(5, name = "a.jpg", path = "x/"), media(6, name = "b.jpg", path = "y/"))
        assertNull(PhotoMatcher.match(photo(id = null), found))
    }

    @Test fun missingFingerprintIsNoMatch() {
        assertNull(PhotoMatcher.match(photo(id = null, name = null, path = null, size = null, taken = null), listOf(media(1))))
    }
}
