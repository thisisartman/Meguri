package io.github.thisisartman.collectibles.photo

import android.content.ContentResolver
import android.content.ContentValues
import android.net.Uri
import android.provider.MediaStore
import io.github.thisisartman.collectibles.data.PhotoInfo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * System-camera capture into the gallery (PRD FR-7): a MediaStore row in DCIM/CollectiblesTracker
 * is created first, the camera app writes into it, and the row is deleted if the camera is cancelled.
 * File names are unique (time plus a short id) so a restore can relink them (FR-9a).
 */
object CameraCapture {
    const val RELATIVE_PATH = "DCIM/CollectiblesTracker/"

    fun newPhotoUri(resolver: ContentResolver): Uri? {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val name = "CT_${stamp}_${UUID.randomUUID().toString().take(6)}.jpg"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, RELATIVE_PATH)
        }
        return resolver.insert(MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values)
    }

    /** Reads the fingerprint of a photo the camera just wrote; null if it is empty or gone. */
    /**
     * Reads the fingerprint of a photo the camera just wrote; null only if the file is really empty
     * or gone. The size is read from the file itself: MediaStore's SIZE column is filled in later
     * and reads 0 right after the camera returns, which must never count as "empty".
     */
    fun describe(resolver: ContentResolver, uri: Uri): PhotoInfo? {
        val size = fileSize(resolver, uri) ?: return null
        if (size <= 0L) return null
        val cols = arrayOf(
            MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME, MediaStore.Images.Media.RELATIVE_PATH,
            MediaStore.Images.Media.DATE_TAKEN,
        )
        resolver.query(uri, cols, null, null, null)?.use { c ->
            if (!c.moveToFirst()) return null
            return PhotoInfo(
                uri = uri.toString(),
                mediastoreId = c.getLong(0),
                displayName = c.getString(1),
                relativePath = c.getString(2),
                sizeBytes = size,
                takenAt = c.getLong(3).takeIf { it > 0 } ?: System.currentTimeMillis(),
            )
        }
        return null
    }

    /** Size of the file behind [uri] from the file itself; null if it cannot be opened. */
    fun fileSize(resolver: ContentResolver, uri: Uri): Long? =
        runCatching { resolver.openFileDescriptor(uri, "r")?.use { it.statSize } }.getOrNull()

    /** Deletes our gallery row only if its file is really empty (or unreadable). */
    fun discardIfEmpty(resolver: ContentResolver, uri: Uri) {
        val size = fileSize(resolver, uri)
        if (size == null || size <= 0L) runCatching { resolver.delete(uri, null, null) }
    }

    /**
     * Start-up clean-up (FR-9b): our own rows that are really empty, left by a camera that was killed.
     * Each candidate is checked on the file, never trusted from the SIZE column alone.
     */
    fun cleanEmptyRows(resolver: ContentResolver) {
        runCatching {
            val base = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            resolver.query(
                base, arrayOf(MediaStore.Images.Media._ID),
                "${MediaStore.Images.Media.RELATIVE_PATH} = ? AND ${MediaStore.Images.Media.SIZE} = 0", arrayOf(RELATIVE_PATH), null,
            )?.use { c ->
                while (c.moveToNext()) discardIfEmpty(resolver, android.content.ContentUris.withAppendedId(base, c.getLong(0)))
            }
        }
    }
}
