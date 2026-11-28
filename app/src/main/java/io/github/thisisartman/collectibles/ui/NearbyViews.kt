package io.github.thisisartman.collectibles.ui

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.TempleBuddhist
import androidx.compose.material.icons.outlined.Train
import androidx.compose.material.icons.outlined.Water
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import io.github.thisisartman.collectibles.R

/** Nearby's two views (#192): places near you, or the collectibles themselves under Stamps and Cards. */
enum class NearbyView { PLACES, COLLECTIBLES }

/**
 * What Nearby remembers between opens (#192): the view, Group by kind, the help bubble once seen, and the Appearance
 * setting that shows the names on the switch. Held in state too, so a change shows at once everywhere.
 */
object NearbyPrefs {
    private const val PREFS = "nearby"
    private val viewState = mutableStateOf<NearbyView?>(null)
    private val groupState = mutableStateOf<Boolean?>(null)
    private val namesState = mutableStateOf<Boolean?>(null)

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun view(context: Context): NearbyView = viewState.value
        ?: runCatching { NearbyView.valueOf(prefs(context).getString("view", NearbyView.PLACES.name)!!) }.getOrDefault(NearbyView.PLACES).also { viewState.value = it }

    fun setView(context: Context, v: NearbyView) { prefs(context).edit().putString("view", v.name).apply(); viewState.value = v }

    fun groupByKind(context: Context): Boolean = groupState.value ?: prefs(context).getBoolean("group_by_kind", false).also { groupState.value = it }

    fun setGroupByKind(context: Context, on: Boolean) { prefs(context).edit().putBoolean("group_by_kind", on).apply(); groupState.value = on }

    /** Settings › Appearance (owner, 2026-11-26): off by default, two icons with the view's name beside them. */
    fun namesOnSwitch(context: Context): Boolean = namesState.value ?: prefs(context).getBoolean("switch_names", false).also { namesState.value = it }

    fun setNamesOnSwitch(context: Context, on: Boolean) { prefs(context).edit().putBoolean("switch_names", on).apply(); namesState.value = on }

    /** The help bubble shows once after this update, then only when ? is tapped. */
    fun helpSeen(context: Context): Boolean = prefs(context).getBoolean("help_seen", false)

    fun setHelpSeen(context: Context) = prefs(context).edit().putBoolean("help_seen", true).apply()
}

/** A place kind's icon: standard Material icons (owner, round 3: no emojis). */
fun PlaceKind.icon(): ImageVector = when (this) {
    PlaceKind.STATION -> Icons.Outlined.Train
    PlaceKind.CITY -> Icons.Outlined.AccountBalance
    PlaceKind.SHRINE -> Icons.Outlined.TempleBuddhist
    PlaceKind.ROADSIDE -> Icons.Outlined.Storefront
    PlaceKind.DAM -> Icons.Outlined.Water
    PlaceKind.OTHER -> Icons.Outlined.Place
}

/** The heading under Group by kind: "Stations", "Cities and towns"... */
fun PlaceKind.plural(): Int = when (this) {
    PlaceKind.STATION -> R.string.place_kind_stations
    PlaceKind.CITY -> R.string.place_kind_cities
    PlaceKind.SHRINE -> R.string.place_kind_shrines
    PlaceKind.ROADSIDE -> R.string.place_kind_roadside
    PlaceKind.DAM -> R.string.place_kind_dams
    PlaceKind.OTHER -> R.string.place_kind_other
}

/** One place's kind, on the place page: "Station", "City or town"... */
fun PlaceKind.one(): Int = when (this) {
    PlaceKind.STATION -> R.string.station_title
    PlaceKind.CITY -> R.string.place_kind_city
    PlaceKind.SHRINE -> R.string.place_kind_shrine
    PlaceKind.ROADSIDE -> R.string.type_roadside
    PlaceKind.DAM -> R.string.place_kind_dam
    PlaceKind.OTHER -> R.string.place_kind_place
}

/**
 * The switch at the top of the Nearby sheet (#192): two icon segments, pin for Places and grid for Collectibles, or
 * with [names] (Settings › Appearance) the full width with each view's name and how many.
 */
@Composable
fun ViewSwitch(view: NearbyView, names: Boolean, places: Int?, collectibles: Int?, onPick: (NearbyView) -> Unit, modifier: Modifier = Modifier) {
    val labels = listOf(stringResource(R.string.nearby_view_places), stringResource(R.string.nearby_view_collectibles))
    val counts = listOf(places, collectibles)
    SingleChoiceSegmentedButtonRow(modifier) {
        NearbyView.entries.forEachIndexed { i, v ->
            SegmentedButton(
                selected = view == v,
                onClick = { onPick(v) },
                shape = SegmentedButtonDefaults.itemShape(i, 2),
                // The icon is the segment's content; no tick beside it
                icon = {},
                modifier = (if (names) Modifier else Modifier.width(56.dp)).heightIn(min = 48.dp)
                    .semantics { if (!names) contentDescription = labels[i] },
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (v == NearbyView.PLACES) Icons.Outlined.LocationOn else Icons.Outlined.GridView, contentDescription = null, modifier = Modifier.size(20.dp))
                    if (names) {
                        Spacer(Modifier.width(8.dp))
                        Text(counts[i]?.let { stringResource(R.string.nearby_view_count, labels[i], it) } ?: labels[i], maxLines = 1)
                    }
                }
            }
        }
    }
}

/**
 * The help bubble (#192): points at the switch and says what the two views are. Shows once after the update, and
 * whenever ? is tapped; Got it or a tap outside closes it.
 */
@Composable
fun HelpBubble(anchorHeight: Int, onDismiss: () -> Unit) {
    val ink = MaterialTheme.colorScheme.inverseOnSurface
    val bg = MaterialTheme.colorScheme.inverseSurface
    Popup(
        alignment = Alignment.TopStart,
        offset = androidx.compose.ui.unit.IntOffset(0, anchorHeight),
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        Column(Modifier.padding(horizontal = 4.dp).widthIn(max = 320.dp)) {
            // The caret, under the Places icon
            Canvas(Modifier.padding(start = 20.dp).size(16.dp, 8.dp)) {
                drawPath(Path().apply { moveTo(0f, size.height); lineTo(size.width / 2, 0f); lineTo(size.width, size.height); close() }, bg)
            }
            Surface(color = bg, contentColor = ink, shape = RoundedCornerShape(16.dp), shadowElevation = 6.dp) {
                // Got it sits on the last line's row, so the bubble ends with the text (S23, 2026-11-28: it had a row
                // of its own and left an empty band)
                Column(Modifier.padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 2.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    HelpLine(Icons.Outlined.LocationOn, stringResource(R.string.nearby_view_places), stringResource(R.string.nearby_help_places))
                    HelpLine(Icons.Outlined.GridView, stringResource(R.string.nearby_view_collectibles), stringResource(R.string.nearby_help_collectibles))
                    HelpLine(Icons.AutoMirrored.Outlined.HelpOutline, null, stringResource(R.string.nearby_help_again), onGotIt = onDismiss)
                }
            }
        }
    }
}

@Composable
internal fun HelpLine(icon: ImageVector, title: String?, text: String, onGotIt: (() -> Unit)? = null) {
    // With Got it, the text takes what is left of the row and wraps, so the button never leaves the bubble at 200 % text
    Row(Modifier.fillMaxWidth().padding(end = if (onGotIt == null) 8.dp else 0.dp), verticalAlignment = if (onGotIt == null) Alignment.Top else Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp).padding(top = 1.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            buildAnnotatedString {
                if (title != null) { withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(title) }; append(": ") }
                append(text)
            },
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        if (onGotIt != null) TextButton(onClick = onGotIt, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(R.string.nearby_help_got_it), color = MaterialTheme.colorScheme.inversePrimary, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

/** A heading under Group by kind: the kind's icon, its name and how many places. */
@Composable
fun KindHeading(kind: PlaceKind, count: Int, modifier: Modifier = Modifier) {
    Row(modifier.padding(start = 8.dp, top = 12.dp, bottom = 4.dp).semantics(mergeDescendants = true) { heading() }, verticalAlignment = Alignment.CenterVertically) {
        Icon(kind.icon(), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            stringResource(R.string.place_kind_heading, stringResource(kind.plural()), count),
            style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** A place's tile on the Places view: its ring, name and the distance to its nearest thing. */
@Composable
fun PlaceTile(place: Place, size: Dp, name: String, distance: String?, onOpen: () -> Unit) {
    TileFrame(size, name, distance, faded = place.noneHere, onOpen = onOpen) { SectorRing(place.things, size) }
}
