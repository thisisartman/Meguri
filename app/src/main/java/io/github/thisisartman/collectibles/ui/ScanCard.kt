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
private data class Scan(val photo: Uri, val code: String?, val row: ItemRow?, val cover: Boolean = false, val nearCards: List<ItemRow> = emptyList())

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
    var failed by remember { mutableStateOf(false) }
    var notReady by remember { mutableStateOf(false) }
    // Ask Play services for the Japanese reader ahead of the first scan (it downloads once, in the background)
    androidx.compose.runtime.LaunchedEffect(Unit) {
        runCatching {
            val recognizer = TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())
            com.google.android.gms.common.moduleinstall.ModuleInstall.getClient(context).installModules(
                com.google.android.gms.common.moduleinstall.ModuleInstallRequest.newBuilder().addApi(recognizer).build(),
            )
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
            val read = runCatching {
                TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())
                    .process(InputImage.fromFilePath(context, page.imageUri)).await().text
            }
            // The reader comes through Play services and downloads on first use: say so instead of "no code" (Poco, 2026-11-08)
            if ((read.exceptionOrNull() as? com.google.mlkit.common.MlKitException)?.errorCode == com.google.mlkit.common.MlKitException.UNAVAILABLE) {
                reading = false; notReady = true; return@launch
            }
            val text = read.getOrDefault("")
            val cards = rows.filter { it.item.type == ItemType.MANHOLE_CARD && it.item.code != null }
            val code = CardCode.best(text, cards.mapNotNull { it.item.code }.toSet())
            val match = cards.firstOrNull { it.item.code == code }
            // No card code: maybe it is a manhole cover (owner, 2026-11-08). Then it is filed as a cover, cut to a circle,
            // on the card you pick from the nearest ones
            val cover = match == null && code == null && runCatching {
                val bmp = android.graphics.ImageDecoder.decodeBitmap(android.graphics.ImageDecoder.createSource(context.contentResolver, page.imageUri)) { d, info, _ ->
                    d.allocator = android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
                    val s = 1200f / maxOf(info.size.width, info.size.height); if (s < 1f) d.setTargetSize((info.size.width * s).toInt(), (info.size.height * s).toInt())
                }
                io.github.thisisartman.collectibles.photo.CoverCrop.find(bmp) != null
            }.getOrDefault(false)
            val near = if (cover) cards.mapNotNull { r -> vm.distanceTo(r)?.let { r to it } }.sortedBy { it.second }.take(3).map { it.first } else emptyList()
            result = Scan(page.imageUri, code, match, cover, near)
            reading = false
        }
    }
    // Lives in the app frame, not the + sheet: the sheet closes while the scanner is open, and the result must land
    androidx.compose.runtime.LaunchedEffect(trigger) {
        if (trigger == 0) return@LaunchedEffect
        val activity = context.activity() ?: return@LaunchedEffect
        val options = GmsDocumentScannerOptions.Builder()
            .setPageLimit(1).setGalleryImportAllowed(true)
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .build()
        GmsDocumentScanning.getClient(options).getStartScanIntent(activity)
            .addOnSuccessListener { launcher.launch(IntentSenderRequest.Builder(it).build()) }
            .addOnFailureListener { failed = true }
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
            title = { Text(if (row != null) row.item.displayName(ja) else stringResource(if (s.cover) R.string.scan_cover_title else R.string.scan_no_match)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LargePhoto(s.photo.toString(), onFullscreen = {})
                    Text(
                        when {
                            row != null -> stringResource(R.string.scan_found, s.code ?: "")
                            s.code != null -> stringResource(R.string.scan_unknown_code, s.code)
                            s.cover -> stringResource(R.string.scan_cover)
                            else -> stringResource(R.string.scan_no_code)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
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
                    // A scan is the card itself
                    vm.attachPhoto(row.item.id, s.photo, io.github.thisisartman.collectibles.data.PhotoKind.STAMP) { nav.openItem(row.item.id) }
                }) { Text(stringResource(if (row.collected) R.string.scan_add_photo else R.string.scan_collect)) }
                else TextButton(onClick = { result = null; vm.openShare(listOf(s.photo)); nav.navigate("share") }) {
                    Text(stringResource(R.string.scan_pick))
                }
            },
            dismissButton = {
                if (row != null) TextButton(onClick = { result = null; nav.openItem(row.item.id) }) { Text(stringResource(R.string.scan_open)) }
                else TextButton(onClick = { result = null }) { Text(stringResource(android.R.string.cancel)) }
            },
        )
    }
}
