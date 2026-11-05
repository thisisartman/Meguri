package io.github.thisisartman.collectibles.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import android.provider.DocumentsContract
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.DisposableEffect
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import android.content.Intent
import android.widget.Toast
import io.github.thisisartman.collectibles.data.AutoBackup
import io.github.thisisartman.collectibles.data.BackupError
import io.github.thisisartman.collectibles.data.BackupInfo
import io.github.thisisartman.collectibles.data.BackupRestore
import io.github.thisisartman.collectibles.data.CurrentCounts
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
class MoreViewModel @Inject constructor(
    val io: CatalogAndBackup,
    val restore: BackupRestore,
    val auto: AutoBackup,
    val log: FileLog,
) : ViewModel() {
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
                .map { context.getString(R.string.more_backup_done, countsText(context.resources, it.finds, it.items)) }
                .getOrElse { vm.log.e("Backup", "Export failed", it); failText }
            scope.launch { snackbar.showSnackbar(msg) }
        }
    }
    val openCatalog = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        vm.run {
            val msg = runCatching { vm.io.importCatalog(uri) }
                .map { if (it.added + it.updated == 0) context.getString(R.string.more_import_none) else context.getString(R.string.more_import_done, it.added, it.updated, it.lines) }
                .getOrElse { vm.log.e("Catalog", "Import failed", it); context.getString(R.string.more_import_failed, it.message ?: "") }
            scope.launch { snackbar.showSnackbar(msg) }
        }
    }
    var pendingRestore by remember { mutableStateOf<PendingRestore?>(null) }
    var autoDialog by remember { mutableStateOf(false) }
    var autoTick by remember { mutableIntStateOf(0) }
    var relinkPending by remember { mutableStateOf(vm.restore.relinkPending) }
    val restoreDone = stringResource(R.string.more_restore_done)
    val restoreFailed = stringResource(R.string.more_restore_failed)
    val notBackup = stringResource(R.string.more_restore_not_backup)
    val newer = stringResource(R.string.more_restore_newer)
    val damaged = stringResource(R.string.more_restore_damaged)

    fun runRestore(info: BackupInfo) {
        vm.run {
            runCatching { vm.restore.restore(info) }
                .onSuccess {
                    Toast.makeText(context, restoreDone.format(countsText(context.resources, it.finds, it.items)), Toast.LENGTH_LONG).show()
                    // Back to a fresh start: no screen may still point at a row that is gone.
                    context.packageManager.getLaunchIntentForPackage(context.packageName)?.let { launch ->
                        context.startActivity(Intent.makeRestartActivityTask(launch.component))
                    }
                }
                .onFailure {
                    vm.log.e("Restore", "Restore failed", it)
                    snackbar.showSnackbar(restoreFailed)
                }
        }
    }
    val askPhotos = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        pendingRestore?.let { p -> pendingRestore = null; runRestore(p.info) } ?: vm.run {
            vm.restore.relinkPhotos()
            relinkPending = vm.restore.relinkPending
        }
    }
    val openBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        vm.run {
            runCatching { PendingRestore(vm.restore.inspect(uri), vm.restore.currentCounts()) }
                .onSuccess { pendingRestore = it }
                .onFailure {
                    vm.log.w("Restore", "Backup refused: ${it.message}", it)
                    snackbar.showSnackbar(
                        when (it) {
                            is BackupError.NewerVersion -> newer
                            is BackupError.NotABackup -> notBackup
                            is BackupError.Damaged -> damaged
                            else -> restoreFailed
                        },
                    )
                }
        }
    }
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) { vm.auto.enable(uri); autoTick++ }
    }

    // The worker runs in the background: redraw the status line when it finishes.
    DisposableEffect(Unit) {
        val listener = vm.auto.listen { autoTick++ }
        onDispose { vm.auto.stopListening(listener) }
    }

    pendingRestore?.let { p ->
        RestoreDialog(
            p,
            onCancel = { vm.restore.discard(p.info); pendingRestore = null },
            onConfirm = {
                if (p.info.photos > 0 && !vm.restore.hasPhotoAccess()) {
                    askPhotos.launch(BackupRestore.photoPermission())
                } else {
                    pendingRestore = null
                    runRestore(p.info)
                }
            },
        )
    }
    if (autoDialog) {
        AlertDialog(
            onDismissRequest = { autoDialog = false },
            title = { Text(stringResource(R.string.more_auto)) },
            confirmButton = {
                TextButton(onClick = { autoDialog = false; vm.auto.runNow() }) { Text(stringResource(R.string.more_auto_now)) }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { autoDialog = false; pickFolder.launch(STARTING_FOLDER) }) { Text(stringResource(R.string.more_auto_change)) }
                    TextButton(onClick = { autoDialog = false; vm.auto.disable(); autoTick++ }) { Text(stringResource(R.string.more_auto_off)) }
                }
            },
        )
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text(stringResource(R.string.more_title), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(16.dp))

        SectionTitle(stringResource(R.string.more_language))
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !ja, onClick = { AppLanguage.set(context, japanese = false) }, label = { Text(stringResource(R.string.more_english)) }, modifier = Modifier.heightIn(min = 48.dp))
            FilterChip(selected = ja, onClick = { AppLanguage.set(context, japanese = true) }, label = { Text(stringResource(R.string.more_japanese)) }, modifier = Modifier.heightIn(min = 48.dp))
        }
        HorizontalDivider(Modifier.padding(top = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)

        SectionTitle(stringResource(R.string.more_backup_section))

        MoreRow(stringResource(R.string.more_backup), stringResource(R.string.more_backup_sub)) {
            val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
            saveBackup.launch("collectibles-backup-$stamp.zip")
        }
        MoreRow(stringResource(R.string.more_restore), stringResource(R.string.more_restore_sub)) {
            openBackup.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
        }
        if (relinkPending) {
            MoreRow(stringResource(R.string.more_relink), stringResource(R.string.more_relink_sub)) {
                askPhotos.launch(BackupRestore.photoPermission())
            }
        }
        val autoOn = remember(autoTick) { vm.auto.folder != null }
        val autoLast = remember(autoTick) { vm.auto.lastSuccessMs }
        val autoFailed = remember(autoTick) { vm.auto.lastFailed }
        val autoSub = when {
            !autoOn -> stringResource(R.string.more_auto_sub_off)
            autoFailed -> stringResource(R.string.more_auto_sub_failed)
            else -> autoLast?.let { stringResource(R.string.more_auto_sub_last, SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(it))) }
                ?: stringResource(R.string.more_auto_sub_on)
        }
        MoreRow(stringResource(R.string.more_auto), autoSub) {
            if (autoOn) autoDialog = true else pickFolder.launch(STARTING_FOLDER)
        }
        MoreRow(stringResource(R.string.more_import), stringResource(R.string.more_import_sub)) {
            openCatalog.launch(arrayOf("text/csv", "text/comma-separated-values", "text/plain", "application/octet-stream", "*/*"))
        }
        HorizontalDivider(Modifier.padding(top = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)
        AlertsSection()
        MoreRow(stringResource(R.string.more_logs), null, onLogs)
        MoreRow(stringResource(R.string.more_about), stringResource(R.string.more_about_sub, BuildConfig.VERSION_NAME)) {}
    }
}

/** The picker's storage root is refused by Android, so open it somewhere it accepts. */
private val STARTING_FOLDER = DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", "primary:Documents")

private class PendingRestore(val info: BackupInfo, val now: CurrentCounts)

@Composable
private fun RestoreDialog(p: PendingRestore, onCancel: () -> Unit, onConfirm: () -> Unit) {
    val date = p.info.createdMs?.let { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(it)) } ?: "?"
    // A valid but empty backup would wipe everything: it needs a second, explicit step.
    val wipes = p.info.finds == 0 && p.now.finds > 0
    var sure by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(if (wipes && !sure) R.string.more_restore_empty_title else R.string.more_restore_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.more_restore_backup_line, date, countsText(LocalContext.current.resources, p.info.finds, p.info.items, p.info.photos)))
                Text(stringResource(R.string.more_restore_now_line, countsText(LocalContext.current.resources, p.now.finds, p.now.items, p.now.photos)))
                Text(stringResource(if (wipes) R.string.more_restore_empty_body else R.string.more_restore_body))
            }
        },
        confirmButton = {
            // It replaces everything, so it doesn't look like Cancel.
            TextButton(
                onClick = { if (wipes && !sure) sure = true else onConfirm() },
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Text(stringResource(if (wipes && !sure) R.string.more_restore_continue else R.string.more_restore_confirm))
            }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text(stringResource(android.R.string.cancel)) } },
    )
}

@Composable
private fun MoreRow(title: String, sub: String?, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}
