package io.github.thisisartman.collectibles.ui

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import io.github.thisisartman.collectibles.R
import io.github.thisisartman.collectibles.data.ImportResult

/**
 * The first screen's welcome before any catalogue is loaded (#58): what Meguri is, what a catalogue is for, and the
 * two ways in. Show it while there are no catalogue items (rows.none { it.item.origin != Origin.USER }).
 */
@Composable
fun WelcomeCard(onLoadBundled: () -> Unit, onLoadFile: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.welcome_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.welcome_what), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.welcome_catalogue), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Button(onClick = onLoadBundled, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(stringResource(R.string.welcome_load_bundled))
            }
            InkOutlinedButton(onClick = onLoadFile, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.welcome_load_file))
            }
            Text(stringResource(R.string.welcome_other_ways), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** The two loads behind [WelcomeCard]; the result is said in a short message. */
class CatalogueActions(val loadBundled: () -> Unit, val loadFile: () -> Unit)

/** Wires [WelcomeCard]'s buttons: `val cat = rememberCatalogueActions(); WelcomeCard(cat.loadBundled, cat.loadFile)`. */
@Composable
fun rememberCatalogueActions(vm: MoreViewModel = hiltViewModel()): CatalogueActions {
    val context = LocalContext.current
    val openFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.run { report(context, runCatching { vm.io.importCatalog(uri) }) }
    }
    return remember(vm) {
        CatalogueActions(
            loadBundled = { vm.run { report(context, runCatching { vm.io.importBundled() }) } },
            loadFile = { openFile.launch(arrayOf("text/csv", "text/comma-separated-values", "text/plain", "application/octet-stream", "*/*")) },
        )
    }
}

/** The same wording More uses for a catalogue load. */
fun catalogueMessage(context: Context, r: Result<ImportResult>): String = r.map {
    when {
        it.added + it.updated + it.unchanged == 0 -> context.getString(R.string.more_import_none)
        it.added + it.updated == 0 -> context.getString(R.string.more_import_same, it.unchanged, it.lines)
        else -> context.getString(R.string.more_import_done, it.added, it.updated, it.lines)
    }
}.getOrElse { context.getString(R.string.more_import_failed, it.message ?: "") }

private fun report(context: Context, r: Result<ImportResult>) {
    val text = catalogueMessage(context, r)
    android.os.Handler(android.os.Looper.getMainLooper()).post { Toast.makeText(context, text, Toast.LENGTH_LONG).show() }
}
