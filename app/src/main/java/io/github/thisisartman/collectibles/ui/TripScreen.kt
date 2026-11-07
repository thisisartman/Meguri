package io.github.thisisartman.collectibles.ui

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import io.github.thisisartman.collectibles.R
import io.github.thisisartman.collectibles.alerts.AlertSettings
import io.github.thisisartman.collectibles.alerts.TripModeService
import io.github.thisisartman.collectibles.data.ItemRow
import io.github.thisisartman.collectibles.data.Leg
import io.github.thisisartman.collectibles.data.RailGraph
import io.github.thisisartman.collectibles.data.SavedTrip
import io.github.thisisartman.collectibles.data.TripPlan
import io.github.thisisartman.collectibles.data.TripRules
import io.github.thisisartman.collectibles.data.TripStop
import io.github.thisisartman.collectibles.data.passed
import io.github.thisisartman.collectibles.location.distanceM
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Route trips (#46, #60, #62): from one station to another on the bundled lines, every station the train passes,
 * and what is within 2 km of each. Station stamps count only where you say you will get off. "Start trip" is Trip
 * mode with this route; "Already went" ticks what you got along it; "Share" sends the plan as text (#69).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripScreen(vm: MainViewModel, nav: NavHostController, snackbar: SnackbarHostState, onBack: () -> Unit, onAlready: () -> Unit) {
    val ja = isJapanese()
    val context = LocalContext.current
    val alerts: AlertsViewModel = hiltViewModel()
    val rows by vm.rows.collectAsStateWithLifecycle()
    val graph by vm.railGraph.collectAsStateWithLifecycle()
    val lines by vm.lines.collectAsStateWithLifecycle()
    val here by vm.here.collectAsStateWithLifecycle()
    val saved = remember { vm.currentTrip() }
    var from by rememberSaveable { mutableStateOf(saved?.from) }
    var to by rememberSaveable { mutableStateOf(saved?.to) }
    var localOnly by rememberSaveable { mutableStateOf(saved?.localOnly ?: false) }
    var getOff by rememberSaveable { mutableStateOf(saved?.getOff?.toList() ?: emptyList()) }
    var picking by remember { mutableStateOf<Boolean?>(null) } // true: choosing From, false: To
    var linkOpen by remember { mutableStateOf(false) }
    var tick by remember { mutableIntStateOf(0) }
    val tripOn = remember(tick) { alerts.settings.tripOn }
    val until = remember(tick) { SimpleDateFormat("HH:mm", Locale.US).format(Date(alerts.settings.tripUntilMs)) }
    val scope = rememberCoroutineScope()
    val byId = remember(rows) { rows.associateBy { it.item.id } }
    val lineName = remember(lines, ja) { lines.associate { it.id to (if (ja) it.nameJa else it.nameRomaji ?: it.nameJa) } }

    val fast = remember(graph, from, to) { from?.let { f -> to?.let { t -> graph.route(f, t) } } }
    val local = remember(graph, from, to) { from?.let { f -> to?.let { t -> graph.route(f, t, avoid = setOf(RailGraph.SHINKANSEN)) } } }
    val both = fast != null && local != null && fast != local
    val legs: List<Leg>? = if (localOnly && local != null) local else fast ?: local
    val plan: TripPlan? = remember(legs, rows, getOff) { legs?.let { TripRules.plan(it, rows, getOff.toSet()) } }

    // A new pair of ends: get off at both, by default
    fun setEnds(f: String?, t: String?) {
        from = f; to = t
        getOff = listOfNotNull(f, t)
    }

    // Today's plan is kept for Trip mode and "Already went", and forgets itself at midnight
    LaunchedEffect(from, to, localOnly, getOff, legs) {
        val f = from; val t = to; val l = legs
        if (f != null && t != null && l != null) {
            vm.saveTrip(SavedTrip(f, t, localOnly && local != null, getOff.toSet(), l.passed().map { it.first }, AlertSettings.endOfDay())); alerts.geofences.refreshInBackground()
        }
    }

    val tripPerms = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        startTrip(context, alerts); tick++
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            windowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
            title = { Text(stringResource(R.string.trip_screen)) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back)) } },
            actions = {
                if (from != null || to != null) IconButton(onClick = { setEnds(null, null); localOnly = false; vm.clearTrip(); alerts.geofences.refreshInBackground() }) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.trip_clear))
                }
                IconButton(onClick = { nav.navigate("trip-report") }) {
                    Icon(painterResource(R.drawable.ic_image), contentDescription = stringResource(R.string.trip_report))
                }
                IconButton(onClick = { linkOpen = true }) { Icon(painterResource(R.drawable.ic_link), contentDescription = stringResource(R.string.trip_link)) }
                IconButton(enabled = plan != null, onClick = {
                    val p = plan ?: return@IconButton
                    val l = legs ?: return@IconButton
                    shareTrip(context, p, l, byId, lineName, ja)
                }) { Icon(Icons.Filled.Share, contentDescription = stringResource(R.string.trip_share)) }
            },
        )
        // From and To, with a swap
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(R.drawable.ic_route), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = 8.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                StationField(stringResource(R.string.trip_from), from?.let { byId[it] }, ja) { picking = true }
                StationField(stringResource(R.string.trip_to), to?.let { byId[it] }, ja) { picking = false }
            }
            IconButton(enabled = from != null || to != null, onClick = { val f = from; from = to; to = f }) {
                Icon(painterResource(R.drawable.ic_swap_vert), contentDescription = stringResource(R.string.trip_swap))
            }
        }
        if (both) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !localOnly, onClick = { localOnly = false }, label = { Text(stringResource(R.string.trip_shinkansen)) },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_type_station), contentDescription = null, modifier = Modifier.size(18.dp)) })
                FilterChip(selected = localOnly, onClick = { localOnly = true }, label = { Text(stringResource(R.string.trip_local)) },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_type_station), contentDescription = null, modifier = Modifier.size(18.dp)) })
            }
        }
        when {
            from == null || to == null -> Hint(stringResource(R.string.trip_empty))
            plan == null -> Hint(stringResource(R.string.trip_no_route))
            else -> {
                val p = plan
                val routePts = remember(p) { p.stops.mapNotNull { s -> byId[s.stationId]?.item?.let { i -> i.lat?.let { la -> i.lng?.let { lo -> la to lo } } } } }
                val pins = remember(p) { p.stops.flatMap { s -> s.things.mapNotNull { it.row.toPin() } } }
                MeguriMap(
                    pins = pins,
                    here = here.fix?.let { it.lat to it.lng },
                    route = routePts,
                    onPinTap = { nav.openItem(it) },
                    modifier = Modifier.fillMaxWidth().height(200.dp).padding(top = 4.dp),
                )
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.trip_summary, p.stops.size, p.toCollect), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                    OutlinedButton(onClick = onAlready, contentPadding = PaddingValues(horizontal = 12.dp), modifier = Modifier.heightIn(min = 48.dp)) {
                        Icon(painterResource(R.drawable.ic_history), contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.trip_already))
                    }
                }
                LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                    items(p.stops, key = { it.stationId }) { s ->
                        StopRow(
                            stop = s, station = byId[s.stationId], ja = ja,
                            first = s == p.stops.first(), last = s == p.stops.last(),
                            changeName = s.changeTo?.let { lineName[it] },
                            onToggle = { getOff = if (s.getOff) getOff - s.stationId else getOff + s.stationId },
                            onOpen = { nav.openItem(it) },
                        )
                    }
                }
                HorizontalDivider()
                if (tripOn) Text(stringResource(R.string.trip_on_note, until), Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(
                    onClick = {
                        if (tripOn) TripModeService.stop(context)
                        else if (!startTrip(context, alerts)) tripPerms.launch(foregroundAlertPermissions())
                        tick++
                    },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp).heightIn(min = 48.dp),
                ) {
                    Icon(painterResource(if (tripOn) R.drawable.ic_stop else R.drawable.ic_type_station), contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(if (tripOn) R.string.trip_stop_trip else R.string.trip_start))
                }
            }
        }
    }

    picking?.let { choosingFrom ->
        StationPicker(
            stations = remember(rows, graph) { rows.filter { graph.has(it.item.id) } },
            here = here.fix?.let { it.lat to it.lng }, ja = ja,
            onDismiss = { picking = null },
            // Picking the other end's station swaps the two, rather than a trip from a station to itself
            onPick = { id ->
                when {
                    choosingFrom && id == to -> setEnds(id, from)
                    !choosingFrom && id == from -> setEnds(to, id)
                    choosingFrom -> setEnds(id, to)
                    else -> setEnds(from, id)
                }
                picking = null
            },
        )
    }
    if (linkOpen) {
        val farText = stringResource(R.string.trip_link_far)
        val modeText = stringResource(R.string.trip_link_mode)
        LinkDialog(
            read = { vm.readLink(it, ja) },
            onDismiss = { linkOpen = false },
            onEnds = { ends ->
                linkOpen = false
                localOnly = false
                setEnds(ends.from?.first ?: from, ends.to?.first ?: to)
                // Say when an end of the link is off the bundled lines, rather than snapping it quietly
                val far = listOfNotNull(ends.from, ends.to).filter { it.second > FAR_M }
                val notes = far.map { (id, m) -> farText.format(byId[id]?.item?.displayName(ja) ?: id, distanceLabel(context, m)) } +
                    listOfNotNull(ends.mode?.takeIf { it != "transit" }?.let { modeText.format(it) })
                if (notes.isNotEmpty()) scope.launch { snackbar.showSnackbar(notes.joinToString("\n"), withDismissAction = true, duration = androidx.compose.material3.SnackbarDuration.Long) }
            },
        )
    }
}

/** A link end further than this from its station is "off the bundled lines". */
private const val FAR_M = 3_000.0

@Composable
private fun Hint(text: String) {
    Text(text, Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun StationField(label: String, station: ItemRow?, ja: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(56.dp))
            Text(
                station?.item?.displayName(ja) ?: stringResource(R.string.trip_pick_station),
                style = MaterialTheme.typography.bodyLarge,
                color = if (station == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun StopRow(
    stop: TripStop, station: ItemRow?, ja: Boolean, first: Boolean, last: Boolean, changeName: String?,
    onToggle: () -> Unit, onOpen: (String) -> Unit,
) {
    val rail = MaterialTheme.colorScheme.primary
    // Intrinsic height, so the line between the dots fills the row (it was missing in the first review)
    Row(Modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Min).heightIn(min = 56.dp).padding(start = 16.dp, end = 8.dp)) {
        // The line: a dot per station, larger where you get off
        Box(Modifier.width(20.dp).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
            Surface(Modifier.padding(top = 4.dp).width(3.dp).fillMaxHeight(), color = rail.copy(alpha = if (last) 0f else 0.5f)) {}
            Surface(
                Modifier.padding(top = 14.dp).size(if (stop.getOff) 14.dp else 10.dp),
                shape = CircleShape,
                color = if (stop.getOff) rail else MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(2.dp, rail),
            ) {}
        }
        Column(Modifier.weight(1f).padding(start = 8.dp, top = 8.dp, bottom = 8.dp)) {
            // The station's own stamp is part of its line, not a second row with the same name (review 2026-11-07)
            val own = stop.things.firstOrNull { it.row.item.id == stop.stationId }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).then(if (own != null) Modifier.clickable(role = Role.Button) { onOpen(stop.stationId) } else Modifier)) {
                    Text(
                        station?.item?.displayName(ja) ?: stop.stationId,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = if (first || last || stop.getOff) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                    if (own != null) Row(verticalAlignment = Alignment.CenterVertically) {
                        val sub = buildList {
                            add(kindName(own.row.item))
                            if (own.row.collected) add(stringResource(R.string.state_collected))
                            else if (!own.counts) add(stringResource(R.string.trip_if_off))
                        }.joinToString(" · ")
                        if (own.row.collected) Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp).padding(end = 2.dp))
                        Text(sub, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                val desc = stringResource(R.string.trip_get_off_at, station?.item?.displayName(ja) ?: "")
                FilterChip(
                    selected = stop.getOff,
                    onClick = onToggle,
                    label = { Text(stringResource(R.string.trip_get_off)) },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_walk), contentDescription = null, modifier = Modifier.size(18.dp)) },
                    modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = desc },
                )
            }
            if (changeName != null) Text(stringResource(R.string.trip_change_to, changeName), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            stop.things.filter { it !== own }.forEach { t ->
                val r = t.row
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 40.dp).clickable(role = Role.Button) { onOpen(r.item.id) }.alpha(if (t.counts || r.collected) 1f else 0.6f),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Mark(r, 24.dp)
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(r.item.displayName(ja), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val sub = buildList {
                            add(kindName(r.item))
                            if (t.meters >= 25) add(distanceLabel(LocalContext.current, t.meters))
                            if (!t.counts && !r.collected) add(stringResource(R.string.trip_if_off))
                        }.joinToString(" · ")
                        Text(sub, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (r.collected) Icon(Icons.Filled.Check, contentDescription = stringResource(R.string.state_collected), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun StationPicker(stations: List<ItemRow>, here: Pair<Double, Double>?, ja: Boolean, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    var q by remember { mutableStateOf("") }
    val shown = remember(stations, q, here) {
        val k = io.github.thisisartman.collectibles.data.MapsLink.stationKey(q)
        stations.filter { r ->
            q.isBlank() || listOfNotNull(r.item.nameJa, r.item.nameRomaji).any { n ->
                io.github.thisisartman.collectibles.data.MapsLink.stationKey(n).contains(k) || n.contains(q.trim(), ignoreCase = true)
            }
        }.sortedWith(compareBy<ItemRow> { r ->
            // "niigata" puts Niigata Station before Niigata Daigaku-mae: exact names first, then names that start so
            val keys = listOfNotNull(r.item.nameJa, r.item.nameRomaji).map { io.github.thisisartman.collectibles.data.MapsLink.stationKey(it) }
            when { q.isBlank() -> 0; keys.any { it == k } -> 0; keys.any { it.startsWith(k) } -> 1; else -> 2 }
        }.thenBy { r ->
            if (here != null && r.item.lat != null && r.item.lng != null) distanceM(here.first, here.second, r.item.lat, r.item.lng) else 0.0
        }.thenBy { it.item.displayName(ja) })
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.trip_pick_station)) },
        text = {
            Column {
                OutlinedTextField(
                    value = q, onValueChange = { q = it }, singleLine = true,
                    label = { Text(stringResource(R.string.trip_station_search)) },
                    trailingIcon = { if (q.isNotEmpty()) IconButton(onClick = { q = "" }) { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.nearby_search_clear)) } },
                    modifier = Modifier.fillMaxWidth(),
                )
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(shown, key = { it.item.id }) { r ->
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { onPick(r.item.id) }.padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(r.item.displayName(ja), style = MaterialTheme.typography.bodyLarge)
                                val other = if (ja) r.item.nameRomaji else r.item.nameJa
                                if (other != null && other != r.item.displayName(ja)) Text(other, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (here != null && r.item.lat != null && r.item.lng != null) {
                                Text(distanceLabel(LocalContext.current, distanceM(here.first, here.second, r.item.lat, r.item.lng)),
                                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } },
    )
}

@Composable
private fun LinkDialog(read: suspend (String) -> LinkRead, onDismiss: () -> Unit, onEnds: (LinkRead.Ends) -> Unit) {
    val context = LocalContext.current
    // Start with what is on the clipboard when it holds a web address (Share copies the link)
    var text by remember {
        mutableStateOf(
            runCatching {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()?.takeIf { it.contains("http") }.orEmpty()
            }.getOrDefault(""),
        )
    }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<Int?>(null) }
    val scope = rememberCoroutineScope()
    fun go() {
        busy = true; error = null
        scope.launch {
            when (val r = read(text)) {
                is LinkRead.Ends -> if (r.from == null && r.to == null) error = R.string.trip_link_missing else onEnds(r)
                LinkRead.NotALink -> error = R.string.trip_link_not
                LinkRead.Offline -> error = R.string.trip_link_offline
            }
            busy = false
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.trip_link)) },
        text = {
            Column {
                OutlinedTextField(
                    value = text, onValueChange = { text = it; error = null },
                    label = { Text(stringResource(R.string.trip_link_hint)) },
                    maxLines = 4, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { if (text.isNotBlank() && !busy) go() }),
                )
                error?.let { Text(stringResource(it), Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(enabled = text.isNotBlank() && !busy, onClick = { go() }) {
                if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text(stringResource(R.string.trip_link_read))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } },
    )
}

fun distanceLabel(context: Context, m: Double): String = formatDistance(m).let { (res, v) -> context.getString(res, v) }

/** The plan as text for a message (#69): the ends, the lines, where to get off, what to collect, and a Directions link. */
private fun shareTrip(context: Context, p: TripPlan, legs: List<Leg>, byId: Map<String, ItemRow>, lineName: Map<String, String>, ja: Boolean) {
    fun name(id: String) = byId[id]?.item?.displayName(ja) ?: id
    val first = p.stops.first().stationId
    val last = p.stops.last().stationId
    val text = buildString {
        appendLine(context.getString(R.string.trip_share_head, name(first), name(last)))
        appendLine(context.getString(R.string.trip_share_lines, legs.joinToString(" → ") { lineName[it.lineId] ?: it.lineId }))
        val off = p.stops.filter { it.getOff }.map { name(it.stationId) }
        if (off.isNotEmpty()) appendLine(context.getString(R.string.trip_share_off, off.joinToString(", ")))
        val todo = p.stops.filter { s -> s.things.any { it.counts } }
        appendLine()
        if (todo.isEmpty()) appendLine(context.getString(R.string.trip_share_none)) else {
            appendLine(context.getString(R.string.trip_share_collect))
            todo.forEach { s ->
                val things = s.things.filter { it.counts }.joinToString(", ") { t ->
                    t.row.item.displayName(ja) + if (t.meters >= 25) " (" + distanceLabel(context, t.meters) + ")" else ""
                }
                appendLine("· " + name(s.stationId) + ": " + things)
            }
        }
        val a = byId[first]?.item
        val b = byId[last]?.item
        if (a?.lat != null && a.lng != null && b?.lat != null && b.lng != null) {
            appendLine()
            append(context.getString(R.string.trip_share_directions,
                "https://www.google.com/maps/dir/?api=1&origin=${a.lat},${a.lng}&destination=${b.lat},${b.lng}&travelmode=transit"))
        }
    }.trim()
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    context.startActivity(Intent.createChooser(send, context.getString(R.string.trip_share)))
}
