package io.github.thisisartman.collectibles.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith

/**
 * One motion for the whole app (owner, 2026-11-10: taps "suddenly move ahead", the app felt "janky, sudden, bolty").
 * Nothing waits before acting; the change itself moves, so the pressed control and its ripple stay readable while the
 * next step comes in.
 */
object Motion {
    const val STEP_MS = 240
    const val PAGE_MS = 280

    /**
     * A new page slides in from the side over the one before, opaque: no fade, as a fade let the map's own surface
     * show through (owner, 2026-11-07). Back slides it away again.
     */
    val pageEnter: EnterTransition = slideInHorizontally(tween(PAGE_MS, easing = FastOutSlowInEasing)) { it }
    val pageExit: ExitTransition = ExitTransition.None
    val pagePopEnter: EnterTransition = EnterTransition.None
    val pagePopExit: ExitTransition = slideOutHorizontally(tween(PAGE_MS, easing = FastOutSlowInEasing)) { it }

    /** A step inside one screen (onboarding, Collection check): the old one fades, the new one rises a little into place. */
    fun <S> step(forward: Boolean = true): AnimatedContentTransitionScope<S>.() -> ContentTransform = {
        (fadeIn(tween(STEP_MS, delayMillis = 60, easing = LinearOutSlowInEasing)) +
            slideInVertically(tween(STEP_MS, delayMillis = 60, easing = FastOutSlowInEasing)) { h -> (if (forward) h else -h) / 12 })
            .togetherWith(fadeOut(tween(STEP_MS / 2)))
            .using(SizeTransform(clip = false) { _, _ -> tween(STEP_MS, easing = FastOutSlowInEasing) })
    }
}
