package io.github.thisisartman.collectibles.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.thisisartman.collectibles.BuildConfig
import io.github.thisisartman.collectibles.R
import io.github.thisisartman.collectibles.data.CatalogAndBackup
import io.github.thisisartman.collectibles.debug.FileLog
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class MoreViewModel @Inject constructor(val io: CatalogAndBackup, val log: FileLog) : ViewModel() {
    fun run(block: suspend () -> Unit) { viewModelScope.launch { block() } }
}

@Composable
fun MoreScreen(snackbar: SnackbarHostState, onLogs: () -> Unit, vm: MoreViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val ja = isJapanese()
    val scope = rememberCoroutineScope()
    val failText = stringResource(R.string.more_backup_failed)

    val saveBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        vm.run {
            val msg = runCatching { vm.io.exportBackup(uri) }
                .map { context.getString(R.string.more_backup_done, it.finds, it.items) }
                .getOrElse { vm.log.e("Backup", "Export failed", it); failText }
            scope.launch { snackbar.showSnackbar(msg) }
        }
    }
    val openCatalog = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        vm.run {
            val msg = runCatching { vm.io.importCatalog(uri) }
                .map { context.getString(R.string.more_import_done, it.added, it.updated, it.lines) }
                .getOrElse { vm.log.e("Catalog", "Import failed", it); context.getString(R.string.more_import_failed, it.message ?: "") }
            scope.launch { snackbar.showSnackbar(msg) }
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text(stringResource(R.string.more_title), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(16.dp))

        SectionTitle(stringResource(R.string.more_language))
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !ja, onClick = { AppLanguage.set(context, japanese = false) }, label = { Text(stringResource(R.string.more_english)) })
            FilterChip(selected = ja, onClick = { AppLanguage.set(context, japanese = true) }, label = { Text(stringResource(R.string.more_japanese)) })
        }
        HorizontalDivider(Modifier.padding(top = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)

        MoreRow(stringResource(R.string.more_backup), stringResource(R.string.more_backup_sub)) {
            val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
            saveBackup.launch("collectibles-backup-$stamp.zip")
        }
        MoreRow(stringResource(R.string.more_import), stringResource(R.string.more_import_sub)) {
            openCatalog.launch(arrayOf("text/csv", "text/comma-separated-values", "text/plain", "application/octet-stream", "*/*"))
        }
        MoreRow(stringResource(R.string.more_logs), null, onLogs)
        MoreRow(stringResource(R.string.more_about), stringResource(R.string.more_about_sub, BuildConfig.VERSION_NAME)) {}
    }
}

@Composable
private fun MoreRow(title: String, sub: String?, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}
