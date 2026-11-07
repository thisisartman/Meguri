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
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val summaries: StateFlow<List<CollectionSummary>> = combine(rows, lines, lineStations) { r, l, ls ->
        summarise(r, l, ls)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val aroundHere: StateFlow<AroundHere> = combine(summaries, nearest, here) { s, n, h ->
        splitAroundHere(s, n, located = h.fix != null)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, AroundHere(emptyList(), emptyList(), false))

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

    private var lastGotIt: Pair<String, Long>? = null

    fun gotIt(row: ItemRow, name: String, onLogged: (String) -> Unit = {}) {
        // A double tap must not log the same find twice (overnight review round 13)
        val now = android.os.SystemClock.elapsedRealtime()
        if (isRepeatTap(lastGotIt, row.item.id, now)) return
        lastGotIt = row.item.id to now
        viewModelScope.launch {
            val id = repo.logFind(row.item.id, Via.MANUAL)
            emit(Logged(id, name, System.currentTimeMillis()))
            onLogged(id)
        }
    }

    /** The Photo action on an alert: log the find, then open the camera for it. */
    fun logFromAlert(itemId: String, japanese: Boolean, fix: io.github.thisisartman.collectibles.location.Fix?, onLogged: (String) -> Unit) {
        viewModelScope.launch {
            val row = repo.itemRowOnce(itemId) ?: return@launch
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
        viewModelScope.launch { repo.undoFind(findId) }
        _logged.value = null
    }

    /** "Remove this find" on the item page, after its confirm: the same removal as Undo. */
    fun removeFind(findId: String) {
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
    fun nextPhotoKind(findId: String, kind: String) = repo.setNextPhotoKind(findId, kind)
    fun addGalleryPhoto(context: android.content.Context, findId: String, uri: android.net.Uri, kind: String) {
        viewModelScope.launch { repo.attachFromGallery(context, findId, uri, kind) }
    }
    fun setPhotoKind(photoId: String, kind: String) { viewModelScope.launch { repo.setPhotoKind(photoId, kind) } }
    fun removePhoto(photoId: String) { viewModelScope.launch { repo.removePhoto(photoId) } }

    fun markBeforeApp(ids: Set<String>, onDone: (Int) -> Unit) {
        viewModelScope.launch { onDone(repo.markBeforeApp(ids)) }
    }

    fun setCover(itemId: String, photoId: String?) { viewModelScope.launch { repo.setCover(itemId, photoId) } }

    fun setNoneHere(itemId: String, none: Boolean) { viewModelScope.launch { repo.setNoneHere(itemId, none) } }

    fun unmarkBeforeApp(itemId: String) {
        viewModelScope.launch { repo.unmarkBeforeApp(itemId) }
    }

    fun markOn(ids: Set<String>, atMs: Long, onDone: (List<String>) -> Unit) {
        viewModelScope.launch { onDone(repo.markOn(ids, atMs)) }
    }

    fun removeFinds(findIds: List<String>) { viewModelScope.launch { repo.removeFinds(findIds) } }

    // Route trips (#46, #60, #62): today's plan, and reading a Google Maps link
    val trip: StateFlow<SavedTrip?> = trips.trip

    val railGraph: StateFlow<RailGraph> = lineStations.map { RailGraph.from(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, RailGraph(emptyMap()))

    // Trip report (#61): the days with dated finds, and one day's finds
    val findTimes: StateFlow<List<Long>> = repo.findTimes().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    fun findsBetween(from: Long, to: Long) = repo.findsBetween(from, to)

    fun saveTrip(t: SavedTrip) = trips.save(t)
    fun clearTrip() = trips.clear()
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

    /** Saves your edits to an item's details; catalogue updates leave it as you made it (owner, 2026-11-08). */
    fun editItem(item: io.github.thisisartman.collectibles.data.Item) {
        viewModelScope.launch { repo.editItem(item) }
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

            listOf(ItemType.GOSHUIN to "g", ItemType.DAM_CARD to "dam", ItemType.MUSEUM_STAMP to "mus").forEach { (kind, key) ->
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
