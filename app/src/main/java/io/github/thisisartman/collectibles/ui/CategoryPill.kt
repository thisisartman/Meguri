package io.github.thisisartman.collectibles.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.thisisartman.collectibles.R
import io.github.thisisartman.collectibles.data.ItemType

/**
 * Picks one category or "All categories": a pill with the choice and a drop-down arrow, opening Android's menu with the
 * chosen line highlighted (owner's option B, 2026-11-09). Used by Proximity alert settings and, in place of a scrolling
 * row of chips, by Nearby's filter and search (owner, 2026-11-09).
 */
@Composable
fun CategoryPill(
    selected: ItemType?,
    types: List<ItemType>,
    label: @Composable (ItemType) -> String,
    onSelect: (ItemType?) -> Unit,
    modifier: Modifier = Modifier,
    trailing: @Composable (ItemType) -> String? = { null },
) {
    var open by remember { mutableStateOf(false) }
    val every = stringResource(R.string.alerts_every)
    // The pill is 40 dp to look; its touch target is 48 dp
    Box(modifier.minimumInteractiveComponentSize()) {
        // The same 12 dp inside on both ends (the arrow's own box carries 6 dp of it)
        Row(
            Modifier.clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.secondaryContainer)
                .clickable(role = Role.DropdownList) { open = true }
                .heightIn(min = 40.dp)
                .padding(start = 12.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PickerMark(selected, MaterialTheme.colorScheme.onSecondaryContainer)
            Spacer(Modifier.width(8.dp))
            Text(
                selected?.let { label(it) } ?: every, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            val chosen = Modifier.background(MaterialTheme.colorScheme.secondaryContainer)
            DropdownMenuItem(
                text = { Text(every) }, leadingIcon = { PickerMark(null, MaterialTheme.colorScheme.onSurfaceVariant) },
                onClick = { open = false; onSelect(null) },
                modifier = if (selected == null) chosen else Modifier,
            )
            types.forEach { t ->
                val extra = trailing(t)
                DropdownMenuItem(
                    text = { Text(label(t)) }, leadingIcon = { PickerMark(t, MaterialTheme.colorScheme.onSurfaceVariant) },
                    trailingIcon = extra?.let { e -> { Text(e, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } },
                    onClick = { open = false; onSelect(t) },
                    modifier = if (selected == t) chosen else Modifier,
                )
            }
        }
    }
}

/** In front of a name: the layers icon for All categories, else the category's dot, in the same 18 dp slot so names line up. */
@Composable
internal fun PickerMark(type: ItemType?, tint: Color) {
    Box(Modifier.size(18.dp), contentAlignment = Alignment.Center) {
        if (type == null) Icon(painterResource(R.drawable.ic_all_categories), contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        else Box(Modifier.size(10.dp).clip(CircleShape).background(LocalTypeColors.current.of(type)))
    }
}
