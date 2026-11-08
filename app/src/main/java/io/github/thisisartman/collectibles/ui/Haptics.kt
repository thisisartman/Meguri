package io.github.thisisartman.collectibles.ui

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/**
 * A short tap of the phone's vibration, so a tap feels answered (#75). Respects the system "touch feedback" switch.
 * VIRTUAL_KEY, the feedback phones give their own navigation buttons: CONTEXT_CLICK (used before) played nothing on
 * the owner's Poco with touch feedback on (2026-11-09), as it does on many phones.
 */
@Composable
fun rememberTick(): () -> Unit {
    val view = LocalView.current
    return remember(view) { { view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY) } }
}

/** A firmer buzz for collecting something, so it feels different from a plain tap. */
@Composable
fun rememberConfirm(): () -> Unit {
    val view = LocalView.current
    return remember(view) {
        {
            view.performHapticFeedback(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.LONG_PRESS,
            )
        }
    }
}
