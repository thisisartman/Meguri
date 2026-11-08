package io.github.thisisartman.collectibles.ui

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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.clickable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
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
) : ViewModel()

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

/** "Alerts" in More (M4): Trip mode, background alerts, distance, types, quiet hours, phone setup. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AlertsSection(vm: AlertsViewModel = hiltViewModel()) {
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
    val until = remember(tick) { SimpleDateFormat("HH:mm", Locale.US).format(Date(s.tripUntilMs)) }

    SectionTitle(stringResource(R.string.alerts_title))

    SwitchRow(
        title = stringResource(R.string.trip_title),
        sub = when {
            tripRefused && !vm.location.hasPermission() -> stringResource(R.string.trip_needs_location)
            tripOn -> stringResource(R.string.trip_sub_on, until)
            else -> stringResource(R.string.trip_sub_off)
        },
        checked = tripOn,
        onChange = { on ->
            if (on) tripPerms.launch(foregroundAlertPermissions()) else TripModeService.stop(context)
            tick++
        },
    )

    SwitchRow(
        title = stringResource(R.string.bg_title),
        sub = when {
            !playOk -> stringResource(R.string.bg_sub_no_play)
            geoOn && !geoReady -> stringResource(R.string.bg_sub_needs)
            geoOn && geoError == GeofenceStatusCodes.GEOFENCE_NOT_AVAILABLE -> stringResource(R.string.bg_sub_accuracy)
            geoOn && geoError != 0 -> stringResource(R.string.bg_sub_failed)
            geoOn -> stringResource(R.string.bg_sub_on)
            else -> stringResource(R.string.bg_sub_off)
        },
        checked = geoOn && geoReady && geoError == 0,
        enabled = playOk,
        onChange = { on ->
            if (on && geoOn && geoReady && geoError != 0) {
                // Tapping a row that says what to fix opens the place to fix it, then tries again on return
                if (geoError == GeofenceStatusCodes.GEOFENCE_NOT_AVAILABLE) openLocationSettings(context)
                vm.geofences.refreshInBackground()
            } else if (!on) {
                s.geofenceOn = false
                vm.geofences.refreshInBackground()
            } else if (!vm.location.hasPermission()) {
                bgFirstPerms.launch(foregroundAlertPermissions())
            } else if (!vm.geofences.hasBackgroundLocation()) {
                askBackground = true
            } else {
                s.geofenceOn = true
                vm.geofences.refreshInBackground()
            }
            tick++
        },
    )

    if ((tripOn || geoOn) && !notificationsOk) {
        Text(
            stringResource(R.string.notif_off),
            Modifier.fillMaxWidth().clickable { openAppNotificationSettings(context) }.padding(horizontal = 16.dp, vertical = 10.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }

    // A distance per kind (#73): stations close together on a line want less than a dam out of town
    Text(stringResource(R.string.alert_distance), Modifier.padding(start = 16.dp, top = 12.dp), style = MaterialTheme.typography.bodyLarge)
    listOf(ItemType.EKI_STAMP to R.string.type_eki_plural, ItemType.MANHOLE_CARD to R.string.type_manhole_plural, ItemType.GOSHUIN to R.string.type_goshuin,
        ItemType.DAM_CARD to R.string.type_dam_plural, ItemType.MUSEUM_STAMP to R.string.type_museum_plural)
        .forEach { (t, label) ->
            val radius = remember(tick) { s.radiusFor(t) }
            Row(Modifier.padding(start = 16.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                TypeDot(t)
                Spacer(Modifier.width(6.dp))
                Text(stringResource(label), style = MaterialTheme.typography.bodyMedium)
            }
            FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AlertSettings.RADIUS_CHOICES.forEach { r ->
                    InkFilterChip(
                        selected = radius == r,
                        onClick = { s.setRadiusFor(t, r); vm.geofences.refreshInBackground(); tick++ },
                        label = { Text(distanceText(r.toDouble()).orEmpty()) },
                        modifier = Modifier.heightIn(min = 48.dp),
                    )
                }
            }
        }

    // Owner's pick B (#73): stations alert only where you plan to get off
    Text(
        stringResource(R.string.alert_station_rule),
        Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(stringResource(R.string.alert_for), Modifier.padding(start = 16.dp, top = 8.dp), style = MaterialTheme.typography.bodyLarge)
    val types = remember(tick) { s.types }
    FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(ItemType.MANHOLE_CARD to R.string.type_manhole_plural, ItemType.GOSHUIN to R.string.type_goshuin, ItemType.EKI_STAMP to R.string.type_eki_plural,
            ItemType.DAM_CARD to R.string.type_dam_plural, ItemType.MUSEUM_STAMP to R.string.type_museum_plural)
            .forEach { (t, label) ->
                InkFilterChip(
                    selected = t in types,
                    onClick = { s.types = if (t in types) types - t else types + t; vm.geofences.refreshInBackground(); tick++ },
                    label = { Text(stringResource(label)) },
                    leadingIcon = { TypeDot(t) },
                )
            }
    }

    val quiet = remember(tick) { s.quietOn }
    SwitchRow(stringResource(R.string.quiet_title), stringResource(R.string.quiet_sub), quiet, onChange = { s.quietOn = it; tick++ })

    // New-photo hint (M7, owner 2026-11-08: yes, with a way to turn it off)
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
