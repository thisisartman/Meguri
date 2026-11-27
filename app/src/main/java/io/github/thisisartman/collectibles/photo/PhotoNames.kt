package io.github.thisisartman.collectibles.photo

/**
 * The names of a photo's files, in the app and in a backup (#79, #66, #208). Pure, for tests.
 *  - The shown copy: `app:photos/<id>.webp`; after an edit `app:photos/<id>-e<time>.webp`, a new name each time,
 *    so nothing keeps showing the picture from before.
 *  - The original, kept beside a copy the app cut on its own (a card, a stamp, a cover, an Ekitag):
 *    `app:photos/<id>.orig.webp`.
 *  - In a backup with photos: `photos/<id>` and `originals/<id>`.
 */
object PhotoNames {
    const val SCHEME = "app:"
    const val DIR = "photos"
    const val BACKUP_PHOTOS = "photos/"
    const val BACKUP_ORIGINALS = "originals/"
    private val ID = Regex("[A-Za-z0-9-]{1,64}")

    fun copy(photoId: String) = "$SCHEME$DIR/$photoId.webp"
    fun edited(photoId: String, at: Long) = "$SCHEME$DIR/$photoId-e$at.webp"
    fun original(photoId: String) = "$SCHEME$DIR/$photoId.orig.webp"

    fun backupPhoto(photoId: String) = BACKUP_PHOTOS + photoId
    fun backupOriginal(photoId: String) = BACKUP_ORIGINALS + photoId

    /** A backup entry's photo id and whether it is an original; null when it is neither, or the name isn't an id. */
    fun parseBackup(entry: String): Pair<String, Boolean>? = when {
        entry.startsWith(BACKUP_PHOTOS) -> entry.removePrefix(BACKUP_PHOTOS).takeIf { ID.matches(it) }?.let { it to false }
        entry.startsWith(BACKUP_ORIGINALS) -> entry.removePrefix(BACKUP_ORIGINALS).takeIf { ID.matches(it) }?.let { it to true }
        else -> null
    }

    /** The file names in the photos folder that some photo still points at, its copy or its original. */
    fun wanted(uris: List<String?>): Set<String> =
        uris.filterNotNull().filter { it.startsWith(SCHEME) }.map { it.removePrefix(SCHEME).substringAfterLast('/') }.toSet()
}
