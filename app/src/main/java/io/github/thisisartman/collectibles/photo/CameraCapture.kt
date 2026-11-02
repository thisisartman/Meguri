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
    fun describe(resolver: ContentResolver, uri: Uri): PhotoInfo? {
        val cols = arrayOf(
            MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME, MediaStore.Images.Media.RELATIVE_PATH,
            MediaStore.Images.Media.SIZE, MediaStore.Images.Media.DATE_TAKEN,
        )
        resolver.query(uri, cols, null, null, null)?.use { c ->
            if (!c.moveToFirst()) return null
            val size = c.getLong(3)
            if (size <= 0L) return null
            return PhotoInfo(
                uri = uri.toString(),
                mediastoreId = c.getLong(0),
                displayName = c.getString(1),
                relativePath = c.getString(2),
                sizeBytes = size,
                takenAt = c.getLong(4).takeIf { it > 0 } ?: System.currentTimeMillis(),
            )
        }
        return null
    }

    fun discard(resolver: ContentResolver, uri: Uri) {
        runCatching { resolver.delete(uri, null, null) }
    }

    /** Start-up clean-up: our own empty rows left by a camera that was killed (FR-9b). */
    fun cleanEmptyRows(resolver: ContentResolver) {
        runCatching {
            resolver.delete(
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
                "${MediaStore.Images.Media.RELATIVE_PATH} = ? AND ${MediaStore.Images.Media.SIZE} = 0",
                arrayOf(RELATIVE_PATH),
            )
        }
    }
}
