package io.github.thisisartman.collectibles.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.thisisartman.collectibles.R
import io.github.thisisartman.collectibles.data.ItemRow
import io.github.thisisartman.collectibles.data.ItemType
import io.github.thisisartman.collectibles.data.Origin
import kotlinx.coroutines.launch

/**
 * Collection check as a card deck (owner, 2026-11-09): pick a category, then a prefecture, then go through what you
 * don't have yet one at a time, like a dating app. Swipe right (or "Have it") and it is saved as had before Meguri;
 * swipe left (or "Not yet") for the next one. Undo takes back the last swipe.
 */
private data class CheckCategory(val key: String, val name: String, val type: ItemType, val rows: List<ItemRow>)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionCheckFlow(vm: MainViewModel, onBack: () -> Unit) {
    val ja = isJapanese()
    val rows by vm.rows.collectAsStateWithLifecycle()
    val customTypes = LocalCustomTypes.current
    val context = androidx.compose.ui.platform.LocalContext.current
    // Only catalogue items (and your own categories) are checked, as before
    val categories = remember(rows, customTypes, ja) {
        val candidates = rows.filter { it.item.origin != Origin.USER || it.item.type == ItemType.CUSTOM }
        val builtIn = listOf(ItemType.MANHOLE_CARD, ItemType.EKI_STAMP, ItemType.EKITAG, ItemType.GOSHUIN, ItemType.DAM_CARD, ItemType.MUSEUM_STAMP, ItemType.ROADSIDE_STATION)
            .mapNotNull { t -> candidates.filter { it.item.type == t }.takeIf { it.isNotEmpty() }?.let { CheckCategory(t.name, context.getString(t.pluralLabel()), t, it) } }
        val custom = candidates.filter { it.item.type == ItemType.CUSTOM }.groupBy { it.item.customTypeId.orEmpty() }
            .map { (id, l) -> CheckCategory("c:$id", customTypes[id]?.name ?: context.getString(R.string.type_custom), ItemType.CUSTOM, l) }
        builtIn + custom
    }
    var categoryKey by rememberSaveable { mutableStateOf<String?>(null) }
    val history = remember { androidx.compose.runtime.mutableStateListOf<Pair<String, Boolean>>() }
    var prefecture by rememberSaveable { mutableStateOf<String?>(null) }
    val category = categories.firstOrNull { it.key == categoryKey }
    val back = {
        when {
            prefecture != null -> prefecture = null
            categoryKey != null -> categoryKey = null
            else -> onBack()
        }
    }
    androidx.activity.compose.BackHandler { back() }
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            windowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
            title = {
                Text(
                    when {
                        category != null && prefecture != null -> category.name + " · " + prefectureLabel(prefecture!!, ja)
                        category != null -> category.name
                        else -> stringResource(R.string.check_title)
                    },
                    maxLines = 1,
                )
            },
            navigationIcon = { IconButton(onClick = back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back)) } },
        )
        when {
            categories.isEmpty() -> Text(stringResource(R.string.check_nothing), Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            category == null -> Column {
                CardPicturesOffer(categories.firstOrNull { it.type == ItemType.MANHOLE_CARD }?.rows.orEmpty())
                ChoiceList(
                    stringResource(R.string.check_pick_category),
                    categories.map { c -> Triple(c.key, c.name, c.rows) },
                    lead = { key -> categories.first { it.key == key }.rows.first().item.let { ItemTypeIcon(it, 24.dp) } },
                ) { categoryKey = it }
            }
            prefecture == null -> ChoiceList(
                stringResource(R.string.check_pick_prefecture),
                category.rows.groupBy { it.item.prefecture.orEmpty() }.toSortedMap().map { (p, l) -> Triple(p, prefectureLabel(p, ja), l) },
            ) { prefecture = it }
            else -> Deck(vm, category.rows.filter { it.item.prefecture.orEmpty() == prefecture }, category.key + "|" + prefecture, history) { prefecture = null }
        }
    }
}

@Composable
private fun prefectureLabel(p: String, ja: Boolean) = if (p.isEmpty()) stringResource(R.string.check_no_prefecture) else prefectureName(p, ja)

/**
 * Card pictures are not on a fresh phone, so the deck showed a seal for every card (S23 run, 2026-11-09). Offered here,
 * where they matter, until all are down; the download is the Catalogue one and carries on after leaving.
 */
@Composable
private fun CardPicturesOffer(cards: List<ItemRow>, more: MoreViewModel = androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel()) {
    val codes = remember(cards) { cards.mapNotNull { it.item.code }.toSet() }
    val have by io.github.thisisartman.collectibles.photo.CardPictures.have.collectAsStateWithLifecycle()
    val progress by more.cardProgress.collectAsStateWithLifecycle()
    val missing = codes.count { it !in have }
    if (codes.isEmpty() || missing == 0) return
    val context = androidx.compose.ui.platform.LocalContext.current
    Surface(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Row(Modifier.padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                progress?.let { (d, t) -> stringResource(R.string.cards_progress, d, t) } ?: stringResource(R.string.check_cards_missing),
                Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            if (progress == null) TextButton(onClick = { more.downloadCards(context) }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.check_cards_download, missing))
            }
        }
    }
}

/** A list of choices, each with how many you have of it and an arrow. */
@Composable
private fun ChoiceList(intro: String, choices: List<Triple<String, String, List<ItemRow>>>, lead: (@Composable (String) -> Unit)? = null, onPick: (String) -> Unit) {
    Column(Modifier.verticalScroll(rememberScrollState())) {
        Text(intro, Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        choices.forEach { (key, name, list) ->
            val have = list.count { it.collected }
            Row(
                Modifier.fillMaxWidth().clickable { onPick(key) }.heightIn(min = 64.dp).padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (lead != null) { lead(key); Spacer(Modifier.width(16.dp)) }
                Column(Modifier.weight(1f)) {
                    Text(name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                    Text(
                        pluralStringResource(R.plurals.check_left, list.size - have, list.size - have) + " · " + stringResource(R.string.count, have, list.size),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            HorizontalDivider(Modifier.padding(start = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

/** The deck: what you don't have yet, one at a time. */
@Composable
private fun Deck(vm: MainViewModel, rows: List<ItemRow>, key: String, history: MutableList<Pair<String, Boolean>>, onFinished: () -> Unit) {
    val ja = isJapanese()
    // The pile is fixed when you open it, so saving one doesn't reshuffle the rest
    val pile = remember(key) { rows.filter { !it.collected }.sortedBy { it.item.code ?: it.item.nameJa }.map { it.item.id } }
    val byId = rows.associateBy { it.item.id }
    var index by rememberSaveable(key) { mutableIntStateOf(0) }
    val offset = remember(key) { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val haptic = rememberConfirm()

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val widthPx = with(LocalDensity.current) { maxWidth.toPx() }
        fun decide(have: Boolean) {
            val id = pile.getOrNull(index) ?: return
            scope.launch {
                offset.animateTo(if (have) widthPx * 1.2f else -widthPx * 1.2f, tween(220))
                if (have) { vm.markBeforeApp(setOf(id)) { }; haptic() }
                history += id to have
                index++
                offset.snapTo(0f)
            }
        }
        fun undo() {
            val last = history.removeLastOrNull() ?: return
            if (last.second) vm.unmarkBeforeApp(last.first)
            // Back to that card when it is in this pile; one from another prefecture just comes off
            val at = pile.indexOf(last.first)
            if (at in 0 until index) index = at
        }
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            val left = (pile.size - index).coerceAtLeast(0)
            Text(
                if (pile.isEmpty()) stringResource(R.string.check_all_had) else pluralStringResource(R.plurals.check_left, left, left),
                Modifier.padding(top = 4.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val row = pile.getOrNull(index)?.let { byId[it] }
            Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
                if (row == null) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.check_done), style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = onFinished) { Text(stringResource(R.string.check_pick_another)) }
                    }
                } else {
                    val dx = offset.value
                    Surface(
                        Modifier.fillMaxSize().graphicsLayer { translationX = dx; rotationZ = dx / 40f }
                            .pointerInput(row.item.id) {
                                detectHorizontalDragGestures(
                                    onDragEnd = {
                                        when {
                                            offset.value > widthPx * 0.28f -> decide(true)
                                            offset.value < -widthPx * 0.28f -> decide(false)
                                            else -> scope.launch { offset.animateTo(0f, tween(180)) }
                                        }
                                    },
                                    onDragCancel = { scope.launch { offset.animateTo(0f, tween(180)) } },
                                ) { _, d -> scope.launch { offset.snapTo(offset.value + d) } }
                            },
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shadowElevation = 6.dp,
                    ) {
                        Box(Modifier.fillMaxSize()) {
                            DeckCard(row, ja)
                            // What letting go now would do
                            val strength = (kotlin.math.abs(dx) / (widthPx * 0.28f)).coerceIn(0f, 1f)
                            if (dx != 0f) Text(
                                stringResource(if (dx > 0) R.string.check_have else R.string.check_not_yet),
                                Modifier.align(if (dx > 0) Alignment.TopStart else Alignment.TopEnd).padding(20.dp)
                                    .graphicsLayer { alpha = strength }
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (dx > 0) colorOf(row.item) else MaterialTheme.colorScheme.outline)
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                color = MaterialTheme.colorScheme.surface, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
            // The same choices as buttons, for one-handed use and screen readers
            Row(
                Modifier.fillMaxWidth().padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically,
            ) {
                DeckButton(stringResource(R.string.check_not_yet), enabled = row != null, filled = false, onClick = { decide(false) }) { Icon(Icons.Filled.Close, null, Modifier.size(30.dp)) }
                DeckButton(stringResource(R.string.undo), enabled = history.isNotEmpty(), filled = false, small = true, onClick = { undo() }) { Icon(androidx.compose.ui.res.painterResource(R.drawable.ic_undo), null) }
                DeckButton(stringResource(R.string.check_have), enabled = row != null, filled = true, onClick = { decide(true) }) { Icon(Icons.Filled.Check, null, Modifier.size(30.dp)) }
            }
        }
    }
}

@Composable
private fun DeckButton(label: String, enabled: Boolean, filled: Boolean, small: Boolean = false, onClick: () -> Unit, icon: @Composable () -> Unit) {
    // The circle and its word are one control, named by the word (the word alone used to do nothing)
    Column(
        Modifier.clip(RoundedCornerShape(16.dp)).clickable(enabled = enabled, role = androidx.compose.ui.semantics.Role.Button, onClickLabel = label, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = label }.padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val size = if (small) 48.dp else 68.dp
        if (filled) FilledIconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(size).clearAndSetSemantics { }) { icon() }
        else OutlinedIconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(size).clearAndSetSemantics { }, colors = IconButtonDefaults.outlinedIconButtonColors()) { icon() }
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** One card in the deck: its picture (the card itself where downloaded, in colour so it can be recognised), name and place. */
@Composable
private fun DeckCard(row: ItemRow, ja: Boolean) {
    val cards by io.github.thisisartman.collectibles.photo.CardPictures.have.collectAsStateWithLifecycle()
    val code = row.item.code
    Column(Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(Modifier.weight(1f, fill = false), contentAlignment = Alignment.Center) {
            when {
                row.item.type == ItemType.MANHOLE_CARD && code != null && code in cards -> WholeCard(code) { Seal("", colorOf(row.item), 180.dp, icon = iconOf(row.item)) }
                else -> Seal("", colorOf(row.item), 180.dp, icon = iconOf(row.item))
            }
        }
        Spacer(Modifier.height(16.dp))
        val (title, sub) = if (row.item.type == ItemType.MANHOLE_CARD) row.item.cardTitle(ja) else row.item.displayName(ja) to null
        Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        listOfNotNull(sub, row.item.nameJa.takeIf { !ja && it != row.item.displayName(ja) }, row.item.municipality)
            .distinct().forEach { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center) }
    }
}

/** A downloaded card picture, whole and as large as the space allows (the deck shows one card at a time). */
@Composable
private fun WholeCard(code: String, fallback: @Composable () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var bmp by remember(code) { mutableStateOf<android.graphics.Bitmap?>(null) }
    var failed by remember(code) { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(code) {
        bmp = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                val f = io.github.thisisartman.collectibles.photo.CardPictures.file(context, code)
                val o = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                android.graphics.BitmapFactory.decodeFile(f.path, o)
                var sample = 1
                while (o.outHeight / (sample * 2) >= 1400) sample *= 2
                android.graphics.BitmapFactory.decodeFile(f.path, android.graphics.BitmapFactory.Options().apply { inSampleSize = sample })
            }.getOrNull()
        }
        failed = bmp == null
    }
    val b = bmp
    when {
        b != null -> androidx.compose.foundation.Image(
            b.asImageBitmap(), contentDescription = null, contentScale = androidx.compose.ui.layout.ContentScale.Fit,
            modifier = Modifier.aspectRatio(b.width.toFloat() / b.height).clip(RoundedCornerShape(12.dp)),
        )
        failed -> fallback()
    }
}
