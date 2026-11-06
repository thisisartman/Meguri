package io.github.thisisartman.collectibles.ui

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
    when {
        row.photoUri != null -> PhotoThumb(row.photoUri, size, modifier)
        // Manhole cards keep their official card code (F001); the rest show their type icon, since stations have
        // no official short form like airport codes (research 2026-11-07, owner's N10)
        row.collected && row.item.type == io.github.thisisartman.collectibles.data.ItemType.MANHOLE_CARD && row.item.code != null ->
            Seal(row.item.sealLabel(ja), LocalTypeColors.current.of(row.item.type), size, modifier)
        row.collected -> Seal("", LocalTypeColors.current.of(row.item.type), size, modifier, icon = typeIcon(row.item.type))
        else -> EmptySlot(size, modifier)
    }
}

/** The type's icon (public domain, CC0: Mapbox Maki and Temaki; see res/drawable/ic_type_*.xml). */
@androidx.annotation.DrawableRes
fun typeIcon(type: io.github.thisisartman.collectibles.data.ItemType): Int = when (type) {
    io.github.thisisartman.collectibles.data.ItemType.MANHOLE_CARD -> io.github.thisisartman.collectibles.R.drawable.ic_type_manhole
    io.github.thisisartman.collectibles.data.ItemType.GOSHUIN -> io.github.thisisartman.collectibles.R.drawable.ic_type_goshuin
    io.github.thisisartman.collectibles.data.ItemType.EKI_STAMP -> io.github.thisisartman.collectibles.R.drawable.ic_type_station
    io.github.thisisartman.collectibles.data.ItemType.EKITAG -> io.github.thisisartman.collectibles.R.drawable.ic_type_ekitag
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
fun EmptySlot(size: Dp, modifier: Modifier = Modifier, strong: Boolean = false) {
    val dash = LocalTypeColors.current.dash
    // strong: the nearest slot still to get in a stamp book, a solid darker ring (round 5, E2)
    val ink = androidx.compose.material3.MaterialTheme.colorScheme.onSurface
    Canvas(modifier.size(size)) {
        val w = (if (strong) 3 else 2).dp.toPx()
        drawCircle(
            color = if (strong) ink else dash,
            radius = (this.size.minDimension - w) / 2,
            center = Offset(this.size.width / 2, this.size.height / 2),
            style = Stroke(width = w, pathEffect = if (strong) null else PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()))),
        )
    }
}

@Composable
fun PhotoThumb(uri: String, size: Dp, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var bmp by remember(uri) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(uri) {
        bmp = withContext(Dispatchers.IO) {
            runCatching { context.contentResolver.loadThumbnail(Uri.parse(uri), Size(256, 256), null) }.getOrNull()
        }
    }
    val shape = RoundedCornerShape(size * 0.22f)
    val b = bmp
    if (b != null) {
        Image(b.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = modifier.size(size).clip(shape))
    } else {
        Box(modifier.size(size).clip(shape).background(LocalTypeColors.current.dash.copy(alpha = 0.3f)))
    }
}
