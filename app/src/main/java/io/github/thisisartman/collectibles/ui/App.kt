package io.github.thisisartman.collectibles.ui

import android.Manifest
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
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
fun NavHostController.openLine(id: String) = navigate("line/${id.ifEmpty { "_other" }}")

@Composable
fun App() {
    val vm: MainViewModel = hiltViewModel()
    val nav = rememberNavController()
    val context = LocalContext.current
    val resolver = context.contentResolver
    val snackbar = remember { SnackbarHostState() }
    var showLog by rememberSaveable { mutableStateOf(false) }

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
    }
    val photo = remember {
        PhotoLauncher { findId ->
            val uri = CameraCapture.newPhotoUri(resolver) ?: return@PhotoLauncher
            pending = "$findId|$uri"
            takePicture.launch(uri)
        }
    }

    val askLocation = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { vm.refreshPosition() }
    LaunchedEffect(Unit) {
        CameraCapture.cleanEmptyRows(resolver)
        askLocation.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }
    LifecycleResumeEffect(Unit) {
        vm.refreshPosition()
        onPauseOrDispose { }
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
    val topLevel = route == null || route in setOf(NEARBY, BOOK, MORE)

    CompositionLocalProvider(LocalPhoto provides photo) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbar) },
            floatingActionButton = {
                if (route == null || route == NEARBY || route == BOOK) {
                    FloatingActionButton(
                        onClick = { showLog = true },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ) { Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.log_title)) }
                }
            },
            bottomBar = {
                if (topLevel) {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                        listOf(
                            Triple(NEARBY, R.string.nav_nearby, Icons.Filled.Place),
                            Triple(BOOK, R.string.nav_book, Icons.AutoMirrored.Filled.List),
                            Triple(MORE, R.string.nav_more, Icons.Filled.MoreVert),
                        ).forEach { (r, label, icon) ->
                            NavigationBarItem(
                                selected = (route ?: NEARBY) == r,
                                onClick = {
                                    nav.navigate(r) {
                                        popUpTo(NEARBY) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = { Icon(icon, contentDescription = null) },
                                label = { Text(stringResource(label)) },
                            )
                        }
                    }
                }
            },
        ) { padding ->
            NavHost(nav, startDestination = NEARBY, modifier = Modifier.padding(padding)) {
                composable(NEARBY) { NearbyScreen(vm, nav, onAllowLocation = { askLocation.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)) }) }
                composable(BOOK) { BookScreen(vm, nav, snackbar) }
                composable(MORE) { MoreScreen(snackbar, onLogs = { nav.navigate("logs") }) }
                composable("item/{id}", listOf(navArgument("id") { type = NavType.StringType })) {
                    ItemScreen(vm, it.arguments?.getString("id").orEmpty(), onBack = { nav.popBackStack() })
                }
                composable("line/{id}", listOf(navArgument("id") { type = NavType.StringType })) {
                    val id = it.arguments?.getString("id").orEmpty().let { s -> if (s == "_other") "" else s }
                    LineScreen(vm, id, nav, onBack = { nav.popBackStack() })
                }
                composable("logs") { LogsScreen(onBack = { nav.popBackStack() }) }
            }
        }
        if (showLog) {
            LogSheet(vm, onDismiss = { showLog = false }, onOpenItem = { showLog = false; nav.openItem(it) })
        }
    }
}
