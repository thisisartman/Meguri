package io.github.thisisartman.collectibles.ui

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
    val auto: AutoBackup,
    val log: FileLog,
    private val db: CollectiblesDb,
) : ViewModel() {
    fun run(block: suspend () -> Unit) { viewModelScope.launch { block() } }

    /** Card codes of the manhole cards loaded on this phone (for #53's picture download). */
    /** Prefecture -> how many items of each category are loaded (Catalogue pages). */
    val catalogue = kotlinx.coroutines.flow.MutableStateFlow<Map<String, Map<ItemType, Int>>>(emptyMap())
    /** Names of the categories the person added (Settings list summary). */
    val customNames = kotlinx.coroutines.flow.MutableStateFlow<List<String>>(emptyList())
    fun refresh() {
        viewModelScope.launch {
            catalogue.value = db.dao().allItems().filter { it.prefecture != null }
                .groupBy { it.prefecture!! }.toSortedMap()
                .mapValues { (_, l) -> l.groupingBy { it.type }.eachCount() }
            customNames.value = db.dao().allCollectionTypes().map { it.name }
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
            val msg = catalogueMessage(context, r)
            scope.launch { snackbar.showSnackbar(msg) }
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
    // Settings as a short list of sections, each its own page (owner's pick B, #71, 2026-11-07). The dialogs and
    // file pickers above stay here, so each section's rows work exactly as before.
    var section by rememberSaveable { mutableStateOf<String?>(null) }
    androidx.activity.compose.BackHandler(enabled = section != null) { section = parentOf(section) }
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
        S_INSTALLED to stringResource(R.string.settings_installed),
        S_ABOUT to stringResource(R.string.more_about),
        S_ALERT_CATS to stringResource(R.string.alerts_categories),
        S_WEEKLY to stringResource(R.string.more_auto),
        S_PHOTOS to stringResource(R.string.photos_kept),
    )
    var whatsNew by remember { mutableStateOf(false) }
    if (whatsNew) WhatsNewDialog(onDismiss = { whatsNew = false })
    LaunchedEffect(Unit) { vm.refresh() }
    val catalogue by vm.catalogue.collectAsState()
    val customNames by vm.customNames.collectAsState()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        // A page of its own, opened from the top-right menu, so it gets a back arrow like the item page
        Row(Modifier.padding(start = 4.dp, top = 8.dp, end = 16.dp, bottom = 8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            androidx.compose.material3.IconButton(onClick = { if (section != null) section = parentOf(section) else onBack() }) {
                androidx.compose.material3.Icon(
                    androidx.compose.material.icons.Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.back),
                )
            }
            Text(section?.let { titles[it] } ?: stringResource(R.string.more_title), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(start = 4.dp))
        }

        when (section) {
            null -> {
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
                    stringResource(
                        R.string.settings_alerts_live,
                        stringResource(if (a.geofenceOn) R.string.alerts_mode_always else if (a.tripOn) R.string.alerts_mode_trip else R.string.alerts_mode_off),
                        radiusText(a.radiusM),
                        ALERT_TYPES.count { it.first in a.types }, ALERT_TYPES.size,
                    ),
                    R.drawable.ic_settings_alerts,
                ) { section = S_ALERTS }
                SectionLine(titles.getValue(S_DATA), autoSub, R.drawable.ic_settings_backup) { section = S_DATA }
                SectionLine(
                    titles.getValue(S_COLLECTIONS),
                    if (customNames.isEmpty()) stringResource(R.string.settings_collections_sub) else customNames.joinToString(", "),
                    R.drawable.ic_settings_categories,
                ) { section = S_COLLECTIONS }
                SectionLine(
                    titles.getValue(S_CATALOGUE),
                    catalogue.keys.joinToString(", ") { prefectureName(it, ja) }.ifEmpty { stringResource(R.string.settings_catalogue_sub) },
                    R.drawable.ic_settings_catalogue,
                ) { section = S_CATALOGUE }
                SectionLine(titles.getValue(S_HELP), stringResource(R.string.settings_help_sub, BuildConfig.VERSION_NAME), R.drawable.ic_settings_help) { section = S_HELP }
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
            S_ALERT_CATS -> AlertCategoriesSection()
            S_LOOK -> {
                // Theme (owner, 2026-11-09): the phone's dark mode, or always light, or always dark; the map follows
                SectionTitle(stringResource(R.string.look_theme))
                val theme = AppLook.theme(context)
                Choice(
                    listOf(R.string.look_theme_phone, R.string.look_theme_light, R.string.look_theme_dark).map { stringResource(it) },
                    AppLook.Theme.entries.indexOf(theme),
                ) { AppLook.setTheme(context, AppLook.Theme.entries[it]) }
                Note(stringResource(R.string.look_theme_sub))
                if (AppLook.available()) {
                    SectionTitle(stringResource(R.string.look_colours))
                    var device by remember { mutableStateOf(AppLook.deviceColours(context)) }
                    Choice(listOf(stringResource(R.string.look_colours_meguri), stringResource(R.string.look_colours_wallpaper)), if (device) 1 else 0) {
                        device = it == 1; AppLook.setDeviceColours(context, device)
                    }
                    Note(stringResource(R.string.look_device_colours_sub))
                }
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
                MoreRow(
                    stringResource(R.string.more_backup),
                    stringResource(if (vm.auto.includePhotos) R.string.backup_now_photos else R.string.backup_now_plain),
                ) {
                    val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
                    withPhotos = vm.auto.includePhotos
                    saveBackup.launch(if (withPhotos) "collectibles-backup-photos-$stamp.zip" else "collectibles-backup-$stamp.zip")
                }
                SplitSwitchRow(stringResource(R.string.more_auto), autoSub, autoOn, onOpen = { section = S_WEEKLY }) { on ->
                    if (on) pickFolder.launch(STARTING_FOLDER) else { vm.auto.disable(); autoTick++ }
                }
                MoreRow(stringResource(R.string.more_restore), stringResource(R.string.more_restore_sub)) {
                    openBackup.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
                }
                if (relinkPending) {
                    MoreRow(stringResource(R.string.more_relink), stringResource(R.string.more_relink_sub)) {
                        askPhotos.launch(BackupRestore.photoPermission())
                    }
                }
                SectionTitle(stringResource(R.string.photos_section))
                PhotoHintRow()
                val photoSummary by vm.photos.collectAsState()
                LaunchedEffect(Unit) { vm.loadPhotos(context) }
                MoreRow(
                    stringResource(R.string.photos_kept),
                    photoSummary?.let { stringResource(R.string.photos_kept_sub, it.kept, android.text.format.Formatter.formatShortFileSize(context, it.bytes)) },
                ) { section = S_PHOTOS }
            }
            S_WEEKLY -> {
                ToggleRow(stringResource(R.string.more_auto), stringResource(R.string.weekly_explain), autoOn) { on ->
                    if (on) pickFolder.launch(STARTING_FOLDER) else { vm.auto.disable(); autoTick++ }
                }
                if (autoOn) {
                    val folderName = remember(autoTick) {
                        vm.auto.folder?.let { androidx.documentfile.provider.DocumentFile.fromTreeUri(context, it)?.name }
                    }
                    MoreRow(stringResource(R.string.weekly_folder), folderName ?: "?") { pickFolder.launch(STARTING_FOLDER) }
                    var photos by remember { mutableStateOf(vm.auto.includePhotos) }
                    ToggleRow(stringResource(R.string.weekly_photos), stringResource(R.string.weekly_photos_sub), photos) {
                        photos = it; vm.auto.includePhotos = it; autoTick++
                    }
                    SectionTitle(stringResource(R.string.weekly_keep))
                    var keep by remember { mutableStateOf(vm.auto.keep) }
                    Choice(AutoBackup.KEEP_CHOICES.map { stringResource(R.string.weekly_keep_n, it) }, AutoBackup.KEEP_CHOICES.indexOf(keep)) {
                        keep = AutoBackup.KEEP_CHOICES[it]; vm.auto.keep = keep
                    }
                    Note(stringResource(R.string.weekly_keep_sub))
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
                MoreRow(stringResource(R.string.settings_installed), pluralStringResource(R.plurals.settings_installed_sub, prefCount, prefCount)) { section = S_INSTALLED }
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
                    openCatalog.launch(arrayOf("text/csv", "text/comma-separated-values", "text/plain", "application/octet-stream", "*/*"))
                }
            }
            S_HELP -> {
                MoreRow(stringResource(R.string.help_whats_new), BuildConfig.VERSION_NAME) { whatsNew = true }
                MoreRow(stringResource(R.string.more_logs), stringResource(R.string.help_logs_sub), onLogs)
                MoreRow(stringResource(R.string.more_about), stringResource(R.string.help_about_sub)) { section = S_ABOUT }
            }
            S_ABOUT -> {
                Text(
                    stringResource(R.string.more_about_sub, BuildConfig.VERSION_NAME),
                    Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium,
                )
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
        }
    }
}

/** Inner pages go back to their section; sections go back to the list. */
private fun parentOf(section: String?): String? = when (section) {
    S_INSTALLED -> S_CATALOGUE
    S_ABOUT -> S_HELP
    S_ALERT_CATS -> S_ALERTS
    S_WEEKLY, S_PHOTOS -> S_DATA
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
private const val S_DATA = "data"
private const val S_LOOK = "look"
private const val S_CATALOGUE = "catalogue"
private const val S_HELP = "help"
private const val S_INSTALLED = "installed"
private const val S_ABOUT = "about"
private const val S_ALERT_CATS = "alert_categories"
private const val S_WEEKLY = "weekly"
private const val S_PHOTOS = "photos"

/** One section on the Settings list: its name, what is set now, and an arrow. */
@Composable
private fun SectionLine(title: String, sub: String, icon: Int, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 64.dp).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        androidx.compose.material3.Icon(
            androidx.compose.ui.res.painterResource(icon), contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        androidx.compose.foundation.layout.Spacer(Modifier.width(16.dp))
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
private fun MoreRow(title: String, sub: String?, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}
