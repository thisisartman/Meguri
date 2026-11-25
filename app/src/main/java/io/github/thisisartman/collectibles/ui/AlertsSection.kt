package io.github.thisisartman.collectibles.ui

import androidx.compose.ui.draw.alpha
import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.clickable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.material3.Slider
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.thisisartman.collectibles.R
import com.google.android.gms.location.GeofenceStatusCodes
import io.github.thisisartman.collectibles.alerts.AlertNotifier
import io.github.thisisartman.collectibles.alerts.AlertSettings
import io.github.thisisartman.collectibles.alerts.Geofences
import io.github.thisisartman.collectibles.alerts.TripModeService
import io.github.thisisartman.collectibles.data.ItemType
import io.github.thisisartman.collectibles.location.LocationSource
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class AlertsViewModel @Inject constructor(
    val settings: AlertSettings,
    val geofences: Geofences,
    val notifier: AlertNotifier,
    val location: LocationSource,
    private val db: io.github.thisisartman.collectibles.data.CollectiblesDb,
) : ViewModel() {
    /** Types with something on the phone; the others aren't offered for alerts (#138). */
    suspend fun typesPresent(): Set<io.github.thisisartman.collectibles.data.ItemType> = db.dao().typesPresent().toSet()
}

/** Permissions Trip mode and alerts need first: notifications (Android 13+) and location while in use. */
fun foregroundAlertPermissions(): Array<String> = buildList {
    add(Manifest.permission.ACCESS_FINE_LOCATION)
    add(Manifest.permission.ACCESS_COARSE_LOCATION)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
}.toTypedArray()

fun startTrip(context: Context, vm: AlertsViewModel): Boolean {
    if (!vm.location.hasPermission()) return false
    TripModeService.start(context, vm.settings)
    return true
}

/** Alerts in Settings: when (Off, Travelling, Always), how close, which categories, quiet hours, phone setup. */
@Composable
fun AlertsSection(onCategories: () -> Unit, vm: AlertsViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val s = vm.settings
    var tick by remember { mutableIntStateOf(0) }
    DisposableEffect(Unit) {
        val l = s.listen { tick++ }
        onDispose { s.stopListening(l) }
    }
    // Coming back from the system settings page: permissions may have changed
    LifecycleResumeEffect(Unit) {
        tick++
        // A setting may have been fixed on the way back (location accuracy, "Allow all the time")
        if (s.geofenceOn) vm.geofences.refreshInBackground()
        onPauseOrDispose { }
    }

    var askBackground by remember { mutableStateOf(false) }
    var tripRefused by remember { mutableStateOf(false) }
    var noPlayTapped by remember { mutableStateOf(false) }

    val tripPerms = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        tripRefused = !startTrip(context, vm)
        tick++
    }
    val backgroundPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            s.geofenceOn = true
            vm.geofences.refreshInBackground()
        }
        tick++
    }
    val bgFirstPerms = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (vm.location.hasPermission()) askBackground = true
        tick++
    }

    val tripOn = remember(tick) { s.tripOn }
    val geoOn = remember(tick) { s.geofenceOn }
    val geoReady = remember(tick) { vm.geofences.ready() }
    val playOk = remember(tick) { vm.geofences.playServicesOk() }
    val notificationsOk = remember(tick) { vm.notifier.canPost() }
    val geoError = remember(tick) { s.geofenceError }
    val until = remember(tick) { Dates.time(s.tripUntilMs) }

    // Design C (owner, 2026-11-09): one mode, one distance, categories one tap in
    val mode = when { geoOn -> 2; tripOn -> 1; else -> 0 }
    SectionTitle(stringResource(R.string.alerts_when))
    Choice3(
        listOf(stringResource(R.string.alerts_mode_off), stringResource(R.string.alerts_mode_trip), stringResource(R.string.alerts_mode_always)),
        mode,
    ) { pick ->
        when (pick) {
            0 -> {
                TripModeService.stop(context)
                s.geofenceOn = false; vm.geofences.refreshInBackground()
            }
            1 -> {
                if (geoOn) { s.geofenceOn = false; vm.geofences.refreshInBackground() }
                tripPerms.launch(foregroundAlertPermissions())
            }
            2 -> {
                // A running trip stays on: it carries a planned route's "get off next" alerts
                when {
                    !playOk -> noPlayTapped = true
                    !vm.location.hasPermission() -> bgFirstPerms.launch(foregroundAlertPermissions())
                    !vm.geofences.hasBackgroundLocation() -> askBackground = true
                    else -> { s.geofenceOn = true; vm.geofences.refreshInBackground() }
                }
            }
        }
        tick++
    }
    // What the chosen mode is doing now, or what to fix (tap opens the place to fix it)
    val status: Pair<String, (() -> Unit)?> = when {
        tripRefused && !vm.location.hasPermission() -> stringResource(R.string.trip_needs_location) to { tripPerms.launch(foregroundAlertPermissions()) }
        mode == 2 && !geoReady -> stringResource(R.string.bg_sub_needs) to { askBackground = true }
        mode == 2 && geoError == GeofenceStatusCodes.GEOFENCE_NOT_AVAILABLE -> stringResource(R.string.bg_sub_accuracy) to { openLocationSettings(context); vm.geofences.refreshInBackground() }
        mode == 2 && geoError != 0 -> stringResource(R.string.bg_sub_failed) to { vm.geofences.refreshInBackground(); tick++ }
        mode == 2 && tripOn -> (stringResource(R.string.bg_sub_on) + " " + stringResource(R.string.trip_sub_on, until)) to null
        mode == 2 -> stringResource(R.string.bg_sub_on) to null
        noPlayTapped && !playOk -> stringResource(R.string.bg_sub_no_play) to null
        mode == 1 -> stringResource(R.string.trip_sub_on, until) to null
        !playOk -> stringResource(R.string.bg_sub_no_play) to null
        else -> stringResource(R.string.alerts_mode_sub) to null
    }
    Text(
        status.first,
        Modifier.fillMaxWidth().let { m -> status.second?.let { m.clickable(onClick = it) } ?: m }.padding(horizontal = 16.dp, vertical = 8.dp),
        style = MaterialTheme.typography.bodySmall,
        color = if (status.second != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if ((tripOn || geoOn) && !notificationsOk) {
        Text(
            stringResource(R.string.notif_off),
            Modifier.fillMaxWidth().clickable { openAppNotificationSettings(context) }.padding(horizontal = 16.dp, vertical = 10.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

    // With alerts Off, what they would use is dimmed (#164); it can still be set up before switching them on
    androidx.compose.foundation.layout.Column(Modifier.alpha(if (mode == 0) 0.45f else 1f)) {
    val main = remember(tick) { s.radiusM }
    // A slider with three stops (owner, 2026-11-09: "maybe a slider would help")
    // Owner, 2026-11-09: one slider, and a menu above it says what it sets: every category, or one of them
    var target by remember { mutableStateOf<ItemType?>(null) }
    val own = remember(tick, target) { target?.let { s.ownRadius(it) } }
    val owns = remember(tick) { ALERT_TYPES.mapNotNull { (t, label) -> s.ownRadius(t)?.let { Triple(t, label, it) } } }
    DistanceSlider(
        value = own ?: main,
        target = target,
        targetIsOwn = own != null,
        owns = owns,
        onTarget = { target = it },
        onReset = { target?.let { s.setRadiusFor(it, null) }; vm.geofences.refreshInBackground(); tick++ },
    ) { r ->
        val t = target
        if (t == null) s.radiusM = r else s.setRadiusFor(t, r)
        vm.geofences.refreshInBackground(); tick++
    }

    val types = remember(tick) { s.types }
    val on = ALERT_TYPES.filter { it.first in types }
    MoreLink(
        stringResource(R.string.alerts_categories),
        stringResource(R.string.alerts_categories_sub, on.size, ALERT_TYPES.size) +
            if (on.isEmpty()) "" else " · " + on.joinToString(", ") { context.getString(it.second) },
        onClick = onCategories,
    )

    val quiet = remember(tick) { s.quietOn }
    SwitchRow(stringResource(R.string.quiet_title), if (quiet) stringResource(R.string.quiet_sub) else null, quiet, onChange = { s.quietOn = it; tick++ })
    }

    MoreLink(
        stringResource(R.string.setup_title),
        stringResource(if (Build.MANUFACTURER.equals("samsung", ignoreCase = true)) R.string.setup_sub_samsung else R.string.setup_sub),
    ) { openAppSettings(context) }

    if (askBackground) {
        AlertDialog(
            onDismissRequest = { askBackground = false },
            title = { Text(stringResource(R.string.bg_dialog_title)) },
            text = { Text(stringResource(R.string.bg_dialog_text)) },
            confirmButton = {
                TextButton(onClick = {
                    askBackground = false
                    backgroundPerm.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                }) { Text(stringResource(R.string.bg_dialog_go)) }
            },
            dismissButton = { TextButton(onClick = { askBackground = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/** "Suggest new photos" (M7). Lives under Backup and data with the other photo settings (owner, 2026-11-09). */
@Composable
fun PhotoHintRow(vm: AlertsViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val s = vm.settings
    var tick by remember { mutableIntStateOf(0) }
    val photoPerms = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        val ok = io.github.thisisartman.collectibles.data.SharedPhotoReader.placePermissions().all { granted[it] == true || context.checkSelfPermission(it) == android.content.pm.PackageManager.PERMISSION_GRANTED }
        s.photoHintsOn = ok
        if (ok) { s.photoHintsSeenSec = System.currentTimeMillis() / 1000; io.github.thisisartman.collectibles.photo.NewPhotoWatcher.schedule(context) }
        tick++
    }
    val photoHints = remember(tick) { s.photoHintsOn }
    SwitchRow(stringResource(R.string.photo_hint_setting), stringResource(R.string.photo_hint_setting_sub), photoHints, onChange = { on ->
        if (on) photoPerms.launch(io.github.thisisartman.collectibles.photo.NewPhotoWatcher.permissions())
        else { s.photoHintsOn = false; io.github.thisisartman.collectibles.photo.NewPhotoWatcher.cancel(context) }
        tick++
    })
}

/** The categories that can alert, in the order they show. */
val ALERT_TYPES = listOf(
    ItemType.EKI_STAMP to R.string.type_eki_plural, ItemType.EKITAG to R.string.type_ekitag, ItemType.MANHOLE_CARD to R.string.type_manhole_plural, ItemType.GOSHUIN to R.string.type_goshuin,
    ItemType.DAM_CARD to R.string.type_dam_plural, ItemType.MUSEUM_STAMP to R.string.type_museum_plural, ItemType.ROADSIDE_STATION to R.string.type_roadside_plural,
)

/** Inside Alerts > Categories: each one on or off, and "Same" (the main distance) or its own. */
@Composable
fun AlertCategoriesSection(vm: AlertsViewModel = hiltViewModel()) {
    val s = vm.settings
    var tick by remember { mutableIntStateOf(0) }
    val types = remember(tick) { s.types }
    val main = remember(tick) { s.radiusM }
    // Only categories with something on the phone, plus any switched on, so it can be switched off (#138)
    val present by androidx.compose.runtime.produceState<Set<ItemType>?>(null) { value = runCatching { vm.typesPresent() }.getOrNull() }
    ALERT_TYPES.filter { (t, _) -> present?.let { t in it || t in types } ?: true }.forEach { (t, label) ->
        val alerts = t in types
        Row(
            Modifier.fillMaxWidth()
                .toggleable(value = alerts, role = Role.Switch, onValueChange = {
                    s.types = if (alerts) types - t else types + t; vm.geofences.refreshInBackground(); tick++
                })
                .heightIn(min = 56.dp).padding(start = 16.dp, end = 16.dp, top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TypeDot(t)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(label), style = MaterialTheme.typography.bodyLarge)
                if (t == ItemType.EKI_STAMP || t == ItemType.EKITAG) Text(stringResource(R.string.alerts_station_short), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = alerts, onCheckedChange = null)
        }
        HorizontalDivider(Modifier.padding(top = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
    }
    Text(
        stringResource(R.string.alert_station_rule),
        Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** "200 m", "500 m", "1 km": the alert distances as the owner wrote them. */
fun radiusText(m: Int): String = if (m % 1000 == 0) "${m / 1000} km" else "$m m"

/**
 * How close: three stops, the chosen one written on the right. The menu under the title picks what the slider sets:
 * every category (the main distance) or one category, which then has its own until "Use the main distance".
 */
@Composable
private fun DistanceSlider(
    value: Int, target: ItemType?, targetIsOwn: Boolean, owns: List<Triple<ItemType, Int, Int>>,
    onTarget: (ItemType?) -> Unit, onReset: () -> Unit, onPick: (Int) -> Unit,
) {
    val choices = AlertSettings.RADIUS_CHOICES
    var pos by remember(value, target) { mutableStateOf(choices.indexOf(value).coerceAtLeast(0).toFloat()) }
    // 16 dp on every side of the section; 8 dp between its parts
    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.alerts_how_close), style = MaterialTheme.typography.bodyLarge)
                // The category pill (shared with Nearby); a category with its own distance shows it in the menu
                CategoryPill(
                    selected = target, types = ALERT_TYPES.map { it.first },
                    label = { t -> stringResource(ALERT_TYPES.first { it.first == t }.second) },
                    onSelect = onTarget,
                    modifier = Modifier.padding(top = 4.dp),
                    trailing = { t -> owns.firstOrNull { it.first == t }?.third?.let { radiusText(it) } },
                )
            }
            Text(radiusText(choices[pos.toInt()]), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        }
        Slider(
            value = pos, onValueChange = { pos = Math.round(it).toFloat() },
            onValueChangeFinished = { onPick(choices[pos.toInt()]) },
            valueRange = 0f..(choices.size - 1).toFloat(), steps = choices.size - 2,
            // The 1 km end sits in the phone's back-swipe edge: dragging it went Back on the Poco
            modifier = Modifier.systemGestureExclusion(),
        )
        Row(Modifier.fillMaxWidth()) {
            choices.forEachIndexed { i, r ->
                Text(
                    radiusText(r), Modifier.weight(1f),
                    textAlign = when (i) { 0 -> TextAlign.Start; choices.lastIndex -> TextAlign.End; else -> TextAlign.Center },
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (target != null) {
            Text(
                stringResource(if (targetIsOwn) R.string.alerts_target_own else R.string.alerts_target_same),
                Modifier.padding(top = 6.dp),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (targetIsOwn) TextButton(onClick = onReset, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.alerts_use_main)) }
        }
        // Owner, 2026-11-09: the categories with their own distance, one per line under the slider, folding away
        if (owns.isNotEmpty()) {
            var open by rememberSaveable { mutableStateOf(true) }
            Row(
                Modifier.fillMaxWidth().clickable { open = !open }.heightIn(min = 48.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.alerts_own_list, owns.size),
                    Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Icon(
                    if (open) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = stringResource(if (open) R.string.alerts_own_hide else R.string.alerts_own_show),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            AnimatedVisibility(open) {
                Column {
                    owns.forEach { (t, label, m) ->
                        Row(
                            Modifier.fillMaxWidth().clickable { onTarget(t) }.heightIn(min = 40.dp).padding(start = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TypeDot(t)
                            Spacer(Modifier.width(10.dp))
                            Text(stringResource(label), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            Text(
                                radiusText(m), style = MaterialTheme.typography.bodyMedium,
                                color = if (t == target) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

/** Off / Travelling / Always. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun Choice3(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        labels.forEachIndexed { i, l ->
            SegmentedButton(
                selected = i == selected, onClick = { onSelect(i) },
                shape = SegmentedButtonDefaults.itemShape(i, labels.size),
            ) { Text(l, maxLines = 1) }
        }
    }
}

@Composable
private fun SwitchRow(title: String, sub: String?, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    // The whole row is the switch, so the label is read with it and the target is the full width
    Row(
        Modifier.fillMaxWidth()
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChange)
            .heightIn(min = 56.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun MoreLink(title: String, sub: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

private fun openLocationSettings(context: Context) {
    context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

private fun openAppSettings(context: Context) {
    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

private fun openAppNotificationSettings(context: Context) {
    context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
