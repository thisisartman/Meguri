package io.github.thisisartman.collectibles.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import io.github.thisisartman.collectibles.R

/**
 * The round button at the top right of Nearby and My book (owner, 2026-11-06: like a profile menu):
 * it opens Settings and Logs, so the bottom bar keeps only Nearby, Log and My book.
 */
@Composable
fun MenuButton(nav: NavHostController, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        IconButton(onClick = { open = true }) {
            // A plain "more" menu: the profile it stood for is now My book itself (owner, 2026-11-08)
            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.menu_open), modifier = Modifier.size(26.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.menu_trip)) },
                leadingIcon = { Icon(androidx.compose.ui.res.painterResource(R.drawable.ic_route), contentDescription = null) },
                onClick = { open = false; nav.navigate("trip") },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.check_title)) },
                leadingIcon = { Icon(Icons.Filled.Check, contentDescription = null) },
                onClick = { open = false; nav.navigate("check") },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.menu_settings)) },
                leadingIcon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                onClick = { open = false; nav.openMore() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.more_logs)) },
                leadingIcon = { Icon(Icons.Filled.Build, contentDescription = null) },
                onClick = { open = false; nav.navigate("logs") },
            )
        }
    }
}
