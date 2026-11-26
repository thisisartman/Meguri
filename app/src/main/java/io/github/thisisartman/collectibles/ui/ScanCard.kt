package io.github.thisisartman.collectibles.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import io.github.thisisartman.collectibles.R
import io.github.thisisartman.collectibles.data.CardCode
import io.github.thisisartman.collectibles.data.ItemRow
import io.github.thisisartman.collectibles.data.ItemType
import kotlinx.coroutines.launch
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private tailrec fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { c ->
    addOnSuccessListener { c.resume(it) }
    addOnFailureListener { c.resumeWithException(it) }
}

/** What a scan found: the photo, the code read from it (if any) and the card it matches (if any). */
/** [bmp] turned [degrees] clockwise, saved as a JPEG in the cache; the app copy is made from it when the photo is attached. */
private suspend fun uprightCopy(context: Context, bmp: android.graphics.Bitmap, degrees: Int): Uri? =
    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
        runCatching {
            val m = android.graphics.Matrix().apply { postRotate(degrees.toFloat()) }
            val turned = android.graphics.Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
            val dir = java.io.File(context.cacheDir, "scans").apply { mkdirs() }
            val f = java.io.File(dir, "scan-${System.currentTimeMillis()}.jpg")
            f.outputStream().use { check(turned.compress(android.graphics.Bitmap.CompressFormat.JPEG, 95, it)) }
            turned.recycle()
            Uri.fromFile(f)
        }.getOrNull()
    }

/** The card turned upright and trimmed to the top card, saved in the cache; null when no clean frame (with why). */
private suspend fun cardCopy(context: Context, bmp: android.graphics.Bitmap, degrees: Int): Pair<Uri?, io.github.thisisartman.collectibles.photo.CardTrim.Result> =
    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
        runCatching {
            val upright = if (degrees == 0) bmp else {
                val m = android.graphics.Matrix().apply { postRotate(degrees.toFloat()) }
                android.graphics.Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
            }
            val (card, why) = io.github.thisisartman.collectibles.photo.CardTrim.trim(upright)
            if (upright !== bmp) upright.recycle()
            if (card == null) return@runCatching Pair<Uri?, io.github.thisisartman.collectibles.photo.CardTrim.Result>(null, why)
            val dir = java.io.File(context.cacheDir, "scans").apply { mkdirs() }
            val f = java.io.File(dir, "card-${System.currentTimeMillis()}.jpg")
            f.outputStream().use { check(card.compress(android.graphics.Bitmap.CompressFormat.JPEG, 95, it)) }
            card.recycle()
            Pair<Uri?, io.github.thisisartman.collectibles.photo.CardTrim.Result>(Uri.fromFile(f), why)
        }.getOrElse { Pair(null, io.github.thisisartman.collectibles.photo.CardTrim.Result.NO_FRAME) }
    }

private data class Scan(
    val photo: Uri, val code: String?, val row: ItemRow?, val cover: Boolean = false, val nearCards: List<ItemRow> = emptyList(),
    /** Cards whose code differs only by a look-alike letter (I or L), offered as "Or is it...?" */
    val lookalikes: List<ItemRow> = emptyList(),
    /** The cover's position printed on the card, when it was read (#203); kept on the card you confirm */
    val coverAt: io.github.thisisartman.collectibles.data.CoverCoords.LatLng? = null,
)

/**
 * Scan a card (M7 auto-crop, M8 recognition; owner 2026-11-08: yes to both). Google's on-device document scanner
 * finds the card's edges and straightens it (you can move the corners), the Japanese text reader reads the printed
 * code (15-202-A001), and the card it matches is offered to collect with that photo. Both run on the phone through
 * Play services; no picture leaves it.
 */
@Composable
fun ScanCardHost(vm: MainViewModel, nav: NavHostController, trigger: Int) {
    val context = LocalContext.current
    val rows by vm.rows.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var reading by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<Scan?>(null) }
    var fullscreen by remember { mutableStateOf<String?>(null) }
    // No code read (owner, 2026-11-09): retake the photo, or type the code (a short tutorial the first time)
    var retake by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    var typing by remember { mutableStateOf<Scan?>(null) }
    var tutorial by remember { mutableStateOf(false) }
    val tips = remember { context.getSharedPreferences("tips", Context.MODE_PRIVATE) }
    var failed by remember { mutableStateOf(false) }
    var notReady by remember { mutableStateOf(false) }
    // Ask Play services for the Japanese reader ahead of the first scan (it downloads once, in the background). Off the
    // main thread and after the first screen has settled: creating the reader loads ML Kit's classes, which held the
    // main thread long enough at start for an "isn't responding" dialog on a slow device (#166)
    androidx.compose.runtime.LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(5_000)
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            runCatching {
                val recognizer = TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())
                com.google.android.gms.common.moduleinstall.ModuleInstall.getClient(context).installModules(
                    com.google.android.gms.common.moduleinstall.ModuleInstallRequest.newBuilder().addApi(recognizer).build(),
                )
            }
        }
    }
    if (notReady) AlertDialog(
        onDismissRequest = { notReady = false },
        text = { Text(stringResource(R.string.scan_not_ready)) },
        confirmButton = { TextButton(onClick = { notReady = false }) { Text(stringResource(android.R.string.ok)) } },
    )
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
        val page = GmsDocumentScanningResult.fromActivityResultIntent(r.data)?.pages?.firstOrNull() ?: return@rememberLauncherForActivityResult
        reading = true
        scope.launch {
            val cards = rows.filter { it.item.type == ItemType.MANHOLE_CARD && it.item.code != null }
            val known = cards.mapNotNull { it.item.code }.toSet()
            val reader = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())
            }
            // The scanner straightens the card but does not know which way is up: it came back upside down on the S23
            // (owner, 2026-11-09). Read it as it comes, then turned 180, 90 and 270 degrees; the turn that reads the
            // card code is the upright one, and the photo is saved that way up
            // Decoded off the main thread: a full-page scan is several megapixels (#167)
            val page0 = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                runCatching {
                    android.graphics.ImageDecoder.decodeBitmap(android.graphics.ImageDecoder.createSource(context.contentResolver, page.imageUri)) { d, _, _ ->
                        d.allocator = android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
                    }
                }.getOrNull()
            }
            var code: String? = null
            var turn = 0
            var failure: Throwable? = null
            var rawText = ""
            var codeText = ""
            for (deg in listOf(0, 180, 90, 270)) {
                val read = runCatching {
                    val input = page0?.let { InputImage.fromBitmap(it, deg) } ?: InputImage.fromFilePath(context, page.imageUri)
                    reader.process(input).await()
                }
                failure = read.exceptionOrNull()
                if (failure != null) break
                val found = read.getOrNull() ?: break
                if (deg == 0) rawText = found.text
                code = CardCode.best(found.text, known)
                if (code != null) {
                    codeText = found.text
                    turn = deg
                    // The reader reads upside-down cards too (owner's photos, 2026-11-09), so reading isn't enough to know
                    // which way is up. A manhole card's code is printed top right: turn until it sits there
                    val box = found.textBlocks.flatMap { it.lines }.firstOrNull { CardCode.best(it.text, known) == code }?.boundingBox
                    if (box != null && page0 != null) {
                        val fw = if (deg % 180 == 0) page0.width else page0.height
                        val fh = if (deg % 180 == 0) page0.height else page0.width
                        turn = (deg + CardCode.turnToTopRight(box.exactCenterX(), box.exactCenterY(), fw.toFloat(), fh.toFloat())) % 360
                    }
                    break
                }
                if (page0 == null) break
            }
            // Nothing read on the whole page: read the card alone, trimmed by its blue frame. The reader missed 3 of the
            // owner's 27 cards on the raw page (the box and the cards under it around the card)
            var page1 = page0
            if (code == null && failure == null && page0 != null) {
                val card = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                    runCatching { io.github.thisisartman.collectibles.photo.CardTrim.trim(page0).first }.getOrNull()
                }
                if (card != null) for (deg in listOf(0, 180)) {
                    val found = runCatching { reader.process(InputImage.fromBitmap(card, deg)).await() }.getOrNull() ?: break
                    val c = CardCode.best(found.text, known) ?: continue
                    code = c
                    codeText = found.text
                    val box = found.textBlocks.flatMap { it.lines }.firstOrNull { CardCode.best(it.text, known) == c }?.boundingBox
                    turn = if (box == null) deg else (deg + CardCode.turnToTopRight(box.exactCenterX(), box.exactCenterY(), card.width.toFloat(), card.height.toFloat())) % 360
                    page1 = card
                    break
                }
            }
            // The reader comes through Play services and downloads on first use: say so instead of "no code" (Poco, 2026-11-08)
            if ((failure as? com.google.mlkit.common.MlKitException)?.errorCode == com.google.mlkit.common.MlKitException.UNAVAILABLE) {
                vm.log.w("Scan", "The text reader is not ready yet (Play services download)"); reading = false; notReady = true; return@launch
            }
            // A card that was read: upright, then trimmed to the top card by its blue frame (owner, 2026-11-09: the
            // scanner took in the cards under it or the box's edge). No clean frame: the upright page as it was
            val src = page1
            // The copies are cut, turned and written to disk off the main thread (#167)
            val trimmed = if (code != null && src != null) kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { cardCopy(context, src, turn) } else null
            vm.log.d("Scan", "Card code ${code ?: "not read"}, turn $turn°, card-only read ${src !== page0}, trim ${trimmed?.second ?: "not tried"}" + (failure?.let { ", reader failed: $it" } ?: ""))
            val photo = trimmed?.first ?: if (src != null && (turn != 0 || src !== page0)) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { uprightCopy(context, src, turn) } ?: page.imageUri
            } else page.imageUri
            // Debug builds keep what the scanner returned and what was read, for tuning the crop on a laptop
            if (io.github.thisisartman.collectibles.BuildConfig.DEBUG) runCatching {
                val dir = java.io.File(context.getExternalFilesDir(null), "scan-debug").apply { mkdirs() }
                val stamp = System.currentTimeMillis()
                context.contentResolver.openInputStream(page.imageUri)?.use { i -> java.io.File(dir, "$stamp-page.jpg").outputStream().use { i.copyTo(it) } }
                java.io.File(dir, "$stamp.txt").writeText("code=${code ?: ""}\nturn=$turn\ntrim=${trimmed?.second ?: "not tried"}\n--- read as it came:\n$rawText\n")
                trimmed?.first?.let { u -> context.contentResolver.openInputStream(u)?.use { i -> java.io.File(dir, "$stamp-trim.jpg").outputStream().use { i.copyTo(it) } } }
            }
            val match = cards.firstOrNull { it.item.code == code }
            // No card code: maybe it is a manhole cover (owner, 2026-11-08). Then it is filed as a cover, cut to a circle,
            // on the card you pick from the nearest ones
            // A card whose code wasn't read is still a card (blue frame), not a cover: then it goes to Pick by hand
            val isCard = code == null && page0 != null && kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                runCatching { io.github.thisisartman.collectibles.photo.CardTrim.looksLikeCard(page0) }.getOrDefault(false)
            }
            // A notebook page (mostly plain paper), before the cover check: station stamps are round too (owner, 2026-11-09)
            if (code == null && !isCard) {
                val small = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { decodePage(context, page.imageUri, 480) }
                val px = small?.let { b -> IntArray(b.width * b.height).also { b.getPixels(it, 0, b.width, 0, 0, b.width, b.height) } }
                if (small != null && px != null && io.github.thisisartman.collectibles.photo.StampFind.isPage(px)) {
                    val boxes = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { io.github.thisisartman.collectibles.photo.StampFind.find(px, small.width, small.height) }
                    small.recycle(); if (page1 !== page0) page1?.recycle(); page0?.recycle()
                    vm.log.d("Scan", "Notebook page: ${boxes.size} stamps found")
                    NotebookSession.start(page.imageUri, boxes)
                    reading = false
                    nav.navigate("stamps")
                    return@launch
                }
                small?.recycle()
            }
            // The cover check decodes the page again: off the main thread too (#167)
            val cover = match == null && code == null && !isCard && kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                runCatching {
                    val bmp = android.graphics.ImageDecoder.decodeBitmap(android.graphics.ImageDecoder.createSource(context.contentResolver, page.imageUri)) { d, info, _ ->
                        d.allocator = android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
                        val s = 1200f / maxOf(info.size.width, info.size.height); if (s < 1f) d.setTargetSize((info.size.width * s).toInt(), (info.size.height * s).toInt())
                    }
                    io.github.thisisartman.collectibles.photo.CoverCrop.find(bmp) != null
                }.getOrDefault(false)
            }
            val near = if (cover) cards.mapNotNull { r -> vm.distanceTo(r)?.let { r to it } }.sortedBy { it.second }.take(3).map { it.first } else emptyList()
            if (page1 !== page0) page1?.recycle()
            page0?.recycle()
            // Look-alike letters for a card that was found; for a code that isn't in the catalogue, the closest codes, as
            // a digit is sometimes misread (S23 run, 2026-11-10: 15-202-C001 read as 15-207-C001)
            val alike = code?.let { c ->
                (if (match != null) CardCode.lookalikes(c, known) else CardCode.nearest(c, known))
                    .mapNotNull { k -> cards.firstOrNull { it.item.code == k } }
            } ?: emptyList()
            // The cover's position, printed on the card under its code (#203): from the read that found the code, else
            // the first read (a code typed by hand afterwards still gets it)
            val coverAt = io.github.thisisartman.collectibles.data.CoverCoords.parse(codeText) ?: io.github.thisisartman.collectibles.data.CoverCoords.parse(rawText)
            vm.log.d("Scan", "Result: ${match?.item?.id ?: "no match"}, cover $cover, card without a code $isCard, ${alike.size} look-alikes, cover position ${if (coverAt != null) "read" else "not read"}")
            result = Scan(photo, code, match, cover, near, alike, coverAt)
            reading = false
        }
    }
    // Lives in the app frame, not the + sheet: the sheet closes while the scanner is open, and the result must land
    androidx.compose.runtime.LaunchedEffect(trigger, retake) {
        if (trigger == 0 && retake == 0) return@LaunchedEffect
        val activity = context.activity() ?: return@LaunchedEffect
        val options = GmsDocumentScannerOptions.Builder()
            .setPageLimit(1).setGalleryImportAllowed(true)
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .build()
        GmsDocumentScanning.getClient(options).getStartScanIntent(activity)
            .addOnSuccessListener { launcher.launch(IntentSenderRequest.Builder(it).build()) }
            // With Play services on the phone, a scanner that won't start is still downloading its module: say so,
            // rather than that the phone lacks Play services (#139)
            .addOnFailureListener {
                val play = com.google.android.gms.common.GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) ==
                    com.google.android.gms.common.ConnectionResult.SUCCESS
                if (play) notReady = true else failed = true
            }
    }

    if (reading) AlertDialog(
        onDismissRequest = {}, confirmButton = {},
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp); Spacer(Modifier.width(16.dp))
                Text(stringResource(R.string.scan_reading))
            }
        },
    )
    if (failed) AlertDialog(
        onDismissRequest = { failed = false },
        text = { Text(stringResource(R.string.scan_unavailable)) },
        confirmButton = { TextButton(onClick = { failed = false }) { Text(stringResource(android.R.string.ok)) } },
    )
    result?.let { s ->
        val ja = isJapanese()
        val row = s.row
        AlertDialog(
            onDismissRequest = { result = null },
            title = {
                if (row != null) {
                    // The card's design as the title, the place and code under it (owner, 2026-11-09)
                    val (t, sub) = row.item.cardTitle(ja)
                    Column {
                        Text(t)
                        sub?.let { Text(it, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                } else Text(stringResource(if (s.cover) R.string.scan_cover_title else R.string.scan_no_match))
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Full screen, like on an item page (owner, 2026-11-09: the button did nothing here)
                    LargePhoto(s.photo.toString(), onFullscreen = { fullscreen = s.photo.toString() })
                    Text(
                        when {
                            row != null -> stringResource(R.string.scan_found, s.code ?: "")
                            s.code != null -> stringResource(R.string.scan_unknown_code, s.code)
                            s.cover -> stringResource(R.string.scan_cover)
                            else -> stringResource(R.string.scan_no_code)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    // The reader can take an L for an I: offer the look-alike card, one tap to switch to it
                    s.lookalikes.forEach { alt ->
                        TextButton(
                            onClick = { result = s.copy(row = alt, code = alt.item.code, lookalikes = listOfNotNull(s.row)) },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        ) { Text(stringResource(R.string.scan_or_maybe, alt.item.shortName(ja)), modifier = Modifier.fillMaxWidth()) }
                    }
                    s.nearCards.forEach { c ->
                        TextButton(onClick = {
                            result = null
                            vm.attachPhoto(c.item.id, s.photo, io.github.thisisartman.collectibles.data.PhotoKind.MANHOLE) { nav.openItem(c.item.id) }
                        }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(c.item.shortName(ja), modifier = Modifier.fillMaxWidth()) }
                    }
                }
            },
            confirmButton = {
                if (row != null) TextButton(onClick = {
                    result = null
                    s.coverAt?.let { vm.setScannedCover(row.item.id, it) }
                    // A scan is the card itself. Already collected: the photo joins its latest find. Not yet: a find now,
                    // where you are, with "Collected ... Undo" like every other way of collecting (finding 29)
                    if (row.collected) vm.attachPhoto(row.item.id, s.photo, io.github.thisisartman.collectibles.data.PhotoKind.STAMP) { nav.openItem(row.item.id) }
                    else vm.collectWithPhoto(context, row, row.item.displayName(ja), s.photo, io.github.thisisartman.collectibles.data.PhotoKind.STAMP) { nav.openItem(row.item.id) }
                }) { Text(stringResource(if (row.collected) R.string.scan_add_photo else R.string.scan_collect)) }
                // No code read, or one that isn't in the catalogue (often a misread digit): the person can usually read
                // it, so type it (owner, 2026-11-09; for a misread code, owner 2026-11-10: "there needs to be an option
                // to add code")
                else if (!s.cover) TextButton(onClick = {
                    result = null; typing = s
                    if (!tips.getBoolean("code_tutorial_seen", false)) tutorial = true
                }) { Text(stringResource(R.string.scan_type_code)) }
                // Pick by hand: the photo is filed as what it shows on the card picked, the card or its cover (finding 30)
                else TextButton(onClick = {
                    result = null
                    vm.openShare(listOf(s.photo), if (s.cover) io.github.thisisartman.collectibles.data.PhotoKind.MANHOLE else io.github.thisisartman.collectibles.data.PhotoKind.STAMP)
                    nav.navigate("share")
                }) {
                    Text(stringResource(R.string.scan_pick))
                }
            },
            dismissButton = {
                if (row != null) TextButton(onClick = {
                    result = null
                    s.coverAt?.let { vm.setScannedCover(row.item.id, it) }
                    nav.openItem(row.item.id)
                }) { Text(stringResource(R.string.scan_open)) }
                else if (s.code == null && !s.cover) TextButton(onClick = { result = null; retake++ }) { Text(stringResource(R.string.scan_retake)) }
                // A code that isn't in the catalogue: Pick by hand stays beside Type code
                else if (s.code != null) TextButton(onClick = {
                    result = null
                    vm.openShare(listOf(s.photo), io.github.thisisartman.collectibles.data.PhotoKind.STAMP)
                    nav.navigate("share")
                }) { Text(stringResource(R.string.scan_pick)) }
                else TextButton(onClick = { result = null }) { Text(stringResource(android.R.string.cancel)) }
            },
        )
    }
    // Typing the code by hand, after the tutorial the first time
    if (tutorial) CodeTutorial(onDone = { tips.edit().putBoolean("code_tutorial_seen", true).apply(); tutorial = false })
    else typing?.let { s ->
        val cards = rows.filter { it.item.type == ItemType.MANHOLE_CARD && it.item.code != null }
        TypeCodeDialog(
            cards = remember(cards) { cards.map { it to vm.distanceTo(it) } },
            onDismiss = { typing = null },
            onCode = { code ->
                val match = cards.firstOrNull { it.item.code == code }
                if (match != null) {
                    typing = null
                    val known = cards.mapNotNull { it.item.code }.toSet()
                    result = s.copy(code = code, row = match, lookalikes = CardCode.lookalikes(code, known).mapNotNull { k -> cards.firstOrNull { it.item.code == k } })
                }
                match != null
            },
        )
    }
    // After the result dialog, so it opens on top of it
    fullscreen?.let { u -> FullscreenPhoto(u, onClose = { fullscreen = null }) }
}

/**
 * Type a card's code by hand. [onCode] gets the tidied code and says whether a card has it. As you type, any part
 * of a code ("A001", "202-A") lists the cards that have it, best match first, then nearest (owner, 2026-11-09).
 */
@Composable
private fun TypeCodeDialog(cards: List<Pair<ItemRow, Double?>>, onDismiss: () -> Unit, onCode: (String) -> Boolean) {
    var text by remember { mutableStateOf("") }
    var notFound by remember { mutableStateOf(false) }
    val code = CardCode.fromTyped(text)
    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
    androidx.compose.runtime.LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.scan_type_code_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                androidx.compose.material3.OutlinedTextField(
                    value = text, onValueChange = { text = it.take(16); notFound = false }, singleLine = true,
                    placeholder = { Text("15-202-A001") },
                    supportingText = { Text(stringResource(if (notFound) R.string.scan_type_code_none else R.string.scan_type_code_hint)) },
                    isError = notFound,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Characters,
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Ascii,
                        imeAction = androidx.compose.ui.text.input.ImeAction.Done,
                    ),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { code?.let { notFound = !onCode(it) } }),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
                val q = text.trim()
                if (q.isNotEmpty()) {
                    val ja = isJapanese()
                    rankSearch(cards, q, { fuzzyScore(q, listOf(it.first.item.code)) }, { it.second }).take(6).forEach { (r, d) ->
                        TextButton(onClick = { r.item.code?.let { onCode(it) } }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                            Text(
                                listOfNotNull(r.item.code, r.item.cardTitle(ja).first, distanceText(d)).joinToString(" · "),
                                modifier = Modifier.fillMaxWidth(), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(enabled = code != null, onClick = { code?.let { notFound = !onCode(it) } }) { Text(stringResource(R.string.scan_type_code_find)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } },
    )
}

/**
 * The first time someone types a code: where it is on a card (owner, 2026-11-09). A small card is drawn, then the
 * view zooms into its top right corner, where every manhole card prints its code, and the code pulses.
 */
@Composable
private fun CodeTutorial(onDone: () -> Unit) {
    val anim = androidx.compose.animation.core.rememberInfiniteTransition(label = "tutorial")
    // 0 to 1: the whole card, then zoomed into the corner; holds there, then starts again
    val t by anim.animateFloat(
        0f, 1f,
        androidx.compose.animation.core.infiniteRepeatable(
            androidx.compose.animation.core.keyframes {
                durationMillis = 3600
                0f at 0; 0f at 700; 1f at 1700 using androidx.compose.animation.core.FastOutSlowInEasing; 1f at 3600
            },
        ),
        label = "zoom",
    )
    val pulse by anim.animateFloat(
        0.35f, 1f, androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(600), androidx.compose.animation.core.RepeatMode.Reverse), label = "pulse",
    )
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    val blue = androidx.compose.ui.graphics.Color(0xFF1E4FA8)
    val accent = MaterialTheme.colorScheme.primary
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(stringResource(R.string.scan_tutorial_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                androidx.compose.foundation.Canvas(Modifier.size(width = 220.dp, height = 220.dp).androidx_clip()) {
                    val cw = size.height * 0.62f * 63f / 88f * 1.4f
                    val ch = size.height * 0.62f * 1.4f
                    // Zoom towards the code: the corner ends up in the middle, 2.6 times as large
                    val zoom = 1f + 1.6f * t
                    val cx = size.width / 2; val cy = size.height / 2
                    // Inside the blue top band, clear of the card's right edge (it ran over it on the Poco)
                    val codeX = cx + cw * 0.22f; val codeY = cy - ch * 0.42f
                    val pivotX = cx + (codeX - cx) * t; val pivotY = cy + (codeY - cy) * t
                    withTransform({
                        translate(cx - pivotX, cy - pivotY)
                        scale(zoom, zoom, androidx.compose.ui.geometry.Offset(pivotX, pivotY))
                    }) {
                        val left = cx - cw / 2; val top = cy - ch / 2
                        drawRoundRect(androidx.compose.ui.graphics.Color.White, androidx.compose.ui.geometry.Offset(left, top), androidx.compose.ui.geometry.Size(cw, ch), androidx.compose.ui.geometry.CornerRadius(cw * 0.05f))
                        drawRoundRect(androidx.compose.ui.graphics.Color.LightGray, androidx.compose.ui.geometry.Offset(left, top), androidx.compose.ui.geometry.Size(cw, ch), androidx.compose.ui.geometry.CornerRadius(cw * 0.05f), style = androidx.compose.ui.graphics.drawscope.Stroke(2f))
                        val fl = left + cw * 0.06f; val ft = top + ch * 0.05f; val fw = cw * 0.88f; val fh = ch * 0.90f
                        drawRect(blue, androidx.compose.ui.geometry.Offset(fl, ft), androidx.compose.ui.geometry.Size(fw, fh))
                        drawRect(androidx.compose.ui.graphics.Color(0xFF8A8A8A), androidx.compose.ui.geometry.Offset(fl + fw * 0.04f, ft + fh * 0.17f), androidx.compose.ui.geometry.Size(fw * 0.92f, fh * 0.62f))
                        drawCircle(androidx.compose.ui.graphics.Color(0xFF333333), radius = fw * 0.36f, center = androidx.compose.ui.geometry.Offset(fl + fw / 2, ft + fh * 0.48f))
                        // The code, top right, in a pale box that pulses
                        val box = androidx.compose.ui.geometry.Size(fw * 0.38f, fh * 0.055f)
                        val boxAt = androidx.compose.ui.geometry.Offset(codeX - box.width / 2, codeY - box.height / 2)
                        drawRoundRect(accent.copy(alpha = pulse), boxAt - androidx.compose.ui.geometry.Offset(3f, 3f), androidx.compose.ui.geometry.Size(box.width + 6f, box.height + 6f), androidx.compose.ui.geometry.CornerRadius(4f), style = androidx.compose.ui.graphics.drawscope.Stroke(3f / zoom))
                        val label = measurer.measure("15-202-A001", androidx.compose.ui.text.TextStyle(color = androidx.compose.ui.graphics.Color.White, fontSize = (box.height * 0.62f / density).sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold))
                        drawText(label, topLeft = androidx.compose.ui.geometry.Offset(codeX - label.size.width / 2f, codeY - label.size.height / 2f))
                    }
                }
                Text(stringResource(R.string.scan_tutorial_text), style = MaterialTheme.typography.bodyMedium)
            }
        },
        confirmButton = { TextButton(onClick = onDone) { Text(stringResource(R.string.scan_tutorial_ok)) } },
    )
}

/** Keeps the zoomed drawing inside its square. */
private fun Modifier.androidx_clip(): Modifier = this.then(Modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp)))
