package io.github.thisisartman.collectibles.ui

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties

/** The first-time tips (#224), by the flow they explain. */
object TipId {
    const val PLUS = "plus"
    const val PLACE = "place"
    const val PHOTO = "photo"
    const val BOOK = "book"
    const val TRIP = "trip"
    const val CHECK = "check"
    const val BACKUP = "backup"
}

/**
 * Which tips have been seen (#224): each shows once, the first time its flow opens, and only one at a time. Pure, so
 * it is unit tested; [Tips] keeps it in the app's settings.
 */
data class TipLog(val seen: Set<String> = emptySet(), val showing: String? = null) {
    /** Whether [id] may show now: not seen before, and no other tip is up. */
    fun canShow(id: String): Boolean = id !in seen && (showing == null || showing == id)

    fun open(id: String): TipLog = if (canShow(id)) copy(showing = id) else this

    /** Got it, a tap outside or leaving the flow: seen for good. */
    fun close(id: String): TipLog = copy(seen = seen + id, showing = if (showing == id) null else showing)

    /** Show tips again, in Help and about. */
    fun reset(): TipLog = TipLog()
}

/** The tips' seen list, in the app's settings, held in state so a change shows at once. */
object Tips {
    private const val PREFS = "tips"
    private val state = mutableStateOf<TipLog?>(null)

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun log(context: Context): TipLog = state.value
        ?: TipLog(prefs(context).getStringSet("seen", emptySet())?.toSet() ?: emptySet()).also { state.value = it }

    fun open(context: Context, id: String): Boolean {
        val before = log(context)
        val after = before.open(id)
        state.value = after
        return after.showing == id
    }

    fun close(context: Context, id: String) {
        val after = log(context).close(id)
        prefs(context).edit().putStringSet("seen", after.seen).apply()
        state.value = after
    }

    /** Every tip shows again, the Nearby bubble too. */
    fun reset(context: Context) {
        prefs(context).edit().remove("seen").apply()
        state.value = TipLog()
        NearbyPrefs.resetHelp(context)
    }
}

/** One line of a tip: an icon, an optional bold name and the text. */
class TipLine(val icon: ImageVector, val title: String?, val text: String)

/**
 * The shared tip bubble (#224): the Nearby help bubble's look, a caret pointing at its target, Got it on the last
 * line's row, a tap outside closes. Opens under its anchor (the composable it is placed in), or over it with [above].
 */
@Composable
fun TipBubble(lines: List<TipLine>, onDismiss: () -> Unit, above: Boolean = false) {
    val ink = MaterialTheme.colorScheme.inverseOnSurface
    val bg = MaterialTheme.colorScheme.inverseSurface
    val margin = with(LocalDensity.current) { 4.dp.roundToPx() }
    val positioner = remember(above, margin) {
        object : PopupPositionProvider {
            override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
                val x = anchorBounds.left.coerceIn(margin, (windowSize.width - popupContentSize.width - margin).coerceAtLeast(margin))
                val y = if (above) anchorBounds.top - popupContentSize.height else anchorBounds.bottom
                return IntOffset(x, y.coerceIn(0, (windowSize.height - popupContentSize.height).coerceAtLeast(0)))
            }
        }
    }
    Popup(popupPositionProvider = positioner, onDismissRequest = onDismiss, properties = PopupProperties(focusable = true)) {
        Column(Modifier.padding(horizontal = 4.dp).widthIn(max = 320.dp)) {
            val caret = @Composable {
                Canvas(Modifier.padding(start = 20.dp).size(16.dp, 8.dp)) {
                    val p = if (above) Path().apply { moveTo(0f, 0f); lineTo(size.width / 2, size.height); lineTo(size.width, 0f); close() }
                    else Path().apply { moveTo(0f, size.height); lineTo(size.width / 2, 0f); lineTo(size.width, size.height); close() }
                    drawPath(p, bg)
                }
            }
            if (!above) caret()
            Surface(color = bg, contentColor = ink, shape = RoundedCornerShape(16.dp), shadowElevation = 6.dp) {
                Column(Modifier.padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 2.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    lines.forEachIndexed { i, l -> HelpLine(l.icon, l.title, l.text, onGotIt = if (i == lines.lastIndex) onDismiss else null) }
                }
            }
            if (above) caret()
        }
    }
}

/**
 * Puts [content] on screen with its first-time tip (#224): the first time it shows, and while no other tip is up,
 * the bubble opens on it after a moment; once closed it never shows again (until Show tips again).
 */
@Composable
fun FirstTip(id: String, lines: List<TipLine>, modifier: Modifier = Modifier, above: Boolean = false, enabled: Boolean = true, content: @Composable () -> Unit) {
    val context = LocalContext.current
    var open by remember { mutableStateOf(false) }
    val log = Tips.log(context)
    LaunchedEffect(id, enabled, log.seen) {
        if (enabled && id !in log.seen) { kotlinx.coroutines.delay(600); open = Tips.open(context, id) }
    }
    // Leaving the flow with the tip up counts as seen, and frees the way for the next tip
    val isOpen by androidx.compose.runtime.rememberUpdatedState(open)
    androidx.compose.runtime.DisposableEffect(id) { onDispose { if (isOpen) Tips.close(context, id) } }
    Box(modifier) {
        content()
        if (open && enabled) TipBubble(lines, onDismiss = { open = false; Tips.close(context, id) }, above = above)
    }
}
