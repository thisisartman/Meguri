package io.github.thisisartman.collectibles.data

import android.Manifest
import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Where and when a shared photo was taken (#80), from its EXIF. Apps that share the file itself keep its position.
 * A gallery that shares a MediaStore address hides the position unless Meguri may see the original, which needs photo
 * access and "photo locations" together (a share grant alone is refused, tested 2026-11-08); such a photo is marked
 * [SharedPhoto.placeHidden] so the screen can offer to allow it.
 */
object SharedPhotoReader {
    private val EXIF_TIME = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss")

    /** What Meguri asks for to see where gallery photos were taken. */
    fun placePermissions(): Array<String> = arrayOf(
        if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE,
        Manifest.permission.ACCESS_MEDIA_LOCATION,
    )

    private fun maySeeOriginals(context: Context) =
        placePermissions().all { context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }

    fun read(context: Context, uri: Uri): SharedPhoto {
        val cr = context.contentResolver
        var (lat, lng, taken) = exif(cr, uri) ?: Triple(null, null, null)
        val fromGallery = uri.authority == MediaStore.AUTHORITY
        if (lat == null && fromGallery && maySeeOriginals(context)) {
            exif(cr, MediaStore.setRequireOriginal(uri))?.let { (a, b, t) -> lat = a; lng = b; if (taken == null) taken = t }
        }
        if (taken == null) taken = runCatching {
            cr.query(uri, arrayOf(MediaStore.Images.Media.DATE_TAKEN), null, null, null)
                ?.use { c -> if (c.moveToFirst() && !c.isNull(0)) c.getLong(0).takeIf { it > 0 } else null }
        }.getOrNull()
        // Some apps write 0,0 for "no position"
        if (lat == 0.0 && lng == 0.0) { lat = null; lng = null }
        return SharedPhoto(uri.toString(), lat, lng, taken, placeHidden = lat == null && fromGallery && !maySeeOriginals(context))
    }

    private fun exif(cr: ContentResolver, uri: Uri): Triple<Double?, Double?, Long?>? = runCatching {
        cr.openInputStream(uri)?.use { s ->
            val exif = ExifInterface(s)
            val ll = FloatArray(2)
            @Suppress("DEPRECATION")
            val has = exif.getLatLong(ll)
            Triple(
                if (has) ll[0].toDouble() else null, if (has) ll[1].toDouble() else null,
                exifMillis(exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL), exif.getAttribute("OffsetTimeOriginal")),
            )
        }
    }.onFailure { android.util.Log.w("Share", "Could not read $uri: $it") }.getOrNull()

    /** EXIF's "2026:11:07 14:03:22" with its offset ("+09:00") when there is one, else read in [zone]. Null if unreadable. */
    fun exifMillis(dateTime: String?, offset: String?, zone: ZoneId = ZoneId.systemDefault()): Long? {
        val local = runCatching { LocalDateTime.parse(dateTime?.trim() ?: return null, EXIF_TIME) }.getOrNull() ?: return null
        val off = offset?.trim()?.let { runCatching { ZoneOffset.of(it) }.getOrNull() }
        return if (off != null) local.toInstant(off).toEpochMilli() else local.atZone(zone).toInstant().toEpochMilli()
    }
}
