package io.github.thisisartman.collectibles.ui

import android.Manifest
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import io.github.thisisartman.collectibles.R
import io.github.thisisartman.collectibles.debug.LogsScreen
import io.github.thisisartman.collectibles.photo.CameraCapture

/** Opens the system camera for a find; the photo is attached when the camera returns. */
fun interface PhotoLauncher {
    fun capture(findId: String)
}

val LocalPhoto = staticCompositionLocalOf<PhotoLauncher> { PhotoLauncher { } }

private const val NEARBY = "nearby"
private const val BOOK = "book"
private const val MORE = "more"

fun NavHostController.openItem(id: String) = navigate("item/$id")
fun NavHostController.openMap(section: MapSection = MapSection.ALL) = navigate("map/${section.name}")
fun NavHostController.openLine(id: String) = navigate("line/${id.ifEmpty { "_other" }}")
fun NavHostController.openTab(route: String) = navigate(route) {
    popUpTo(NEARBY) { saveState = true }
    launchSingleTop = true
    restoreState = true
}
// Settings is a page of its own now (opened from the top-right menu), not a tab
fun NavHostController.openMore() = navigate(MORE) { launchSingleTop = true }
fun NavHostController.openBook() = openTab(BOOK)

/** A page on the app's background colour, so nothing behind it (the map's surface) shows through while it loads. */
@Composable
private fun Page(content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.Box(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
    ) { content() }
}

@Composable
fun App(request: AppRequest? = null, onRequestHandled: () -> Unit = {}) {
    val vm: MainViewModel = hiltViewModel()
    val nav = rememberNavController()
    val context = LocalContext.current
    val resolver = context.contentResolver
    val snackbar = remember { SnackbarHostState() }
    var showLog by rememberSaveable { mutableStateOf(false) }
    var bookMarking by remember { mutableStateOf(false) }

    // Pending camera capture "findId|uri", kept across process death while the camera is open (FR-9b).
    var pending by rememberSaveable { mutableStateOf<String?>(null) }
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val p = pending ?: return@rememberLauncherForActivityResult
        pending = null
        val findId = p.substringBefore('|')
        val uri = Uri.parse(p.substringAfter('|'))
        val info = if (ok) CameraCapture.describe(resolver, uri) else null
        if (info != null) vm.attachPhoto(findId, info) else CameraCapture.discardIfEmpty(resolver, uri)
        vm.retryPosition(findId)
        vm.reshowLogged(findId)
    }
    val photo = remember {
        PhotoLauncher { findId ->
            val uri = CameraCapture.newPhotoUri(resolver) ?: return@PhotoLauncher
            pending = "$findId|$uri"
            takePicture.launch(uri)
        }
    }

    // After two refusals Android stops showing its prompt, so "Allow location" must open Settings instead (round 9)
    val activity = androidx.activity.compose.LocalActivity.current
    val locationPrefs = remember { context.getSharedPreferences("location", android.content.Context.MODE_PRIVATE) }
    var locationBlocked by remember { mutableStateOf(locationPrefs.getBoolean("blocked", false)) }
    val askLocation = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        val granted = result.values.any { it }
        val canAskAgain = activity?.let { androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.ACCESS_FINE_LOCATION) } ?: true
        locationBlocked = !granted && !canAskAgain && !LocationAccess.has(context)
        locationPrefs.edit().putBoolean("blocked", locationBlocked).apply()
        vm.refreshPosition()
    }
    val onAllowLocation: () -> Unit = {
        when {
            // Allowed and switched on, but no position yet: ask again
            LocationAccess.has(context) && LocationAccess.systemOn(context) -> vm.refreshPosition()
            // Allowed, but the phone's location is switched off
            LocationAccess.has(context) -> LocationAccess.openLocationSettings(context)
            locationBlocked -> LocationAccess.openAppSettings(context)
            else -> askLocation.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }
    LaunchedEffect(Unit) {
        CameraCapture.cleanEmptyRows(resolver)
        askLocation.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }
    LifecycleResumeEffect(Unit) {
        // Back from Settings with location allowed: the blocked note no longer applies
        if (LocationAccess.has(context) && locationBlocked) { locationBlocked = false; locationPrefs.edit().putBoolean("blocked", false).apply() }
        vm.refreshPosition()
        onPauseOrDispose { }
    }

    val alertsVm: AlertsViewModel = hiltViewModel()
    val japanese = isJapanese()
    val tripPerms = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (!startTrip(context, alertsVm)) nav.openMore()
    }
    LaunchedEffect(request) {
        when (val r = request ?: return@LaunchedEffect) {
            is AppRequest.OpenItem -> { AlertNotifierCancel.cancel(context, r.itemId); nav.openItem(r.itemId) }
            is AppRequest.Photo -> {
                AlertNotifierCancel.cancel(context, r.itemId)
                vm.logFromAlert(r.itemId, japanese) { id -> photo.capture(id) }
            }
            AppRequest.StartTrip -> if (!startTrip(context, alertsVm)) tripPerms.launch(foregroundAlertPermissions())
        }
        onRequestHandled()
    }

    val logged by vm.logged.collectAsStateWithLifecycle()
    val loggedText = logged?.let { stringResource(R.string.logged, it.name, formatTime(it.atMs)) }
    val undoText = stringResource(R.string.undo)
    LaunchedEffect(logged) {
        val l = logged ?: return@LaunchedEffect
        val r = snackbar.showSnackbar(loggedText ?: "", actionLabel = undoText, duration = SnackbarDuration.Long)
        if (r == SnackbarResult.ActionPerformed) vm.undo(l.findId) else vm.clearLogged()
    }

    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val topLevel = route == null || route in setOf(NEARBY, BOOK)

    CompositionLocalProvider(LocalPhoto provides photo) {
        Scaffold(
            // On an item page the snackbar would cover its Photo and Got it buttons, so it sits above them
            snackbarHost = { SnackbarHost(snackbar, Modifier.padding(bottom = if (route?.startsWith("item/") == true) 72.dp else 0.dp)) },
            bottomBar = {
                // Hidden while marking owned cards: the mark button takes the bottom of the screen
                if (topLevel && !(route == BOOK && bookMarking)) {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                        // Owner, 2026-11-06: the app is used from the bottom, so Log a find is the middle button
                        // (one + for the whole app, replacing the header + and the My book floating button)
                        val items = listOf(
                            Triple(NEARBY, R.string.nav_nearby, Icons.Filled.Place),
                            Triple("log", R.string.nav_log, Icons.Filled.Add),
                            Triple(BOOK, R.string.nav_book, Icons.AutoMirrored.Filled.List),
                        )
                        items.forEach { (r, label, icon) ->
                            if (r == "log") {
                                // A 56 dp gold circle centred in the bar, with no label of its own: a raised circle
                                // was cut off at the bar's top edge and touched "Log" at large text (2026-11-06 review)
                                androidx.compose.foundation.layout.Box(
                                    // Not fillMaxHeight: in the bar's row that made the bar take the whole screen
                                    Modifier.weight(1f).align(androidx.compose.ui.Alignment.CenterVertically),
                                    contentAlignment = androidx.compose.ui.Alignment.Center,
                                ) {
                                    androidx.compose.material3.Surface(
                                        onClick = { showLog = true },
                                        shape = androidx.compose.foundation.shape.CircleShape,
                                        color = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary,
                                        shadowElevation = 2.dp,
                                        modifier = Modifier.size(56.dp),
                                    ) {
                                        androidx.compose.foundation.layout.Box(contentAlignment = androidx.compose.ui.Alignment.Center) {
                                            Icon(icon, contentDescription = stringResource(R.string.log_title), modifier = Modifier.size(30.dp))
                                        }
                                    }
                                }
                                return@forEach
                            }
                            NavigationBarItem(
                                selected = (route ?: NEARBY) == r,
                                onClick = { nav.openTab(r) },
                                icon = { Icon(icon, contentDescription = null) },
                                label = { Text(stringResource(label)) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.onSurface,
                                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                ),
                            )
                        }
                    }
                }
            },
        ) { padding ->
            // Landscape and wide screens: content keeps a readable width, centred (overnight review round 10)
            androidx.compose.foundation.layout.Box(Modifier.fillMaxSize().padding(padding), contentAlignment = androidx.compose.ui.Alignment.TopCenter) {
                // No crossfade between pages: the map draws on its own surface and stayed on screen for the fade
                // before the next page's background appeared (owner, 2026-11-07)
                NavHost(
                    nav, startDestination = NEARBY, modifier = Modifier.widthIn(max = 640.dp).fillMaxSize(),
                    enterTransition = { androidx.compose.animation.EnterTransition.None },
                    exitTransition = { androidx.compose.animation.ExitTransition.None },
                    popEnterTransition = { androidx.compose.animation.EnterTransition.None },
                    popExitTransition = { androidx.compose.animation.ExitTransition.None },
                ) {
                    composable(NEARBY) { NearbyScreen(vm, nav, onLog = { showLog = true }, locationBlocked = locationBlocked && !LocationAccess.has(context), onAllowLocation = onAllowLocation) }
                    // Every page but the maps sits on a solid background: the item page had none, so for a moment
                    // after a tap the Nearby map showed through it (screen recording on the Poco, 2026-11-07)
                    composable(BOOK) { Page { BookScreen(vm, nav, snackbar, onMarkingChange = { bookMarking = it }) } }
                    composable(MORE) { Page { MoreScreen(snackbar, onLogs = { nav.navigate("logs") }, onBack = { nav.popBackStack() }) } }
                    composable("item/{id}", listOf(navArgument("id") { type = NavType.StringType })) {
                        Page { ItemScreen(vm, it.arguments?.getString("id").orEmpty(), onBack = { nav.popBackStack() }) }
                    }
                    composable("line/{id}", listOf(navArgument("id") { type = NavType.StringType })) {
                        val id = it.arguments?.getString("id").orEmpty().let { s -> if (s == "_other") "" else s }
                        Page { LineScreen(vm, id, nav, onBack = { nav.popBackStack() }) }
                    }
                    composable("map/{section}", listOf(navArgument("section") { type = NavType.StringType })) {
                        val section = runCatching { MapSection.valueOf(it.arguments?.getString("section").orEmpty()) }.getOrDefault(MapSection.ALL)
                        MapScreen(vm, nav, section)
                    }
                    composable("logs") { Page { LogsScreen(onBack = { nav.popBackStack() }) } }
                }
            }
        }
        if (showLog) {
            LogSheet(vm, onDismiss = { showLog = false }, onOpenItem = { showLog = false; nav.openItem(it) })
        }
    }
}
