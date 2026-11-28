package io.github.thisisartman.collectibles.mockup

import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.thisisartman.collectibles.data.CollectionRepository
import io.github.thisisartman.collectibles.data.Csv
import io.github.thisisartman.collectibles.data.PhotoSuggest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** One picture as read: its file, the suggestion (null: Unsorted) and where it points. */
private data class Line(val file: String, val type: String, val key: String, val name: String, val text: String)

/**
 * #206 on the phone's own reader: every picture in [dir] goes through exactly the share page's code
 * ([CollectionRepository.readAndSuggest]) against the items on this phone. Read only: nothing is written to the
 * database. The result also goes to the app's external files folder as bulk-suggest.csv, for adb pull.
 */
@Composable
fun BulkSuggestPage(dir: String?, repo: CollectionRepository) {
    val context = LocalContext.current
    val lines = remember { mutableStateListOf<Line>() }
    var status by remember { mutableStateOf("Starting…") }
    LaunchedEffect(dir) {
        val folder = dir?.let(::File)
        val files = folder?.listFiles()?.filter { it.isFile && it.extension.lowercase() in setOf("jpg", "jpeg", "png", "webp", "heic", "heif") }
            ?.sortedBy { it.name }.orEmpty()
        if (files.isEmpty()) { status = "No pictures in ${dir ?: "(no --es dir)"} (or no photo access: pm grant READ_MEDIA_IMAGES)"; return@LaunchedEffect }
        val known = repo.suggestKnown()
        val byId = withContext(Dispatchers.IO) { repo.itemsById(known.map { it.id }) }
        val t0 = System.currentTimeMillis()
        status = "Reading ${files.size} pictures against ${known.size} items…"
        repo.readAndSuggest(files.map { Uri.fromFile(it) }, known) { i, text, s ->
            val (key, name) = when (val t = s?.target) {
                is PhotoSuggest.Target.Existing -> byId[t.id] ?: ("" to t.id)
                is PhotoSuggest.Target.New -> "" to t.name
                null -> "" to ""
            }
            val line = Line(files[i].name, s?.let { "${it.rule} ${it.type}" + if (it.target is PhotoSuggest.Target.New) " NEW" else "" } ?: "Unsorted", key, name, text)
            lines += line
            status = "Read ${i + 1} of ${files.size}"
        }
        val out = File(context.getExternalFilesDir(null), "bulk-suggest.csv")
        withContext(Dispatchers.IO) {
            out.writeText(buildString {
                append(Csv.line("file", "suggestion_type", "target_key", "target_name", "read_text"))
                lines.forEach { append(Csv.line(it.file, it.type, it.key, it.name, it.text)) }
            })
        }
        status = "Done: ${files.size} pictures in ${(System.currentTimeMillis() - t0) / 1000} s, ${lines.count { it.type != "Unsorted" }} suggested. Wrote ${out.path}"
    }
    Column(Modifier.fillMaxSize()) {
        Text(status, Modifier.padding(16.dp), style = MaterialTheme.typography.titleSmall)
        HorizontalDivider()
        LazyColumn(contentPadding = PaddingValues(16.dp)) {
            items(lines) { l ->
                Column(Modifier.padding(vertical = 8.dp)) {
                    Text("${l.file} → ${if (l.type == "Unsorted") "Unsorted" else "${l.type}: ${l.name} ${l.key}"}", style = MaterialTheme.typography.bodyMedium)
                    Text(l.text.replace('\n', ' ').take(240), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                HorizontalDivider()
            }
        }
    }
}
