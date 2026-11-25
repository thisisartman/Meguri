package io.github.thisisartman.collectibles.ui

import androidx.compose.material.icons.outlined.Info
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxSize
import android.graphics.Color as AColor
import android.os.Bundle
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
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
data class MapPin(
    val id: String, val lat: Double, val lng: Double, val type: ItemType, val collected: Boolean,
    val picture: String? = null, val cardCode: String? = null, val customTypeId: String? = null,
)

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
    // Frame every pin and leave your position out of it: "Where you collected" shows your finds (#136)
    fitAllPins: Boolean = false,
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
    // Offline inside the trip area, online elsewhere; decided from where you are or the first pin, again when the
    // maps on this phone change
    val mapsVersion by OfflineMap.version.collectAsState()
    val offline = remember(mapsVersion) {
        val c = here ?: pins.firstOrNull()?.let { it.lat to it.lng }
        OfflineMap.available(context) && (c == null || OfflineMap.covers(context, c.first, c.second))
    }
    val types = LocalTypeColors.current
    val hollow = androidx.compose.material3.MaterialTheme.colorScheme.surface.toArgb()
    val routeColor = androidx.compose.material3.MaterialTheme.colorScheme.primary.toArgb()
    val groupText = androidx.compose.material3.MaterialTheme.colorScheme.onPrimary.toArgb()
    val groupEdge = androidx.compose.material3.MaterialTheme.colorScheme.surface.toArgb()
    val customTypes = LocalCustomTypes.current
    val markerPx = with(androidx.compose.ui.platform.LocalDensity.current) { (if (inCard) 26.dp else 40.dp).roundToPx() }
    // Marker bitmaps by look, kept while this map is shown; the ones added to the current style are tracked apart
    val markerCache = remember { HashMap<String, android.graphics.Bitmap>() }
    // A flat map (owner, 2026-11-08: the textures looked odd). Land is set apart from the page (owner, 2026-11-09,
    // S23 screenshot: in the page colour the search bar and sheet had no edge against the map): lighter than the
    // page in dark mode, a shade darker in light mode. Water is a plain grey a step from the land (owner, 2026-11-09:
    // the gold-tinted water "looks incredibly ugly"; the gold stays in the app, not on the map).
    val page = androidx.compose.material3.MaterialTheme.colorScheme.background
    val ink = androidx.compose.material3.MaterialTheme.colorScheme.onBackground
    // In light mode a neutral light grey: the cream page colour made the land beige, which read as yellow beside the
    // roads and gold groups (owner, 2026-11-09: "revert that to a light gray")
    val land = if (dark) androidx.compose.ui.graphics.lerp(page, ink, 0.16f) else androidx.compose.ui.graphics.Color(0xFFEDEDED)
    val landColor = land.toArgb()
    val waterColor = if (dark) androidx.compose.ui.graphics.lerp(land, ink, 0.22f).toArgb() else 0xFFD7DADD.toInt()
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
    // The listeners added below, so a change of theme, language or maps replaces them instead of adding more (#173)
    val listeners = remember { MapListeners() }
    LaunchedEffect(mapView, dark, japanese, mapsVersion) {
        val offlineStyle = if (offline) {
            runCatching { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { OfflineMap.styleJson(context, dark, japanese) } }.getOrNull()
        } else null
        mapView.getMapAsync { m ->
            map = m
            listeners.removeFrom(m)
            listeners.move = MapLibreMap.OnCameraMoveListener { emitAnchor(m) }.also(m::addOnCameraMoveListener)
            memoryKey?.let { key ->
                CameraMemory.saved[key]?.let { m.cameraPosition = it }
                listeners.idle = MapLibreMap.OnCameraIdleListener { CameraMemory.saved[key] = m.cameraPosition }.also(m::addOnCameraIdleListener)
            }
            m.uiSettings.apply {
                isScrollGesturesEnabled = interactive; isZoomGesturesEnabled = interactive
                isRotateGesturesEnabled = false; isTiltGesturesEnabled = false
                // The library's credit button was 21 dp and its dialog was titled "MapLibre Android" (#152): the map
                // draws its own 48 dp button instead, top left under the search bar, opening "Map data"
                isAttributionEnabled = false; isLogoEnabled = false
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
                    // Road numbers: OpenStreetMap writes "8;17" where two routes share a road; show the first (#150)
                    else if ("ref" in tf && "name" !in tf) runCatching { layer.setProperties(PropertyFactory.textField(FIRST_REF)) }
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
                // Pins as pictures (owner, 2026-11-09, option A, after Immich's map): items close together on screen
                // merge into a gold circle with how many; each single item is its own picture marker
                s.addSource(
                    GeoJsonSource(
                        SRC,
                        org.maplibre.android.style.sources.GeoJsonOptions()
                            .withCluster(true).withClusterRadius(if (inCard) 28 else 44).withClusterMaxZoom(16),
                    ),
                )
                s.addLayer(
                    CircleLayer(LAYER_GROUP, SRC).withProperties(
                        // A little larger: taps on a zoomed-out map kept landing beside the groups (owner's run, 2026-11-10)
                        PropertyFactory.circleRadius(Expression.literal(if (inCard) 14f else 22f)),
                        PropertyFactory.circleColor(Expression.color(routeColor)),
                        PropertyFactory.circleStrokeWidth(Expression.literal(2f)),
                        PropertyFactory.circleStrokeColor(Expression.color(groupEdge)),
                    ).withFilter(Expression.has("point_count")),
                )
                s.addLayer(
                    org.maplibre.android.style.layers.SymbolLayer(LAYER_GROUP_COUNT, SRC).withProperties(
                        PropertyFactory.textField(Expression.toString(Expression.get("point_count_abbreviated"))),
                        // The offline pack carries its own font names; the online styles use OpenFreeMap's
                        PropertyFactory.textFont(arrayOf(if (offlineStyle != null) "NotoSans-Medium" else "Noto Sans Bold")),
                        PropertyFactory.textSize(if (inCard) 11f else 13f),
                        PropertyFactory.textColor(Expression.color(groupText)),
                        PropertyFactory.textAllowOverlap(true), PropertyFactory.textIgnorePlacement(true),
                    ).withFilter(Expression.has("point_count")),
                )
                s.addLayer(
                    org.maplibre.android.style.layers.SymbolLayer(LAYER, SRC).withProperties(
                        // Zoomed out, the category icon; from street level (14), the picture (owner, 2026-11-10: card
                        // photos are unreadable zoomed out, and pictures swapping in late made group taps look janky)
                        PropertyFactory.iconImage(
                            Expression.step(Expression.zoom(), Expression.get("icon"), Expression.stop(PICTURE_ZOOM, Expression.get("img"))),
                        ),
                        PropertyFactory.iconAllowOverlap(true), PropertyFactory.iconIgnorePlacement(true),
                        PropertyFactory.iconOffset(Expression.get("off")),
                        PropertyFactory.symbolSortKey(Expression.get("rank")),
                    ).withFilter(Expression.not(Expression.has("point_count"))),
                )
                style = s
            }
            // A 48 dp square around the finger, not a single point (owner, 2026-11-07: pins were hard to hit);
            // when several pins fall inside, the one nearest the finger wins
            val slop = 28f * context.resources.displayMetrics.density
            listeners.click = MapLibreMap.OnMapClickListener { point ->
                val p = m.projection.toScreenLocation(point)
                val box = android.graphics.RectF(p.x - slop, p.y - slop, p.x + slop, p.y + slop)
                val dpx = context.resources.displayMetrics.density
                val hit = m.queryRenderedFeatures(box, LAYER, LAYER_GROUP).minByOrNull { f ->
                    val g = f.geometry() as? Point ?: return@minByOrNull Float.MAX_VALUE
                    val s = m.projection.toScreenLocation(LatLng(g.latitude(), g.longitude()))
                    // Where the marker is drawn: items sharing a spot are pushed apart by "off" (dp)
                    val off = runCatching { f.getProperty("off").asJsonArray }.getOrNull()
                    val x = s.x + (off?.get(0)?.asFloat ?: 0f) * dpx
                    val y = s.y + (off?.get(1)?.asFloat ?: 0f) * dpx
                    (x - p.x) * (x - p.x) + (y - p.y) * (y - p.y)
                }
                val g = hit?.geometry() as? Point
                when {
                    hit == null -> tapMap()
                    hit.hasProperty("point_count") && g != null -> {
                        val zoom = runCatching { m.style?.getSourceAs<GeoJsonSource>(SRC)?.getClusterExpansionZoom(hit) }.getOrNull()
                            ?: (m.cameraPosition.zoom + 2).toInt()
                        // An even glide in, a little slower, so the group opening reads as one movement
                        if (interactive) m.easeCamera(CameraUpdateFactory.newLatLngZoom(LatLng(g.latitude(), g.longitude()), zoom.toDouble()), 650)
                        else tapMap()
                    }
                    else -> tapPin(hit.getStringProperty("id"))
                }
                true
            }.also(m::addOnMapClickListener)
        }
    }
    LaunchedEffect(map, anchor) { map?.let { emitAnchor(it) } }
    val cardsHave by io.github.thisisartman.collectibles.photo.CardPictures.have.collectAsState()
    val looks = remember(pins, customTypes, cardsHave, types, markerPx) {
        pins.map { p ->
            val custom = p.customTypeId?.let { customTypes[it] }
            p to MapMarkers.Look(
                picture = p.picture,
                cardCode = p.cardCode?.takeIf { it in cardsHave },
                icon = if (p.type == ItemType.CUSTOM && custom != null) customIconRes(custom.icon) else typeIcon(p.type),
                ring = if (p.type == ItemType.CUSTOM && custom != null) custom.color.toInt() else types.of(p.type).toArgb(),
                collected = p.collected, sizePx = markerPx,
            )
        }
    }
    val addedTo = remember { java.util.WeakHashMap<Style, HashSet<String>>() }
    suspend fun ensureMarkers(s: Style, wanted: Collection<MapMarkers.Look>) {
        val added = addedTo.getOrPut(s) { HashSet() }
        val missing = wanted.filter { it.key !in added }.distinctBy { it.key }
        if (missing.isEmpty()) return
        val drawn = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            missing.associate { l -> l.key to (markerCache[l.key] ?: MapMarkers.draw(context, l).also { markerCache[l.key] = it }) }
        }
        if (!s.isFullyLoaded) return
        s.addImages(HashMap(drawn)); added += drawn.keys
    }
    fun publishMarkers(s: Style) {
        val added = addedTo[s] ?: return
        // Items on the same spot never separate by zooming (Urasa's stamp hid under its Ekitag on the Poco):
        // once their group opens they sit side by side around the spot
        val spread = HashMap<String, Pair<Float, Float>>()
        looks.groupBy { (p, _) -> "%.5f,%.5f".format(java.util.Locale.US, p.lat, p.lng) }.values.filter { it.size > 1 }.forEach { same ->
            val step = markerPx / context.resources.displayMetrics.density * 0.6f
            same.forEachIndexed { i, (p, _) ->
                val a = 2 * Math.PI * i / same.size - Math.PI
                spread[p.id] = (step * kotlin.math.cos(a)).toFloat() to (step * kotlin.math.sin(a)).toFloat()
            }
        }
        s.getSourceAs<GeoJsonSource>(SRC)?.setGeoJson(
            FeatureCollection.fromFeatures(
                looks.map { (p, l) ->
                    Feature.fromGeometry(Point.fromLngLat(p.lng, p.lat)).apply {
                        addStringProperty("id", p.id)
                        // The picture once it is drawn; until then the category icon
                        addStringProperty("img", if (l.key in added) l.key else l.plain.key)
                        addStringProperty("icon", l.plain.key)
                        // Pictures and collected items draw above the rest
                        addNumberProperty("rank", (if (l.picture != null || l.cardCode != null) 2 else 0) + (if (l.collected) 1 else 0))
                        val (dx, dy) = spread[p.id] ?: (0f to 0f)
                        addProperty("off", com.google.gson.JsonArray().apply { add(dx); add(dy) })
                    }
                },
            ),
        )
    }
    // Pictures: drawn off the main thread, then each marker switches from its icon to its picture
    LaunchedEffect(style, looks) {
        val s = style ?: return@LaunchedEffect
        if (looks.none { it.second.picture != null || it.second.cardCode != null }) return@LaunchedEffect
        ensureMarkers(s, looks.map { it.second.plain })
        ensureMarkers(s, looks.map { it.second })
        publishMarkers(s)
    }
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
    // When the onboarding lets go of the map, place it afresh: what it remembered then was the onboarding's view of
    // Japan or the world, and Nearby stayed on it after the first region loaded (#136, seen on a fresh 0.7.0 install)
    var wasHeld by remember { mutableStateOf(holdOnJapan) }
    LaunchedEffect(holdOnJapan) {
        if (holdOnJapan) { wasHeld = true; return@LaunchedEffect }
        if (!wasHeld) return@LaunchedEffect
        wasHeld = false
        memoryKey?.let { CameraMemory.saved.remove(it); CameraMemory.placedAt.remove(it) }
        placed = false; placedAt = null; framedWithoutPins = false; userMoved = false
    }

    LaunchedEffect(style, hasHere, holdOnJapan, hasPins) {
        if (!startOnJapan || holdOnJapan || style == null || !hasHere) return@LaunchedEffect
        if (placed && !(framedWithoutPins && hasPins && !userMoved)) return@LaunchedEffect
        // A short pause once the position is known, so the map doesn't jump the moment it arrives
        kotlinx.coroutines.delay(300)
        val m = map ?: return@LaunchedEffect
        val h = here ?: return@LaunchedEffect
        placed = true
        placedAt = h
        val focus = framing(pins, h, fitAllPins)
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
        // Category icons first (a handful, quick), so every item shows at once; pictures follow below
        ensureMarkers(s, looks.map { it.second.plain })
        publishMarkers(s)
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
            val focus = framing(pins, here, fitAllPins)
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
    androidx.compose.foundation.layout.Box(modifier) {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
        if (interactive && !inCard) MapCreditsButton(Modifier.align(androidx.compose.ui.Alignment.TopStart).padding(start = 4.dp, top = 116.dp))
    }
}

/** Cameras kept while the app runs, by map name, so going to another page and back does not move the map. */
private object CameraMemory {
    val saved = mutableMapOf<String, CameraPosition>()
    val placedAt = mutableMapOf<String, Pair<Double, Double>>()
}

private fun dist2(p: MapPin, h: Pair<Double, Double>) = (p.lat - h.first).let { it * it } + (p.lng - h.second).let { it * it }

/** About 110 km, as a squared distance in degrees: farther than this, your position is left out of the first frame. */
private const val FAR_DEG2 = 1.0

/**
 * What the camera frames first: you and your nearest pins; or every pin when the map shows your finds, when your
 * position isn't known, or when the nearest pin is far away. Framing you with pins in another country showed the
 * whole world (#136).
 */
internal fun framing(pins: List<MapPin>, here: Pair<Double, Double>?, all: Boolean): List<LatLng> {
    val every = pins.map { LatLng(it.lat, it.lng) }
    if (here == null) return every
    val me = LatLng(here.first, here.second)
    if (pins.isEmpty()) return listOf(me)
    if (all) return every
    val near = pins.sortedBy { dist2(it, here) }.take(8)
    return if (dist2(near.first(), here) > FAR_DEG2) every else near.map { LatLng(it.lat, it.lng) } + me
}

private fun colorHex(c: Color) = String.format("#%06X", c.toArgb() and 0xFFFFFF)

private const val SRC = "items"
/** From this zoom (street level) markers show their picture; below it, the category icon. */
private const val PICTURE_ZOOM = 14f
private const val LAYER = "items-pins"
private const val LAYER_GROUP = "items-groups"
private const val LAYER_GROUP_COUNT = "items-group-count"
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

/** The map's credits (#152): OpenStreetMap's data and the tiles, one tap away on every full map, at 48 dp. */
@Composable
private fun MapCreditsButton(modifier: Modifier) {
    var open by remember { mutableStateOf(false) }
    val context = LocalContext.current
    androidx.compose.material3.IconButton(onClick = { open = true }, modifier = modifier) {
        androidx.compose.material3.Icon(
            androidx.compose.material.icons.Icons.Outlined.Info,
            contentDescription = androidx.compose.ui.res.stringResource(io.github.thisisartman.collectibles.R.string.map_credits_title),
            tint = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (open) androidx.compose.material3.AlertDialog(
        onDismissRequest = { open = false },
        title = { androidx.compose.material3.Text(androidx.compose.ui.res.stringResource(io.github.thisisartman.collectibles.R.string.map_credits_title)) },
        text = {
            androidx.compose.foundation.layout.Column {
                listOf(
                    "© OpenStreetMap contributors" to "https://www.openstreetmap.org/copyright",
                    "OpenFreeMap" to "https://openfreemap.org",
                    "Protomaps" to "https://protomaps.com",
                ).forEach { (name, url) ->
                    androidx.compose.material3.TextButton(onClick = { openLink(context, url) }, modifier = Modifier.heightIn(min = 48.dp)) {
                        androidx.compose.material3.Text(name)
                    }
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = { open = false }) {
                androidx.compose.material3.Text(androidx.compose.ui.res.stringResource(android.R.string.ok))
            }
        },
    )

/** A road's number up to the first ";" ("8;17" shows 8), for road number labels (#150). */
private val FIRST_REF: Expression = Expression.raw(
    """["let", "r", ["to-string", ["get", "ref"]], ["case", [">=", ["index-of", ";", ["var", "r"]], 0], """ +
        """["slice", ["var", "r"], 0, ["index-of", ";", ["var", "r"]]], ["var", "r"]]]""",
)

/** The map listeners [MeguriMap] adds, kept so each style change removes the last set before adding its own (#173). */
private class MapListeners {
    var move: MapLibreMap.OnCameraMoveListener? = null
    var idle: MapLibreMap.OnCameraIdleListener? = null
    var click: MapLibreMap.OnMapClickListener? = null
    fun removeFrom(m: MapLibreMap) {
        move?.let(m::removeOnCameraMoveListener); idle?.let(m::removeOnCameraIdleListener); click?.let(m::removeOnMapClickListener)
        move = null; idle = null; click = null
    }
}
