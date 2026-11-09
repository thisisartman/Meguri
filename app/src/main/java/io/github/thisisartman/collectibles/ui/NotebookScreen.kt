package io.github.thisisartman.collectibles.ui

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.thisisartman.collectibles.R
import io.github.thisisartman.collectibles.photo.StampFind
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * A notebook page being worked through (owner, 2026-11-09): the page, and the boxes around its stamps. Lives outside
 * the screens so the review and the matching share it; one page at a time.
 */
object NotebookSession {
    var page: Uri? = null
    val boxes = mutableStateListOf<StampFind.Box>()

    /**
     * The stamps as read from the page and the choices made, kept here so a trip to the Add page and back does not
     * read the page again or lose what was chosen (finding 33). [workBoxes] are the boxes they were read from: a
     * change in the review reads them again.
     */
    internal var work by mutableStateOf<List<StampWork>?>(null)
    internal var workBoxes: List<StampFind.Box> = emptyList()

    /** The stamp whose "Not in Meguri? Add it" is open. */
    internal var adding: StampWork? = null

    /** The day the stamps were collected (local noon), or null: had before Meguri ("Notebook 4: date optional"). */
    var dateMs by mutableStateOf<Long?>(null)

    fun start(page: Uri, found: List<StampFind.Box>) {
        this.page = page; boxes.clear(); boxes.addAll(found)
        work = null; workBoxes = emptyList(); adding = null; dateMs = null
    }

    /** The item just made on the Add page becomes the choice of the stamp that asked for it. */
    fun added(itemId: String) {
        adding?.let { it.chosen = itemId; it.query = "" }
        adding = null
    }
}

/** One stamp being matched: its picture as photographed and cleaned, what was read, and the station chosen. */
internal class StampWork(val original: Bitmap, val cleaned: Bitmap, val text: String, val matches: List<io.github.thisisartman.collectibles.data.StationMatch.Match>) {
    var showOriginal by mutableStateOf(false)
    var chosen by mutableStateOf<String?>(matches.firstOrNull()?.takeIf { it.score >= io.github.thisisartman.collectibles.data.StationMatch.ASSIGN }?.id)
    var skipped by mutableStateOf(false)
    var query by mutableStateOf("")
}

/** The stamp types a notebook stamp can be: stations, roadside stations, museums, and your own stamp types. */
private fun isStampTarget(r: io.github.thisisartman.collectibles.data.ItemRow, custom: Map<String, io.github.thisisartman.collectibles.data.CollectionType>): Boolean =
    when (r.item.type) {
        io.github.thisisartman.collectibles.data.ItemType.EKI_STAMP, io.github.thisisartman.collectibles.data.ItemType.ROADSIDE_STATION,
        io.github.thisisartman.collectibles.data.ItemType.MUSEUM_STAMP, io.github.thisisartman.collectibles.data.ItemType.GOSHUIN -> true
        io.github.thisisartman.collectibles.data.ItemType.CUSTOM -> r.item.customTypeId?.let { custom[it] }?.category() == Category.STAMP
        else -> false
    }

/**
 * Each stamp found, matched to a station by the name printed on it (owner's bands: 75% and up is chosen, 60 to 75%
 * is offered as "This seems to be ...", below that the person picks by typing). Cleaned by default, "Show original"
 * to switch. Saved as "had before Meguri" (no date), or on the day given under "Date", never at the photo's time and
 * place.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotebookMatchScreen(vm: MainViewModel, onBack: () -> Unit, onAddNew: () -> Unit, onSaved: (Int) -> Unit) {
    val context = LocalContext.current
    val ja = isJapanese()
    val rows by vm.rows.collectAsStateWithLifecycle()
    val custom = LocalCustomTypes.current
    val targets = remember(rows, custom) { rows.filter { isStampTarget(it, custom) } }
    val work = NotebookSession.work
    var saving by remember { mutableStateOf(false) }
    LaunchedEffect(NotebookSession.page) {
        val page = NotebookSession.page ?: return@LaunchedEffect
        val boxes = NotebookSession.boxes.toList()
        // Back from the Add page (or Back from here and Done again with the same boxes): the stamps as they were
        if (NotebookSession.work != null && NotebookSession.workBoxes == boxes) return@LaunchedEffect
        NotebookSession.work = null
        val cands = targets.map { io.github.thisisartman.collectibles.data.StationMatch.Candidate(it.item.id, it.item.nameJa, it.item.nameRomaji) }
        val reader = com.google.mlkit.vision.text.TextRecognition.getClient(com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions.Builder().build())
        val read = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            val full = decodePage(context, page, 3000) ?: return@withContext emptyList()
            boxes.mapNotNull { b ->
                val x = (b.left * full.width).toInt(); val y = (b.top * full.height).toInt()
                val w = ((b.right - b.left) * full.width).toInt().coerceAtLeast(8); val h = ((b.bottom - b.top) * full.height).toInt().coerceAtLeast(8)
                val crop = runCatching { Bitmap.createBitmap(full, x, y, min(w, full.width - x), min(h, full.height - y)) }.getOrNull() ?: return@mapNotNull null
                // Clean on a copy no bigger than 1200 px: plenty for a stamp, and quick
                val s = min(1f, 1200f / max(crop.width, crop.height))
                val small = if (s < 1f) Bitmap.createScaledBitmap(crop, (crop.width * s).toInt(), (crop.height * s).toInt(), true) else crop
                val px = IntArray(small.width * small.height).also { small.getPixels(it, 0, small.width, 0, 0, small.width, small.height) }
                val cleaned = Bitmap.createBitmap(StampFind.clean(px, small.width, small.height), small.width, small.height, Bitmap.Config.ARGB_8888)
                // Read both: a faint print may only read once cleaned
                fun read(bmp: Bitmap) = runCatching {
                    com.google.android.gms.tasks.Tasks.await(reader.process(com.google.mlkit.vision.common.InputImage.fromBitmap(bmp, 0))).text
                }.getOrDefault("")
                val text = read(small) + "\n" + read(cleaned)
                val matches = io.github.thisisartman.collectibles.data.StationMatch.match(text, cands)
                if (io.github.thisisartman.collectibles.BuildConfig.DEBUG) runCatching {
                    val dir = java.io.File(context.getExternalFilesDir(null), "stamp-debug").apply { mkdirs() }
                    val stamp = System.currentTimeMillis()
                    java.io.File(dir, "$stamp.txt").writeText("read:\n$text\n--- matches:\n" + matches.joinToString("\n") { m -> "${rows.firstOrNull { it.item.id == m.id }?.item?.nameJa} ${"%.2f".format(m.score)}" })
                    java.io.File(dir, "$stamp-clean.jpg").outputStream().use { cleaned.compress(Bitmap.CompressFormat.JPEG, 90, it) }
                }
                StampWork(small, cleaned, text, matches)
            }
        }
        NotebookSession.workBoxes = boxes
        NotebookSession.work = read
    }
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            windowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
            title = { Text(stringResource(R.string.nb_match_title)) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
        )
        val list = work
        if (list == null) {
            Column(Modifier.weight(1f).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                CircularProgressIndicator(); Text(stringResource(R.string.nb_reading), Modifier.padding(top = 12.dp))
            }
            return@Column
        }
        androidx.compose.foundation.lazy.LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            items(list.size) { i -> StampRow(list[i], i + 1, targets, rows, vm, ja) { NotebookSession.adding = list[i]; onAddNew() } }
        }
        // Date, optional ("Notebook 4"): none means had before Meguri; a day saves the stamps on that day
        NotebookDateRow()
        val ready = list.count { !it.skipped && it.chosen != null }
        Button(
            onClick = {
                saving = true
                val out = list.filter { !it.skipped && it.chosen != null }.map { w ->
                    val bmp = if (w.showOriginal) w.original else w.cleaned
                    val f = java.io.File(java.io.File(context.cacheDir, "scans").apply { mkdirs() }, "stamp-${System.nanoTime()}.jpg")
                    f.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 95, it) }
                    w.chosen!! to Uri.fromFile(f)
                }
                vm.addNotebookStamps(out, NotebookSession.dateMs) { n -> saving = false; onSaved(n) }
            },
            enabled = ready > 0 && !saving,
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp).heightIn(min = 48.dp),
        ) { Text(androidx.compose.ui.res.pluralStringResource(R.plurals.nb_save, ready, ready)) }
    }
}

/** "Date": "No date" until one is picked; the picker offers no day after today. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotebookDateRow() {
    var picking by remember { mutableStateOf(false) }
    val date = NotebookSession.dateMs
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.nb_date), Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
        androidx.compose.material3.InputChip(
            selected = date != null,
            onClick = { picking = true },
            label = { Text(if (date != null) dateText(date) else stringResource(R.string.nb_no_date)) },
            leadingIcon = { Icon(Icons.Filled.DateRange, contentDescription = null, modifier = Modifier.size(18.dp)) },
            trailingIcon = if (date == null) null else {
                {
                    IconButton(onClick = { NotebookSession.dateMs = null }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.nb_date_clear), modifier = Modifier.size(18.dp))
                    }
                }
            },
            modifier = Modifier.heightIn(min = 48.dp),
        )
    }
    if (picking) {
        val state = androidx.compose.material3.rememberDatePickerState(
            initialSelectedDateMillis = Dates.pickerDay(date ?: System.currentTimeMillis()),
            selectableDates = object : androidx.compose.material3.SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = Dates.notFuture(utcTimeMillis, System.currentTimeMillis())
            },
        )
        androidx.compose.material3.DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    state.selectedDateMillis?.let { NotebookSession.dateMs = Dates.noonFromPicker(it) }
                    picking = false
                }) { Text(stringResource(android.R.string.ok)) }
            },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { picking = false }) { Text(stringResource(android.R.string.cancel)) } },
        ) { androidx.compose.material3.DatePicker(state) }
    }
}

@Composable
private fun StampRow(
    w: StampWork, n: Int, targets: List<io.github.thisisartman.collectibles.data.ItemRow>, rows: List<io.github.thisisartman.collectibles.data.ItemRow>,
    vm: MainViewModel, ja: Boolean, onAddNew: () -> Unit,
) {
    val byId = remember(rows) { rows.associateBy { it.item.id } }
    androidx.compose.material3.Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image((if (w.showOriginal) w.original else w.cleaned).asImageBitmap(), contentDescription = null, modifier = Modifier.size(110.dp), contentScale = ContentScale.Fit)
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(stringResource(R.string.nb_stamp_n, n), style = MaterialTheme.typography.labelLarge)
                    androidx.compose.material3.TextButton(onClick = { w.showOriginal = !w.showOriginal }) {
                        Text(stringResource(if (w.showOriginal) R.string.nb_show_cleaned else R.string.nb_show_original))
                    }
                    androidx.compose.material3.TextButton(onClick = { w.skipped = !w.skipped }) {
                        Text(stringResource(if (w.skipped) R.string.nb_keep else R.string.nb_skip))
                    }
                }
            }
            if (w.skipped) return@Column
            val chosen = w.chosen?.let { byId[it] }
            val top = w.matches.firstOrNull()
            when {
                chosen != null -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(chosen.item.displayName(ja), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    androidx.compose.material3.TextButton(onClick = { w.chosen = null }) { Text(stringResource(R.string.nb_change)) }
                }
                top != null && top.score >= io.github.thisisartman.collectibles.data.StationMatch.SUGGEST && byId[top.id] != null ->
                    InkOutlinedButton(onClick = { w.chosen = top.id }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.nb_seems, byId[top.id]!!.item.displayName(ja)))
                    }
                else -> {}
            }
            if (chosen == null) {
                // Pick by typing, in English or Japanese: the forgiving search, nearest first
                androidx.compose.material3.OutlinedTextField(
                    value = w.query, onValueChange = { w.query = it }, singleLine = true,
                    placeholder = { Text(stringResource(R.string.nb_search)) }, modifier = Modifier.fillMaxWidth(),
                )
                val q = w.query.trim()
                if (q.isNotEmpty()) {
                    val hits = rankSearch(targets, q, { it.item.searchScore(q) }, { vm.distanceTo(it) }).take(5).map { it to (vm.distanceTo(it) ?: Double.MAX_VALUE) }
                    hits.forEach { (r, d) ->
                        androidx.compose.material3.TextButton(onClick = { w.chosen = r.item.id; w.query = "" }, modifier = Modifier.fillMaxWidth()) {
                            Text(listOfNotNull(r.item.displayName(ja), distanceText(d.takeIf { it < Double.MAX_VALUE })).joinToString(" · "), modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
                androidx.compose.material3.TextButton(onClick = onAddNew) { Text(stringResource(R.string.nb_not_in_meguri)) }
            }
        }
    }
}

/** The page shrunk for the screen, and its stamps found there (fractions, so they fit the full photo too). */
internal fun decodePage(context: android.content.Context, uri: Uri, longSide: Int): Bitmap? = runCatching {
    android.graphics.ImageDecoder.decodeBitmap(android.graphics.ImageDecoder.createSource(context.contentResolver, uri)) { d, info, _ ->
        d.allocator = android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
        val s = longSide.toFloat() / max(info.size.width, info.size.height)
        if (s < 1f) d.setTargetSize(max(1, (info.size.width * s).toInt()), max(1, (info.size.height * s).toInt()))
    }
}.getOrNull()

/**
 * The one review step (owner: "halfway"): the page with a box round each stamp found. Drag a box to move it, drag a
 * corner to resize it, tap one and then its cross to remove it, or add a box for a stamp that was missed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotebookReviewScreen(onBack: () -> Unit, onDone: () -> Unit) {
    val context = LocalContext.current
    val page = NotebookSession.page
    var bmp by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(page) {
        bmp = page?.let { u -> kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { decodePage(context, u, 1600) } }
    }
    val boxes = NotebookSession.boxes
    var selected by remember { mutableIntStateOf(-1) }
    val accent = MaterialTheme.colorScheme.primary
    val handle = with(LocalDensity.current) { 28.dp.toPx() }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            windowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
            title = { Text(stringResource(R.string.nb_review_title)) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
        )
        Text(stringResource(R.string.nb_review_hint), Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box(Modifier.weight(1f).fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
            val b = bmp
            if (b == null) CircularProgressIndicator()
            else {
                // The page at the largest size that fits; boxes are drawn and dragged over it in its own coordinates
                androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    val density = LocalDensity.current
                    val maxW = with(density) { maxWidth.toPx() }; val maxH = with(density) { maxHeight.toPx() }
                    val k = min(maxW / b.width, maxH / b.height)
                    val pw = b.width * k; val ph = b.height * k
                    Box(Modifier.size(with(density) { pw.toDp() }, with(density) { ph.toDp() })) {
                        Image(b.asImageBitmap(), contentDescription = null, contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize())
                        Canvas(
                            Modifier.fillMaxSize()
                                .pointerInput(boxes.size) {
                                    detectTapGestures { p ->
                                        // Tap the selected box's cross to remove it; tap a box to select it
                                        val s = selected
                                        if (s in boxes.indices) {
                                            val bx = boxes[s]
                                            if (abs(p.x - bx.right * pw) < handle && abs(p.y - bx.top * ph) < handle) {
                                                boxes.removeAt(s); selected = -1; return@detectTapGestures
                                            }
                                        }
                                        selected = boxes.indexOfLast { p.x in it.left * pw..it.right * pw && p.y in it.top * ph..it.bottom * ph }
                                    }
                                }
                                .pointerInput(boxes.size) {
                                    var target = -1; var mode = 0   // 0 move, 1-4 corners (tl, tr, br, bl)
                                    detectDragGestures(
                                        onDragStart = { p ->
                                            target = -1
                                            for (i in boxes.indices.reversed()) {
                                                val bx = boxes[i]
                                                val corners = listOf(Offset(bx.left * pw, bx.top * ph), Offset(bx.right * pw, bx.top * ph), Offset(bx.right * pw, bx.bottom * ph), Offset(bx.left * pw, bx.bottom * ph))
                                                val c = corners.indexOfFirst { (it - p).getDistance() < handle }
                                                if (c >= 0) { target = i; mode = c + 1; break }
                                                if (p.x in bx.left * pw..bx.right * pw && p.y in bx.top * ph..bx.bottom * ph) { target = i; mode = 0; break }
                                            }
                                            if (target >= 0) selected = target
                                        },
                                        onDrag = { change, d ->
                                            if (target !in boxes.indices) return@detectDragGestures
                                            change.consume()
                                            val bx = boxes[target]; val dx = d.x / pw; val dy = d.y / ph; val minS = 0.05f
                                            boxes[target] = when (mode) {
                                                0 -> {
                                                    val mx = dx.coerceIn(-bx.left, 1f - bx.right); val my = dy.coerceIn(-bx.top, 1f - bx.bottom)
                                                    StampFind.Box(bx.left + mx, bx.top + my, bx.right + mx, bx.bottom + my)
                                                }
                                                1 -> bx.copy(left = (bx.left + dx).coerceIn(0f, bx.right - minS), top = (bx.top + dy).coerceIn(0f, bx.bottom - minS))
                                                2 -> bx.copy(right = (bx.right + dx).coerceIn(bx.left + minS, 1f), top = (bx.top + dy).coerceIn(0f, bx.bottom - minS))
                                                3 -> bx.copy(right = (bx.right + dx).coerceIn(bx.left + minS, 1f), bottom = (bx.bottom + dy).coerceIn(bx.top + minS, 1f))
                                                else -> bx.copy(left = (bx.left + dx).coerceIn(0f, bx.right - minS), bottom = (bx.bottom + dy).coerceIn(bx.top + minS, 1f))
                                            }
                                        },
                                    )
                                },
                        ) {
                            boxes.forEachIndexed { i, bx ->
                                val tl = Offset(bx.left * pw, bx.top * ph); val sz = Size((bx.right - bx.left) * pw, (bx.bottom - bx.top) * ph)
                                val on = i == selected
                                // Accent colour with a dark edge: white boxes vanished on the white page (Poco, 2026-11-09)
                                drawRect(Color.Black.copy(alpha = 0.45f), tl, sz, style = Stroke(if (on) 11f else 9f))
                                drawRect(accent, tl, sz, style = Stroke(if (on) 7f else 4f))
                                // Corner handles, and a cross to remove the selected box
                                listOf(tl, Offset(tl.x + sz.width, tl.y), Offset(tl.x + sz.width, tl.y + sz.height), Offset(tl.x, tl.y + sz.height)).forEach {
                                    drawCircle(Color.Black.copy(alpha = 0.45f), radius = if (on) 15f else 11f, center = it)
                                    drawCircle(accent, radius = if (on) 12f else 8f, center = it)
                                }
                                if (on) {
                                    val c = Offset(tl.x + sz.width, tl.y)
                                    drawCircle(Color(0xFFB3261E), radius = 22f, center = c)
                                    drawLine(Color.White, c + Offset(-9f, -9f), c + Offset(9f, 9f), strokeWidth = 4f)
                                    drawLine(Color.White, c + Offset(9f, -9f), c + Offset(-9f, 9f), strokeWidth = 4f)
                                }
                            }
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            InkOutlinedButton(
                onClick = { boxes.add(StampFind.Box(0.3f, 0.3f, 0.7f, 0.6f)); selected = boxes.lastIndex },
                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
            ) { Text(stringResource(R.string.nb_add_box), maxLines = 1) }
            Button(onClick = onDone, enabled = boxes.isNotEmpty(), modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                Text(androidx.compose.ui.res.pluralStringResource(R.plurals.nb_done, boxes.size, boxes.size), maxLines = 1)
            }
        }
    }
}
