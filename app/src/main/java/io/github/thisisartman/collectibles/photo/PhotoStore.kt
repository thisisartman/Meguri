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
    private const val DIR = "photos"
    const val LONG_SIDE = 3000
    const val QUALITY = 85

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
    fun importCopy(context: Context, source: ImageDecoder.Source, photoId: String, circle: Boolean = false, ekitagTitle: String? = null): String? {
        val crop = when {
            circle -> PhotoSort.Crop.CIRCLE
            ekitagTitle != null -> PhotoSort.Crop.EKITAG
            else -> PhotoSort.Crop.NONE
        }
        return importSorted(context, source, photoId, { PhotoSort.Decision(PhotoKind.OTHER, crop) }, ekitagTitle)?.first
    }

    /**
     * Like [importCopy], with the slot and cut chosen from the picture itself ([PhotoSort]): [decide] sees the
     * picture as read, before any cut. Returns the new content_uri (null if it couldn't be written) and the
     * decision, or null if the source can't be read.
     */
    fun importSorted(
        context: Context, source: ImageDecoder.Source, photoId: String,
        decide: (Bitmap) -> PhotoSort.Decision, ekitagTitle: String? = null,
    ): Pair<String?, PhotoSort.Decision>? {
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
        val bmp = cut?.takeIf { it !== whole }?.also { whole.recycle() } ?: whole
        return write(context, photoId, bmp) to decision
    }

    private fun write(context: Context, photoId: String, bmp: Bitmap): String? {
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

    fun importCopy(context: Context, source: Uri, photoId: String, circle: Boolean = false, ekitagTitle: String? = null): String? =
        importCopy(context, ImageDecoder.createSource(context.contentResolver, source), photoId, circle, ekitagTitle)

    fun importSorted(context: Context, source: Uri, photoId: String, decide: (Bitmap) -> PhotoSort.Decision, ekitagTitle: String? = null): Pair<String?, PhotoSort.Decision>? =
        importSorted(context, ImageDecoder.createSource(context.contentResolver, source), photoId, decide, ekitagTitle)

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
