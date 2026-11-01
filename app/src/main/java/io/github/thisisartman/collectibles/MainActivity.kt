package io.github.thisisartman.collectibles

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import io.github.thisisartman.collectibles.debug.LogsScreen
import io.github.thisisartman.collectibles.ui.CollectiblesTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CollectiblesTheme {
                val nav = rememberNavController()
                NavHost(navController = nav, startDestination = "home") {
                    composable("home") { HomeScreen(onOpenLogs = { nav.navigate("logs") }) }
                    composable("logs") { LogsScreen(onBack = { nav.popBackStack() }) }
                }
            }
        }
    }
}
