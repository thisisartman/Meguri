package io.github.thisisartman.collectibles.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Rotate90DegreesCw
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.thisisartman.collectibles.R
import io.github.thisisartman.collectibles.data.Photo
import io.github.thisisartman.collectibles.data.PhotoKind
import io.github.thisisartman.collectibles.photo.CoverCrop
import io.github.thisisartman.collectibles.photo.EditGeometry
import io.github.thisisartman.collectibles.photo.EditRect
import io.github.thisisartman.collectibles.photo.Handle
import io.github.thisisartman.collectibles.photo.PhotoEdit
import io.github.thisisartman.collectibles.photo.PhotoStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** The preview's long side: enough to place a crop by finger, small enough for memory. Save cuts from the full picture. */
private const val PREVIEW_PX = 1600

/**
 * Edit a photo (#208; owner, 2026-11-28: "some cards are not properly cropped"): a crop box with corners and edges
 * to drag, rotate a quarter turn, straighten up to 15 degrees, Reset, and for a manhole cover a circle. It starts
 * from the kept original when there is one (photos the app cut since this version); Reset goes back to that
 * whole original. Without one it edits the photo as it is: rotate and crop tighter only, and it says so.
 * Cancel and Save stay at the bottom. [onSave] reports whether the edit was written.
 */
@Composable
fun PhotoEditScreen(photo: Photo, onCancel: () -> Unit, onSave: (PhotoEdit, (Boolean) -> Unit) -> Unit) {
    Dialog(onDismissRequest = onCancel, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val context = LocalContext.current
        var bmp by remember(photo.id) { mutableStateOf<Bitmap?>(null) }
        var fromOriginal by remember(photo.id) { mutableStateOf(false) }
        var loaded by remember(photo.id) { mutableStateOf(false) }
        var turns by remember(photo.id) { mutableIntStateOf(0) }
        var degrees by remember(photo.id) { mutableFloatStateOf(0f) }
        var crop by remember(photo.id) { mutableStateOf(EditRect(0f, 0f, 1f, 1f)) }
        var circle by remember(photo.id) { mutableStateOf(false) }
        var saving by remember(photo.id) { mutableStateOf(false) }
        var failed by remember(photo.id) { mutableStateOf(false) }
        val cover = photo.role == PhotoKind.MANHOLE

        LaunchedEffect(photo.id) {
            withContext(Dispatchers.IO) {
                val orig = photo.originalUri?.takeIf { PhotoStore.exists(context, it) }
                val b = (orig ?: photo.contentUri).let { PhotoStore.load(context, it, PREVIEW_PX) }
                // A cover cut as a circle opens as a circle, placed on the cover found in the original
                val wasCircle = cover && PhotoStore.load(context, photo.contentUri, 64)?.let { s ->
                    (s.hasAlpha() && android.graphics.Color.alpha(s.getPixel(0, 0)) == 0).also { s.recycle() }
                } == true
                var start = b?.let { EditRect.of(it.width.toFloat(), it.height.toFloat()) }
                if (b != null && wasCircle && orig != null) {
                    val c = runCatching { CoverCrop.find(b) }.getOrNull()
                    val whole = EditRect.of(b.width.toFloat(), b.height.toFloat())
                    start = if (c != null) {
                        val r = c.r * 1.03f
                        EditGeometry.square(EditGeometry.clamp(EditRect(c.cx - r, c.cy - r, c.cx + r, c.cy + r), whole, 32f))
                    } else EditGeometry.square(whole)
                }
                withContext(Dispatchers.Main) {
                    bmp = b
                    fromOriginal = orig != null
                    circle = wasCircle && orig != null
                    start?.let { crop = it }
                    loaded = true
                }
            }
        }

        val b = bmp
        val (fw, fh) = if (b != null) EditGeometry.turnedSize(b.width.toFloat(), b.height.toFloat(), turns) else (1f to 1f)
        val minSide = max(32f, min(fw, fh) * 0.05f)
        val bounds = EditGeometry.allowed(fw, fh, degrees)

        fun reset() {
            val s = b ?: return
            turns = 0; degrees = 0f; circle = false
            crop = EditRect.of(s.width.toFloat(), s.height.toFloat())
        }

        Column(Modifier.fillMaxSize().background(Color.Black)) {
            Box(Modifier.weight(1f).fillMaxWidth().statusBarsPadding().padding(16.dp).clipToBounds(), contentAlignment = Alignment.Center) {
                when {
                    b != null -> CropCanvas(b, turns, degrees, crop, circle) { handle, dx, dy ->
                        crop = EditGeometry.drag(crop, handle, dx, dy, bounds, minSide, circle)
                    }
                    loaded -> Text(stringResource(R.string.photo_edit_failed), color = Color.White)
                    else -> CircularProgressIndicator(color = Color.White)
                }
            }
            // Tools under the picture: icons, each named for a screen reader and a long press
            val toolColors = IconButtonDefaults.iconButtonColors(contentColor = Color.White, disabledContentColor = Color.Gray)
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = {
                    if (b == null) return@IconButton
                    val turned = EditGeometry.turnRect(crop, fw, fh)
                    turns = (turns + 1) % 4
                    val (nw, nh) = EditGeometry.turnedSize(b.width.toFloat(), b.height.toFloat(), turns)
                    crop = EditGeometry.fitAfterStraighten(turned, nw, nh, degrees, minSide)
                }, enabled = b != null && !saving, colors = toolColors) {
                    Icon(Icons.Outlined.Rotate90DegreesCw, contentDescription = stringResource(R.string.photo_edit_rotate))
                }
                // A cover can be cut as a circle again, or kept as the full photo (needs the original)
                if (cover && fromOriginal) IconToggleButton(
                    checked = circle,
                    onCheckedChange = { on -> circle = on; if (on) crop = EditGeometry.square(crop) },
                    enabled = b != null && !saving,
                    colors = IconButtonDefaults.iconToggleButtonColors(contentColor = Color.Gray, checkedContentColor = MaterialTheme.colorScheme.primary),
                ) { Icon(Icons.Outlined.Circle, contentDescription = stringResource(R.string.photo_edit_circle)) }
                IconButton(onClick = { reset() }, enabled = b != null && !saving, colors = toolColors) {
                    Icon(Icons.Outlined.RestartAlt, contentDescription = stringResource(R.string.photo_edit_reset))
                }
            }
            if (fromOriginal) Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Straighten, contentDescription = stringResource(R.string.photo_edit_straighten), tint = Color.White)
                Spacer(Modifier.width(12.dp))
                Slider(
                    value = degrees,
                    onValueChange = { v ->
                        // Steps of a tenth of a degree; the box shrinks only as much as the turned picture needs
                        degrees = (v * 10).roundToInt() / 10f
                        crop = EditGeometry.fitAfterStraighten(crop, fw, fh, degrees, minSide)
                        if (circle) crop = EditGeometry.square(crop)
                    },
                    valueRange = -EditGeometry.MAX_DEGREES..EditGeometry.MAX_DEGREES,
                    enabled = b != null && !saving,
                    colors = SliderDefaults.colors(inactiveTrackColor = Color.DarkGray),
                    modifier = Modifier.weight(1f),
                )
                Text("%+.1f°".format(degrees), color = Color.White, style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(56.dp).padding(start = 8.dp))
            } else if (loaded && b != null) Text(
                stringResource(R.string.photo_edit_no_original), color = Color.LightGray, style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            )
            if (failed) Text(
                stringResource(R.string.photo_edit_failed), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
            // Cancel and Save, always on screen above the gesture bar
            Row(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.cancel), color = Color.White) }
                Button(
                    onClick = {
                        saving = true; failed = false
                        val c = if (circle) EditGeometry.square(crop) else crop
                        onSave(PhotoEdit(turns, degrees, EditGeometry.toShares(c, fw, fh), circle, fromOriginal)) { ok ->
                            saving = false
                            if (ok) onCancel() else failed = true
                        }
                    },
                    enabled = b != null && !saving,
                    modifier = Modifier.weight(1f),
                ) {
                    if (saving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Text(stringResource(R.string.photo_edit_save))
                }
            }
        }
    }
}

/**
 * The picture turned and straightened, fitted to the space, with the crop box (or circle) over it: outside it
 * dimmed, its corners marked. A drag on a corner or edge moves that side, inside the box moves the box;
 * [onDrag] gets the move in the frame's pixels.
 */
@Composable
private fun CropCanvas(bmp: Bitmap, turns: Int, degrees: Float, crop: EditRect, circle: Boolean, onDrag: (Handle, Float, Float) -> Unit) {
    val (fw, fh) = EditGeometry.turnedSize(bmp.width.toFloat(), bmp.height.toFloat(), turns)
    var held by remember { mutableStateOf<Handle?>(null) }
    val paint = remember { android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG or android.graphics.Paint.ANTI_ALIAS_FLAG) }
    val slopPx = with(androidx.compose.ui.platform.LocalDensity.current) { 24.dp.toPx() }
    // Read inside the gesture, so a drag always sees the box as it is now
    val latest = androidx.compose.runtime.rememberUpdatedState(crop)
    Canvas(
        Modifier.fillMaxSize().pointerInput(bmp, turns, degrees) {
            detectDragGestures(
                onDragStart = { at ->
                    val (s, ox, oy) = EditGeometry.fit(fw, fh, size.width.toFloat(), size.height.toFloat())
                    held = EditGeometry.handleAt(latest.value, (at.x - ox) / s, (at.y - oy) / s, slopPx / s)
                },
                onDragEnd = { held = null },
                onDragCancel = { held = null },
                onDrag = { change, amount ->
                    val h = held ?: return@detectDragGestures
                    change.consume()
                    val s = EditGeometry.fit(fw, fh, size.width.toFloat(), size.height.toFloat()).first
                    onDrag(h, amount.x / s, amount.y / s)
                },
            )
        },
    ) {
        val (s, ox, oy) = EditGeometry.fit(fw, fh, size.width, size.height)
        val m = EditGeometry.affine(bmp.width.toFloat(), bmp.height.toFloat(), turns, degrees)
        val matrix = android.graphics.Matrix().apply {
            setValues(floatArrayOf(m[0], m[1], m[2], m[3], m[4], m[5], 0f, 0f, 1f))
            postScale(s, s)
            postTranslate(ox, oy)
        }
        drawIntoCanvas { it.nativeCanvas.drawBitmap(bmp, matrix, paint) }
        val r = Rect(ox + crop.left * s, oy + crop.top * s, ox + crop.right * s, oy + crop.bottom * s)
        val dim = Path().apply {
            fillType = PathFillType.EvenOdd
            addRect(Rect(Offset.Zero, size))
            if (circle) addOval(r) else addRect(r)
        }
        drawPath(dim, Color.Black.copy(alpha = 0.6f))
        val line = 1.5.dp.toPx()
        drawRect(Color.White, r.topLeft, r.size, style = Stroke(line))
        if (circle) drawOval(Color.White, r.topLeft, r.size, style = Stroke(line))
        // Thirds, as a guide for straightening
        for (i in 1..2) {
            val x = r.left + r.width * i / 3; val y = r.top + r.height * i / 3
            drawLine(Color.White.copy(alpha = 0.35f), Offset(x, r.top), Offset(x, r.bottom), 1f)
            drawLine(Color.White.copy(alpha = 0.35f), Offset(r.left, y), Offset(r.right, y), 1f)
        }
        // Corner marks: thick short lines a finger can find
        val k = min(20.dp.toPx(), min(r.width, r.height) / 3)
        val w = 4.dp.toPx()
        val corners = listOf(
            Triple(r.topLeft, 1f, 1f), Triple(r.topRight, -1f, 1f), Triple(r.bottomLeft, 1f, -1f), Triple(r.bottomRight, -1f, -1f),
        )
        for ((p, sx, sy) in corners) {
            drawLine(Color.White, p, Offset(p.x + sx * k, p.y), w)
            drawLine(Color.White, p, Offset(p.x, p.y + sy * k), w)
        }
        // Edge marks at each side's middle
        val e = min(16.dp.toPx(), min(r.width, r.height) / 4)
        drawLine(Color.White, Offset(r.center.x - e, r.top), Offset(r.center.x + e, r.top), w)
        drawLine(Color.White, Offset(r.center.x - e, r.bottom), Offset(r.center.x + e, r.bottom), w)
        drawLine(Color.White, Offset(r.left, r.center.y - e), Offset(r.left, r.center.y + e), w)
        drawLine(Color.White, Offset(r.right, r.center.y - e), Offset(r.right, r.center.y + e), w)
        if (held != null) drawRect(Color.White.copy(alpha = 0.08f), r.topLeft, Size(r.width, r.height))
    }
}
