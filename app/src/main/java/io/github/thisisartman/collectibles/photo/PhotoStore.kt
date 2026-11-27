package io.github.thisisartman.collectibles.photo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.util.Size
import io.github.thisisartman.collectibles.data.PhotoKind
import java.io.File
import java.io.InputStream

/**
 * Meguri's own copy of every photo (#79): a 3000 px WebP in the app's files, so clearing the gallery loses nothing.
 * 3000 px since 2026-11-09 (owner: full screen must stay sharp; 1600 px was stretched 1.5x on the S23's 2340 px screen
 * and blurred when zoomed into card text). About 0.6 to 1 MB a photo. Copies made before stay at 1600 px.
 * A photo's content_uri is `app:photos/<id>.webp` once copied; older rows still point at the gallery until
 * [PhotoCopier] copies them. The camera's full-size original stays in the gallery as the owner's own picture.
 */
object PhotoStore {
    const val SCHEME = "app:"
    private const val DIR = PhotoNames.DIR
    const val LONG_SIDE = 3000
    const val QUALITY = 85

    fun appUri(photoId: String) = PhotoNames.copy(photoId)

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
    fun importCopy(context: Context, source: ImageDecoder.Source, photoId: String, circle: Boolean = false, ekitagTitle: String? = null): String? {
        val crop = when {
            circle -> PhotoSort.Crop.CIRCLE
            ekitagTitle != null -> PhotoSort.Crop.EKITAG
            else -> PhotoSort.Crop.NONE
        }
        return importSorted(context, source, photoId, { PhotoSort.Decision(PhotoKind.OTHER, crop) }, ekitagTitle)?.uri
    }

    /**
     * What [importSorted] wrote: the shown copy's content_uri (null if it couldn't be written), how it was sorted, and
     * the original kept beside a copy the app cut ([PhotoNames.original]; null when nothing was cut or it failed).
     */
    data class Saved(val uri: String?, val decision: PhotoSort.Decision, val originalUri: String?)

    /**
     * Like [importCopy], with the slot and cut chosen from the picture itself ([PhotoSort]): [decide] sees the
     * picture as read, before any cut. When the app cuts the picture (a card, a stamp, a cover, an Ekitag), the
     * picture as read is kept too, upright and at most [LONG_SIDE] px (#208), so the cut can be redone. Null if the
     * source can't be read.
     */
    fun importSorted(
        context: Context, source: ImageDecoder.Source, photoId: String,
        decide: (Bitmap) -> PhotoSort.Decision, ekitagTitle: String? = null,
    ): Saved? {
        val whole = runCatching { decode(source, LONG_SIDE) }.getOrNull() ?: return null
        val decision = decide(whole)
        // A manhole cover is kept as the cover itself, cut out as a circle (owner, 2026-11-08); no clear circle, the whole photo.
        // A card is trimmed to the card (2026-11-27); no whole card found, the whole photo.
        // A stamp photo from a photo file is cut to its stamp (#188); no stamp found, the whole photo.
        // An Ekitag screenshot becomes its stamp on a titled square (owner, 2026-11-09); no stamp found, the screenshot.
        val cut: Bitmap? = when (decision.crop) {
            PhotoSort.Crop.CIRCLE -> runCatching { CoverCrop.crop(whole) }.getOrNull()
            PhotoSort.Crop.CARD -> runCatching { CardTrim.trim(whole).first }.getOrNull()
            PhotoSort.Crop.STAMP -> StampCrop.cut(whole)
            PhotoSort.Crop.EKITAG -> ekitagTitle?.let { t ->
                runCatching {
                    // "first\nsecond": the station's name in the app's language first
                    // M PLUS Rounded 1c, the owner's pick (Telegram 500, 2026-11-09)
                    EkitagArt.render(
                        whole, t.substringBefore('\n'), t.substringAfter('\n', ""),
                        bold = androidx.core.content.res.ResourcesCompat.getFont(context, io.github.thisisartman.collectibles.R.font.mplus_rounded_bold) ?: android.graphics.Typeface.DEFAULT_BOLD,
                        regular = androidx.core.content.res.ResourcesCompat.getFont(context, io.github.thisisartman.collectibles.R.font.mplus_rounded_medium) ?: android.graphics.Typeface.DEFAULT,
                    )
                }.getOrNull()
            }
            PhotoSort.Crop.NONE -> null
        }
        val cutDone = cut?.takeIf { it !== whole }
        // The original, before the cut: what the edit screen starts from (#208). Written first, as write() recycles it
        val original = cutDone?.let { write(context, PhotoNames.original(photoId), whole) }
        val bmp = cutDone ?: whole
        return Saved(write(context, appUri(photoId), bmp), decision, original)
    }

    /** Writes [bmp] as WebP to the app file of [uri] (an `app:` address), through a temporary file; recycles [bmp]. */
    private fun write(context: Context, uri: String, bmp: Bitmap): String? {
        val target = file(context, uri).also { it.parentFile?.mkdirs() }
        val tmp = File(target.path + ".tmp")
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
        return if (ok) uri else null
    }

    /**
     * Writes an edited copy of [photoId] (#208): the picture at [sourceUri] (the original, or the photo as it was)
     * turned, straightened and cut as [edit] says, as a circle for a cover, under a new file name. Its content_uri, or null.
     */
    fun saveEdit(context: Context, sourceUri: String, photoId: String, edit: PhotoEdit, now: Long = System.currentTimeMillis()): String? {
        val src = load(context, sourceUri, LONG_SIDE) ?: return null
        val out = runCatching { render(src, edit) }.getOrNull()
        src.recycle()
        return out?.let { write(context, PhotoNames.edited(photoId, now), it) }
    }

    /** [src] turned, straightened and cut by [edit]. */
    fun render(src: Bitmap, edit: PhotoEdit): Bitmap {
        val (fw, fh) = EditGeometry.turnedSize(src.width.toFloat(), src.height.toFloat(), edit.quarterTurns)
        var box = EditGeometry.fromShares(edit.crop, fw, fh)
        if (edit.circle) box = EditGeometry.square(box)
        val w = maxOf(1, Math.round(box.width))
        val h = maxOf(1, Math.round(box.height))
        val m = EditGeometry.affine(src.width.toFloat(), src.height.toFloat(), edit.quarterTurns, edit.degrees)
        val matrix = android.graphics.Matrix().apply {
            setValues(floatArrayOf(m[0], m[1], m[2], m[3], m[4], m[5], 0f, 0f, 1f))
            postTranslate(-box.left, -box.top)
        }
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(out)
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG or android.graphics.Paint.FILTER_BITMAP_FLAG)
        if (edit.circle) {
            // As CoverCrop cuts a cover: transparent outside the circle
            canvas.drawCircle(w / 2f, h / 2f, minOf(w, h) / 2f, paint)
            paint.xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_IN)
        }
        canvas.drawBitmap(src, matrix, paint)
        return out
    }

    fun importCopy(context: Context, source: Uri, photoId: String, circle: Boolean = false, ekitagTitle: String? = null): String? =
        importCopy(context, ImageDecoder.createSource(context.contentResolver, source), photoId, circle, ekitagTitle)

    fun importSorted(context: Context, source: Uri, photoId: String, decide: (Bitmap) -> PhotoSort.Decision, ekitagTitle: String? = null): Saved? =
        importSorted(context, ImageDecoder.createSource(context.contentResolver, source), photoId, decide, ekitagTitle)

    fun importCopy(context: Context, source: File, photoId: String): String? =
        importCopy(context, ImageDecoder.createSource(source), photoId)

    /** Deletes the app copy, if the photo has one. */
    fun delete(context: Context, uri: String) {
        if (isApp(uri)) file(context, uri).delete()
    }

    /** Deletes a photo's app copy and its kept original (#208). */
    fun delete(context: Context, uri: String, originalUri: String?) {
        delete(context, uri)
        originalUri?.let { delete(context, it) }
    }

    /** Writes a backup's original of [photoId] as its kept original, upright and resized as on the way in; its content_uri, or null. */
    fun importOriginal(context: Context, source: File, photoId: String): String? {
        val bmp = runCatching { decode(ImageDecoder.createSource(source), LONG_SIDE) }.getOrNull() ?: return null
        return write(context, PhotoNames.original(photoId), bmp)
    }

    /** A picture from the app's files at most [px] on its long side, for the edit screen; null if it can't be read. */
    fun load(context: Context, uri: String, px: Int): Bitmap? =
        if (!isApp(uri)) null else runCatching { decode(ImageDecoder.createSource(file(context, uri)), px) }.getOrNull()

    /** Scaled down so the long side is at most [px]; ImageDecoder applies the EXIF rotation. Software, so it can be compressed. */
    private fun decode(source: ImageDecoder.Source, px: Int): Bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        val w = info.size.width
        val h = info.size.height
        val scale = px.toFloat() / maxOf(w, h)
        if (scale < 1f) decoder.setTargetSize(maxOf(1, (w * scale).toInt()), maxOf(1, (h * scale).toInt()))
    }
}
