package io.github.thisisartman.collectibles.ui

import androidx.compose.material.icons.outlined.Backup
import io.github.thisisartman.collectibles.photo.CardPictures
import io.github.thisisartman.collectibles.data.CollectiblesDb
import io.github.thisisartman.collectibles.data.ItemType
import io.github.thisisartman.collectibles.data.CollectionType
import io.github.thisisartman.collectibles.photo.PhotoStore
import io.github.thisisartman.collectibles.data.Photo
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
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Box
import kotlinx.coroutines.flow.stateIn
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
import io.github.thisisartman.collectibles.data.DataJob
import io.github.thisisartman.collectibles.data.DataStep
import androidx.compose.ui.draw.alpha
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Text
import androidx.compose.material3.SegmentedButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
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
    /** A photo file's photos added to what is here (#188). */
    val photoFile: io.github.thisisartman.collectibles.data.PhotoImport,
    val auto: AutoBackup,
    val log: FileLog,
    private val db: CollectiblesDb,
    /** Runs long work for the life of the app, so leaving Settings does not cancel it (finding 32). */
    val work: SettingsWork,
    /** Back up now, Restore and Add photos from a file: how far, and how the last one went (#217). */
    val jobs: DataJobs,
    /** Region packs (0.5): the catalogue and offline map per prefecture, downloaded. */
    val packs: io.github.thisisartman.collectibles.data.RegionPacks,
) : ViewModel() {
    fun run(block: suspend () -> Unit) { work.launch(block) }

    /** The pack index: the last one read, then refreshed from the server when Regions opens. */
    val packIndex = kotlinx.coroutines.flow.MutableStateFlow(packs.cachedIndex())
    /** True while the index is being read; false with no index means no connection. */
    val packIndexLoading = kotlinx.coroutines.flow.MutableStateFlow(false)
    /** The prefecture being downloaded and how far (0 to 1), or null. */
    val packProgress = kotlinx.coroutines.flow.MutableStateFlow<Pair<String, Float>?>(null)
    /** A failed download or removal, said once on the Regions page. */
    val packError = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)

    fun refreshPackIndex() = run {
        packIndexLoading.value = true
        packs.fetchIndex()?.let { packIndex.value = it }
        packIndexLoading.value = false
    }

    /** Downloads a prefecture: its main catalogue packs, its map when [withMap], and any [extras] (optional types). */
    fun downloadPrefecture(prefecture: String, withMap: Boolean, extras: Set<ItemType> = emptySet()) = run {
        val index = packIndex.value ?: return@run
        val installed = packs.installed.value
        // A pack already on the phone at the same version is not fetched again ("Download the rest", #134)
        val chosen = index.of(prefecture).filter { p ->
            (if (p.isMap) withMap else p.core || p.type in extras) && installed[p.id] != p.sha256
        }
        packProgress.value = prefecture to 0f
        packs.install(chosen) { done, total -> packProgress.value = prefecture to (if (total > 0) done.toFloat() / total else 1f) }
            .onFailure { packError.value = prefecture }
        packProgress.value = null
    }

    /** Downloads what changed for every prefecture on this phone. */
    fun updatePacks() = run {
        val index = packIndex.value ?: return@run
        val changed = packs.updates(index)
        if (changed.isEmpty()) return@run
        packProgress.value = "" to 0f
        packs.install(changed) { done, total -> packProgress.value = "" to (if (total > 0) done.toFloat() / total else 1f) }
            .onFailure { packError.value = "" }
        packProgress.value = null
    }

    fun removePrefecture(prefecture: String) = run {
        val index = packIndex.value ?: return@run
        packs.remove(prefecture, index)
    }

    /** For the onboarding's region list: counts from the pack index, or the catalogue inside the app without a connection. */
    suspend fun regionCounts(): Map<String, Map<ItemType, Int>> {
        val index = packs.fetchIndex()?.also { packIndex.value = it } ?: return io.bundledPrefectures()
        return index.packs.filter { !it.isMap && it.core && it.type != null }
            .groupBy { it.prefecture }.mapValues { (_, l) -> l.associate { it.type!! to it.items } }
    }

    /**
     * The onboarding's Load: the chosen prefectures' main packs and, when ticked, their offline maps (#135), or the catalogue inside
     * the app when the pack server can't be reached. Returns false when a pack did not install.
     *
     * It runs in the app-wide work scope, not the caller's: the onboarding sheet leaves the screen as soon as the first
     * pack fills the catalogue, and its own scope used to cancel every pack after that one (#134).
     */
    /** Size of the offline maps of [regions], from the list of downloads; null until it is in. */
    fun mapBytes(regions: Set<String>): Long? = packIndex.value?.packs?.filter { it.isMap && it.prefecture in regions }?.sumOf { it.bytes }

    suspend fun loadRegions(regions: Set<String>, withMap: Boolean = false): Boolean {
        val done = kotlinx.coroutines.CompletableDeferred<Boolean>()
        work.launch {
            val ok = runCatching {
                val index = packIndex.value ?: packs.fetchIndex()
                if (index == null) { io.importBundled(regions); true }
                else packs.install(index.packs.filter { (if (it.isMap) withMap else it.core) && it.prefecture in regions }).isSuccess
            }.getOrDefault(false)
            if (!ok) runCatching { log.w("Packs", "First-run load incomplete for $regions") }
            done.complete(ok)
        }
        return done.await()
    }

    /** Card codes of the manhole cards loaded on this phone (for #53's picture download). */
    /** Prefecture -> how many items of each category are loaded (Catalogue pages). */
    val catalogue = kotlinx.coroutines.flow.MutableStateFlow<Map<String, Map<ItemType, Int>>>(emptyMap())
    /** Names of the categories the person added (Settings list summary). */
    val customNames = kotlinx.coroutines.flow.MutableStateFlow<List<String>>(emptyList())
    val findCount = kotlinx.coroutines.flow.MutableStateFlow(0)
    fun refresh() {
        viewModelScope.launch {
            catalogue.value = db.dao().allItems().filter { it.prefecture != null }
                .groupBy { it.prefecture!! }.toSortedMap()
                .mapValues { (_, l) -> l.groupingBy { it.type }.eachCount() }
            customNames.value = db.dao().allCollectionTypes().map { it.name }
            findCount.value = db.dao().allFinds().size
            customCounts.value = db.dao().allItems().mapNotNull { it.customTypeId }.groupingBy { it }.eachCount()
        }
    }

    /** Your own categories, live, and how many items each has (Settings > Categories). */
    val customTypes = db.dao().observeCollectionTypes()
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, emptyList())
    val customCounts = kotlinx.coroutines.flow.MutableStateFlow<Map<String, Int>>(emptyMap())

    fun saveCategory(t: CollectionType) = viewModelScope.launch {
        db.dao().upsertCollectionType(t.copy(name = t.name.trim()))
        log.i("Categories", "Saved category ${t.id}")
        refresh()
    }

    fun addCategory(name: String, color: Long, icon: String, hasCost: Boolean) = saveCategory(
        CollectionType(
            id = java.util.UUID.randomUUID().toString(), name = name, color = color, icon = icon, hasCost = hasCost,
            createdAt = System.currentTimeMillis(),
        ),
    )

    /** Removes a category only when nothing is in it. */
    fun removeCategory(id: String) = viewModelScope.launch {
        if (db.dao().allItems().none { it.customTypeId == id }) {
            db.dao().deleteCollectionType(id)
            log.i("Categories", "Removed category $id")
        }
        refresh()
    }

    /** Photos kept in Meguri, by collectible then by what the photo shows. */
    data class PhotoLine(val kindLabel: Int, val count: Int, val bytes: Long)
    data class PhotoGroup(val type: ItemType, val customName: String?, val lines: List<PhotoLine>)
    data class PhotoSummary(val groups: List<PhotoGroup>, val kept: Int, val bytes: Long, val linked: Int, val missing: Int)
    val photos = kotlinx.coroutines.flow.MutableStateFlow<PhotoSummary?>(null)

    fun loadPhotos(context: android.content.Context) = viewModelScope.launch {
        photos.value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val items = db.dao().allItems().associateBy { it.id }
            val itemOfFind = db.dao().allFinds().associate { it.id to it.itemId }
            val names = db.dao().allCollectionTypes().associate { it.id to it.name }
            val all = db.dao().allPhotos()
            val kept = all.filter { PhotoStore.isApp(it.contentUri) }
            fun size(p: Photo) =
                PhotoStore.file(context, p.contentUri).length()
            val groups = kept.mapNotNull { p -> itemOfFind[p.acquisitionId]?.let { items[it] }?.let { it to p } }
                .groupBy { (item, _) -> item.type to (if (item.type == ItemType.CUSTOM) item.customTypeId else null) }
                .toList()
                .sortedWith(compareBy({ it.first.first.ordinal }, { names[it.first.second] ?: "" }))
                .map { (key, list) ->
                    val lines = list.groupBy { (item, p) -> kindLabel(item.type, p.role) }
                        .map { (label, ps) -> PhotoLine(label, ps.size, ps.sumOf { size(it.second) }) }
                        .sortedByDescending { it.count }
                    PhotoGroup(key.first, key.second?.let { names[it] }, lines)
                }
            PhotoSummary(groups, kept.size, kept.sumOf { size(it) }, all.size - kept.size, all.count { it.missing })
        }
    }

    suspend fun cardCodes(): List<String> = db.dao().allItems()
        .filter { it.type == ItemType.MANHOLE_CARD }.mapNotNull { it.code }

    /** Card pictures being downloaded: done and total, or null when idle. Held by [work], so it survives leaving Settings. */
    val cardProgress = work.cardProgress

    fun downloadCards(context: android.content.Context) {
        val app = context.applicationContext
        work.downloadCards({ progress -> CardPictures.download(app, cardCodes(), progress) }) { r ->
            log.i("Cards", "Card pictures: ${r.downloaded} downloaded, ${r.notOnSite} not on the site yet, ${r.failed} failed")
            val text = app.getString(SettingsWork.cardsDoneMessage(r), r.downloaded, r.failed)
            work.say(
                if (r.notOnSite == 0) text
                else text + " " + app.resources.getQuantityString(R.plurals.cards_not_on_site, r.notOnSite, r.notOnSite),
            )
        }
    }
}

@Composable
fun MoreScreen(
    snackbar: SnackbarHostState, onLogs: () -> Unit, onBack: () -> Unit = {},
    vm: MoreViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val ja = isJapanese()
    val scope = rememberCoroutineScope()
    val failText = stringResource(R.string.more_backup_failed)

    // The work below runs in the app's scope (finding 32): it reads words from the app, not this page, and its result
    // goes to the app frame's snackbar, so both still work after Settings has closed
    val app = context.applicationContext
    // Back up now asks each time, with photos or without (finding 31); saveable, so the file picker can come back to it
    var withPhotos by rememberSaveable { mutableStateOf(true) }
    var askBackup by remember { mutableStateOf(false) }
    val busyText = stringResource(R.string.job_busy)
    // One of Back up now, Restore and Add photos runs at a time (#217): the rows wait while one runs
    val running by vm.jobs.running.collectAsState()
    val jobResults by vm.jobs.results.collectAsState()
    fun fileName(uri: android.net.Uri) = runCatching { androidx.documentfile.provider.DocumentFile.fromSingleUri(app, uri)?.name }.getOrNull()
    val saveBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        if (!vm.jobs.start(DataJob.BACKUP)) { vm.work.say(busyText); return@rememberLauncherForActivityResult }
        val photos = withPhotos
        vm.run {
            val name = fileName(uri)
            val msg = runCatching { vm.io.exportBackup(uri, photos, vm.jobs::report) }
                .map {
                    vm.jobs.finish(DataResult(DataJob.BACKUP, true, System.currentTimeMillis(), it.finds, it.photoFiles, name))
                    val counts = countsText(app.resources, it.finds, it.items)
                    if (name != null) app.getString(R.string.more_backup_done_named, name, counts) else app.getString(R.string.more_backup_done, counts)
                }
                .getOrElse {
                    vm.log.e("Backup", "Export failed", it)
                    vm.jobs.finish(DataResult(DataJob.BACKUP, false, System.currentTimeMillis(), file = name))
                    failText
                }
            vm.work.say(msg)
        }
    }
    // Two buttons and a switch (#163): the photos are a setting of the one backup, not a third button
    var includePhotos by remember { mutableStateOf(true) }
    if (askBackup) AlertDialog(
        onDismissRequest = { askBackup = false },
        title = { Text(stringResource(R.string.more_backup)) },
        text = {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 56.dp)
                    .toggleable(value = includePhotos, role = Role.Switch, onValueChange = { includePhotos = it }),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.backup_include_photos), style = MaterialTheme.typography.bodyLarge)
                    Text(stringResource(R.string.backup_include_photos_sub), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                androidx.compose.material3.Switch(checked = includePhotos, onCheckedChange = null)
            }
        },
        confirmButton = {
            TextButton(onClick = { askBackup = false; withPhotos = includePhotos; saveBackup.launch(backupFileName(photos = includePhotos)) }) {
                Text(stringResource(R.string.backup_save))
            }
        },
        dismissButton = { TextButton(onClick = { askBackup = false }) { Text(stringResource(R.string.cancel)) } },
    )
    val openCatalog = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        vm.run {
            val r = runCatching { vm.io.importCatalog(uri) }
            vm.work.say(catalogueMessage(app, r))
        }
    }
    var pendingRestore by remember { mutableStateOf<PendingRestore?>(null) }
    var autoTick by remember { mutableIntStateOf(0) }
    var relinkPending by remember { mutableStateOf(vm.restore.relinkPending) }
    val restoreDone = stringResource(R.string.more_restore_done)
    val restoreFailed = stringResource(R.string.more_restore_failed)
    val notBackup = stringResource(R.string.more_restore_not_backup)
    val newer = stringResource(R.string.more_restore_newer)
    val damaged = stringResource(R.string.more_restore_damaged)

    // The chosen backup's name, for the Restore row's last result (#217)
    var restoreName by rememberSaveable { mutableStateOf<String?>(null) }
    fun restoreFailedAt(name: String?) =
        vm.jobs.finish(DataResult(DataJob.RESTORE, false, System.currentTimeMillis(), file = name))

    fun runRestore(info: BackupInfo) {
        val name = restoreName
        if (!vm.jobs.start(DataJob.RESTORE)) { vm.work.say(busyText); return }
        vm.run {
            runCatching { vm.restore.restore(info, vm.jobs::report) }
                .onSuccess {
                    vm.jobs.finish(DataResult(DataJob.RESTORE, true, System.currentTimeMillis(), it.finds, it.photos, name))
                    Toast.makeText(app, restoreDone.format(countsText(app.resources, it.finds, it.items)), Toast.LENGTH_LONG).show()
                    // Back to a fresh start: no screen may still point at a row that is gone.
                    app.packageManager.getLaunchIntentForPackage(app.packageName)?.let { launch ->
                        app.startActivity(Intent.makeRestartActivityTask(launch.component))
                    }
                }
                .onFailure {
                    vm.log.e("Restore", "Restore failed", it)
                    restoreFailedAt(name)
                    vm.work.say(restoreFailed)
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
        if (!vm.jobs.start(DataJob.RESTORE, DataStep.READING)) { vm.work.say(busyText); return@rememberLauncherForActivityResult }
        val name = fileName(uri)
        restoreName = name
        vm.run {
            runCatching { PendingRestore(vm.restore.inspect(uri), vm.restore.currentCounts()) }
                .onSuccess { vm.jobs.pause(); pendingRestore = it }
                .onFailure {
                    vm.log.w("Restore", "Backup refused: ${it.message}", it)
                    restoreFailedAt(name)
                    vm.work.say(
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
    // A photo file (#188): read and shown first, then added on "Add"; nothing is replaced
    var pendingPhotoFile by remember { mutableStateOf<io.github.thisisartman.collectibles.data.PhotoFileInfo?>(null) }
    val photoFileNot = stringResource(R.string.photo_file_not)
    val photoFileFailed = stringResource(R.string.photo_file_failed)
    val photoFileDone = stringResource(R.string.photo_file_done)
    val photoFileSomeFailed = stringResource(R.string.photo_file_some_failed)
    var photoFileName by rememberSaveable { mutableStateOf<String?>(null) }
    fun photoFileFailedAt(name: String?) =
        vm.jobs.finish(DataResult(DataJob.PHOTO_FILE, false, System.currentTimeMillis(), file = name))
    val openPhotoFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        if (!vm.jobs.start(DataJob.PHOTO_FILE, DataStep.READING)) { vm.work.say(busyText); return@rememberLauncherForActivityResult }
        val name = fileName(uri)
        photoFileName = name
        vm.run {
            runCatching { vm.photoFile.inspect(uri) }
                .onSuccess { vm.jobs.pause(); pendingPhotoFile = it }
                .onFailure {
                    vm.log.w("PhotoFile", "Photo file refused: ${it.message}", it)
                    photoFileFailedAt(name)
                    vm.work.say(if (it is io.github.thisisartman.collectibles.data.PhotoFileError) photoFileNot else photoFileFailed)
                }
        }
    }
    pendingPhotoFile?.let { info ->
        val p = info.plan
        AlertDialog(
            onDismissRequest = { pendingPhotoFile = null; vm.photoFile.discard(info) },
            title = { Text(stringResource(R.string.photo_file_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.photo_file_body, p.photos, p.toItems, p.newItems))
                    if (p.missing.isNotEmpty()) Text(stringResource(R.string.photo_file_missing, p.missing.size))
                    if (info.problems.isNotEmpty()) Text(stringResource(R.string.photo_file_problems, info.problems.size))
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingPhotoFile = null
                    val name = photoFileName
                    if (!vm.jobs.start(DataJob.PHOTO_FILE, DataStep.COPY)) { vm.photoFile.discard(info); vm.work.say(busyText); return@TextButton }
                    vm.run {
                        runCatching { vm.photoFile.add(info, vm.jobs::report) }
                            .onSuccess { r ->
                                vm.jobs.finish(DataResult(DataJob.PHOTO_FILE, true, System.currentTimeMillis(), photos = r.added, file = name))
                                vm.work.say(photoFileDone.format(r.added, r.newItems, r.alreadyThere) + if (r.failed > 0) " " + photoFileSomeFailed.format(r.failed) else "")
                            }
                            .onFailure {
                                vm.log.e("PhotoFile", "Adding a photo file failed", it)
                                photoFileFailedAt(name)
                                vm.work.say(photoFileFailed)
                            }
                    }
                }) { Text(stringResource(R.string.photo_file_add)) }
            },
            dismissButton = { TextButton(onClick = { pendingPhotoFile = null; vm.photoFile.discard(info) }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) { vm.auto.enable(uri); autoTick++ }
    }
    // One file, replaced each week: how Google Drive works through Android's own picker (0.6)
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) { vm.auto.enableFile(uri); autoTick++ }
    }
    var askWhere by remember { mutableStateOf(false) }
    var editServer by remember { mutableStateOf(false) }
    if (askWhere) WhereDialog(
        onPick = { w ->
            askWhere = false
            when (w) {
                AutoBackup.Where.FOLDER -> pickFolder.launch(STARTING_FOLDER)
                AutoBackup.Where.FILE -> pickFile.launch(if (BuildConfig.DEBUG) "meguri-dev-backup.zip" else "meguri-backup.zip")
                AutoBackup.Where.SERVER -> editServer = true
            }
        },
        onDismiss = { askWhere = false },
    )
    if (editServer) ServerDialog(
        saved = remember { vm.auto.server.login },
        onSaved = { login -> vm.auto.server.save(login); vm.auto.enableServer(); editServer = false; autoTick++ },
        onRemove = if (vm.auto.server.address != null) ({
            if (vm.auto.where == AutoBackup.Where.SERVER) vm.auto.disable()
            vm.auto.server.forget(); editServer = false; autoTick++
        }) else null,
        onDismiss = { editServer = false },
    )
    val noServerBackups = stringResource(R.string.restore_server_none)
    fun restoreFromServer() {
        if (!vm.jobs.start(DataJob.RESTORE, DataStep.READING)) { vm.work.say(busyText); return }
        restoreName = null
        vm.run {
            runCatching {
                val newest = vm.auto.serverBackups().firstOrNull()
                if (newest == null) { vm.jobs.pause(); vm.work.say(noServerBackups); return@run }
                restoreName = newest.name
                val uri = vm.auto.downloadFromServer(newest.name)
                val info = try { vm.restore.inspect(uri) } finally { vm.auto.clearServerDownload() }
                PendingRestore(info, vm.restore.currentCounts())
            }.onSuccess { vm.jobs.pause(); pendingRestore = it }.onFailure {
                vm.log.w("Restore", "Server restore refused: ${it.javaClass.simpleName}")
                restoreFailedAt(restoreName)
                vm.work.say(if (it is BackupError) notBackup else restoreFailed)
            }
        }
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
    // Settings as a short list of sections, each its own page (owner's pick B, #71, 2026-11-07). The dialogs and
    // file pickers above stay here, so each section's rows work exactly as before.
    var section by rememberSaveable { mutableStateOf<String?>(null) }
    androidx.activity.compose.BackHandler(enabled = section != null) { section = parentOf(section) }
    val autoWhere = remember(autoTick) { vm.auto.where }
    val autoOn = autoWhere != null
    val autoLast = remember(autoTick) { vm.auto.lastSuccessMs }
    val autoFailed = remember(autoTick) { vm.auto.lastFailed }
    val autoProblem = remember(autoTick) { vm.auto.lastProblem }
    val autoSub = when {
        !autoOn -> stringResource(R.string.more_auto_sub_off)
        autoFailed && autoProblem == "login" -> stringResource(R.string.auto_failed_login)
        autoFailed && autoProblem == "certificate" -> stringResource(R.string.auto_failed_certificate)
        autoFailed && autoProblem == "offline" -> stringResource(R.string.auto_failed_offline)
        autoFailed -> stringResource(R.string.more_auto_sub_failed)
        else -> autoLast?.let { stringResource(R.string.more_auto_sub_last, dateTimeText(it)) }
            ?: stringResource(R.string.more_auto_sub_on)
    }
    val titles = mapOf(
        S_COLLECTIONS to stringResource(R.string.cat_yours),
        S_ALERTS to stringResource(R.string.settings_alerts),
        S_NOTIFY to stringResource(R.string.notify_title),
        S_DATA to stringResource(R.string.settings_data),
        S_LOOK to stringResource(R.string.settings_look),
        S_CATALOGUE to stringResource(R.string.settings_catalogue),
        S_REGIONS to stringResource(R.string.regions_title),
        S_HELP to stringResource(R.string.settings_help),
        S_INSTALLED to stringResource(R.string.settings_installed),
        S_ABOUT to stringResource(R.string.more_about),
        S_ALERT_CATS to stringResource(R.string.alerts_categories),
        S_SOURCES to stringResource(R.string.about_sources),
        S_ART to stringResource(R.string.about_art),
        S_LICENCES to stringResource(R.string.about_licences),
        S_PRIVACY to stringResource(R.string.about_privacy),
        S_WEEKLY to stringResource(R.string.more_auto),
        S_PHOTOS to stringResource(R.string.photos_kept),
    )
    var whatsNew by remember { mutableStateOf(false) }
    var feedback by remember { mutableStateOf(false) }
    if (feedback) FeedbackSheet(logFiles = { vm.log.files() }, onDismiss = { feedback = false })
    if (whatsNew) WhatsNewDialog(onDismiss = { whatsNew = false })
    LaunchedEffect(Unit) { vm.refresh() }
    val catalogue by vm.catalogue.collectAsState()
    val customNames by vm.customNames.collectAsState()
    val findCount by vm.findCount.collectAsState()
    Column(Modifier.fillMaxSize()) {
        // A page of its own, opened from the top-right menu, so it gets a back arrow like the item page. The bar stays
        // on screen while the page scrolls under it, so Back is always in reach (#141)
        Row(Modifier.padding(start = 4.dp, top = 8.dp, end = 16.dp, bottom = 8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            androidx.compose.material3.IconButton(onClick = { if (section != null) section = parentOf(section) else onBack() }) {
                androidx.compose.material3.Icon(
                    androidx.compose.material.icons.Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.back),
                )
            }
            Text(section?.let { titles[it] } ?: stringResource(R.string.more_title), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(start = 4.dp))
        }

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
        when (section) {
            null -> {
                // A newer release, when Check for updates is on (#131): first, where people see it
                UpdateNote()
                // Each line says what is set now, so nothing needs opening just to check. Order (owner, 2026-11-09):
                // Appearance, Alerts, Backup and data, Categories, Catalogue, Help and about
                val themeName = stringResource(
                    when (AppLook.theme(context)) { AppLook.Theme.PHONE -> R.string.look_theme_phone; AppLook.Theme.LIGHT -> R.string.look_theme_light; AppLook.Theme.DARK -> R.string.look_theme_dark },
                )
                SectionLine(
                    titles.getValue(S_LOOK),
                    listOfNotNull(
                        themeName,
                        stringResource(if (AppLook.available() && AppLook.deviceColours(context)) R.string.look_colours_wallpaper else R.string.look_colours_meguri),
                        stringResource(if (ja) R.string.more_japanese else R.string.more_english),
                    ).joinToString(" · "),
                    R.drawable.ic_settings_look,
                ) { section = S_LOOK }
                val alerts: AlertsViewModel = hiltViewModel()
                val a = alerts.settings
                SectionLine(
                    titles.getValue(S_ALERTS),
                    listOfNotNull(
                        stringResource(if (a.geofenceOn) R.string.alerts_mode_always else if (a.tripOn) R.string.alerts_line_trip else R.string.alerts_mode_off),
                        radiusText(a.radiusM),
                        pluralStringResource(R.plurals.alerts_line_categories, ALERT_TYPES.count { it.first in a.types }, ALERT_TYPES.count { it.first in a.types }),
                        if (a.quietOn) stringResource(R.string.alerts_line_quiet) else null,
                    ).joinToString(" · "),
                    R.drawable.ic_settings_alerts,
                ) { section = S_ALERTS }
                // The four kinds of notification, each a switch (#228)
                val notifyOn = io.github.thisisartman.collectibles.alerts.NotifyCategory.entries.count { io.github.thisisartman.collectibles.alerts.NotifyPrefs.on(context, it) }
                SectionLine(
                    titles.getValue(S_NOTIFY),
                    if (notifyOn == 4) stringResource(R.string.notify_sub_all) else stringResource(R.string.notify_sub_some, notifyOn),
                    R.drawable.ic_notification,
                ) { section = S_NOTIFY }
                val findsText = pluralStringResource(R.plurals.data_finds, findCount, findCount)
                val dataLine = when {
                    !autoOn -> stringResource(R.string.data_line_off, findsText)
                    autoFailed -> autoSub
                    autoLast != null -> stringResource(
                        R.string.data_line_on,
                        dateTimeText(autoLast),
                        findsText,
                    )
                    else -> autoSub
                }
                SectionLine(titles.getValue(S_DATA), dataLine, R.drawable.ic_settings_backup) { section = S_DATA }
                SectionLine(
                    titles.getValue(S_CATALOGUE),
                    run {
                        val pics by CardPictures.have.collectAsState()
                        listOfNotNull(
                            catalogue.keys.joinToString(", ") { prefectureName(it, ja) }.ifEmpty { null },
                            if (pics.isNotEmpty()) pluralStringResource(R.plurals.catalogue_line_pictures, pics.size, pics.size) else null,
                        ).joinToString(" · ").ifEmpty { stringResource(R.string.settings_catalogue_sub) }
                    },
                    R.drawable.ic_settings_catalogue,
                ) { section = S_CATALOGUE }
                SectionLine(titles.getValue(S_HELP), stringResource(R.string.settings_help_sub, BuildConfig.VERSION_NAME), R.drawable.ic_settings_help, last = true) { section = S_HELP }
            }
            S_COLLECTIONS -> {
                // Owner, 2026-11-09: "categories you added yourself"; tap one to change it, remove it when empty
                val types by vm.customTypes.collectAsState()
                val counts by vm.customCounts.collectAsState()
                var editing by remember { mutableStateOf<CollectionType?>(null) }
                var adding by remember { mutableStateOf(false) }
                if (types.isEmpty()) Note(stringResource(R.string.cat_empty))
                types.forEach { t ->
                    Row(
                        Modifier.fillMaxWidth().clickable { editing = t }.heightIn(min = 64.dp).padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    ) {
                        androidx.compose.material3.Icon(
                            androidx.compose.ui.res.painterResource(customIconRes(t.icon)), contentDescription = null,
                            tint = androidx.compose.ui.graphics.Color(t.color),
                        )
                        androidx.compose.foundation.layout.Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(t.name, style = MaterialTheme.typography.bodyLarge)
                            val n = counts[t.id] ?: 0
                            Text(
                                stringResource(t.category().label()) + " · " + pluralStringResource(R.plurals.items, n, n),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        androidx.compose.material3.Icon(
                            androidx.compose.material.icons.Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
                TextButton(onClick = { adding = true }, modifier = Modifier.padding(horizontal = 8.dp).heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.collection_new))
                }
                editing?.let { t ->
                    NewCollectionDialog(
                        onDismiss = { editing = null },
                        existingNames = types.filter { it.id != t.id }.map { it.name }.toSet(),
                        initial = t, itemCount = counts[t.id] ?: 0,
                        onRemove = { vm.removeCategory(t.id); editing = null },
                    ) { name, color, icon, hasCost ->
                        vm.saveCategory(t.copy(name = name, color = color, icon = icon, hasCost = hasCost)); editing = null
                    }
                }
                if (adding) NewCollectionDialog(onDismiss = { adding = false }, existingNames = types.map { it.name }.toSet()) { name, color, icon, hasCost ->
                    vm.addCategory(name, color, icon, hasCost); adding = false
                }
            }
            S_ALERTS -> AlertsSection(onCategories = { section = S_ALERT_CATS })
            S_NOTIFY -> {
                // Each its own Android channel too (#228); off here stops that kind everywhere in the app
                val prefs = io.github.thisisartman.collectibles.alerts.NotifyPrefs
                if (!androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()) {
                    Note(stringResource(R.string.notify_off_in_phone))
                    MoreRow(stringResource(R.string.notify_open_phone), null) {
                        context.startActivity(
                            android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                }
                listOf(
                    Triple(io.github.thisisartman.collectibles.alerts.NotifyCategory.ALERTS, R.string.notify_alerts, R.string.notify_alerts_sub),
                    Triple(io.github.thisisartman.collectibles.alerts.NotifyCategory.SUGGESTIONS, R.string.notify_suggestions, R.string.notify_suggestions_sub),
                    Triple(io.github.thisisartman.collectibles.alerts.NotifyCategory.BACKUPS, R.string.notify_backups, R.string.notify_backups_sub),
                    Triple(io.github.thisisartman.collectibles.alerts.NotifyCategory.UPDATES, R.string.notify_updates, R.string.notify_updates_sub),
                ).forEach { (c, title, sub) ->
                    ToggleRow(stringResource(title), stringResource(sub), prefs.on(context, c)) { prefs.set(context, c, it) }
                }
            }
            S_ALERT_CATS -> AlertCategoriesSection()
            S_LOOK -> {
                // Theme (owner, 2026-11-09): the phone's dark mode, or always light, or always dark; the map follows
                SectionTitle(stringResource(R.string.look_theme))
                val theme = AppLook.theme(context)
                Choice(
                    listOf(R.string.look_theme_phone, R.string.look_theme_light, R.string.look_theme_dark).map { stringResource(it) },
                    AppLook.Theme.entries.indexOf(theme),
                ) { AppLook.setTheme(context, AppLook.Theme.entries[it]) }
                if (AppLook.available()) {
                    SectionTitle(stringResource(R.string.look_colours))
                    var device by remember { mutableStateOf(AppLook.deviceColours(context)) }
                    Choice(listOf(stringResource(R.string.look_colours_meguri), stringResource(R.string.look_colours_wallpaper)), if (device) 1 else 0) {
                        device = it == 1; AppLook.setDeviceColours(context, device)
                    }
                    Note(stringResource(R.string.look_device_colours_sub))
                }
                // Nearby's switch with the names and counts, in place of two icons (#192); off by default
                SectionTitle(stringResource(R.string.look_nearby))
                ToggleRow(
                    stringResource(R.string.look_switch_names), stringResource(R.string.look_switch_names_sub),
                    NearbyPrefs.namesOnSwitch(context),
                ) { NearbyPrefs.setNamesOnSwitch(context, it) }
                SectionTitle(stringResource(R.string.more_language))
                Choice(listOf(stringResource(R.string.more_english), stringResource(R.string.more_japanese)), if (ja) 1 else 0) {
                    // The in-app switch needs Android 13; before that the phone's own language page sets it
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) AppLanguage.set(context, japanese = it == 1)
                    else runCatching { context.startActivity(android.content.Intent(android.provider.Settings.ACTION_LOCALE_SETTINGS)) }
                }
                if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) Note(stringResource(R.string.look_language_old))
            }
            S_DATA -> {
                // Owner, 2026-11-09: weekly backup as a row with its own switch; its details one tap in. Photos grouped.
                SectionTitle(stringResource(R.string.backup_section))
                // Its own choice each time, not the weekly backup's "Include photos" (finding 31)
                // Each says how its last run went until the next one, and how far it is while it runs (#217)
                // The first time Backup and data opens: Back up now and Weekly backup (#224)
                FirstTip(TipId.BACKUP, listOf(TipLine(androidx.compose.material.icons.Icons.Outlined.Backup, stringResource(R.string.more_backup), stringResource(R.string.tip_backup)))) {
                    JobRow(stringResource(R.string.more_backup), stringResource(R.string.backup_now_sub), DataJob.BACKUP, running, jobResults) { askBackup = true }
                }
                SplitSwitchRow(stringResource(R.string.more_auto), autoSub, autoOn, onOpen = { section = S_WEEKLY }) { on ->
                    if (on) askWhere = true else { vm.auto.disable(); autoTick++ }
                }
                JobRow(stringResource(R.string.more_restore), stringResource(R.string.more_restore_sub), DataJob.RESTORE, running, jobResults) {
                    openBackup.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
                }
                JobRow(stringResource(R.string.photo_file), stringResource(R.string.photo_file_sub), DataJob.PHOTO_FILE, running, jobResults) {
                    openPhotoFile.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
                }
                if (remember(autoTick) { vm.auto.server.address } != null) {
                    MoreRow(stringResource(R.string.restore_server), stringResource(R.string.restore_server_sub), enabled = running == null) { restoreFromServer() }
                }
                if (relinkPending) {
                    MoreRow(stringResource(R.string.more_relink), stringResource(R.string.more_relink_sub)) {
                        askPhotos.launch(BackupRestore.photoPermission())
                    }
                }
                // Your own categories are data (owner, 2026-11-09: "alerts is just about notifications")
                SectionTitle(stringResource(R.string.more_my_collections))
                MoreRow(
                    stringResource(R.string.cat_yours),
                    if (customNames.isEmpty()) stringResource(R.string.settings_collections_sub) else customNames.joinToString(", "),
                ) { section = S_COLLECTIONS }
                SectionTitle(stringResource(R.string.photos_section))
                PhotoHintRow()
                val photoSummary by vm.photos.collectAsState()
                LaunchedEffect(Unit) { vm.loadPhotos(context) }
                MoreRow(
                    stringResource(R.string.photos_kept),
                    photoSummary?.let { androidx.compose.ui.res.pluralStringResource(R.plurals.photos_kept_sub, it.kept, it.kept, android.text.format.Formatter.formatShortFileSize(context, it.bytes)) },
                ) { section = S_PHOTOS }
            }
            S_WEEKLY -> {
                ToggleRow(stringResource(R.string.more_auto), stringResource(R.string.weekly_explain), autoOn) { on ->
                    if (on) askWhere = true else { vm.auto.disable(); autoTick++ }
                }
                if (autoOn) {
                    val whereName = remember(autoTick) {
                        when (vm.auto.where) {
                            AutoBackup.Where.FOLDER -> vm.auto.folder?.let { androidx.documentfile.provider.DocumentFile.fromTreeUri(context, it)?.name }
                            AutoBackup.Where.FILE -> vm.auto.file?.let { androidx.documentfile.provider.DocumentFile.fromSingleUri(context, it)?.name }
                            AutoBackup.Where.SERVER -> vm.auto.server.address?.let { android.net.Uri.parse(it).host }
                            null -> null
                        }
                    }
                    val whereLabel = when (autoWhere) {
                        AutoBackup.Where.FILE -> R.string.where_file
                        AutoBackup.Where.SERVER -> R.string.where_server
                        else -> R.string.where_folder
                    }
                    MoreRow(stringResource(R.string.where_row), stringResource(whereLabel) + " · " + (whereName ?: "?")) {
                        askWhere = true
                    }
                    var photos by remember { mutableStateOf(vm.auto.includePhotos) }
                    ToggleRow(stringResource(R.string.weekly_photos), stringResource(R.string.weekly_photos_sub), photos) {
                        photos = it; vm.auto.includePhotos = it; autoTick++
                    }
                    if (autoWhere == AutoBackup.Where.FILE) {
                        Note(stringResource(R.string.where_file_note))
                    } else {
                        SectionTitle(stringResource(R.string.weekly_keep))
                        var keep by remember { mutableStateOf(vm.auto.keep) }
                        Choice(AutoBackup.KEEP_CHOICES.map { stringResource(R.string.weekly_keep_n, it) }, AutoBackup.KEEP_CHOICES.indexOf(keep)) {
                            keep = AutoBackup.KEEP_CHOICES[it]; vm.auto.keep = keep
                        }
                        Note(stringResource(R.string.weekly_keep_sub))
                    }
                    val startedText = stringResource(R.string.weekly_started)
                    MoreRow(stringResource(R.string.more_auto_now), autoSub) {
                        vm.auto.runNow()
                        scope.launch { snackbar.showSnackbar(startedText) }
                    }
                }
            }
            S_PHOTOS -> {
                val summary by vm.photos.collectAsState()
                LaunchedEffect(Unit) { vm.loadPhotos(context) }
                val sum = summary
                if (sum == null) Note("…")
                else {
                    Note(stringResource(R.string.photos_kept_note, sum.kept, android.text.format.Formatter.formatShortFileSize(context, sum.bytes)))
                    if (sum.groups.isEmpty()) Note(stringResource(R.string.photos_none))
                    sum.groups.forEach { g ->
                        SectionTitle(g.customName ?: stringResource(g.type.pluralLabel()))
                        g.lines.forEach { l ->
                            InfoRow(
                                stringResource(l.kindLabel),
                                pluralStringResource(R.plurals.photos_count, l.count, l.count) + " · " +
                                    android.text.format.Formatter.formatShortFileSize(context, l.bytes),
                            )
                        }
                    }
                    if (sum.linked > 0) Note(pluralStringResource(R.plurals.photos_linked, sum.linked, sum.linked))
                    if (sum.missing > 0) Note(pluralStringResource(R.plurals.photos_missing, sum.missing, sum.missing))
                }
            }
            S_CATALOGUE -> {
                // Prefectures sit one tap in, so the page stays short as more are added (owner, 2026-11-09)
                val prefCount = catalogue.size
                // Region packs (0.5): download a prefecture's catalogue and offline map
                MoreRow(stringResource(R.string.regions_title), stringResource(R.string.regions_sub)) { section = S_REGIONS }
                MoreRow(stringResource(R.string.settings_installed), pluralStringResource(R.plurals.settings_installed_sub, prefCount, prefCount)) { section = S_INSTALLED }
                // Card pictures (#53): onto this phone only, never in the app or backups
                val ctx = androidx.compose.ui.platform.LocalContext.current
                val progress by vm.cardProgress.collectAsState()
                val have by CardPictures.have.collectAsState()
                val none by CardPictures.none.collectAsState()
                var codes by remember { mutableStateOf<List<String>?>(null) }
                LaunchedEffect(Unit) { codes = vm.cardCodes() }
                val total = codes?.size
                // Nothing left to fetch: every card is here, or the site has no picture of it yet (#160)
                val left = remember(codes, have, none) { codes?.let { CardPictures.toFetch(it).size } }
                val allHere = total != null && total > 0 && left == 0
                val allHereText = stringResource(R.string.cards_all_here)
                MoreRow(
                    stringResource(R.string.cards_download),
                    progress?.takeIf { it.second > 0 }?.let { (d, t) -> stringResource(R.string.cards_progress, d, t) }
                        ?: if (allHere) stringResource(R.string.cards_all, have.size)
                        // No cards to fetch pictures for: say what to do first, not "0 of 0, tap to download" (#142)
                        else if (total == 0) stringResource(R.string.cards_none)
                        else stringResource(R.string.cards_download_sub, have.size, total ?: 0),
                ) {
                    // Every picture already here: say so, rather than "0 downloaded, 0 not found" (finding 40)
                    if (total == 0) Unit
                    else if (allHere && progress == null) scope.launch { snackbar.showSnackbar(allHereText) } else vm.downloadCards(ctx)
                }
                var confirmRemove by remember { mutableStateOf(false) }
                if (have.isNotEmpty() && progress == null) MoreRow(stringResource(R.string.cards_remove), null) { confirmRemove = true }
                // Asks first (finding 39)
                if (confirmRemove) AlertDialog(
                    onDismissRequest = { confirmRemove = false },
                    title = { Text(stringResource(R.string.cards_remove_title)) },
                    text = { Text(stringResource(R.string.cards_remove_text)) },
                    confirmButton = {
                        TextButton(onClick = { confirmRemove = false; CardPictures.removeAll(ctx) }) { Text(stringResource(R.string.cards_remove_confirm)) }
                    },
                    dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text(stringResource(android.R.string.cancel)) } },
                )
                Text(
                    stringResource(R.string.settings_catalogue_note),
                    Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            S_REGIONS -> RegionsPage(vm)
            S_INSTALLED -> {
                // One block per prefecture: what of each category is in the app
                catalogue.forEach { (pref, byType) ->
                    InfoRow(
                        prefectureName(pref, ja),
                        byType.toList().sortedBy { it.first.ordinal }
                            // "Ekitag 20": a name then its count, so names keep their capitals and 1 reads fine
                            .joinToString(" · ") { (t, n) -> context.getString(t.pluralLabel()) + " " + n },
                    )
                }
                MoreRow(stringResource(R.string.more_import), stringResource(R.string.more_import_sub)) {
                    openCatalog.launch(CATALOGUE_TYPES)
                }
            }
            S_HELP -> {
                // Feedback by mail to the owner, with screenshots (2026-11-15)
                MoreRow(stringResource(R.string.feedback_title), stringResource(R.string.feedback_row_sub)) { feedback = true }
                MoreRow(stringResource(R.string.help_whats_new), BuildConfig.VERSION_NAME) { whatsNew = true }
                // The bottom bar's shortcuts, in one line (#223)
                InfoRow(stringResource(R.string.help_shortcuts), stringResource(R.string.help_shortcuts_sub))
                // Every first-time tip once more (#224)
                val tipsDone = stringResource(R.string.tips_again_done)
                MoreRow(stringResource(R.string.tips_again), stringResource(R.string.tips_again_sub)) {
                    Tips.reset(context); scope.launch { snackbar.showSnackbar(tipsDone) }
                }
                // Check for updates (#131), off by default; the note sits right under it when there is one
                val updates = io.github.thisisartman.collectibles.data.UpdateCheck
                ToggleRow(stringResource(R.string.update_check), stringResource(R.string.update_check_sub), updates.on(context)) { on ->
                    updates.set(context, on)
                    if (on) scope.launch { updates.checkIfDue(app) }
                }
                UpdateNote()
                MoreRow(stringResource(R.string.more_logs), stringResource(R.string.help_logs_sub), onClick = onLogs)
                MoreRow(stringResource(R.string.more_about), stringResource(R.string.help_about_sub)) { section = S_ABOUT }
            }
            S_ABOUT -> {
                // Laid out like Fossify, Breezy Weather and Organic Maps (owner, 2026-11-09)
                AboutHeader()
                SectionTitle(stringResource(R.string.about_credits))
                MoreRow(stringResource(R.string.about_sources), SOURCES.joinToString(", ") { it.name }) { section = S_SOURCES }
                MoreRow(stringResource(R.string.about_art), ART.joinToString(", ") { it.name }) { section = S_ART }
                SectionTitle(stringResource(R.string.about_legal))
                MoreRow(stringResource(R.string.about_licences), stringResource(R.string.about_licences_sub, LIBRARIES.size)) { section = S_LICENCES }
                MoreRow(stringResource(R.string.about_privacy), stringResource(R.string.about_privacy_sub)) { section = S_PRIVACY }
                SectionTitle(stringResource(R.string.about_developer))
                // Developer mode (owner, 2026-11-09): the release writes the detailed log only with this on.
                // The dev build always does, so there it shows on and cannot be changed.
                var dev by remember { mutableStateOf(BuildConfig.DEBUG || io.github.thisisartman.collectibles.debug.DevMode.on(context)) }
                Row(
                    Modifier.fillMaxWidth()
                        .toggleable(value = dev, enabled = !BuildConfig.DEBUG, role = Role.Switch, onValueChange = {
                            dev = it; io.github.thisisartman.collectibles.debug.DevMode.set(context, it, vm.log)
                        })
                        .heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.about_dev_mode), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            stringResource(if (BuildConfig.DEBUG) R.string.about_dev_mode_dev else R.string.about_dev_mode_sub),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    androidx.compose.foundation.layout.Spacer(Modifier.width(12.dp))
                    androidx.compose.material3.Switch(checked = dev, onCheckedChange = null, enabled = !BuildConfig.DEBUG)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
            S_SOURCES -> CreditList(stringResource(R.string.about_sources_intro), SOURCES)
            S_ART -> CreditList(null, ART)
            S_LICENCES -> CreditList(stringResource(R.string.about_licences_intro), LIBRARIES)
            S_PRIVACY -> PrivacyPage()
        }
        }
    }
}

/** Inner pages go back to their section; sections go back to the list. */
private fun parentOf(section: String?): String? = when (section) {
    S_INSTALLED, S_REGIONS -> S_CATALOGUE
    S_ABOUT -> S_HELP
    S_ALERT_CATS -> S_ALERTS
    S_SOURCES, S_ART, S_LICENCES, S_PRIVACY -> S_ABOUT
    S_WEEKLY, S_PHOTOS, S_COLLECTIONS -> S_DATA
    else -> null
}

/** A one-of-several choice as a segmented row (theme, colours, language). */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun Choice(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    androidx.compose.material3.SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        labels.forEachIndexed { i, l ->
            SegmentedButton(
                selected = i == selected, onClick = { onSelect(i) },
                shape = androidx.compose.material3.SegmentedButtonDefaults.itemShape(i, labels.size),
            ) { Text(l, maxLines = 1) }
        }
    }
}

@Composable
private fun Note(text: String) = Text(
    text, Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
)

private const val S_COLLECTIONS = "collections"
private const val S_ALERTS = "alerts"
private const val S_NOTIFY = "notifications"
private const val S_DATA = "data"
private const val S_LOOK = "look"
private const val S_CATALOGUE = "catalogue"
private const val S_REGIONS = "regions"
private const val S_HELP = "help"
private const val S_INSTALLED = "installed"
private const val S_ABOUT = "about"
private const val S_ALERT_CATS = "alert_categories"
private const val S_SOURCES = "sources"
private const val S_ART = "art"
private const val S_LICENCES = "licences"
private const val S_PRIVACY = "privacy"
private const val S_WEEKLY = "weekly"
private const val S_PHOTOS = "photos"

/** One section on the Settings list: its name, what is set now, and an arrow. */
@Composable
private fun SectionLine(title: String, sub: String, icon: Int, last: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 68.dp).padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(40.dp).clip(androidx.compose.foundation.shape.CircleShape)
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = androidx.compose.ui.Alignment.Center,
        ) {
            androidx.compose.material3.Icon(
                androidx.compose.ui.res.painterResource(icon), contentDescription = null,
                tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp),
            )
        }
        androidx.compose.foundation.layout.Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
        }
        androidx.compose.material3.Icon(
            androidx.compose.material.icons.Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (!last) HorizontalDivider(Modifier.padding(start = 74.dp, end = 18.dp), color = MaterialTheme.colorScheme.outlineVariant)
}

/** The file types the catalogue picker offers (Settings and the onboarding). */
internal val CATALOGUE_TYPES = arrayOf("text/csv", "text/comma-separated-values", "text/plain", "application/octet-stream", "*/*")


/** The picker's storage root is refused by Android, so open it somewhere it accepts. */
private val STARTING_FOLDER = DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", "primary:Documents")

private class PendingRestore(val info: BackupInfo, val now: CurrentCounts)

@Composable
private fun RestoreDialog(p: PendingRestore, onCancel: () -> Unit, onConfirm: () -> Unit) {
    val date = p.info.createdMs?.let { dateTimeText(it) } ?: "?"
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

/** A whole-row switch, read with its words. */
@Composable
private fun ToggleRow(title: String, sub: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        androidx.compose.foundation.layout.Spacer(Modifier.width(12.dp))
        androidx.compose.material3.Switch(checked = checked, onCheckedChange = null)
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

/** Words that open a page, a thin line, then a switch (Android's own settings pattern; owner, 2026-11-09). */
@Composable
private fun SplitSwitchRow(title: String, sub: String, checked: Boolean, onOpen: () -> Unit, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Column(Modifier.weight(1f).clickable(onClick = onOpen).padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        androidx.compose.material3.VerticalDivider(Modifier.height(32.dp), color = MaterialTheme.colorScheme.outlineVariant)
        Box(
            Modifier.toggleable(value = checked, role = Role.Switch, onValueChange = onChange).padding(horizontal = 16.dp, vertical = 12.dp),
            contentAlignment = androidx.compose.ui.Alignment.Center,
        ) { androidx.compose.material3.Switch(checked = checked, onCheckedChange = null) }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

/** A line that only tells (nothing to tap). */
@Composable
private fun InfoRow(title: String, sub: String) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun MoreRow(title: String, sub: String?, enabled: Boolean = true, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick)
            .then(if (enabled) Modifier else Modifier.alpha(DISABLED_ALPHA))
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

private const val DISABLED_ALPHA = 0.38f

/**
 * Back up now, Restore or Add photos from a file (#217). While it runs: the step and its count ("Backing up… 45 of
 * 120 photos") over a bar, and nothing to tap. After: how it went, until the next run ("Last backup: today 8:46 ·
 * 90 finds, 120 photos · meguri-backup.zip"); a failure in the error colour, with a Try again button. Waits, faded,
 * while one of the others runs.
 */
@Composable
private fun JobRow(
    title: String, idleSub: String, job: DataJob, running: DataRun?, results: Map<DataJob, DataResult>, onClick: () -> Unit,
) {
    val res = LocalContext.current.resources
    val mine = running?.takeIf { it.job == job }
    val result = results[job]
    val enabled = running == null
    Row(
        Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick)
            .then(if (enabled || mine != null) Modifier else Modifier.alpha(DISABLED_ALPHA))
            .heightIn(min = 64.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            when {
                mine != null -> {
                    Text(DataJobText.progressText(res, mine), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    val f = DataJobText.fraction(mine)
                    val bar = Modifier.fillMaxWidth().padding(top = 6.dp)
                    if (f != null) androidx.compose.material3.LinearProgressIndicator(progress = { f }, modifier = bar)
                    else androidx.compose.material3.LinearProgressIndicator(modifier = bar)
                }
                result != null -> Text(
                    DataJobText.resultText(res, result),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (result.ok) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                )
                else -> Text(idleSub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (mine == null && result?.ok == false) {
            androidx.compose.material3.IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.padding(end = 4.dp)) {
                androidx.compose.material3.Icon(
                    androidx.compose.material.icons.Icons.Filled.Refresh,
                    contentDescription = stringResource(R.string.job_try_again),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}
