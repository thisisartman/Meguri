package io.github.thisisartman.collectibles.photo

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import java.io.File
import kotlin.math.max
import kotlin.math.min

/**
 * A stamp photo cut to its stamp, as a notebook page is (owner, 2026-11-26, photo file #188: "autocrop them"):
 * the page is looked at small, the biggest stamp on it ([StampFind.find]) is cut from the full photo and cleaned
 * ([StampFind.clean]), as the notebook saves it by default. A photo that isn't mostly paper, or with no stamp
 * found, is left whole.
 */
object StampCrop {
    /** Writes [file]'s stamp to [out] as a JPEG; false when there is none to cut, and then nothing is written. */
    fun crop(file: File, out: File): Boolean = runCatching {
        val full = decode(file, 3000) ?: return false
        val cleaned = cut(full) ?: return false
        out.outputStream().use { cleaned.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        true
    }.getOrDefault(false)

    /** [full]'s stamp, cut and cleaned, or null when it isn't a page with a stamp on it. */
    fun cut(full: Bitmap): Bitmap? = runCatching {
        val s = 480f / max(full.width, full.height)
        val small = if (s < 1f) Bitmap.createScaledBitmap(full, max(1, (full.width * s).toInt()), max(1, (full.height * s).toInt()), true) else full
        val px = IntArray(small.width * small.height).also { small.getPixels(it, 0, small.width, 0, 0, small.width, small.height) }
        if (!StampFind.isPage(px)) return null
        val b = StampFind.find(px, small.width, small.height).firstOrNull() ?: return null
        val x = (b.left * full.width).toInt(); val y = (b.top * full.height).toInt()
        val w = ((b.right - b.left) * full.width).toInt().coerceAtLeast(8); val h = ((b.bottom - b.top) * full.height).toInt().coerceAtLeast(8)
        val cut = Bitmap.createBitmap(full, x, y, min(w, full.width - x), min(h, full.height - y))
        // Cleaned no bigger than 1200 px, as on a notebook page: plenty for a stamp
        val c = min(1f, 1200f / max(cut.width, cut.height))
        val stamp = if (c < 1f) Bitmap.createScaledBitmap(cut, (cut.width * c).toInt(), (cut.height * c).toInt(), true) else cut
        val sp = IntArray(stamp.width * stamp.height).also { stamp.getPixels(it, 0, stamp.width, 0, 0, stamp.width, stamp.height) }
        Bitmap.createBitmap(StampFind.clean(sp, stamp.width, stamp.height), stamp.width, stamp.height, Bitmap.Config.ARGB_8888)
    }.getOrNull()

    private fun decode(file: File, longSide: Int): Bitmap? = runCatching {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { d, info, _ ->
            d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val s = longSide.toFloat() / max(info.size.width, info.size.height)
            if (s < 1f) d.setTargetSize(max(1, (info.size.width * s).toInt()), max(1, (info.size.height * s).toInt()))
        }
    }.getOrNull()
}
