package io.github.thisisartman.collectibles.ui

import android.graphics.Color as AColor
import android.os.Bundle
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import io.github.thisisartman.collectibles.data.ItemType
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point

/** One item on a map. */
data class MapPin(val id: String, val lat: Double, val lng: Double, val type: ItemType, val collected: Boolean)

/**
 * The map (#47). The offline Niigata pack inside the trip area (#9, [OfflineMap]), online OpenFreeMap tiles elsewhere.
 * Pins: filled in the type's colour when collected, hollow when still to get. [onPinTap] gets the item id.
 */
@Composable
fun MeguriMap(
    pins: List<MapPin>,
    here: Pair<Double, Double>?,
    modifier: Modifier = Modifier,
    interactive: Boolean = true,
    onPinTap: (String) -> Unit = {},
    onMapTap: () -> Unit = {},
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val dark = isSystemInDarkTheme()
    val japanese = isJapanese()
    // Offline inside the trip area, online elsewhere; decided once, from where you are or the first pin
    val offline = remember {
        val c = here ?: pins.firstOrNull()?.let { it.lat to it.lng }
        c == null || OfflineMap.covers(c.first, c.second)
    }
    val types = LocalTypeColors.current
    val tapPin by rememberUpdatedState(onPinTap)
    val tapMap by rememberUpdatedState(onMapTap)
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var style by remember { mutableStateOf<Style?>(null) }
    val mapView = remember {
        MapLibre.getInstance(context)
        MapView(context).apply { onCreate(Bundle()) }
    }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> {}
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); mapView.onStop(); mapView.onDestroy() }
    }
    LaunchedEffect(mapView, dark, japanese) {
        val offlineStyle = if (offline) {
            runCatching { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { OfflineMap.styleJson(context, dark, japanese) } }.getOrNull()
        } else null
        mapView.getMapAsync { m ->
            map = m
            m.uiSettings.apply {
                isScrollGesturesEnabled = interactive; isZoomGesturesEnabled = interactive
                isRotateGesturesEnabled = false; isTiltGesturesEnabled = false
                isAttributionEnabled = true; isLogoEnabled = false
            }
            val builder = offlineStyle?.let { Style.Builder().fromJson(it) } ?: Style.Builder().fromUri(if (dark) STYLE_DARK else STYLE_LIGHT)
            m.setStyle(builder) { s ->
                // Under the pins, larger and soft, so a pin where you stand stays visible
                s.addSource(GeoJsonSource(SRC_HERE))
                s.addLayer(
                    CircleLayer(LAYER_HERE_HALO, SRC_HERE).withProperties(
                        PropertyFactory.circleRadius(Expression.literal(16f)),
                        PropertyFactory.circleColor(Expression.color(0x332A6BE0)),
                    ),
                )
                s.addLayer(
                    CircleLayer(LAYER_HERE, SRC_HERE).withProperties(
                        PropertyFactory.circleRadius(Expression.literal(6f)),
                        PropertyFactory.circleColor(Expression.color(0xFF2A6BE0.toInt())),
                        PropertyFactory.circleStrokeWidth(Expression.literal(2f)),
                        PropertyFactory.circleStrokeColor(Expression.color(AColor.WHITE)),
                    ),
                )
                s.addSource(GeoJsonSource(SRC))
                s.addLayer(
                    CircleLayer(LAYER, SRC).withProperties(
                        PropertyFactory.circleRadius(Expression.literal(7f)),
                        PropertyFactory.circleStrokeWidth(Expression.literal(3f)),
                        PropertyFactory.circleStrokeColor(Expression.get("color")),
                        PropertyFactory.circleColor(
                            Expression.switchCase(Expression.get("collected"), Expression.get("color"), Expression.color(AColor.WHITE)),
                        ),
                    ),
                )
                style = s
            }
            m.addOnMapClickListener { point ->
                val hit = m.queryRenderedFeatures(m.projection.toScreenLocation(point), LAYER).firstOrNull()
                if (hit != null) tapPin(hit.getStringProperty("id")) else tapMap()
                true
            }
        }
    }
    // Pins and position follow the data; the camera is placed once, around you and the nearest pins.
    var placed by remember { mutableStateOf(false) }
    LaunchedEffect(style, pins, here) {
        val s = style ?: return@LaunchedEffect
        val m = map ?: return@LaunchedEffect
        s.getSourceAs<GeoJsonSource>(SRC)?.setGeoJson(
            FeatureCollection.fromFeatures(
                pins.map { p ->
                    Feature.fromGeometry(Point.fromLngLat(p.lng, p.lat)).apply {
                        addStringProperty("id", p.id)
                        addBooleanProperty("collected", p.collected)
                        addStringProperty("color", colorHex(types.of(p.type)))
                    }
                },
            ),
        )
        s.getSourceAs<GeoJsonSource>(SRC_HERE)?.setGeoJson(
            FeatureCollection.fromFeatures(listOfNotNull(here?.let { Feature.fromGeometry(Point.fromLngLat(it.second, it.first)) })),
        )
        if (!placed && (here != null || pins.isNotEmpty())) {
            placed = true
            val focus = (here?.let { h -> pins.sortedBy { dist2(it, h) }.take(8).map { LatLng(it.lat, it.lng) } + LatLng(h.first, h.second) }
                ?: pins.map { LatLng(it.lat, it.lng) })
            if (focus.size >= 2) {
                m.moveCamera(CameraUpdateFactory.newLatLngBounds(LatLngBounds.Builder().includes(focus).build(), 80))
            } else {
                m.cameraPosition = CameraPosition.Builder().target(focus.first()).zoom(13.0).build()
            }
        }
    }
    AndroidView(factory = { mapView }, modifier = modifier)
}

private fun dist2(p: MapPin, h: Pair<Double, Double>) = (p.lat - h.first).let { it * it } + (p.lng - h.second).let { it * it }

private fun colorHex(c: Color) = String.format("#%06X", c.toArgb() and 0xFFFFFF)

private const val SRC = "items"
private const val LAYER = "items-pins"
private const val SRC_HERE = "here"
private const val LAYER_HERE = "here-dot"
private const val LAYER_HERE_HALO = "here-halo"
private const val STYLE_LIGHT = "https://tiles.openfreemap.org/styles/liberty"
private const val STYLE_DARK = "https://tiles.openfreemap.org/styles/dark"
