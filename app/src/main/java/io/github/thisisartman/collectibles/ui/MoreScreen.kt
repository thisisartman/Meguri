package io.github.thisisartman.collectibles.ui

import io.github.thisisartman.collectibles.photo.CardPictures
import io.github.thisisartman.collectibles.data.CollectiblesDb
import io.github.thisisartman.collectibles.data.ItemType
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.toggleable
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
    private val db: CollectiblesDb,
) : ViewModel() {
    fun run(block: suspend () -> Unit) { viewModelScope.launch { block() } }

    /** Card codes of the manhole cards loaded on this phone (for #53's picture download). */
    suspend fun cardCodes(): List<String> = db.dao().allItems()
        .filter { it.type == ItemType.MANHOLE_CARD }.mapNotNull { it.code }

    /** Card pictures being downloaded: done and total, or null when idle. Survives leaving the screen. */
    val cardProgress = kotlinx.coroutines.flow.MutableStateFlow<Pair<Int, Int>?>(null)

    fun downloadCards(context: android.content.Context, onDone: (Int, Int) -> Unit) {
        if (cardProgress.value != null) return
        viewModelScope.launch {
            cardProgress.value = 0 to 0
            val (ok, failed) = CardPictures.download(context.applicationContext, cardCodes()) { d, t -> cardProgress.value = d to t }
            log.i("Cards", "Card pictures: $ok downloaded, $failed failed")
            cardProgress.value = null
            onDone(ok, failed)
        }
    }
}

@Composable
fun MoreScreen(
    snackbar: SnackbarHostState, onLogs: () -> Unit, onBack: () -> Unit = {},
    myCollections: @Composable () -> Unit = {},
    vm: MoreViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val ja = isJapanese()
    val scope = rememberCoroutineScope()
    val failText = stringResource(R.string.more_backup_failed)

    var withPhotos by remember { mutableStateOf(false) }
    val saveBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val photos = withPhotos
        vm.run {
            val msg = runCatching { vm.io.exportBackup(uri, photos) }
                .map {
                    val name = androidx.documentfile.provider.DocumentFile.fromSingleUri(context, uri)?.name
                    val counts = countsText(context.resources, it.finds, it.items)
                    if (name != null) context.getString(R.string.more_backup_done_named, name, counts) else context.getString(R.string.more_backup_done, counts)
                }
                .getOrElse { vm.log.e("Backup", "Export failed", it); failText }
            scope.launch { snackbar.showSnackbar(msg) }
        }
    }
    val openCatalog = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        vm.run {
            val r = runCatching { vm.io.importCatalog(uri) }
            r.exceptionOrNull()?.let { vm.log.e("Catalog", "Import failed", it) }
            val msg = catalogueMessage(context, r)
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
            text = {
                val folderName = remember(autoTick) {
                    vm.auto.folder?.let { androidx.documentfile.provider.DocumentFile.fromTreeUri(context, it)?.name }
                }
                val last = remember(autoTick) { vm.auto.lastSuccessMs?.let { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(it)) } }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.more_auto_body, folderName ?: "?", AutoBackup.KEEP))
                    if (last != null) Text(stringResource(R.string.more_auto_sub_last, last), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
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
    // Settings as a short list of sections, each its own page (owner's pick B, #71, 2026-11-07). The dialogs and
    // file pickers above stay here, so each section's rows work exactly as before.
    var section by rememberSaveable { mutableStateOf<String?>(null) }
    androidx.activity.compose.BackHandler(enabled = section != null) { section = null }
    val autoOn = remember(autoTick) { vm.auto.folder != null }
    val autoLast = remember(autoTick) { vm.auto.lastSuccessMs }
    val autoFailed = remember(autoTick) { vm.auto.lastFailed }
    val autoSub = when {
        !autoOn -> stringResource(R.string.more_auto_sub_off)
        autoFailed -> stringResource(R.string.more_auto_sub_failed)
        else -> autoLast?.let { stringResource(R.string.more_auto_sub_last, SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(it))) }
            ?: stringResource(R.string.more_auto_sub_on)
    }
    val titles = mapOf(
        S_COLLECTIONS to stringResource(R.string.more_my_collections),
        S_ALERTS to stringResource(R.string.settings_alerts),
        S_DATA to stringResource(R.string.settings_data),
        S_LOOK to stringResource(R.string.settings_look),
        S_CATALOGUE to stringResource(R.string.settings_catalogue),
        S_HELP to stringResource(R.string.settings_help),
    )
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        // A page of its own, opened from the top-right menu, so it gets a back arrow like the item page
        Row(Modifier.padding(start = 4.dp, top = 8.dp, end = 16.dp, bottom = 8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            androidx.compose.material3.IconButton(onClick = { if (section != null) section = null else onBack() }) {
                androidx.compose.material3.Icon(
                    androidx.compose.material.icons.Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.back),
                )
            }
            Text(section?.let { titles[it] } ?: stringResource(R.string.more_title), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(start = 4.dp))
        }

        when (section) {
            null -> {
                // Each line says what is set now, so nothing needs opening just to check (round, option B)
                SectionLine(titles.getValue(S_COLLECTIONS), stringResource(R.string.settings_collections_sub)) { section = S_COLLECTIONS }
                SectionLine(titles.getValue(S_ALERTS), stringResource(R.string.settings_alerts_sub)) { section = S_ALERTS }
                SectionLine(titles.getValue(S_DATA), autoSub) { section = S_DATA }
                SectionLine(
                    titles.getValue(S_LOOK),
                    stringResource(if (ja) R.string.more_japanese else R.string.more_english) +
                        if (AppLook.available() && AppLook.deviceColours(context)) " · " + stringResource(R.string.look_device_colours) else "",
                ) { section = S_LOOK }
                SectionLine(titles.getValue(S_CATALOGUE), stringResource(R.string.settings_catalogue_sub)) { section = S_CATALOGUE }
                SectionLine(titles.getValue(S_HELP), stringResource(R.string.settings_help_sub, BuildConfig.VERSION_NAME)) { section = S_HELP }
            }
            S_COLLECTIONS -> myCollections()
            S_ALERTS -> AlertsSection()
            S_LOOK -> {
                SectionTitle(stringResource(R.string.more_language))
                Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    InkFilterChip(selected = !ja, onClick = { AppLanguage.set(context, japanese = false) }, label = { Text(stringResource(R.string.more_english)) })
                    InkFilterChip(selected = ja, onClick = { AppLanguage.set(context, japanese = true) }, label = { Text(stringResource(R.string.more_japanese)) })
                }
                if (AppLook.available()) {
                    var device by remember { mutableStateOf(AppLook.deviceColours(context)) }
                    Row(
                        Modifier.fillMaxWidth()
                            .toggleable(value = device, role = Role.Switch, onValueChange = { device = it; AppLook.setDeviceColours(context, it) })
                            .heightIn(min = 56.dp)
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.look_device_colours), style = MaterialTheme.typography.bodyLarge)
                            Text(stringResource(R.string.look_device_colours_sub), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        androidx.compose.foundation.layout.Spacer(Modifier.width(12.dp))
                        androidx.compose.material3.Switch(checked = device, onCheckedChange = null)
                    }
                }
            }
            S_DATA -> {
                MoreRow(stringResource(R.string.more_backup), stringResource(R.string.more_backup_sub)) {
                    val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
                    withPhotos = false
                    saveBackup.launch("collectibles-backup-$stamp.zip")
                }
                // Moving to a new phone (#66): the same backup with every photo inside, so nothing depends on the gallery
                MoreRow(stringResource(R.string.more_backup_photos), stringResource(R.string.more_backup_photos_sub)) {
                    val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
                    withPhotos = true
                    saveBackup.launch("collectibles-backup-photos-$stamp.zip")
                }
                MoreRow(stringResource(R.string.more_auto), autoSub) {
                    if (autoOn) autoDialog = true else pickFolder.launch(STARTING_FOLDER)
                }
                MoreRow(stringResource(R.string.more_restore), stringResource(R.string.more_restore_sub)) {
                    openBackup.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
                }
                if (relinkPending) {
                    MoreRow(stringResource(R.string.more_relink), stringResource(R.string.more_relink_sub)) {
                        askPhotos.launch(BackupRestore.photoPermission())
                    }
                }
            }
            S_CATALOGUE -> {
                MoreRow(stringResource(R.string.more_import), stringResource(R.string.more_import_sub)) {
                    openCatalog.launch(arrayOf("text/csv", "text/comma-separated-values", "text/plain", "application/octet-stream", "*/*"))
                }
                // Card pictures (#53): onto this phone only, never in the app or backups
                val ctx = androidx.compose.ui.platform.LocalContext.current
                val progress by vm.cardProgress.collectAsState()
                val have by CardPictures.have.collectAsState()
                var total by remember { mutableStateOf<Int?>(null) }
                LaunchedEffect(Unit) { total = vm.cardCodes().size }
                val doneText = stringResource(R.string.cards_done)
                MoreRow(
                    stringResource(R.string.cards_download),
                    progress?.let { (d, t) -> stringResource(R.string.cards_progress, d, t) }
                        ?: if (total != null && total!! > 0 && have.size >= total!!) stringResource(R.string.cards_all, have.size)
                        else stringResource(R.string.cards_download_sub, have.size, total ?: 0),
                ) {
                    vm.downloadCards(ctx) { ok, failed -> scope.launch { snackbar.showSnackbar(doneText.format(ok, failed)) } }
                }
                if (have.isNotEmpty() && progress == null) MoreRow(stringResource(R.string.cards_remove), null) {
                    CardPictures.removeAll(ctx)
                }
                Text(
                    stringResource(R.string.settings_catalogue_note),
                    Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            S_HELP -> {
                MoreRow(stringResource(R.string.more_logs), null, onLogs)
                MoreRow(stringResource(R.string.more_about), stringResource(R.string.more_about_sub, BuildConfig.VERSION_NAME)) {}
            }
        }
    }
}

private const val S_COLLECTIONS = "collections"
private const val S_ALERTS = "alerts"
private const val S_DATA = "data"
private const val S_LOOK = "look"
private const val S_CATALOGUE = "catalogue"
private const val S_HELP = "help"

/** One section on the Settings list: its name, what is set now, and an arrow. */
@Composable
private fun SectionLine(title: String, sub: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 64.dp).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
        }
        androidx.compose.material3.Icon(
            androidx.compose.material.icons.Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
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
