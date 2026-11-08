package io.github.thisisartman.collectibles.ui

import androidx.compose.ui.unit.dp
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
    // A place whose screen position is reported as the camera moves, so a pin card can stay on its pin (#76)
    anchor: Pair<Double, Double>? = null,
    onAnchorScreen: (androidx.compose.ui.geometry.Offset?) -> Unit = {},
    // A map inside a card on a solid page: drawn as an ordinary view, since the default surface sits behind the
    // window and the page's background hid it completely (the profile page's map, 2026-11-07)
    inCard: Boolean = false,
    // Map-first start (#81, owner 2026-11-07): open on all of Japan, then fly in to you a moment after your
    // position arrives; without a position it stays on Japan. Pins alone never place the camera.
    startOnJapan: Boolean = false,
    // While true (the onboarding), stay on Japan and the chosen prefectures; fly in to you once it turns false
    holdOnJapan: Boolean = false,
    // Prefectures shaded on the map (the onboarding's region step, #81); the camera fits them above [bottomInset]
    highlight: List<io.github.thisisartman.collectibles.data.Prefecture> = emptyList(),
    bottomInset: androidx.compose.ui.unit.Dp = 0.dp,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    // The app's theme, not only the phone's: Light or Dark can be chosen in Appearance
    val dark = LocalAppDark.current
    val japanese = isJapanese()
    // Offline inside the trip area, online elsewhere; decided once, from where you are or the first pin
    val offline = remember {
        val c = here ?: pins.firstOrNull()?.let { it.lat to it.lng }
        c == null || OfflineMap.covers(context, c.first, c.second)
    }
    val types = LocalTypeColors.current
    val hollow = androidx.compose.material3.MaterialTheme.colorScheme.surface.toArgb()
    val routeColor = androidx.compose.material3.MaterialTheme.colorScheme.primary.toArgb()
    // A flat map (owner, 2026-11-08: the textures looked odd). Land is set apart from the page (owner, 2026-11-09,
    // S23 screenshot: in the page colour the search bar and sheet had no edge against the map): lighter than the
    // page in dark mode, a shade darker in light mode. Water is the land tinted with the accent.
    val page = androidx.compose.material3.MaterialTheme.colorScheme.background
    val ink = androidx.compose.material3.MaterialTheme.colorScheme.onBackground
    val land = androidx.compose.ui.graphics.lerp(page, ink, if (dark) 0.16f else 0.06f)
    val landColor = land.toArgb()
    val waterColor = androidx.compose.ui.graphics.lerp(
        land, androidx.compose.material3.MaterialTheme.colorScheme.primary, if (dark) 0.22f else 0.16f,
    ).toArgb()
    // On the lighter dark-mode land the style's grey roads would vanish, so they are lifted above it
    val roads = if (dark) androidx.compose.ui.graphics.lerp(land, ink, 0.18f).toArgb() to androidx.compose.ui.graphics.lerp(land, ink, 0.32f).toArgb() else null
    val tapPin by rememberUpdatedState(onPinTap)
    val tapMap by rememberUpdatedState(onMapTap)
    val anchorNow by rememberUpdatedState(anchor)
    val reportAnchor by rememberUpdatedState(onAnchorScreen)
    fun emitAnchor(m: MapLibreMap) {
        val a = anchorNow
        reportAnchor(a?.let { m.projection.toScreenLocation(LatLng(it.first, it.second)).let { p -> androidx.compose.ui.geometry.Offset(p.x, p.y) } })
    }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var style by remember { mutableStateOf<Style?>(null) }
    // Until the style has drawn, the map shows this colour: the page's own background, not MapLibre's light grey,
    // which flashed in dark mode when coming back to Nearby (screen recording on the Poco, 2026-11-07)
    val loadColor = landColor
    val mapView = remember {
        MapLibre.getInstance(context)
        val options = org.maplibre.android.maps.MapLibreMapOptions.createFromAttributes(context).foregroundLoadColor(loadColor).textureMode(inCard)
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
            m.addOnCameraMoveListener { emitAnchor(m) }
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
                flatten(s, landColor, waterColor, roads)
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
                // Chosen prefectures, shaded under everything else
                s.addSource(GeoJsonSource(SRC_HIGHLIGHT))
                s.addLayer(
                    org.maplibre.android.style.layers.FillLayer(LAYER_HIGHLIGHT, SRC_HIGHLIGHT).withProperties(
                        PropertyFactory.fillColor(Expression.color(routeColor)),
                        PropertyFactory.fillOpacity(Expression.literal(0.28f)),
                        PropertyFactory.fillOutlineColor(Expression.color(routeColor)),
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
    LaunchedEffect(map, anchor) { map?.let { emitAnchor(it) } }
    // Pins and position follow the data; the camera is placed once, around you and the nearest pins.
    // A remembered camera counts as placed (it was placed on an earlier visit)
    var placed by remember { mutableStateOf(memoryKey != null && CameraMemory.saved[memoryKey] != null) }
    var placedAt by remember { mutableStateOf(if (placed) memoryKey?.let { CameraMemory.placedAt[it] } else null) }
    LaunchedEffect(placedAt) { memoryKey?.let { k -> placedAt?.let { CameraMemory.placedAt[k] = it } } }
    var fittedRoute by remember { mutableStateOf<List<Pair<Double, Double>>>(emptyList()) }
    LaunchedEffect(style) {
        val m = map ?: return@LaunchedEffect
        if (startOnJapan && !placed && style != null) m.moveCamera(CameraUpdateFactory.newLatLngBounds(JAPAN, 0))
    }
    val hasHere = here != null
    val fitInsetPx = with(androidx.compose.ui.platform.LocalDensity.current) { bottomInset.roundToPx() }
    // Framed on you alone because the pins had not loaded yet: frame again once they arrive, unless you moved the map
    var framedWithoutPins by remember { mutableStateOf(false) }
    var userMoved by remember { mutableStateOf(false) }
    LaunchedEffect(map) {
        map?.addOnCameraMoveStartedListener { reason -> if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) userMoved = true }
    }
    val hasPins = pins.isNotEmpty()
    LaunchedEffect(style, hasHere, holdOnJapan, hasPins) {
        if (!startOnJapan || holdOnJapan || style == null || !hasHere) return@LaunchedEffect
        if (placed && !(framedWithoutPins && hasPins && !userMoved)) return@LaunchedEffect
        // A short pause once the position is known, so the map doesn't jump the moment it arrives
        kotlinx.coroutines.delay(300)
        val m = map ?: return@LaunchedEffect
        val h = here ?: return@LaunchedEffect
        placed = true
        placedAt = h
        val focus = pins.sortedBy { dist2(it, h) }.take(8).map { LatLng(it.lat, it.lng) } + LatLng(h.first, h.second)
        framedWithoutPins = focus.size < 2
        // Before the pins have loaded there is only you to frame; a bounds of one point throws. The sheet covers the
        // lower part, so the frame sits above it (Poco 2026-11-08: opened at street level with nothing in view)
        val d = context.resources.displayMetrics.density
        val update = if (focus.size >= 2) CameraUpdateFactory.newLatLngBounds(LatLngBounds.Builder().includes(focus).build(),
            (40 * d).toInt(), (140 * d).toInt(), (40 * d).toInt(), fitInsetPx + (40 * d).toInt())
        else CameraUpdateFactory.newLatLngZoom(LatLng(h.first, h.second), 14.0)
        m.animateCamera(update, 1400)
    }
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
        if ((!placed || movedFar) && (here != null || pins.isNotEmpty()) && !(startOnJapan && !placed)) {
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
    val insetPx = with(androidx.compose.ui.platform.LocalDensity.current) { bottomInset.roundToPx() }
    LaunchedEffect(style, highlight, insetPx) {
        val s = style ?: return@LaunchedEffect
        val m = map ?: return@LaunchedEffect
        s.getSourceAs<GeoJsonSource>(SRC_HIGHLIGHT)?.setGeoJson(
            FeatureCollection.fromFeatures(
                highlight.map { p ->
                    Feature.fromGeometry(
                        org.maplibre.geojson.MultiPolygon.fromLngLats(p.rings.map { ring -> listOf(ring.map { (x, y) -> Point.fromLngLat(x, y) }) }),
                    )
                },
            ),
        )
        // Fit what is chosen (or all of Japan) into the space above the sheet
        if (startOnJapan && !placed) {
            // Framed by each prefecture's main outline: Tokyo's islands reach 24.7°N and would frame most of Japan
            val pts = highlight.flatMap { it.mainRing() }.map { (x, y) -> LatLng(y, x) }
            val bounds = if (pts.size >= 2) LatLngBounds.Builder().includes(pts).build() else JAPAN
            m.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 40, 140, 40, insetPx + 40), 600)
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
                // Above whatever covers the map's lower part (the onboarding picker's sheet, #81)
                m.animateCamera(CameraUpdateFactory.newLatLngBounds(LatLngBounds.Builder().includes(pts).build(),
                    (40 * d).toInt(), (130 * d).toInt(), (40 * d).toInt(), maxOf((60 * d).toInt(), insetPx + (40 * d).toInt())), 700)
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

/** The four main islands and Okinawa's north, for the map-first start (#81). */
private const val SRC_HIGHLIGHT = "meguri-highlight"
private const val LAYER_HIGHLIGHT = "meguri-highlight-fill"

private val JAPAN: LatLngBounds = LatLngBounds.Builder().include(LatLng(30.0, 129.3)).include(LatLng(45.6, 146.0)).build()

/**
 * One flat look for every map source (OpenFreeMap online, Protomaps offline): hill shading, land cover textures,
 * parks, woods and buildings off; land and water in two flat colours. Roads and labels stay as the style draws them.
 */
private fun flatten(s: Style, land: Int, water: Int, roads: Pair<Int, Int>? = null) {
    // roads = (minor, major) colours; casings, tunnels and rail keep the style's own darker colours as outlines
    val major = Regex("highway|motorway|major|trunk|primary|link")
    val hide = Regex("hillshade|landcover|landuse|park|wood|forest|grass|scrub|wetland|sand|glacier|ice|building|aeroway|pitch|cemetery|military|zoo|hospital|school|beach|farmland|orchard|vineyard")
    s.layers.forEach { l ->
        val id = l.id.lowercase()
        when {
            // Shaded relief: a hillshade layer (Protomaps) or a raster image (OpenFreeMap's natural_earth)
            l is org.maplibre.android.style.layers.HillshadeLayer || l is org.maplibre.android.style.layers.RasterLayer ->
                l.setProperties(PropertyFactory.visibility(org.maplibre.android.style.layers.Property.NONE))
            l is org.maplibre.android.style.layers.BackgroundLayer -> l.setProperties(PropertyFactory.backgroundColor(land))
            l is org.maplibre.android.style.layers.FillLayer && (id == "earth" || id.startsWith("earth") || id == "land") ->
                l.setProperties(PropertyFactory.fillColor(land), PropertyFactory.fillPattern(Expression.literal("")))
            l is org.maplibre.android.style.layers.FillLayer && "water" in id -> l.setProperties(PropertyFactory.fillColor(water), PropertyFactory.fillOpacity(1f))
            l is org.maplibre.android.style.layers.LineLayer && "water" in id -> l.setProperties(PropertyFactory.lineColor(water))
            roads != null && l is org.maplibre.android.style.layers.LineLayer && ("road" in id || "street" in id || "transportation" in id || "highway" in id) &&
                "casing" !in id && "tunnel" !in id && "rail" !in id && "path" !in id ->
                l.setProperties(PropertyFactory.lineColor(if (major.containsMatchIn(id)) roads.second else roads.first))
            hide.containsMatchIn(id) && (l is org.maplibre.android.style.layers.FillLayer || l is org.maplibre.android.style.layers.FillExtrusionLayer || l is org.maplibre.android.style.layers.LineLayer) ->
                l.setProperties(PropertyFactory.visibility(org.maplibre.android.style.layers.Property.NONE))
        }
    }
}
