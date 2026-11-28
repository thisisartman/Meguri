package io.github.thisisartman.collectibles.photo

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.google.android.gms.common.moduleinstall.ModuleInstall
import com.google.android.gms.common.moduleinstall.ModuleInstallRequest
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

/**
 * Reads the text on photos on the phone (#206), with the same Japanese reader as Scan a card and the notebook. The
 * reader comes from Play services and downloads once; it is asked for first, as Scan a card does, so a fresh phone
 * doesn't leave every photo unsorted. Each photo is read as it is and once cleaned ([StampFind.clean]), as a faint
 * stamp may only read cleaned.
 */
class PhotoText private constructor(private val reader: TextRecognizer) : AutoCloseable {
    /** What a photo says, and its size as read; empty text when it can't be opened or read. */
    data class Read(val text: String, val width: Int, val height: Int)

    suspend fun read(context: Context, uri: Uri): Read = withContext(Dispatchers.Default) {
        val bmp = decode(context, uri, LONG_SIDE) ?: return@withContext Read("", 0, 0)
        val plain = text(bmp)
        val s = min(1f, CLEAN_SIDE.toFloat() / max(bmp.width, bmp.height))
        val small = if (s < 1f) Bitmap.createScaledBitmap(bmp, (bmp.width * s).toInt().coerceAtLeast(1), (bmp.height * s).toInt().coerceAtLeast(1), true) else bmp
        val px = IntArray(small.width * small.height).also { small.getPixels(it, 0, small.width, 0, 0, small.width, small.height) }
        val cleaned = runCatching { Bitmap.createBitmap(StampFind.clean(px, small.width, small.height), small.width, small.height, Bitmap.Config.ARGB_8888) }.getOrNull()
        val more = cleaned?.let { text(it) }.orEmpty()
        Read(listOf(plain, more).filter { it.isNotBlank() }.joinToString("\n"), bmp.width, bmp.height).also {
            if (small !== bmp) small.recycle()
            cleaned?.recycle()
            bmp.recycle()
        }
    }

    private fun text(bmp: Bitmap): String = runCatching { Tasks.await(reader.process(InputImage.fromBitmap(bmp, 0))).text }.getOrDefault("")

    override fun close() = reader.close()

    companion object {
        private const val LONG_SIDE = 2000
        private const val CLEAN_SIDE = 1200

        /** The reader, once Play services has it (asked for and waited on, at most a while); off the main thread. */
        suspend fun open(context: Context): PhotoText = withContext(Dispatchers.Default) {
            val r = TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())
            runCatching {
                Tasks.await(ModuleInstall.getClient(context).installModules(ModuleInstallRequest.newBuilder().addApi(r).build()), 60, java.util.concurrent.TimeUnit.SECONDS)
            }
            PhotoText(r)
        }

        private fun decode(context: Context, uri: Uri, longSide: Int): Bitmap? = runCatching {
            android.graphics.ImageDecoder.decodeBitmap(android.graphics.ImageDecoder.createSource(context.contentResolver, uri)) { d, info, _ ->
                d.allocator = android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
                val s = longSide.toFloat() / max(info.size.width, info.size.height)
                if (s < 1f) d.setTargetSize(max(1, (info.size.width * s).toInt()), max(1, (info.size.height * s).toInt()))
            }
        }.getOrNull()
    }
}
