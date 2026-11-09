package io.github.thisisartman.collectibles.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.thisisartman.collectibles.R
import io.github.thisisartman.collectibles.data.ItemType
import io.github.thisisartman.collectibles.data.Prefecture
import io.github.thisisartman.collectibles.data.Prefectures
import kotlinx.coroutines.launch

/** Where the first-open onboarding is (#81, docs/PLAN-onboarding.md). It lives in the Nearby sheet, over the map. */
enum class OnboardingStep { START, PERMISSIONS, ASKING, REGIONS, LOADING, HAVE, FINISHED }

/**
 * The onboarding's state, hoisted so Nearby can size the sheet and shade the chosen prefectures on the map.
 * [already] is the "I already have some" choice.
 */
class OnboardingState {
    var step by mutableStateOf(OnboardingStep.START)
    var already by mutableStateOf(false)
    var chosen by mutableStateOf<Set<String>>(emptySet())
    /** Items ticked in the picker, in the order added (the stacked circles above Done). */
    var added by mutableStateOf<List<String>>(emptyList())

    /** What the picker shows now, for the map under it when the sheet is pulled down (owner's spec). */
    var pickerShown by mutableStateOf<List<io.github.thisisartman.collectibles.data.ItemRow>>(emptyList())

    /** Still showing after the catalogue has loaded: the picker and the closing sheet. */
    val afterLoad: Boolean get() = step == OnboardingStep.HAVE || step == OnboardingStep.FINISHED
}

@Composable
fun rememberOnboardingState() = remember { OnboardingState() }

/**
 * The most the sheet may cover for each step. It actually sits at its content's measured height, up to this (owner,
 * 2026-11-08: the sheet goes up and down with what it holds and snaps there, no empty space under the buttons).
 */
@Composable
fun onboardingPeek(state: OnboardingState): Dp {
    val screen = LocalConfiguration.current.screenHeightDp.dp
    return when (state.step) {
        OnboardingStep.START -> screen * 0.6f
        OnboardingStep.PERMISSIONS -> screen * 0.75f
        OnboardingStep.ASKING, OnboardingStep.LOADING -> screen * 0.4f
        OnboardingStep.REGIONS -> screen * 0.6f
        // The picker takes the screen (owner): it's a full search; pull it down to see the map
        // Opens fully expanded (a full search); pulled down, it rests here and the map above shows the results
        OnboardingStep.HAVE -> screen * 0.45f
        OnboardingStep.FINISHED -> screen * 0.5f
    }
}

/**
 * The onboarding in the Nearby sheet. Starting fresh: start, permissions, regions, then Nearby. Already have some:
 * the same, then the picker of what you have (for now Collection check, until the picker is built, #81 O3).
 */
@Composable
fun OnboardingSheet(
    state: OnboardingState,
    prefectures: List<Prefecture>,
    here: Pair<Double, Double>?,
    loadCounts: suspend () -> Map<String, Map<ItemType, Int>>,
    onLoad: suspend (Set<String>) -> Unit,
    onFinished: (already: Boolean) -> Unit,
    onLoadFile: () -> Unit,
    onPermissionsAnswered: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val ja = isJapanese()
    val scope = rememberCoroutineScope()
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        // Precise or approximate are both fine (owner): carry on either way, even if refused
        onPermissionsAnswered()
        state.step = OnboardingStep.REGIONS
    }
    Column(modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
        // Each step comes in, rather than replacing the last at the touch (owner, 2026-11-10: "I already have some"
        // moved on "the moment my finger touches the button")
        androidx.compose.animation.AnimatedContent(targetState = state.step, transitionSpec = Motion.step(), label = "onboarding") { step ->
        Column(Modifier.fillMaxWidth()) {
        when (step) {
            OnboardingStep.START -> {
                Text(stringResource(R.string.onb_welcome), style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(6.dp))
                Text(stringResource(R.string.onb_question), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(16.dp))
                Choice(stringResource(R.string.onb_fresh), stringResource(R.string.onb_fresh_sub)) { state.already = false; state.step = OnboardingStep.PERMISSIONS }
                Spacer(Modifier.height(10.dp))
                Choice(stringResource(R.string.onb_already), stringResource(R.string.onb_already_sub)) { state.already = true; state.step = OnboardingStep.PERMISSIONS }
                TextButton(onClick = onLoadFile, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.onb_file)) }
            }
            OnboardingStep.PERMISSIONS -> {
                Text(stringResource(R.string.onb_perm_title), style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(6.dp))
                Text(stringResource(R.string.onb_perm_sub), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                    Perm(R.drawable.ic_near_me, R.string.onb_perm_location, R.string.onb_perm_location_why)
                    if (Build.VERSION.SDK_INT >= 33) Perm(R.drawable.ic_notification, R.string.onb_perm_notify, R.string.onb_perm_notify_why)
                    Perm(R.drawable.ic_near_me, R.string.onb_perm_background, R.string.onb_perm_background_why)
                    Perm(R.drawable.ic_collect, R.string.onb_perm_photos, R.string.onb_perm_photos_why)
                }
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        state.step = OnboardingStep.ASKING
                        ask.launch(buildList {
                            add(Manifest.permission.ACCESS_FINE_LOCATION); add(Manifest.permission.ACCESS_COARSE_LOCATION)
                            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
                        }.toTypedArray())
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) { Text(stringResource(R.string.onb_next)) }
            }
            OnboardingStep.ASKING -> {
                // Covered by Android's own prompts; shown for the moment between them
                Text(stringResource(R.string.onb_asking), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text(stringResource(R.string.onb_asking_sub), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            OnboardingStep.REGIONS -> {
                var counts by remember { mutableStateOf<Map<String, Map<ItemType, Int>>>(emptyMap()) }
                LaunchedEffect(Unit) { counts = runCatching { loadCounts() }.getOrDefault(emptyMap()) }
                val (mine, rest) = remember(prefectures, counts, here) { Prefectures.ordered(prefectures, counts.keys, here) }
                // Your own prefecture is chosen to start with
                LaunchedEffect(mine) { if (mine != null && state.chosen.isEmpty()) state.chosen = setOf(mine.ja) }
                Text(stringResource(R.string.onb_regions_title), style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.onb_regions_sub), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                    if (mine != null) {
                        Group(stringResource(R.string.onb_regions_here))
                        Region(mine, counts[mine.ja], ja, mine.ja in state.chosen) { state.chosen = state.chosen.toggle(mine.ja) }
                    }
                    if (rest.isNotEmpty()) {
                        Group(stringResource(R.string.onb_regions_popular))
                        rest.forEach { p -> Region(p, counts[p.ja], ja, p.ja in state.chosen) { state.chosen = state.chosen.toggle(p.ja) } }
                    }
                    TextButton(
                        onClick = { state.chosen = (listOfNotNull(mine) + rest).map { it.ja }.toSet() },
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) { Text(stringResource(R.string.onb_regions_all)) }
                    Text(stringResource(R.string.onb_regions_more), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(8.dp))
                Button(
                    enabled = state.chosen.isNotEmpty(),
                    onClick = {
                        state.step = OnboardingStep.LOADING
                        scope.launch { runCatching { onLoad(state.chosen) }; onFinished(state.already) }
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) { Text(androidx.compose.ui.res.pluralStringResource(R.plurals.onb_load, state.chosen.size, state.chosen.size)) }
            }
            // The picker and the closing sheet are drawn by Nearby, which has the rows and the profile route
            OnboardingStep.HAVE, OnboardingStep.FINISHED -> {}
            OnboardingStep.LOADING -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(24.dp))
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.onb_loading), style = MaterialTheme.typography.titleMedium)
            }
        }
        }
        }
    }
}

private fun Set<String>.toggle(v: String) = if (v in this) this - v else this + v

@Composable
private fun Choice(title: String, sub: String, onClick: () -> Unit) {
    // Surface's own onClick: the ripple is clipped to the card and shows while the next step comes in
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Perm(icon: Int, title: Int, why: Int) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.Top) {
        Icon(painterResource(icon), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Column {
            Text(stringResource(title), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(why), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Group(text: String) {
    Text(text, Modifier.padding(top = 14.dp, bottom = 4.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun Region(p: Prefecture, counts: Map<ItemType, Int>?, ja: Boolean, on: Boolean, onToggle: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable(onClick = onToggle), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Start) {
        Checkbox(checked = on, onCheckedChange = { onToggle() })
        Column(Modifier.weight(1f)) {
            Text(p.name(ja), style = MaterialTheme.typography.bodyLarge)
            counts?.let { c ->
                val labels = c.entries.sortedByDescending { it.value }.map { (t, n) -> n to stringResource(if (n == 1) t.label() else t.pluralLabel()) }
                Text(
                    labels.joinToString(" · ") { (n, label) -> "$n $label" },
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
