package io.github.thisisartman.collectibles.photo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.util.Size
import java.io.File
import java.io.InputStream

/**
 * Meguri's own copy of every photo (#79): a 1600 px WebP in the app's files, so clearing the gallery loses nothing.
 * A photo's content_uri is `app:photos/<id>.webp` once copied; older rows still point at the gallery until
 * [PhotoCopier] copies them. The camera's full-size original stays in the gallery as the owner's own picture.
 */
object PhotoStore {
    const val SCHEME = "app:"
    private const val DIR = "photos"
    const val LONG_SIDE = 1600
    const val QUALITY = 80

    fun appUri(photoId: String) = "${SCHEME}$DIR/$photoId.webp"

    fun isApp(uri: String) = uri.startsWith(SCHEME)

    fun dir(context: Context) = File(context.filesDir, DIR).apply { mkdirs() }

    fun file(context: Context, uri: String): File = File(context.filesDir, uri.removePrefix(SCHEME))

    fun exists(context: Context, uri: String) = isApp(uri) && file(context, uri).let { it.isFile && it.length() > 0 }

    fun open(context: Context, uri: String): InputStream? =
        if (isApp(uri)) file(context, uri).takeIf { it.isFile }?.inputStream()
        else context.contentResolver.openInputStream(Uri.parse(uri))

    /** A small bitmap for a tile or grid; null if the picture can't be read. */
    fun thumbnail(context: Context, uri: String, px: Int): Bitmap? = runCatching {
        if (isApp(uri)) decode(ImageDecoder.createSource(file(context, uri)), px)
        else context.contentResolver.loadThumbnail(Uri.parse(uri), Size(px, px), null)
    }.getOrNull()

    /**
     * Writes the app copy for [photoId] from [source] (a gallery or picker address, or a file from a backup):
     * upright, at most [LONG_SIDE] px on the long side, WebP. Written to a temporary file and renamed, so a crash
     * never leaves half a picture. Returns the new content_uri, or null if the source can't be read.
     */
    fun importCopy(context: Context, source: ImageDecoder.Source, photoId: String): String? {
        val bmp = runCatching { decode(source, LONG_SIDE) }.getOrNull() ?: return null
        val target = File(dir(context), "$photoId.webp")
        val tmp = File(dir(context), "$photoId.webp.tmp")
        val ok = runCatching {
            tmp.outputStream().use { out ->
                @Suppress("DEPRECATION")
                val format = if (Build.VERSION.SDK_INT >= 30) Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.WEBP
                check(bmp.compress(format, QUALITY, out))
            }
            check(tmp.length() > 0 && tmp.renameTo(target))
        }.isSuccess
        bmp.recycle()
        if (!ok) tmp.delete()
        return if (ok) appUri(photoId) else null
    }

    fun importCopy(context: Context, source: Uri, photoId: String): String? =
        importCopy(context, ImageDecoder.createSource(context.contentResolver, source), photoId)

    fun importCopy(context: Context, source: File, photoId: String): String? =
        importCopy(context, ImageDecoder.createSource(source), photoId)

    /** Deletes the app copy, if the photo has one. */
    fun delete(context: Context, uri: String) {
        if (isApp(uri)) file(context, uri).delete()
    }

    /** Scaled down so the long side is at most [px]; ImageDecoder applies the EXIF rotation. Software, so it can be compressed. */
    private fun decode(source: ImageDecoder.Source, px: Int): Bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        val w = info.size.width
        val h = info.size.height
        val scale = px.toFloat() / maxOf(w, h)
        if (scale < 1f) decoder.setTargetSize(maxOf(1, (w * scale).toInt()), maxOf(1, (h * scale).toInt()))
    }
}
