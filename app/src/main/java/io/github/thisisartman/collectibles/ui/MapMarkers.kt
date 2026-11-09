package io.github.thisisartman.collectibles.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import androidx.core.content.ContextCompat
import io.github.thisisartman.collectibles.photo.CardPictures
import io.github.thisisartman.collectibles.photo.PhotoStore

/**
 * Map markers as pictures (owner, 2026-11-09, option A after a study of Immich's map): a round marker with a ring in
 * the category colour. Inside: your photo, else the downloaded card picture, else the category icon in colour.
 * Not collected yet: the inside is grey, as in the book; the ring keeps the colour so the category still reads.
 * Each marker is drawn once into a small bitmap and handed to MapLibre, which draws them all in one layer.
 */
object MapMarkers {
    /** What a marker shows, without drawing it: also its image name in the map style. */
    data class Look(val picture: String?, val cardCode: String?, val icon: Int, val ring: Int, val collected: Boolean, val sizePx: Int) {
        val key: String get() = listOf(
            picture?.let { "p:$it" } ?: cardCode?.let { "c:$it" } ?: "i:$icon",
            Integer.toHexString(ring), if (collected) "1" else "0", sizePx,
        ).joinToString("|")
        /** The same marker with the category icon instead of a picture (shown while the picture loads). */
        val plain: Look get() = copy(picture = null, cardCode = null)
    }

    private const val LIGHT = 0xFFF4F0E8.toInt()

    /** Draws [look]; a picture that can't be read falls back to the icon. */
    fun draw(context: Context, look: Look): Bitmap {
        val d = look.sizePx
        val out = Bitmap.createBitmap(d, d, Bitmap.Config.ARGB_8888)
        out.density = context.resources.displayMetrics.densityDpi
        val c = Canvas(out)
        val ringW = d * 0.09f
        val r = d / 2f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        // Ring in the category colour, with a thin dark edge so it stands off light and dark maps alike
        paint.color = 0x66000000; c.drawCircle(r, r, r, paint)
        paint.color = look.ring; c.drawCircle(r, r, r - 1f, paint)
        val inner = r - ringW
        val grey = if (look.collected) null else ColorMatrixColorFilter(ColorMatrix().apply {
            setSaturation(0f)
            postConcat(ColorMatrix().apply { setScale(0.82f, 0.82f, 0.82f, 1f) })
        })
        val photo = look.picture?.let { PhotoStore.thumbnail(context, it, d * 2) }
            ?: look.cardCode?.let { decodeCard(context, it, d * 2) }
        if (photo != null) {
            // Centre-cropped into the inner circle
            val s = maxOf(2 * inner / photo.width, 2 * inner / photo.height)
            val m = Matrix().apply {
                setScale(s, s)
                postTranslate(r - photo.width * s / 2f, r - photo.height * s / 2f)
            }
            paint.shader = BitmapShader(photo, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply { setLocalMatrix(m) }
            paint.colorFilter = grey
            c.drawCircle(r, r, inner, paint)
            paint.shader = null; paint.colorFilter = null
            photo.recycle()
        } else {
            paint.color = LIGHT; paint.colorFilter = grey
            c.drawCircle(r, r, inner, paint)
            ContextCompat.getDrawable(context, look.icon)?.mutate()?.let { dr ->
                // A tint is a colour filter, so grey is a grey tint rather than a filter on top
                dr.setTint(if (look.collected) look.ring else 0xFF8E8A84.toInt())
                val pad = (inner * 0.45f).toInt()
                dr.setBounds((r - pad).toInt(), (r - pad).toInt(), (r + pad).toInt(), (r + pad).toInt())
                dr.draw(c)
            }
            paint.colorFilter = null
        }
        return out
    }

    private fun decodeCard(context: Context, code: String, px: Int): Bitmap? = runCatching {
        val f = CardPictures.file(context, code)
        if (!f.isFile) return null
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, o)
        var s = 1
        while (o.outWidth / (s * 2) >= px && o.outHeight / (s * 2) >= px) s *= 2
        BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = s })
    }.getOrNull()
}
