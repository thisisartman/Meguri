package io.github.thisisartman.collectibles.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import io.github.thisisartman.collectibles.R
import io.github.thisisartman.collectibles.data.ItemRow
import io.github.thisisartman.collectibles.data.ItemType
import io.github.thisisartman.collectibles.data.Origin

@Composable
fun NearbyScreen(vm: MainViewModel, nav: NavHostController, locationBlocked: Boolean = false, onAllowLocation: () -> Unit) {
    val ja = isJapanese()
    val here by vm.here.collectAsStateWithLifecycle()
    val nearest by vm.nearest.collectAsStateWithLifecycle()
    val summaries by vm.summaries.collectAsStateWithLifecycle()
    val lines by vm.lines.collectAsStateWithLifecycle()
    val rows by vm.rows.collectAsStateWithLifecycle()
    val around by vm.aroundHere.collectAsStateWithLifecycle()
    val photo = LocalPhoto.current

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
        item {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)) {
                Text(stringResource(R.string.nearby_title), style = MaterialTheme.typography.headlineMedium)
                val fix = here.fix
                val ctx = androidx.compose.ui.platform.LocalContext.current
                val allowed = LocationAccess.has(ctx)
                val systemOn = LocationAccess.systemOn(ctx)
                val where = when {
                    fix == null && here.searching -> stringResource(R.string.nearby_finding)
                    fix == null && !allowed -> stringResource(R.string.nearby_location_not_allowed)
                    fix == null && !systemOn -> stringResource(R.string.nearby_location_off)
                    fix == null -> stringResource(R.string.nearby_no_position)
                    here.live -> stringResource(R.string.nearby_from_now)
                    else -> stringResource(R.string.nearby_from_fix, formatTime(fix.timeMs))
                }
                Text(where, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (fix == null && !here.searching) {
                    val label = when {
                        !allowed && locationBlocked -> R.string.nearby_allow_in_settings
                        !allowed -> R.string.nearby_allow_location
                        !systemOn -> R.string.nearby_turn_on_location
                        else -> R.string.nearby_try_again
                    }
                    TextButton(onClick = onAllowLocation, contentPadding = PaddingValues(0.dp)) { Text(stringResource(label)) }
                }
            }
        }
        item { SectionTitle(stringResource(R.string.nearby_still_to_get)) }
        item {
            if (nearest.isEmpty()) {
                // No catalogue yet is not the same as having everything
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    // Only things added by hand: still no catalogue, so not "you have everything"
                    val noCatalogue = rows.none { it.item.origin != Origin.USER }
                    Text(
                        stringResource(if (noCatalogue) R.string.nearby_no_catalogue else R.string.nearby_all_done),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (noCatalogue) {
                        Spacer(Modifier.height(8.dp))
                        InkOutlinedButton(onClick = { nav.openMore() }) { Text(stringResource(R.string.nearby_load_catalogue)) }
                    }
                }
            } else {
                // An undone find comes back at the front; keyed items would otherwise keep it scrolled off the left edge
                val strip = rememberLazyListState()
                var lastSize by remember { mutableIntStateOf(nearest.size) }
                LaunchedEffect(nearest.firstOrNull()?.row?.item?.id, nearest.size) {
                    if (nearest.size > lastSize) strip.animateScrollToItem(0)
                    lastSize = nearest.size
                }
                LazyRow(state = strip, contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(nearest.take(25), key = { it.row.item.id }) { r ->
                        val name = r.row.item.displayName(ja)
                        NearTile(
                            row = r.row, name = name, meters = r.meters,
                            onOpen = { nav.openItem(r.row.item.id) },
                            onGotIt = { vm.gotIt(r.row, name) },
                            onPhoto = { vm.gotIt(r.row, name) { id -> photo.capture(id) } },
                        )
                    }
                }
            }
        }
        // Round 5, A2: with a position, only collections with something to get nearby; the rest on one line
        item {
            SectionTitle(
                if (around.located) stringResource(R.string.nearby_around_here, (MainViewModel.AROUND_RADIUS_M / 1000).toInt())
                else stringResource(R.string.nearby_my_book)
            )
        }
        if (summaries.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.nearby_empty_book),
                    Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(around.near, key = { it.summary.key }) { a ->
            val s = a.summary
            val title = summaryTitle(s, lineName(lines, s.lineId, ja))
            SummaryCard(s, title, next = a.next, onClick = { if (s.type == ItemType.EKI_STAMP) nav.openLine(s.lineId.orEmpty()) else nav.openBook() })
        }
        if (around.elsewhere.isNotEmpty()) {
            item { SectionTitle(stringResource(R.string.nearby_elsewhere)) }
            items(around.elsewhere, key = { "elsewhere-" + it.key }) { s ->
                Row(
                    Modifier.fillMaxWidth()
                        .clickable { if (s.type == ItemType.EKI_STAMP) nav.openLine(s.lineId.orEmpty()) else nav.openBook() }
                        .heightIn(min = 48.dp)
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TypeDot(s.type)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        summaryTitle(s, lineName(lines, s.lineId, ja)),
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.width(8.dp))
                    CountText(
                        if (s.total != null) stringResource(R.string.count, s.collected, s.total) else s.collected.toString(),
                        complete = s.total != null && s.total > 0 && s.collected == s.total,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun lineName(lines: List<io.github.thisisartman.collectibles.data.Line>, lineId: String?, ja: Boolean): String? =
    lines.firstOrNull { it.id == lineId }?.let { if (ja) it.nameJa else it.nameRomaji ?: it.nameJa }

@Composable
fun SectionTitle(text: String) {
    Text(
        text,
        Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 6.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
fun TypeDot(type: ItemType) {
    Box(Modifier.size(8.dp).clip(CircleShape).background(LocalTypeColors.current.of(type)))
}

@Composable
fun TypeChip(type: ItemType, extra: String? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        TypeDot(type)
        Spacer(Modifier.width(4.dp))
        Text(
            stringResource(type.label()) + (extra?.let { sep() + it } ?: ""),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun distanceText(meters: Double?): String? = meters?.let { val (res, v) = formatDistance(it); stringResource(res, v) }

@Composable
private fun NearTile(row: ItemRow, name: String, meters: Double?, onOpen: () -> Unit, onGotIt: () -> Unit, onPhoto: () -> Unit) {
    // Wider with large text, so the name and distance still fit
    val width = 150.dp * LocalDensity.current.fontScale.coerceIn(1f, 1.6f)
    Card(
        Modifier.width(width).clickable(onClick = onOpen),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.padding(10.dp)) {
            Text(name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis)
            TypeChip(row.item.type)
            // Distance on its own line: it is what the tile is for, and it must not be the part that gets cut
            Text(distanceText(meters) ?: "", style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"), maxLines = 1)
            Spacer(Modifier.height(8.dp))
            Button(onClick = onGotIt, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 4.dp)) { Text(stringResource(R.string.got_it)) }
            InkOutlinedButton(onClick = onPhoto, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 2.dp)) { Text(stringResource(R.string.photo)) }
        }
    }
}

@Composable
fun summaryTitle(s: CollectionSummary, lineName: String?): String = when {
    s.type == ItemType.MANHOLE_CARD -> stringResource(R.string.type_manhole_plural) + sep() + s.title.ifEmpty { stringResource(R.string.book_no_prefecture) }.let { prefectureName(it, isJapanese()) }
    s.type == ItemType.EKI_STAMP && s.lineId != null -> stringResource(R.string.type_eki_plural) + sep() + (lineName ?: s.lineId)
    s.type == ItemType.EKI_STAMP -> stringResource(R.string.type_eki_plural) + sep() + stringResource(R.string.book_other_stations)
    else -> stringResource(s.type.label())
}

@Composable
fun SummaryCard(s: CollectionSummary, title: String, next: Ranked? = null, onClick: () -> Unit) {
    val ja = isJapanese()
    val color = LocalTypeColors.current.of(s.type)
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TypeDot(s.type)
                Spacer(Modifier.width(6.dp))
                Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                CountText(
                    if (s.total != null) stringResource(R.string.count, s.collected, s.total) else s.collected.toString(),
                    complete = s.total != null && s.total > 0 && s.collected == s.total,
                    style = MaterialTheme.typography.titleSmall,
                )
            }
            // Around here: what to get next in this collection, and how far
            next?.let { n ->
                Text(
                    stringResource(R.string.nearby_next, n.row.item.displayName(ja), distanceText(n.meters).orEmpty()),
                    style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    // Two lines, so the distance at the end survives long design names and large text
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 14.dp, top = 2.dp),
                )
            }
            if (s.total != null && s.total > 0) {
                LinearProgressIndicator(
                    progress = { s.collected.toFloat() / s.total },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).height(6.dp),
                    color = color,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    strokeCap = StrokeCap.Round,
                    gapSize = 2.dp,
                    drawStopIndicator = {},
                )
            } else {
                Spacer(Modifier.height(6.dp))
            }
            s.last?.takeIf { next == null }?.let { last ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Mark(last, 26.dp)
                    Spacer(Modifier.width(8.dp))
                    val whenText = if (last.lastAt != null) formatDate(last.lastAt, ja) else stringResource(R.string.had_before_app)
                    Text(
                        stringResource(R.string.last, last.item.displayName(ja) + sep() + whenText),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
