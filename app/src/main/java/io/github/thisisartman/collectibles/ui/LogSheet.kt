package io.github.thisisartman.collectibles.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.thisisartman.collectibles.R

/**
 * The + card (owner, 2026-11-09): three square buttons, Scan, Search and Add, in a small card that rises from just
 * above the + button. It lives in the page area, so the bottom bar stays visible and usable (owner: it must not cover
 * the navigation); the buttons are a quarter smaller than the first try. Tapping outside, + again or Back closes it.
 */
@Composable
fun BoxScope.LogSheet(open: Boolean, onDismiss: () -> Unit, onScan: () -> Unit, onSearch: () -> Unit, onAdd: () -> Unit) {
    if (open) BackHandler(onBack = onDismiss)
    AnimatedVisibility(open, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.matchParentSize()) {
        // A light dim over the page only; a tap on it closes the card
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.32f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        )
    }
    AnimatedVisibility(
        open,
        enter = fadeIn() + slideInVertically { it / 2 },
        exit = fadeOut() + slideOutVertically { it / 2 },
        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
    ) {
        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, shadowElevation = 6.dp) {
            Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SquareAction(R.drawable.ic_scan, stringResource(R.string.add_scan), onScan)
                SquareAction(R.drawable.ic_search, stringResource(R.string.add_search), onSearch)
                SquareAction(R.drawable.ic_collect, stringResource(R.string.add_new), onAdd)
            }
        }
    }
}

@Composable
private fun SquareAction(icon: Int, label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(84.dp),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Column(Modifier.fillMaxSize().padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(28.dp))
            Spacer(Modifier.height(6.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, maxLines = 1)
        }
    }
}
