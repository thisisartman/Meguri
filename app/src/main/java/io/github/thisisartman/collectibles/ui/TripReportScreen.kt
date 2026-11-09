package io.github.thisisartman.collectibles.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import android.util.Size
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.thisisartman.collectibles.R
import io.github.thisisartman.collectibles.data.ItemType
import io.github.thisisartman.collectibles.data.ReportRow
import io.github.thisisartman.collectibles.data.TripReport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** What the report image shows, already in the app's language and the theme's colours. */
private data class ReportSpec(
    val title: String,
    val subtitle: String?,
    val countText: String,
    val kinds: List<Pair<String, Int>>, // label, colour
    val lines: List<Triple<String, String, Int>>, // name, time, colour
    val more: String?,
    val photos: List<String>,
    val bg: Int, val fg: Int, val muted: Int, val accent: Int,
    val footer: String,
)

/**
 * Trip report (#61): pick a day with finds; share it as a story image (portrait 9:16, owner 2026-11-06) or as a CSV
 * (owner 2026-11-07: image, maybe CSV, no PDF). A trip is one day (owner, #46).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripReportScreen(vm: MainViewModel, startDay: Long? = null, onBack: () -> Unit) {
    val ja = isJapanese()
    val context = LocalContext.current
    val times by vm.findTimes.collectAsStateWithLifecycle()
    val days = remember(times) { TripReport.days(times) }
    // The day it was opened on (a Timeline day header), else the newest
    var day by rememberSaveable { mutableStateOf(startDay) }
    val chosen = day ?: days.firstOrNull()?.first
    val rowsById by vm.rows.collectAsStateWithLifecycle()
    val items = remember(rowsById) { rowsById.associateBy { it.item.id } }
    val finds by remember(chosen) { chosen?.let { vm.findsBetween(it, TripReport.nextDay(it)) } ?: flowOf(emptyList()) }.collectAsState(emptyList())
    val customTypes = LocalCustomTypes.current
    val rows = remember(finds, items, customTypes) {
        finds.mapNotNull { f -> items[f.find.itemId]?.item?.let { i -> ReportRow(f.find, i, i.customTypeId?.let { customTypes[it]?.name }) } }
    }
    val trip = remember { vm.currentTrip() }

    // Everything the image needs, resolved here so the drawing code needs no Compose
    val scheme = MaterialTheme.colorScheme
    val types = LocalTypeColors.current
    val countText = stringResource(R.string.trip_report_collected, rows.sumOf { it.find.quantity })
    val kindFmt = stringResource(R.string.trip_report_kind)
    val moreFmt = stringResource(R.string.trip_report_more)
    val footer = stringResource(R.string.trip_report_footer)
    val typeNames = mapOf(
        ItemType.MANHOLE_CARD to stringResource(R.string.type_manhole), ItemType.EKI_STAMP to stringResource(R.string.type_eki),
        ItemType.EKITAG to stringResource(R.string.type_ekitag), ItemType.GOSHUIN to stringResource(R.string.type_goshuin),
        ItemType.CUSTOM to stringResource(R.string.type_custom),
        ItemType.DAM_CARD to stringResource(R.string.type_dam), ItemType.MUSEUM_STAMP to stringResource(R.string.type_museum),
        ItemType.ROADSIDE_STATION to stringResource(R.string.type_roadside),
    )
    fun colorFor(r: ReportRow): Int = (r.item.customTypeId?.let { customTypes[it] }?.takeIf { r.item.type == ItemType.CUSTOM }?.let { androidx.compose.ui.graphics.Color(it.color) } ?: types.of(r.item.type)).toArgb()
    val spec = remember(rows, chosen, ja, scheme) {
        if (chosen == null || rows.isEmpty()) null else {
            val time = SimpleDateFormat("HH:mm", Locale.US)
            val shown = rows.take(MAX_LINES)
            // Today's planned route names the trip; another day is just its date
            val routeLine = trip?.takeIf { TripReport.dayStart(it.untilMs) == chosen }?.let { t ->
                listOfNotNull(items[t.from]?.item?.displayName(ja), items[t.to]?.item?.displayName(ja)).takeIf { it.size == 2 }?.joinToString(" → ")
            }
            ReportSpec(
                title = Dates.day(context, chosen), subtitle = routeLine, countText = countText,
                kinds = TripReport.byKind(rows).map { (k, n) ->
                    val sample = rows.first { (it.collection ?: it.item.type.name) == k }
                    kindFmt.format(sample.collection ?: typeNames[sample.item.type] ?: k, n) to colorFor(sample)
                },
                lines = shown.map { r -> Triple(r.item.displayName(ja), r.find.acquiredAt?.takeIf { TripReport.hasTime(r.find) }?.let { time.format(Date(it)) }.orEmpty(), colorFor(r)) },
                more = (rows.size - shown.size).takeIf { it > 0 }?.let { moreFmt.format(it) },
                photos = finds.flatMap { f -> f.photos.filter { !it.missing }.map { it.contentUri } }.take(6),
                bg = scheme.surface.toArgb(), fg = scheme.onSurface.toArgb(), muted = scheme.onSurfaceVariant.toArgb(), accent = scheme.primary.toArgb(),
                footer = footer,
            )
        }
    }
    var image by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(spec) { image = spec?.let { s -> withContext(Dispatchers.Default) { render(context, s) } } }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            windowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
            title = { Text(stringResource(R.string.trip_report)) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back)) } },
        )
        if (days.isEmpty()) {
            Text(stringResource(R.string.trip_report_none), Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@Column
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(days, key = { it.first }) { (d, n) ->
                FilterChip(selected = d == chosen, onClick = { day = d }, label = { Text(stringResource(R.string.trip_report_day, dateText(d), n)) },
                    modifier = Modifier.heightIn(min = 48.dp))
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
            image?.let { bmp ->
                Image(bmp.asImageBitmap(), contentDescription = spec?.let { it.title + ", " + it.countText },
                    // An outline, so the card reads as a card when its colour matches the page (light mode review)
                    modifier = Modifier.aspectRatio(9f / 16f).clip(RoundedCornerShape(12.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp)))
            }
        }
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                enabled = rows.isNotEmpty() && chosen != null,
                onClick = { chosen?.let { shareCsv(context, it, rows) } },
                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
            ) {
                Icon(painterResource(R.drawable.ic_table), contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.trip_report_share_csv))
            }
            Button(
                enabled = image != null && chosen != null,
                onClick = { val b = image; val c = chosen; if (b != null && c != null) shareImage(context, c, b) },
                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
            ) {
                Icon(painterResource(R.drawable.ic_image), contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.trip_report_share_image))
            }
        }
    }
}

private const val MAX_LINES = 10
private const val W = 1080
private const val H = 1920

/** Draws the 1080 × 1920 story image. */
private fun render(context: Context, s: ReportSpec): Bitmap {
    val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    c.drawColor(s.bg)
    val pad = 96f
    fun paint(size: Float, color: Int, bold: Boolean = false) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size; this.color = color; typeface = Typeface.create(Typeface.DEFAULT, if (bold) Typeface.BOLD else Typeface.NORMAL)
    }
    fun fit(text: String, p: Paint, width: Float): String {
        if (p.measureText(text) <= width) return text
        var t = text
        while (t.isNotEmpty() && p.measureText("$t…") > width) t = t.dropLast(1)
        return "$t…"
    }
    // A gold band across the top
    c.drawRect(0f, 0f, W.toFloat(), 24f, Paint().apply { color = s.accent })
    var y = 220f
    c.drawText(fit(s.title, paint(64f, s.fg, bold = true), W - 2 * pad), pad, y, paint(64f, s.fg, bold = true))
    s.subtitle?.let { y += 76f; c.drawText(fit(it, paint(44f, s.muted), W - 2 * pad), pad, y, paint(44f, s.muted)) }
    y += 150f
    c.drawText(s.countText, pad, y, paint(110f, s.accent, bold = true))
    y += 40f
    // What kinds, as coloured dots
    var x = pad
    y += 50f
    s.kinds.forEach { (label, color) ->
        val p = paint(38f, s.fg)
        val w = 36f + p.measureText(label) + 40f
        if (x + w > W - pad) { x = pad; y += 60f }
        c.drawCircle(x + 12f, y - 12f, 12f, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color })
        c.drawText(label, x + 36f, y, p)
        x += w
    }
    y += 50f
    // Up to six photos in a 3 × 2 grid
    val thumbs = s.photos.mapNotNull { uri ->
        io.github.thisisartman.collectibles.photo.PhotoStore.thumbnail(context, uri, 400)
    }
    if (thumbs.isNotEmpty()) {
        val gap = 18f
        val cell = (W - 2 * pad - 2 * gap) / 3
        thumbs.forEachIndexed { i, t ->
            val left = pad + (i % 3) * (cell + gap)
            val top = y + (i / 3) * (cell + gap)
            val save = c.save()
            val r = RectF(left, top, left + cell, top + cell)
            c.clipPath(android.graphics.Path().apply { addRoundRect(r, 24f, 24f, android.graphics.Path.Direction.CW) })
            // Centre-crop to a square
            val side = minOf(t.width, t.height)
            val src = android.graphics.Rect((t.width - side) / 2, (t.height - side) / 2, (t.width + side) / 2, (t.height + side) / 2)
            c.drawBitmap(t, src, r, Paint(Paint.FILTER_BITMAP_FLAG))
            c.restoreToCount(save)
        }
        y += ((thumbs.size + 2) / 3) * (cell + gap) + 20f
    }
    // The finds, with the time of each
    val nameP = paint(42f, s.fg)
    val timeP = paint(36f, s.muted)
    val bottom = H - 200f
    for ((name, time, color) in s.lines) {
        if (y + 70f > bottom) break
        y += 70f
        c.drawCircle(pad + 12f, y - 14f, 12f, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color })
        val tw = timeP.measureText(time)
        c.drawText(fit(name, nameP, W - 2 * pad - 60f - tw - 30f), pad + 44f, y, nameP)
        c.drawText(time, W - pad - tw, y, timeP)
    }
    s.more?.let { y += 64f; if (y < bottom + 40f) c.drawText(it, pad + 44f, y, paint(38f, s.muted)) }
    c.drawText(s.footer, pad, H - 110f, paint(40f, s.accent, bold = true))
    return bmp
}

private fun sharedFile(context: Context, name: String): File = File(context.cacheDir, "shared").apply { mkdirs() }.let { File(it, name) }

private fun fileName(day: Long, ext: String) = "meguri-trip-" + SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(day)) + "." + ext

private fun share(context: Context, file: File, type: String, title: String) {
    val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
    val send = Intent(Intent.ACTION_SEND).setType(type).putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(send, title).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
}

private fun shareImage(context: Context, day: Long, bmp: Bitmap) {
    val f = sharedFile(context, fileName(day, "png"))
    f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    share(context, f, "image/png", context.getString(R.string.trip_report_share_image))
}

private fun shareCsv(context: Context, day: Long, rows: List<ReportRow>) {
    val f = sharedFile(context, fileName(day, "csv"))
    // A byte-order mark, so spreadsheet apps read the Japanese names as UTF-8
    f.writeText("﻿" + TripReport.csv(rows), Charsets.UTF_8)
    share(context, f, "text/csv", context.getString(R.string.trip_report_share_csv))
}
