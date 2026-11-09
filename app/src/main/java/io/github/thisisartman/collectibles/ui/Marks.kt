package io.github.thisisartman.collectibles.ui

import androidx.compose.ui.res.painterResource

import android.graphics.Bitmap
import android.net.Uri
import android.util.Size
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.thisisartman.collectibles.data.ItemRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The collected / still-to-get mark (docs/DESIGN.md §3): your photo if the latest find has one,
 * else a filled seal in the type's colour, else (not collected) an empty dashed circle.
 */
@Composable
fun Mark(row: ItemRow, size: Dp, modifier: Modifier = Modifier) {
    val ja = isJapanese()
    @Composable fun noPhoto() = when {
        // Manhole cards keep their official card code (F001); the rest show their type icon, since stations have
        // no official short form like airport codes (research 2026-11-07, owner's N10)
        row.collected && row.item.type == io.github.thisisartman.collectibles.data.ItemType.MANHOLE_CARD && row.item.code != null ->
            Seal(row.item.sealLabel(ja), LocalTypeColors.current.of(row.item.type), size, modifier)
        row.collected -> Seal("", colorOf(row.item), size, modifier, icon = iconOf(row.item))
        else -> EmptySlot(size, modifier, icon = iconOf(row.item))
    }
    // The card's own picture when downloaded (#53): greyed until collected; your photo still comes first
    val cards by io.github.thisisartman.collectibles.photo.CardPictures.have.collectAsState()
    val code = row.item.code
    when {
        row.photoUri != null -> PhotoThumb(row.photoUri, size, modifier, fallback = { noPhoto() })
        row.item.type == io.github.thisisartman.collectibles.data.ItemType.MANHOLE_CARD && code != null && code in cards ->
            CardPictureThumb(code, row.collected, size, modifier) { noPhoto() }
        else -> noPhoto()
    }
}

/** Decoded card pictures, small, kept while the app runs so lists scroll without reloading. */
private val cardThumbCache = android.util.LruCache<String, Bitmap>(120)

@Composable
fun CardPictureThumb(code: String, collected: Boolean, size: Dp, modifier: Modifier = Modifier, fallback: @Composable () -> Unit) {
    val context = LocalContext.current
    val px = with(LocalDensity.current) { size.roundToPx() }
    var bmp by remember(code) { mutableStateOf(cardThumbCache.get(code)) }
    var failed by remember(code) { mutableStateOf(false) }
    LaunchedEffect(code) {
        if (bmp != null) return@LaunchedEffect
        bmp = withContext(Dispatchers.IO) {
            runCatching {
                val f = io.github.thisisartman.collectibles.photo.CardPictures.file(context, code)
                val o = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                android.graphics.BitmapFactory.decodeFile(f.path, o)
                var s = 1; while (o.outWidth / (s * 2) >= px) s *= 2
                android.graphics.BitmapFactory.decodeFile(f.path, android.graphics.BitmapFactory.Options().apply { inSampleSize = s })
            }.getOrNull()
        }?.also { cardThumbCache.put(code, it) }
        failed = bmp == null
    }
    val b = bmp
    when {
        b != null -> Image(
            b.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop,
            colorFilter = if (collected) null else androidx.compose.ui.graphics.ColorFilter.colorMatrix(androidx.compose.ui.graphics.ColorMatrix().apply { setToSaturation(0f) }),
            alpha = if (collected) 1f else 0.55f,
            modifier = modifier.size(size).clip(RoundedCornerShape(size * 0.22f)),
        )
        failed -> fallback()
        else -> Box(modifier.size(size).clip(RoundedCornerShape(size * 0.22f)).background(LocalTypeColors.current.dash.copy(alpha = 0.3f)))
    }
}

/** The type's icon (public domain, CC0: Mapbox Maki and Temaki; see res/drawable/ic_type_*.xml). */
@androidx.annotation.DrawableRes
fun typeIcon(type: io.github.thisisartman.collectibles.data.ItemType): Int = when (type) {
    io.github.thisisartman.collectibles.data.ItemType.MANHOLE_CARD -> io.github.thisisartman.collectibles.R.drawable.ic_type_manhole
    io.github.thisisartman.collectibles.data.ItemType.GOSHUIN -> io.github.thisisartman.collectibles.R.drawable.ic_type_goshuin
    io.github.thisisartman.collectibles.data.ItemType.EKI_STAMP -> io.github.thisisartman.collectibles.R.drawable.ic_type_station
    io.github.thisisartman.collectibles.data.ItemType.EKITAG -> io.github.thisisartman.collectibles.R.drawable.ic_type_ekitag
    // Your own collections: their chosen icon is drawn by [customIcon]; this is the fallback
    io.github.thisisartman.collectibles.data.ItemType.CUSTOM -> io.github.thisisartman.collectibles.R.drawable.ic_collect
    io.github.thisisartman.collectibles.data.ItemType.DAM_CARD -> io.github.thisisartman.collectibles.R.drawable.ic_type_dam
    io.github.thisisartman.collectibles.data.ItemType.MUSEUM_STAMP -> io.github.thisisartman.collectibles.R.drawable.ic_type_museum
    io.github.thisisartman.collectibles.data.ItemType.ROADSIDE_STATION -> io.github.thisisartman.collectibles.R.drawable.ic_type_roadside
}

/** A small type icon in the type's colour, e.g. next to a name. */
@Composable
fun TypeIcon(type: io.github.thisisartman.collectibles.data.ItemType, size: Dp = 20.dp, modifier: Modifier = Modifier) {
    androidx.compose.material3.Icon(
        androidx.compose.ui.res.painterResource(typeIcon(type)),
        contentDescription = null,
        tint = LocalTypeColors.current.of(type),
        modifier = modifier.size(size),
    )
}

@Composable
fun Seal(label: String, color: androidx.compose.ui.graphics.Color, size: Dp, modifier: Modifier = Modifier, @androidx.annotation.DrawableRes icon: Int? = null) {
    Box(modifier.size(size).clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
        if (icon != null) {
            androidx.compose.material3.Icon(
                androidx.compose.ui.res.painterResource(icon), contentDescription = null,
                tint = LocalTypeColors.current.onSeal, modifier = Modifier.size(size * 0.55f),
            )
            return@Box
        }
        Text(
            label,
            color = LocalTypeColors.current.onSeal,
            // Sized to the seal, not to the phone's text size: the seal does not grow, so its label must not either
            fontSize = with(LocalDensity.current) { (size * 0.28f).coerceIn(8.dp, 18.dp).toSp() },
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Clip,
        )
    }
}

@Composable
fun EmptySlot(size: Dp, modifier: Modifier = Modifier, strong: Boolean = false, @androidx.annotation.DrawableRes icon: Int? = null) {
    val dash = LocalTypeColors.current.dash
    // strong: the nearest slot still to get in a stamp book, a solid darker ring (round 5, E2)
    val ink = androidx.compose.material3.MaterialTheme.colorScheme.onSurface
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val w = (if (strong) 3 else 2).dp.toPx()
            drawCircle(
                color = if (strong) ink else dash,
                radius = (this.size.minDimension - w) / 2,
                center = Offset(this.size.width / 2, this.size.height / 2),
                style = Stroke(width = w, pathEffect = if (strong) null else PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()))),
            )
        }
        // Its category icon in grey: not collected yet, but you can see what it is (a station stamp or its Ekitag)
        if (icon != null) androidx.compose.material3.Icon(
            painterResource(icon), contentDescription = null, tint = dash, modifier = Modifier.size(size * 0.42f),
        )
    }
}

@Composable
fun PhotoThumb(uri: String, size: Dp, modifier: Modifier = Modifier, fallback: (@Composable () -> Unit)? = null) {
    val context = LocalContext.current
    var bmp by remember(uri) { mutableStateOf<Bitmap?>(null) }
    var failed by remember(uri) { mutableStateOf(false) }
    LaunchedEffect(uri) {
        bmp = withContext(Dispatchers.IO) {
            runCatching { io.github.thisisartman.collectibles.photo.PhotoStore.thumbnail(context, uri, 256) }.getOrNull()
        }
        failed = bmp == null
    }
    val shape = RoundedCornerShape(size * 0.22f)
    val b = bmp
    when {
        b != null -> Image(b.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = modifier.size(size).clip(shape))
        // A picture that can't be read (deleted from the gallery, or no longer the app's) shows the seal instead of
        // a grey box (Poco, 2026-11-08)
        failed && fallback != null -> fallback()
        else -> Box(modifier.size(size).clip(shape).background(LocalTypeColors.current.dash.copy(alpha = 0.3f)))
    }
}
