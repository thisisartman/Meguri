package io.github.thisisartman.collectibles.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.thisisartman.collectibles.data.CollectionRepository
import io.github.thisisartman.collectibles.data.ItemRow
import io.github.thisisartman.collectibles.data.ItemType
import io.github.thisisartman.collectibles.data.Line
import io.github.thisisartman.collectibles.data.LineStation
import io.github.thisisartman.collectibles.data.PhotoInfo
import io.github.thisisartman.collectibles.data.Via
import io.github.thisisartman.collectibles.data.LinkEnd
import io.github.thisisartman.collectibles.data.MapsLink
import io.github.thisisartman.collectibles.data.PlaceLookup
import io.github.thisisartman.collectibles.data.RailGraph
import io.github.thisisartman.collectibles.data.SavedTrip
import io.github.thisisartman.collectibles.data.TripStore
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import io.github.thisisartman.collectibles.location.Fix
import io.github.thisisartman.collectibles.location.LocationSource
import io.github.thisisartman.collectibles.location.distanceM
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Where distances are measured from, and whether it is live. */
data class Here(val fix: Fix?, val live: Boolean, val searching: Boolean = false)

data class Ranked(val row: ItemRow, val meters: Double?)

/** One card in "My book": a prefecture of manhole cards, a railway line, goshuin, or other stations. */
data class CollectionSummary(
    val key: String,
    val type: ItemType,
    val title: String,
    val collected: Int,
    val total: Int?,
    val last: ItemRow?,
    val lineId: String? = null,
    val memberIds: Set<String> = emptySet(),
)

/** A collection with its nearest item still to get, for "Around here" on Nearby. */
data class Around(val summary: CollectionSummary, val next: Ranked?)

/** Nearby's lower half: collections with something to get within the radius, nearest first, and the rest. */
data class AroundHere(val near: List<Around>, val elsewhere: List<CollectionSummary>, val located: Boolean)

/** What a pasted link gave: each end as (station id, metres from the place in the link), or why it gave nothing. */
sealed interface LinkRead {
    data class Ends(val from: Pair<String, Double>?, val to: Pair<String, Double>?, val mode: String?) : LinkRead
    data object NotALink : LinkRead
    /** A short link that could not be opened: it needs the network once. */
    data object Offline : LinkRead
}

/** A snackbar message with an optional undo. */
data class Logged(val findId: String, val name: String, val atMs: Long, val shownAt: Long = System.nanoTime())

@HiltViewModel
class MainViewModel @Inject constructor(
    private val repo: CollectionRepository,
    private val location: LocationSource,
    private val trips: TripStore,
    /** The app log; screens use it for one-line detailed entries (screen changes, scans). */
    val log: io.github.thisisartman.collectibles.debug.FileLog,
    /** Long work started from Settings or the onboarding; the app frame shows its messages. */
    val work: SettingsWork,
) : ViewModel() {

    /** Null until the database has answered once (#78). One query: "loaded" and the rows arrive together. */
    val rowsOrNull: StateFlow<List<ItemRow>?> = repo.itemRows().stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val rows: StateFlow<List<ItemRow>> = rowsOrNull.map { it.orEmpty() }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val lines: StateFlow<List<Line>> = repo.lines().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val lineStations: StateFlow<List<LineStation>> = repo.lineStations().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _here = MutableStateFlow(Here(null, false))
    val here: StateFlow<Here> = _here.asStateFlow()

    private val _logged = MutableStateFlow<Logged?>(null)
    val logged: StateFlow<Logged?> = _logged.asStateFlow()

    /** Uncollected items with a position, nearest first; null distance when there is no fix. */
    val nearest: StateFlow<List<Ranked>> = combine(rows, here) { r, h ->
        val todo = r.filter { !it.collected && !it.noneHere && it.item.type != ItemType.EKITAG }
        val fix = h.fix
        if (fix == null) {
            todo.sortedByDescending { it.item.updatedAt }.map { Ranked(it, null) }
        } else {
            todo.filter { it.item.lat != null && it.item.lng != null }
                .map { Ranked(it, distanceM(fix.lat, fix.lng, it.item.lat!!, it.item.lng!!)) }
                .sortedBy { it.meters }
        }
    }.flowOn(kotlinx.coroutines.Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val summaries: StateFlow<List<CollectionSummary>> = combine(rows, lines, lineStations) { r, l, ls ->
        summarise(r, l, ls)
    }.flowOn(kotlinx.coroutines.Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val aroundHere: StateFlow<AroundHere> = combine(summaries, nearest, here) { s, n, h ->
        splitAroundHere(s, n, located = h.fix != null)
    }.flowOn(kotlinx.coroutines.Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, AroundHere(emptyList(), emptyList(), false))

    fun itemRow(id: String) = repo.itemRow(id)
    fun finds(itemId: String) = repo.finds(itemId)

    fun refreshPosition() {
        if (!location.hasPermission()) return
        location.lastKnown()?.let { _here.value = Here(it, false) }
        // With permission but no fix yet, say so rather than "Location off"
        if (_here.value.fix == null) _here.value = _here.value.copy(searching = true)
        viewModelScope.launch {
            val fix = location.current()
            _here.value = if (fix != null) Here(fix, true) else _here.value.copy(searching = false)
        }
    }

    /**
     * Follows the position while it is collected (Nearby on screen): distances refresh as you move, every 30 s at
     * most, through the same provider as a single fix (finding 41).
     */
    suspend fun followPosition() {
        if (!location.hasPermission()) return
        location.updates(30_000).collect { _here.value = Here(it, true) }
    }

    private var lastGotIt: Pair<String, Long>? = null

    fun gotIt(row: ItemRow, name: String, onLogged: (String) -> Unit = {}) {
        // A double tap must not log the same find twice (overnight review round 13)
        val now = android.os.SystemClock.elapsedRealtime()
        if (isRepeatTap(lastGotIt, row.item.id, now)) { log.d("Tap", "Got it on ${row.item.id} again within ${REPEAT_TAP_MS} ms: ignored"); return }
        log.d("Tap", "Got it on ${row.item.id} (${row.item.type})")
        lastGotIt = row.item.id to now
        viewModelScope.launch {
            val id = repo.logFind(row.item.id, Via.MANUAL)
            emit(Logged(id, name, System.currentTimeMillis()))
            onLogged(id)
        }
    }

    /** The Photo action on an alert: log the find, then open the camera for it. */
    fun logFromAlert(itemId: String, japanese: Boolean, fix: io.github.thisisartman.collectibles.location.Fix?, onLogged: (String) -> Unit) {
        log.d("Tap", "Photo on the alert for $itemId")
        viewModelScope.launch {
            val row = repo.itemRowOnce(itemId) ?: return@launch log.w("Alert", "Photo on an alert for $itemId, which is gone")
            val id = repo.logFindAt(itemId, Via.NOTIFICATION, fix)
            emit(Logged(id, if (japanese) row.item.nameJa else row.item.nameRomaji ?: row.item.nameJa, System.currentTimeMillis()))
            onLogged(id)
        }
    }

    val collectionTypes: StateFlow<List<io.github.thisisartman.collectibles.data.CollectionType>> =
        repo.collectionTypes().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun addCollectionType(name: String, color: Long, icon: String, hasCost: Boolean, onDone: (String) -> Unit = {}) {
        viewModelScope.launch { onDone(repo.addCollectionType(name, color, icon, hasCost)) }
    }

    fun setQuantity(findId: String, quantity: Int) { viewModelScope.launch { repo.setQuantity(findId, quantity) } }

    fun setCost(findId: String, yen: Int?) { viewModelScope.launch { repo.setCost(findId, yen) } }

    suspend fun lookUpPlace(query: String, japanese: Boolean): List<io.github.thisisartman.collectibles.data.PlaceHit> {
        val f = here.value.fix
        return io.github.thisisartman.collectibles.data.PlaceLookup.search(query, f?.lat, f?.lng, japanese)
    }

    fun addNew(
        type: ItemType, nameJa: String, nameRomaji: String?,
        customTypeId: String? = null, place: io.github.thisisartman.collectibles.data.PlaceHit? = null, costYen: Int? = null,
        onDone: (itemId: String, findId: String) -> Unit = { _, _ -> },
    ) {
        viewModelScope.launch {
            val (itemId, findId) = repo.addItemWithFind(type, nameJa, nameRomaji, customTypeId, place, costYen)
            emit(Logged(findId, nameRomaji?.ifBlank { null } ?: nameJa, System.currentTimeMillis()))
            onDone(itemId, findId)
        }
    }

    /** A new item with no find yet, for a notebook stamp: saving the page gives it its find (finding 33). */
    fun addItemOnly(
        type: ItemType, nameJa: String, nameRomaji: String?,
        customTypeId: String? = null, place: io.github.thisisartman.collectibles.data.PlaceHit? = null,
        onDone: (itemId: String) -> Unit,
    ) {
        viewModelScope.launch { onDone(repo.addItem(type, nameJa, nameRomaji, customTypeId, place)) }
    }

    private var lastLogged: Logged? = null

    private fun emit(l: Logged) {
        lastLogged = l
        _logged.value = l
    }

    /**
     * Shows "Logged ... Undo" again once the camera closes: the bar timed out or was hidden behind the camera,
     * and without it a cancelled photo left no sign that the find was kept (overnight review round 4).
     */
    fun reshowLogged(findId: String) {
        val l = lastLogged?.takeIf { it.findId == findId } ?: return
        emit(l.copy(shownAt = System.nanoTime()))
    }

    fun undo(findId: String) {
        log.d("Tap", "Undo on the snackbar for find $findId")
        viewModelScope.launch { repo.undoFind(findId) }
        _logged.value = null
    }

    /** "Remove this find" on the item page, after its confirm: the same removal as Undo. */
    fun removeFind(findId: String) {
        log.d("Tap", "Remove this find: $findId")
        viewModelScope.launch { repo.undoFind(findId) }
    }

    fun clearLogged() {
        _logged.value = null
    }

    fun attachPhoto(findId: String, info: PhotoInfo) {
        viewModelScope.launch { repo.attachPhoto(findId, info) }
    }

    fun retryPosition(findId: String) = repo.retryPosition(findId)

    // Several photos per find, each with a kind (#54)
    fun nextPhotoKind(findId: String, kind: String) {
        log.d("Photo", "Camera for find $findId, kind $kind")
        repo.setNextPhotoKind(findId, kind)
    }
    fun addGalleryPhoto(context: android.content.Context, findId: String, uri: android.net.Uri, kind: String) {
        log.d("Photo", "Gallery photo picked for find $findId, kind $kind")
        viewModelScope.launch { repo.attachFromGallery(context, findId, uri, kind) }
    }
    fun setPhotoKind(photoId: String, kind: String) { viewModelScope.launch { repo.setPhotoKind(photoId, kind) } }
    fun removeItem(itemId: String, alsoItem: Boolean, onDone: () -> Unit = {}) {
        viewModelScope.launch { repo.removeItem(itemId, alsoItem); onDone() }
    }

    fun removePhoto(photoId: String) { viewModelScope.launch { repo.removePhoto(photoId) } }

    /** Saves a photo edit (#208); [done] says whether it was written, on the main thread. */
    fun editPhoto(photoId: String, edit: io.github.thisisartman.collectibles.photo.PhotoEdit, done: (Boolean) -> Unit) {
        viewModelScope.launch { done(repo.editPhoto(photoId, edit)) }
    }

    fun setCover(itemId: String, photoId: String?) { viewModelScope.launch { repo.setCover(itemId, photoId) } }

    fun setNoneHere(itemId: String, none: Boolean) { viewModelScope.launch { repo.setNoneHere(itemId, none) } }

    /** Things collected on [atMs], no place: a past trip's day, or now for what you already have. */
    fun markOn(ids: Set<String>, atMs: Long, onDone: (List<String>) -> Unit) {
        viewModelScope.launch { onDone(repo.markOn(ids, atMs)) }
    }

    fun removeFinds(findIds: List<String>) { viewModelScope.launch { repo.removeFinds(findIds) } }

    // Route trips (#46, #60, #62): today's plan, and reading a Google Maps link
    val trip: StateFlow<SavedTrip?> = trips.trip

    val railGraph: StateFlow<RailGraph> = lineStations.map { RailGraph.from(it) }
        .flowOn(kotlinx.coroutines.Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, RailGraph(emptyMap()))

    // Trip report (#61): the days with dated finds, and one day's finds
    val findTimes: StateFlow<List<Long>> = repo.findTimes().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    fun findsBetween(from: Long, to: Long) = repo.findsBetween(from, to)

    fun saveTrip(t: SavedTrip) {
        log.d("Trip", "Route trip saved: ${t.getOff.size} stations to get off at")
        trips.save(t)
    }
    fun clearTrip() {
        log.d("Trip", "Route trip cleared")
        trips.clear()
    }
    fun currentTrip() = trips.current()

    /**
     * A pasted link to two stations. Each end is matched by name first, then by position (the nearest station on
     * the bundled lines), then by looking the name up on OpenStreetMap. [LinkRead.Ends] carries how far each
     * matched station is from the place in the link, so the screen can say when an end is off the bundled lines.
     */
    /** Where a shared Google Maps link points (its destination, else its start), for editing an item's position. */
    suspend fun positionFromLink(text: String, japanese: Boolean): Pair<Double, Double>? {
        val raw = MapsLink.findUrl(text) ?: return null
        val url = if (MapsLink.isShort(raw)) MapsLink.resolve(raw) ?: return null else raw
        val t = MapsLink.parse(url)
        val e = t?.to ?: t?.from ?: MapsLink.point(url) ?: return null
        if (e.hasPosition) return e.lat!! to e.lng!!
        return e.name?.let { n -> PlaceLookup.search(n, here.value.fix?.lat, here.value.fix?.lng, japanese).firstOrNull()?.let { it.lat to it.lng } }
    }

    /** Photos shared into Meguri (#80): their addresses as they arrive, then what their files say, once read. */
    private val sharedUris = MutableStateFlow<List<android.net.Uri>>(emptyList())
    private val _shared = MutableStateFlow<List<io.github.thisisartman.collectibles.data.SharedPhoto>?>(null)
    val shared: StateFlow<List<io.github.thisisartman.collectibles.data.SharedPhoto>?> = _shared.asStateFlow()
    val shareCount: StateFlow<Int> = sharedUris.map { it.size }.stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    /** What the shared photos are filed as on a manhole card: a scanned card (or cover) says so, a gallery share is Other. */
    private var shareCardKind = io.github.thisisartman.collectibles.data.PhotoKind.OTHER

    /** For a trimmed card scan among the shared photos, its untrimmed page, by the scan's address (#208). */
    private var shareOriginals: Map<String, android.net.Uri> = emptyMap()

    fun openShare(
        uris: List<android.net.Uri>, cardKind: String = io.github.thisisartman.collectibles.data.PhotoKind.OTHER,
        originals: Map<String, android.net.Uri> = emptyMap(),
    ) {
        log.d("Share", "${uris.size} photos shared into Meguri")
        sharedUris.value = uris
        _shared.value = null
        shareCardKind = cardKind
        shareOriginals = originals
    }

    /** The new-photo hint's "Attach" (M7): the photo onto the item, collected on the day it was taken if not yet. */
    /** Notebook stamps: each (item, picture), found on [atMs] or today unless already collected; [onDone] gets how many. */
    fun addNotebookStamps(stamps: List<Pair<String, android.net.Uri>>, atMs: Long? = null, onDone: (Int) -> Unit) {
        viewModelScope.launch {
            var n = 0
            val t0 = System.currentTimeMillis()
            for ((itemId, uri) in stamps) if (repo.addNotebookStamp(itemId, uri, atMs)) n++
            log.d("Notebook", "Saved $n of ${stamps.size} stamps from the page in ${System.currentTimeMillis() - t0} ms")
            onDone(n)
        }
    }

    /**
     * Scan, "Collect with this photo" (finding 29): a find now, placed where you are like Got it, with the scan as its
     * photo, and the same "Collected ... Undo" snackbar as every other way of collecting.
     */
    fun collectWithPhoto(
        context: android.content.Context, row: ItemRow, name: String, uri: android.net.Uri, kind: String,
        original: android.net.Uri? = null, onDone: (String) -> Unit,
    ) {
        log.d("Tap", "Collect with this photo on ${row.item.id}")
        viewModelScope.launch {
            val findId = repo.logFind(row.item.id, Via.MANUAL)
            emit(Logged(findId, name, System.currentTimeMillis()))
            repo.attachFromGallery(context.applicationContext, findId, uri, kind, original = original)
            onDone(findId)
        }
    }

    /** [original]: a trimmed card scan's untrimmed page, kept as the photo's original (#208). */
    fun attachPhoto(
        itemId: String, uri: android.net.Uri, kind: String = io.github.thisisartman.collectibles.data.PhotoKind.OTHER,
        original: android.net.Uri? = null, onDone: () -> Unit,
    ) {
        viewModelScope.launch {
            val p = repo.readShared(uri)
            repo.addShared(itemId, listOf(p), if (p.hasPlace) p.lat!! to p.lng!! else null, kind, original?.let { mapOf(p.uri to it) } ?: emptyMap())
            onDone()
        }
    }

    /** Reads where and when each shared photo was taken; called once "photo locations" has been asked for. */
    fun readShare() {
        val uris = sharedUris.value
        viewModelScope.launch { _shared.value = uris.map { repo.readShared(it) } }
    }

    /** Adds each group's photos to the item chosen for it. Returns photos copied and items they went to. */
    suspend fun addShared(choices: List<Pair<String, io.github.thisisartman.collectibles.data.PhotoGroup>>): Pair<Int, Int> {
        var photos = 0
        val types = rows.value.associate { it.item.id to it.item.type }
        for ((itemId, group) in choices) {
            photos += repo.addShared(itemId, group.photos, group.centre, sharedKind(types[itemId], shareCardKind), shareOriginals)
        }
        sharedUris.value = emptyList()
        _shared.value = null
        shareCardKind = io.github.thisisartman.collectibles.data.PhotoKind.OTHER
        shareOriginals = emptyMap()
        return photos to choices.map { it.first }.distinct().size
    }

    /** Saves your edits to an item's details; catalogue updates leave it as you made it (owner, 2026-11-08). */
    fun fillPrefectures(found: List<Pair<String, String>>) {
        viewModelScope.launch { repo.fillPrefectures(found) }
    }

    fun editItem(item: io.github.thisisartman.collectibles.data.Item) {
        viewModelScope.launch { repo.editItem(item) }
    }

    /** The cover position printed on a scanned card, kept on that card unless it already has one (#203). */
    fun setScannedCover(itemId: String, cover: io.github.thisisartman.collectibles.data.CoverCoords.LatLng) {
        viewModelScope.launch { repo.setScannedCover(itemId, cover.lat, cover.lng) }
    }

    /** Metres from where you are to a card's cover, when both are known (#203). */
    fun distanceToCover(item: io.github.thisisartman.collectibles.data.Item): Double? {
        val f = _here.value.fix ?: return null
        val lat = item.coverLat ?: return null
        val lng = item.coverLng ?: return null
        return distanceM(f.lat, f.lng, lat, lng)
    }

    suspend fun readLink(text: String, japanese: Boolean): LinkRead {
        val raw = MapsLink.findUrl(text) ?: return LinkRead.NotALink
        val url = if (MapsLink.isShort(raw)) MapsLink.resolve(raw) ?: return LinkRead.Offline else raw
        val t = MapsLink.parse(url) ?: return LinkRead.NotALink
        val graph = railGraph.value
        val stations = rows.value.filter { graph.has(it.item.id) && it.item.lat != null && it.item.lng != null }
        suspend fun snap(e: LinkEnd?): Pair<String, Double>? {
            e ?: return null
            e.name?.let { n ->
                // Google adds the town ("Nagaoka Station Niigata"): take the longest station name the link's name starts with
                MapsLink.matchStation(n, stations.map { it.item.id to listOfNotNull(it.item.nameJa, it.item.nameRomaji) })
                    ?.let { return it to 0.0 }
            }
            val pos = if (e.hasPosition) e.lat!! to e.lng!! else e.name?.let { n ->
                PlaceLookup.search(n, here.value.fix?.lat, here.value.fix?.lng, japanese).firstOrNull()?.let { it.lat to it.lng }
            } ?: return null
            return stations.map { it.item.id to distanceM(pos.first, pos.second, it.item.lat!!, it.item.lng!!) }.minByOrNull { it.second }
        }
        return LinkRead.Ends(snap(t.from), snap(t.to), t.mode)
    }

    fun distanceTo(row: ItemRow): Double? {
        val f = _here.value.fix ?: return null
        val lat = row.item.lat ?: return null
        val lng = row.item.lng ?: return null
        return distanceM(f.lat, f.lng, lat, lng)
    }

    companion object {
        /** How long a second Got it on the same item counts as the same tap. */
        const val REPEAT_TAP_MS = 2_000L

        fun isRepeatTap(last: Pair<String, Long>?, itemId: String, nowMs: Long): Boolean =
            last != null && last.first == itemId && nowMs - last.second < REPEAT_TAP_MS

        /**
         * The kind a shared photo is filed as (finding 30): a scan picked by hand onto a manhole card keeps what it is
         * (the card, or a cover); anything else, or onto anything else, is Other, to sort later.
         */
        fun sharedKind(type: ItemType?, cardKind: String): String =
            if (type == ItemType.MANHOLE_CARD) cardKind else io.github.thisisartman.collectibles.data.PhotoKind.OTHER

        fun summarise(rows: List<ItemRow>, lines: List<Line>, ls: List<LineStation>): List<CollectionSummary> {
            val out = mutableListOf<CollectionSummary>()
            fun last(list: List<ItemRow>) = list.filter { it.collected }
                .maxWithOrNull(compareBy<ItemRow> { it.lastAt ?: 0L }.thenBy { it.item.updatedAt })

            rows.filter { it.item.type == ItemType.MANHOLE_CARD }.groupBy { it.item.prefecture.orEmpty() }
                .toSortedMap().forEach { (pref, list) ->
                    out += CollectionSummary("m:$pref", ItemType.MANHOLE_CARD, pref, list.count { it.collected }, list.count { !it.noneHere }, last(list), memberIds = list.ids())
                }

            val byId = rows.associateBy { it.item.id }
            val onLine = mutableSetOf<String>()
            lines.forEach { line ->
                val stations = ls.filter { it.lineId == line.id }.sortedBy { it.seq }.mapNotNull { byId[it.itemId] }
                onLine += stations.map { it.item.id }
                if (stations.isNotEmpty()) {
                    out += CollectionSummary("l:${line.id}", ItemType.EKI_STAMP, line.id, stations.count { it.collected }, stations.count { !it.noneHere }, last(stations), lineId = line.id, memberIds = stations.ids())
                }
            }
            val other = rows.filter { it.item.type == ItemType.EKI_STAMP && it.item.id !in onLine }
            if (other.isNotEmpty()) out += CollectionSummary("l:", ItemType.EKI_STAMP, "", other.count { it.collected }, null, last(other), memberIds = other.ids())

            listOf(ItemType.GOSHUIN to "g", ItemType.DAM_CARD to "dam", ItemType.MUSEUM_STAMP to "mus", ItemType.ROADSIDE_STATION to "road").forEach { (kind, key) ->
                val l = rows.filter { it.item.type == kind }
                if (l.isNotEmpty()) out += CollectionSummary(key, kind, "", l.count { it.collected }, null, last(l), memberIds = l.ids())
            }
            return out
        }

        private fun List<ItemRow>.ids() = mapTo(mutableSetOf()) { it.item.id }

        /** How far "Around here" reaches (round 5, A2). */
        const val AROUND_RADIUS_M = 30_000.0

        /**
         * Splits the collections for Nearby (round 5, A2): those with an item still to get within [radiusM],
         * ordered by that item's distance, and the rest. Without a position nothing can be "around here",
         * so every collection is listed as before.
         */
        fun splitAroundHere(summaries: List<CollectionSummary>, nearest: List<Ranked>, located: Boolean, radiusM: Double = AROUND_RADIUS_M): AroundHere {
            if (!located) return AroundHere(summaries.map { Around(it, null) }, emptyList(), located = false)
            val withNext = summaries.map { s -> Around(s, nearest.firstOrNull { it.row.item.id in s.memberIds && it.meters != null }) }
            val (near, far) = withNext.partition { (it.next?.meters ?: Double.MAX_VALUE) <= radiusM }
            // Far from every collection (at home in Tokyo, say): an empty "Around here" helps nobody, so list them all
            if (near.isEmpty()) return AroundHere(summaries.map { Around(it, null) }, emptyList(), located = false)
            return AroundHere(near.sortedBy { it.next?.meters }, far.map { it.summary }, located = true)
        }
    }
}
