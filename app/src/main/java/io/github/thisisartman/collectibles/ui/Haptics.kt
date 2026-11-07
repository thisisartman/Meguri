package io.github.thisisartman.collectibles.ui

import android.view.HapticFeedbackConstants
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/** A short tap of the phone's vibration, so a tap feels answered (#75). Respects the system "touch feedback" switch. */
@Composable
fun rememberTick(): () -> Unit {
    val view = LocalView.current
    return remember(view) { { view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK) } }
}
