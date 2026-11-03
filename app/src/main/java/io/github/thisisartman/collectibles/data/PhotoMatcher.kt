package io.github.thisisartman.collectibles.data

/** What MediaStore says about a picture in the gallery. */
data class MediaEntry(
    val id: Long,
    val displayName: String?,
    val relativePath: String?,
    val sizeBytes: Long?,
    val dateTaken: Long?,
)

/**
 * Finds a photo's picture again after a restore (PRD FR-9a): MediaStore ID + name, then
 * name + path + size, then date taken + size. Two pictures matching equally is no match:
 * a photo marked missing is better than a find showing the wrong picture.
 */
object PhotoMatcher {
    fun match(photo: Photo, media: List<MediaEntry>): MediaEntry? {
        val byId = media.firstOrNull { it.id == photo.mediastoreId && it.displayName != null && it.displayName == photo.displayName }
        if (byId != null) return byId
        if (photo.displayName != null && photo.relativePath != null && photo.sizeBytes != null) {
            only(media.filter { it.displayName == photo.displayName && it.relativePath == photo.relativePath && it.sizeBytes == photo.sizeBytes })
                ?.let { return it }
        }
        if (photo.takenAt != null && photo.sizeBytes != null) {
            return only(media.filter { it.dateTaken == photo.takenAt && it.sizeBytes == photo.sizeBytes })
        }
        return null
    }

    private fun only(found: List<MediaEntry>): MediaEntry? = found.singleOrNull()
}
