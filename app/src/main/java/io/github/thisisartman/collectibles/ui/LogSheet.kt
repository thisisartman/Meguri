package io.github.thisisartman.collectibles.ui

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource

/**
 * The + card (owner, 2026-11-09): square buttons, Scan and Add (#223: Search went, the search bar does it), in a small card that rises from just
 * above the + button. It lives in the page area, so the bottom bar stays visible and usable (owner: it must not cover
 * the navigation); the buttons are a quarter smaller than the first try. Tapping outside, + again or Back closes it.
 */
@Composable
fun BoxScope.LogSheet(open: Boolean, onDismiss: () -> Unit, onScan: () -> Unit, onAdd: () -> Unit) {
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
        // The first time the card opens: what Scan and Add do, and that a hold on + scans (#224)
        val tip = listOf(
            TipLine(ImageVector.vectorResource(R.drawable.ic_scan), stringResource(R.string.add_scan), stringResource(R.string.tip_plus_scan)),
            TipLine(ImageVector.vectorResource(R.drawable.ic_collect), stringResource(R.string.add_new), stringResource(R.string.tip_plus_add)),
            TipLine(Icons.Filled.TouchApp, null, stringResource(R.string.tip_plus_hold)),
        )
        FirstTip(TipId.PLUS, tip, above = true, enabled = open) {
            Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, shadowElevation = 6.dp) {
                Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SquareAction(R.drawable.ic_scan, stringResource(R.string.add_scan), onScan)
                    SquareAction(R.drawable.ic_collect, stringResource(R.string.add_new), onAdd)
                }
            }
        }
    }
}

@Composable
private fun SquareAction(icon: Int, label: String, onClick: () -> Unit) {
    val tick = rememberTick()
    Surface(
        onClick = { tick(); onClick() },
        modifier = Modifier.widthIn(min = 84.dp).heightIn(min = 84.dp),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(28.dp))
            Spacer(Modifier.height(6.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, maxLines = 1)
        }
    }
}
