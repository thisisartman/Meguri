package io.github.thisisartman.collectibles.ui

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.thisisartman.collectibles.R

/**
 * Settings, Catalogue, Regions (0.5): each prefecture Meguri has data for, with what is in it, its sizes, and
 * Download, Update or Remove. The catalogue packs are a few KB; the offline map is tens of MB, so it can be left off.
 */
@Composable
fun RegionsPage(vm: MoreViewModel) {
    val context = LocalContext.current
    val ja = isJapanese()
    val index by vm.packIndex.collectAsState()
    val loading by vm.packIndexLoading.collectAsState()
    val progress by vm.packProgress.collectAsState()
    val error by vm.packError.collectAsState()
    val installed by vm.packs.installed.collectAsState()
    LaunchedEffect(Unit) { vm.refreshPackIndex() }
    var confirmRemove by remember { mutableStateOf<String?>(null) }
    fun size(bytes: Long) = Formatter.formatShortFileSize(context, bytes)

    Column(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            stringResource(R.string.regions_intro, size(vm.packs.bytesOnPhone())),
            Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        error?.let {
            Text(stringResource(R.string.regions_failed), Modifier.padding(horizontal = 4.dp), color = MaterialTheme.colorScheme.error)
        }
        val idx = index
        if (idx == null) {
            if (loading) CircularProgressIndicator(Modifier.padding(16.dp))
            else Text(stringResource(R.string.regions_offline), Modifier.padding(4.dp))
            return@Column
        }
        val updates = vm.packs.updates(idx)
        if (updates.isNotEmpty()) Button(
            onClick = { vm.packError.value = null; vm.updatePacks() }, enabled = progress == null,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) { Text(stringResource(R.string.regions_update_all, size(updates.sumOf { it.bytes }))) }

        // Prefectures on this phone first, so yours aren't at the bottom of the list
        idx.prefectures.sortedBy { pref -> if (idx.of(pref).any { it.id in installed }) 0 else 1 }.forEach { pref ->
            val packs = idx.of(pref)
            val catalogue = packs.filter { !it.isMap }
            val map = packs.firstOrNull { it.isMap }
            val have = packs.filter { it.id in installed }
            // Read once: the bar's lambda runs later, after the download may have cleared it
            val fraction = progress?.takeIf { it.first == pref }?.second
            val busy = fraction != null
            var withMap by rememberSaveable(pref) { mutableStateOf(map != null) }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Text(prefectureName(pref, ja), style = MaterialTheme.typography.titleMedium)
                    Text(
                        catalogue.filter { it.core }.sortedBy { it.type?.ordinal }.joinToString(" · ") { p ->
                            context.getString(p.type!!.pluralLabel()).replace(" ", "\u00A0") + "\u00A0" + p.items
                        },
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    catalogue.filter { !it.core }.takeIf { it.isNotEmpty() }?.let { extras ->
                        Text(
                            stringResource(R.string.regions_extras, extras.joinToString(" · ") { p -> context.getString(p.type!!.pluralLabel()).replace(" ", "\u00A0") + "\u00A0" + p.items }),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (busy) {
                        LinearProgressIndicator(progress = { fraction ?: 1f }, modifier = Modifier.fillMaxWidth().padding(top = 10.dp))
                        return@Column
                    }
                    when {
                        have.isEmpty() -> {
                            if (map != null) Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(withMap, { withMap = it })
                                Text(stringResource(R.string.regions_with_map, size(map.bytes)), style = MaterialTheme.typography.bodyMedium)
                            }
                            Button(
                                onClick = { vm.packError.value = null; vm.downloadPrefecture(pref, withMap) }, enabled = progress == null,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(top = 4.dp),
                            ) {
                                val bytes = catalogue.filter { it.core }.sumOf { it.bytes } + if (withMap && map != null) map.bytes else 0
                                Text(stringResource(R.string.regions_download, size(bytes)))
                            }
                        }
                        else -> {
                            Text(
                                stringResource(if (map != null && map.id !in installed) R.string.regions_on_phone_no_map else R.string.regions_on_phone),
                                Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodyMedium,
                            )
                            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (map != null && map.id !in installed) OutlinedButton(
                                    onClick = { vm.packError.value = null; vm.downloadPrefecture(pref, withMap = true) }, enabled = progress == null,
                                    modifier = Modifier.heightIn(min = 48.dp),
                                ) { Text(stringResource(R.string.regions_add_map, size(map.bytes))) }
                                TextButton(onClick = { confirmRemove = pref }, enabled = progress == null, modifier = Modifier.heightIn(min = 48.dp)) {
                                    Text(stringResource(R.string.regions_remove))
                                }
                            }
                        }
                    }
                }
            }
        }
        Text(stringResource(R.string.regions_note), Modifier.padding(4.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }

    confirmRemove?.let { pref ->
        AlertDialog(
            onDismissRequest = { confirmRemove = null },
            title = { Text(stringResource(R.string.regions_remove_title, prefectureName(pref, ja))) },
            text = { Text(stringResource(R.string.regions_remove_text)) },
            confirmButton = { TextButton(onClick = { confirmRemove = null; vm.removePrefecture(pref) }) { Text(stringResource(R.string.regions_remove)) } },
            dismissButton = { TextButton(onClick = { confirmRemove = null }) { Text(stringResource(android.R.string.cancel)) } },
        )
    }
}

