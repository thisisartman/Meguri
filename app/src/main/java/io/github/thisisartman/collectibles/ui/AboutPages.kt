package io.github.thisisartman.collectibles.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.thisisartman.collectibles.BuildConfig
import io.github.thisisartman.collectibles.R

/**
 * About, laid out like the open-source apps on the owner's phone (Fossify Gallery and Calendar, Breezy Weather,
 * Organic Maps; owner 2026-11-09): a header with icon, name and version, then credits, privacy and developer
 * sections. Every credit links to its project and to its licence.
 */
data class Credit(val name: String, @StringRes val use: Int, val licence: String?, val url: String?, val licenceUrl: String?)

private const val APACHE = "https://www.apache.org/licenses/LICENSE-2.0"
private const val OFL = "https://openfontlicense.org"
private const val CC0 = "https://creativecommons.org/publicdomain/zero/1.0/"

/** Map, places and catalogue data. */
val SOURCES = listOf(
    Credit("OpenStreetMap", R.string.credit_osm, "ODbL", "https://www.openstreetmap.org/copyright", "https://opendatacommons.org/licenses/odbl/"),
    Credit("OpenFreeMap", R.string.credit_openfreemap, "OpenMapTiles", "https://openfreemap.org", "https://github.com/openmaptiles/openmaptiles/blob/master/LICENSE.md"),
    Credit("Protomaps", R.string.credit_protomaps, "BSD-3-Clause", "https://protomaps.com", "https://github.com/protomaps/basemaps/blob/main/LICENSE.md"),
    Credit("Nominatim", R.string.credit_nominatim, "ODbL", "https://nominatim.org", "https://operations.osmfoundation.org/policies/nominatim/"),
    Credit("Eki kara Manhole", R.string.credit_ekikara, "© GKP", "https://ekikaramanhole.whitebeach.org/", "https://www.gk-p.jp/mhcard/"),
    Credit("国土交通省 (MLIT): dam cards", R.string.credit_mlit_dams, "PDL 1.0", "https://www.mlit.go.jp/river/kankyo/campaign/shunnkan/damcard.html", "https://www.digital.go.jp/resources/open_data/public_data_license_v1.0"),
    // Roadside stations: MLIT's list.xlsx (as of R8.9.4), positions from OpenStreetMap (build_michi.py, 2026-11-08)
    Credit("国土交通省 (MLIT): roadside stations", R.string.credit_mlit_michi, "PDL 1.0", "https://www.mlit.go.jp/road/Michi-no-Eki/list.html", "https://www.digital.go.jp/resources/open_data/public_data_license_v1.0"),
    // A few dam positions came from Wikidata (loadgeo.py: wikidata_st_dams.json, wikidata_gongendo.json)
    Credit("Wikidata", R.string.credit_wikidata, "CC0 1.0", "https://www.wikidata.org", CC0),
    Credit("国土地理院 (GSI)", R.string.credit_gsi, "Terms", "https://www.gsi.go.jp/kikakuchousei/kikakuchousei40182.html", "https://www.gsi.go.jp/LAW/2930-index.html"),
)

/** Fonts and icon sets. */
val ART = listOf(
    Credit("Noto Sans", R.string.credit_noto, "SIL OFL 1.1", "https://fonts.google.com/noto/specimen/Noto+Sans", OFL),
    Credit("M PLUS Rounded 1c", R.string.credit_mplus, "SIL OFL 1.1", "https://fonts.google.com/specimen/M+PLUS+Rounded+1c", OFL),
    Credit("Material Icons", R.string.credit_material_icons, "Apache 2.0", "https://fonts.google.com/icons", APACHE),
    Credit("Maki", R.string.credit_maki, "CC0 1.0", "https://github.com/mapbox/maki", CC0),
    Credit("Temaki", R.string.credit_temaki, "CC0 1.0", "https://github.com/rapideditor/temaki", CC0),
)

/** Libraries the app is built with. */
val LIBRARIES = listOf(
    Credit("Jetpack Compose and Material 3", R.string.credit_compose, "Apache 2.0", "https://developer.android.com/compose", APACHE),
    Credit("AndroidX (Activity, Lifecycle, Navigation)", R.string.credit_androidx, "Apache 2.0", "https://developer.android.com/jetpack/androidx", APACHE),
    Credit("Room", R.string.credit_room, "Apache 2.0", "https://developer.android.com/jetpack/androidx/releases/room", APACHE),
    Credit("WorkManager", R.string.credit_work, "Apache 2.0", "https://developer.android.com/jetpack/androidx/releases/work", APACHE),
    Credit("Hilt (Dagger)", R.string.credit_hilt, "Apache 2.0", "https://dagger.dev/hilt/", APACHE),
    Credit("kotlinx.serialization", R.string.credit_serialization, "Apache 2.0", "https://github.com/Kotlin/kotlinx.serialization", APACHE),
    Credit("MapLibre Native", R.string.credit_maplibre, "BSD-2-Clause", "https://github.com/maplibre/maplibre-native", "https://github.com/maplibre/maplibre-native/blob/main/LICENSE.md"),
    Credit("Google Play services location", R.string.credit_play_location, "Android SDK terms", "https://developers.google.com/android/guides/overview", "https://developer.android.com/studio/terms"),
    Credit("ML Kit (document scanner, Japanese text)", R.string.credit_mlkit, "ML Kit terms", "https://developers.google.com/ml-kit", "https://developers.google.com/ml-kit/terms"),
)

fun openLink(context: Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

/** Icon, name and version at the top of About; tapping the version copies it (for bug reports). */
@Composable
fun AboutHeader() {
    val context = LocalContext.current
    val version = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
    val copied = stringResource(R.string.about_version_copied)
    Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(72.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(R.drawable.ic_launcher_foreground), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(72.dp))
        }
        Text(stringResource(R.string.about_app_name), Modifier.padding(top = 12.dp), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text(
            stringResource(R.string.about_version, version),
            Modifier.clip(MaterialTheme.shapes.small).clickable {
                (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Meguri", version))
                Toast.makeText(context, copied, Toast.LENGTH_SHORT).show()
            }.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** One credit: name (opens the project), what it is used for, and the licence (opens the licence). */
@Composable
fun CreditRow(c: Credit) {
    val context = LocalContext.current
    Row(
        Modifier.fillMaxWidth().then(if (c.url != null) Modifier.clickable { openLink(context, c.url) } else Modifier).heightIn(min = 64.dp).padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(c.name, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
            Text(stringResource(c.use), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (c.url != null) Text(c.url.removePrefix("https://").removeSuffix("/"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (c.licence != null && c.licenceUrl != null) TextButton(onClick = { openLink(context, c.licenceUrl) }) { Text(c.licence, maxLines = 1) }
    }
    HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
fun CreditList(intro: String?, credits: List<Credit>) {
    Column(verticalArrangement = Arrangement.Top) {
        if (intro != null) Text(
            intro, Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        credits.forEach { CreditRow(it) }
    }
}

/** What stays on the phone and what goes online, each service linked. */
@Composable
fun PrivacyPage() {
    Text(
        stringResource(R.string.privacy_intro), Modifier.padding(16.dp),
        style = MaterialTheme.typography.bodyMedium,
    )
    Text(
        stringResource(R.string.privacy_online), Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
    )
    Text(
        stringResource(R.string.privacy_online_sub), Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    listOf(
        Credit("OpenFreeMap", R.string.privacy_tiles, "Privacy", "https://openfreemap.org", "https://openfreemap.org/privacy/"),
        Credit("Nominatim", R.string.privacy_places, "Policy", "https://nominatim.org", "https://osmfoundation.org/wiki/Privacy_Policy"),
        // Region downloads come from the meguri-data releases (was missing from this list, found 2026-11-20)
        Credit("GitHub", R.string.privacy_regions, "Privacy", "https://github.com/thisisartman/meguri-data", "https://docs.github.com/site-policy/privacy-policies/github-general-privacy-statement"),
        Credit("GitHub", R.string.privacy_reports, "Privacy", "https://github.com/thisisartman/Meguri/issues", "https://docs.github.com/site-policy/privacy-policies/github-general-privacy-statement"),
        Credit("Eki kara Manhole", R.string.privacy_cards, "Site", "https://ekikaramanhole.whitebeach.org/", "https://ekikaramanhole.whitebeach.org/"),
        Credit("Google Maps", R.string.privacy_directions, "Privacy", "https://www.google.com/maps", "https://policies.google.com/privacy"),
        // Backup places added in 0.6.0 (#137)
        Credit("Google Drive", R.string.privacy_drive, "Privacy", "https://drive.google.com", "https://policies.google.com/privacy"),
        Credit("WebDAV", R.string.privacy_webdav, null, null, null),
        Credit("Google Play services", R.string.privacy_play, "Privacy", "https://developers.google.com/ml-kit", "https://policies.google.com/privacy"),
    ).forEach { CreditRow(it) }
}
