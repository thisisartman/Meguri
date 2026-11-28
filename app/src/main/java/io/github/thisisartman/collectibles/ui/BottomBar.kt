package io.github.thisisartman.collectibles.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.indication
import androidx.compose.ui.draw.shadow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** A press-and-hold shortcut on a bottom bar button (#223): what it is called for TalkBack, and what it does. */
class HoldAction(val label: String, val run: () -> Unit)

/**
 * A bottom bar button that also takes a press and hold (#223): NavigationBarItem has no long press, so this draws
 * the same thing, the pill behind the icon when selected and the label under it. A hold gives a short haptic; the
 * same shortcuts are TalkBack custom actions, so they work without the gesture.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RowScope.BarItem(
    selected: Boolean, icon: ImageVector, label: String, onClick: () -> Unit,
    onHold: (() -> Unit)? = null, actions: List<HoldAction> = emptyList(),
) {
    val haptic = LocalHapticFeedback.current
    val pill by animateColorAsState(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0f), label = "pill")
    val ink = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
    val source = remember { MutableInteractionSource() }
    Column(
        Modifier.weight(1f).fillMaxHeight().heightIn(min = 64.dp)
            .combinedClickable(
                interactionSource = source, indication = null, role = Role.Tab,
                onClick = onClick,
                onLongClick = onHold?.let { h -> { haptic.performHapticFeedback(HapticFeedbackType.LongPress); h() } },
            )
            .semantics {
                this.selected = selected
                if (actions.isNotEmpty()) customActions = actions.map { a -> CustomAccessibilityAction(a.label) { a.run(); true } }
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // The ripple sits on the pill, as on NavigationBarItem
        Box(
            Modifier.size(64.dp, 32.dp).clip(RoundedCornerShape(16.dp)).background(pill).indication(source, ripple()),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, contentDescription = null, tint = ink) }
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = ink, maxLines = 1)
    }
}

/** The round + in the middle of the bar: a tap opens the card, a hold opens Scan straight away (#223). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RowScope.PlusItem(label: String, icon: ImageVector, onClick: () -> Unit, onHold: () -> Unit, holdLabel: String) {
    val haptic = LocalHapticFeedback.current
    Box(Modifier.weight(1f).align(Alignment.CenterVertically), contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(56.dp).shadow(2.dp, CircleShape).clip(CircleShape).background(MaterialTheme.colorScheme.primary)
                .combinedClickable(
                    role = Role.Button, onClickLabel = label,
                    onClick = onClick,
                    onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onHold() },
                    onLongClickLabel = holdLabel,
                )
                .semantics { customActions = listOf(CustomAccessibilityAction(holdLabel) { onHold(); true }) },
            contentAlignment = Alignment.Center,
        ) { Icon(icon, contentDescription = label, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(30.dp)) }
    }
}
