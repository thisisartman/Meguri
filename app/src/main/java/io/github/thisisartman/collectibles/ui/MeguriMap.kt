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
    // Each increase moves the camera back to you (the recentre button, owner 2026-11-06)
    recenter: Int = 0,
    // Each increase fits the camera to the pins shown (search "Show on map", #67)
    fitTick: Int = 0,
    // When set, the camera is remembered under this name: coming back to the page puts the map where it was
    // instead of re-framing it (owner, 2026-11-07: the transition back to Nearby)
    memoryKey: String? = null,
    // A planned trip's line, station to station (#46); the camera fits it when the map first opens
    route: List<Pair<Double, Double>> = emptyList(),
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
    val hollow = androidx.compose.material3.MaterialTheme.colorScheme.surface.toArgb()
    val routeColor = androidx.compose.material3.MaterialTheme.colorScheme.primary.toArgb()
    val tapPin by rememberUpdatedState(onPinTap)
    val tapMap by rememberUpdatedState(onMapTap)
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var style by remember { mutableStateOf<Style?>(null) }
    // Until the style has drawn, the map shows this colour: the page's own background, not MapLibre's light grey,
    // which flashed in dark mode when coming back to Nearby (screen recording on the Poco, 2026-11-07)
    val loadColor = androidx.compose.material3.MaterialTheme.colorScheme.background.toArgb()
    val mapView = remember {
        MapLibre.getInstance(context)
        val options = org.maplibre.android.maps.MapLibreMapOptions.createFromAttributes(context).foregroundLoadColor(loadColor)
        MapView(context, options).apply { setBackgroundColor(loadColor); onCreate(Bundle()) }
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
            memoryKey?.let { key ->
                CameraMemory.saved[key]?.let { m.cameraPosition = it }
                m.addOnCameraIdleListener { CameraMemory.saved[key] = m.cameraPosition }
            }
            m.uiSettings.apply {
                isScrollGesturesEnabled = interactive; isZoomGesturesEnabled = interactive
                isRotateGesturesEnabled = false; isTiltGesturesEnabled = false
                isAttributionEnabled = true; isLogoEnabled = false
                // The map's credit button sat bottom-left under the Nearby sheet (2026-11-07 review): put it top left,
                // just under the search bar, where it can be seen and tapped
                val d = context.resources.displayMetrics.density
                attributionGravity = android.view.Gravity.TOP or android.view.Gravity.START
                setAttributionMargins((16 * d).toInt(), (128 * d).toInt(), 0, 0)
            }
            val builder = offlineStyle?.let { Style.Builder().fromJson(it) } ?: Style.Builder().fromUri(if (dark) STYLE_DARK else STYLE_LIGHT)
            m.setStyle(builder) { s ->
                // Place names in one language (owner, 2026-11-07: the Japanese name belongs on the card and the
                // page, not under every map label). The online OpenFreeMap styles write "{latin}\n{nonlatin}";
                // every label that shows a name gets one name instead. Road numbers ("ref") are left alone.
                val oneName = if (japanese)
                    Expression.coalesce(Expression.get("name:ja"), Expression.get("name:nonlatin"), Expression.get("name"))
                else
                    Expression.coalesce(Expression.get("name:en"), Expression.get("name:latin"), Expression.get("name"))
                s.layers.filterIsInstance<org.maplibre.android.style.layers.SymbolLayer>().forEach { layer ->
                    val tf = layer.textField.expression?.toString() ?: layer.textField.value?.toString() ?: return@forEach
                    if ("name" in tf && "ref" !in tf) layer.setProperties(PropertyFactory.textField(oneName))
                }
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
                // The trip's line, under the pins, in the theme's primary colour
                s.addSource(GeoJsonSource(SRC_ROUTE))
                s.addLayer(
                    org.maplibre.android.style.layers.LineLayer(LAYER_ROUTE, SRC_ROUTE).withProperties(
                        PropertyFactory.lineColor(Expression.color(routeColor)),
                        PropertyFactory.lineWidth(Expression.literal(4f)),
                        PropertyFactory.lineOpacity(Expression.literal(0.85f)),
                        PropertyFactory.lineCap(org.maplibre.android.style.layers.Property.LINE_CAP_ROUND),
                        PropertyFactory.lineJoin(org.maplibre.android.style.layers.Property.LINE_JOIN_ROUND),
                    ),
                )
                s.addSource(GeoJsonSource(SRC))
                s.addLayer(
                    CircleLayer(LAYER, SRC).withProperties(
                        // Small at regional zoom, so a line of stations doesn't hide the place names; full size near you
                        PropertyFactory.circleRadius(
                            Expression.interpolate(Expression.linear(), Expression.zoom(), Expression.stop(8, 2f), Expression.stop(11, 4f), Expression.stop(13, 7f)),
                        ),
                        PropertyFactory.circleStrokeWidth(
                            Expression.interpolate(Expression.linear(), Expression.zoom(), Expression.stop(8, 1f), Expression.stop(13, 3f)),
                        ),
                        PropertyFactory.circleStrokeColor(Expression.get("color")),
                        PropertyFactory.circleColor(
                            // Hollow pins take the page colour, so in dark mode "still to get" isn't the brightest thing on the map
                            Expression.switchCase(Expression.get("collected"), Expression.get("color"), Expression.color(hollow)),
                        ),
                    ),
                )
                style = s
            }
            // A 48 dp square around the finger, not a single point (owner, 2026-11-07: pins were hard to hit);
            // when several pins fall inside, the one nearest the finger wins
            val slop = 24f * context.resources.displayMetrics.density
            m.addOnMapClickListener { point ->
                val p = m.projection.toScreenLocation(point)
                val box = android.graphics.RectF(p.x - slop, p.y - slop, p.x + slop, p.y + slop)
                val hit = m.queryRenderedFeatures(box, LAYER).minByOrNull { f ->
                    val g = f.geometry() as? Point ?: return@minByOrNull Float.MAX_VALUE
                    val s = m.projection.toScreenLocation(LatLng(g.latitude(), g.longitude()))
                    (s.x - p.x) * (s.x - p.x) + (s.y - p.y) * (s.y - p.y)
                }
                if (hit != null) tapPin(hit.getStringProperty("id")) else tapMap()
                true
            }
        }
    }
    // Pins and position follow the data; the camera is placed once, around you and the nearest pins.
    // A remembered camera counts as placed (it was placed on an earlier visit)
    var placed by remember { mutableStateOf(memoryKey != null && CameraMemory.saved[memoryKey] != null) }
    var placedAt by remember { mutableStateOf(if (placed) memoryKey?.let { CameraMemory.placedAt[it] } else null) }
    LaunchedEffect(placedAt) { memoryKey?.let { k -> placedAt?.let { CameraMemory.placedAt[k] = it } } }
    var fittedRoute by remember { mutableStateOf<List<Pair<Double, Double>>>(emptyList()) }
    LaunchedEffect(style, pins, here, route) {
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
        s.getSourceAs<GeoJsonSource>(SRC_ROUTE)?.setGeoJson(
            FeatureCollection.fromFeatures(
                if (route.size >= 2) listOf(Feature.fromGeometry(org.maplibre.geojson.LineString.fromLngLats(route.map { Point.fromLngLat(it.second, it.first) })))
                else emptyList(),
            ),
        )
        // A trip map frames the whole line, once
        if (route.size >= 2 && route != fittedRoute) {
            placed = true
            fittedRoute = route
            m.moveCamera(CameraUpdateFactory.newLatLngBounds(LatLngBounds.Builder().includes(route.map { LatLng(it.first, it.second) }).build(), 60))
            return@LaunchedEffect
        }
        // Place once, and again when you have moved far (over about 5 km) from where it was placed
        val movedFar = here != null && placedAt != null && dist2(MapPin("", placedAt!!.first, placedAt!!.second, ItemType.EKI_STAMP, false), here) > 0.0025
        if ((!placed || movedFar) && (here != null || pins.isNotEmpty())) {
            placed = true
            placedAt = here
            val focus = (here?.let { h -> pins.sortedBy { dist2(it, h) }.take(8).map { LatLng(it.lat, it.lng) } + LatLng(h.first, h.second) }
                ?: pins.map { LatLng(it.lat, it.lng) })
            if (focus.size >= 2) {
                m.moveCamera(CameraUpdateFactory.newLatLngBounds(LatLngBounds.Builder().includes(focus).build(), 80))
            } else {
                m.cameraPosition = CameraPosition.Builder().target(focus.first()).zoom(13.0).build()
            }
        }
    }
    LaunchedEffect(fitTick) {
        if (fitTick == 0) return@LaunchedEffect
        val m = map ?: return@LaunchedEffect
        val pts = pins.map { LatLng(it.lat, it.lng) }
        when {
            // Room for the search bar above and the sheet's top edge below, so no pin sits under either
            pts.size >= 2 -> {
                val d = context.resources.displayMetrics.density
                m.animateCamera(CameraUpdateFactory.newLatLngBounds(LatLngBounds.Builder().includes(pts).build(),
                    (40 * d).toInt(), (130 * d).toInt(), (40 * d).toInt(), (60 * d).toInt()), 700)
            }
            pts.size == 1 -> m.animateCamera(CameraUpdateFactory.newLatLngZoom(pts.first(), 15.0), 700)
        }
    }
    LaunchedEffect(recenter) {
        if (recenter == 0) return@LaunchedEffect
        val m = map ?: return@LaunchedEffect
        val h = here ?: return@LaunchedEffect
        m.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(h.first, h.second), 15.0), 600)
    }
    AndroidView(factory = { mapView }, modifier = modifier)
}

/** Cameras kept while the app runs, by map name, so going to another page and back does not move the map. */
private object CameraMemory {
    val saved = mutableMapOf<String, CameraPosition>()
    val placedAt = mutableMapOf<String, Pair<Double, Double>>()
}

private fun dist2(p: MapPin, h: Pair<Double, Double>) = (p.lat - h.first).let { it * it } + (p.lng - h.second).let { it * it }

private fun colorHex(c: Color) = String.format("#%06X", c.toArgb() and 0xFFFFFF)

private const val SRC = "items"
private const val LAYER = "items-pins"
private const val SRC_HERE = "here"
private const val SRC_ROUTE = "route"
private const val LAYER_ROUTE = "route-line"
private const val LAYER_HERE = "here-dot"
private const val LAYER_HERE_HALO = "here-halo"
private const val STYLE_LIGHT = "https://tiles.openfreemap.org/styles/liberty"
private const val STYLE_DARK = "https://tiles.openfreemap.org/styles/dark"
