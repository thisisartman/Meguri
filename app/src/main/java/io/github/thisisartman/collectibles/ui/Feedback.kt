package io.github.thisisartman.collectibles.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import io.github.thisisartman.collectibles.BuildConfig
import io.github.thisisartman.collectibles.R
import java.io.File

/**
 * Send feedback (owner, 2026-11-15): a note and screenshots, sent by the phone's own mail app to the owner's address
 * with a fixed subject tag, so feedback can be pulled from the mailbox later (tools/feedback/pull_feedback.py) instead
 * of being typed into a chat. Nothing goes through a server of ours.
 */
object Feedback {
    /** Where feedback goes: the owner's address, the one their GitHub uses (owner, 2026-11-15). */
    const val TO = "omniversal07@gmail.com"
    /** Every feedback mail's subject starts with this, so the pull script finds them. */
    const val TAG = "[Meguri feedback]"

    fun subject(text: String): String =
        TAG + " " + text.lineSequence().firstOrNull { it.isNotBlank() }?.trim()?.take(60).orEmpty().ifEmpty { "(no text)" }

    fun body(text: String): String = buildString {
        append(text.trim())
        append("\n\n--\nMeguri ").append(BuildConfig.VERSION_NAME).append(" (").append(BuildConfig.VERSION_CODE).append(")")
        append(", Android ").append(Build.VERSION.RELEASE).append(", ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL)
    }

    /** Copies the chosen pictures (and the log, if asked) where the mail app may read them, and opens the mail app. */
    fun send(context: Context, text: String, pictures: List<Uri>, withLog: Boolean, logFiles: List<File>): Boolean {
        val dir = File(context.cacheDir, "shared/feedback").apply { deleteRecursively(); mkdirs() }
        val files = mutableListOf<File>()
        pictures.forEachIndexed { i, uri ->
            runCatching {
                val out = File(dir, "screenshot-${i + 1}.jpg")
                context.contentResolver.openInputStream(uri)?.use { input -> out.outputStream().use { input.copyTo(it) } }
                if (out.length() > 0) files += out
            }
        }
        if (withLog) logFiles.filter { it.exists() && it.length() > 0 }.forEach { f ->
            runCatching { File(dir, f.name).also { f.copyTo(it, overwrite = true); files += it } }
        }
        val uris = ArrayList(files.map { FileProvider.getUriForFile(context, context.packageName + ".files", it) })
        fun mail(pkg: String) = Intent(if (uris.isEmpty()) Intent.ACTION_SEND else Intent.ACTION_SEND_MULTIPLE).apply {
            setPackage(pkg)
            type = if (uris.isEmpty()) "message/rfc822" else "*/*"
            putExtra(Intent.EXTRA_EMAIL, arrayOf(TO))
            putExtra(Intent.EXTRA_SUBJECT, subject(text))
            putExtra(Intent.EXTRA_TEXT, body(text))
            if (uris.isNotEmpty()) putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        // Mail apps only (those that open "mailto:"): any-file sharing offered Drive, Maps, even Meguri itself, and a
        // mailto selector on a multi-file send is refused (emulator, 2026-11-15). One mail app opens straight away.
        val apps = context.packageManager.queryIntentActivities(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")), 0)
            .map { it.activityInfo.packageName }.distinct()
        if (apps.isEmpty()) return false
        val intents = apps.map(::mail)
        val start = if (intents.size == 1) intents[0] else Intent.createChooser(intents[0], context.getString(R.string.feedback_title))
            .putExtra(Intent.EXTRA_INITIAL_INTENTS, intents.drop(1).toTypedArray())
        return runCatching { context.startActivity(start); true }.getOrDefault(false)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedbackSheet(logFiles: () -> List<File>, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var text by remember { mutableStateOf("") }
    var withLog by remember { mutableStateOf(true) }
    val pictures = remember { mutableStateListOf<Uri>() }
    var failed by remember { mutableStateOf(false) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(6)) { picked ->
        picked.forEach { if (it !in pictures) pictures += it }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        // Send stays on screen (owner's rule, 2026-11-20: important actions never scroll away): the form scrolls,
        // the button sits under it, above the keyboard and the navigation bar
        Column(Modifier.fillMaxWidth().navigationBarsPadding().imePadding()) {
        Column(Modifier.weight(1f, fill = false).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            Text(stringResource(R.string.feedback_title), style = MaterialTheme.typography.headlineSmall)
            Text(
                stringResource(R.string.feedback_intro), Modifier.padding(top = 4.dp, bottom = 10.dp),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                text, { text = it }, Modifier.fillMaxWidth().heightIn(min = 140.dp),
                label = { Text(stringResource(R.string.feedback_hint)) },
            )
            if (pictures.isNotEmpty()) LazyRow(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(pictures.toList()) { uri ->
                    // Tap a picture to take it out again
                    Box(Modifier.clickable { pictures.remove(uri) }) { PhotoThumb(uri.toString(), 72.dp) }
                }
            }
            OutlinedButton(
                onClick = { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp).heightIn(min = 48.dp),
            ) { Text(stringResource(if (pictures.isEmpty()) R.string.feedback_add_screenshots else R.string.feedback_more_screenshots, pictures.size)) }
            Row(Modifier.fillMaxWidth().clickable { withLog = !withLog }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(withLog, { withLog = it })
                Text(stringResource(R.string.feedback_with_log), style = MaterialTheme.typography.bodyMedium)
            }
            if (failed) Text(stringResource(R.string.feedback_no_mail_app), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
            Button(
                enabled = text.isNotBlank() || pictures.isNotEmpty(),
                onClick = {
                    if (Feedback.send(context, text, pictures, withLog, logFiles())) onDismiss() else failed = true
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp).heightIn(min = 52.dp),
            ) { Text(stringResource(R.string.feedback_send)) }
        }
    }
}
