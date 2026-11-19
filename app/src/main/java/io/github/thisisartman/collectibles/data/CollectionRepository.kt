package io.github.thisisartman.collectibles.data

import androidx.room.withTransaction
import io.github.thisisartman.collectibles.debug.FileLog
import io.github.thisisartman.collectibles.location.LocationSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CollectionRepository @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
    private val db: CollectiblesDb,
    private val location: LocationSource,
    private val log: FileLog,
) {
    private val dao = db.dao()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private fun now() = System.currentTimeMillis()
    private fun newId() = UUID.randomUUID().toString()

    fun itemRows(): Flow<List<ItemRow>> = dao.observeItemRows()
    fun itemRow(id: String): Flow<ItemRow?> = dao.observeItemRow(id)
    suspend fun itemRowOnce(id: String): ItemRow? = dao.itemRowOnce(id)
    fun finds(itemId: String): Flow<List<FindWithPhotos>> = dao.observeFinds(itemId)
    fun findsBetween(from: Long, to: Long): Flow<List<FindWithPhotos>> = dao.observeFindsBetween(from, to)
    fun findTimes(): Flow<List<Long>> = dao.observeFindTimes()
    fun lines(): Flow<List<Line>> = dao.observeLines()
    fun line(id: String): Flow<Line?> = dao.observeLine(id)
    fun lineStations(): Flow<List<LineStation>> = dao.observeLineStations()
    fun stations(lineId: String): Flow<List<StationRow>> = dao.observeStations(lineId)

    /**
     * Logs a find straight away (time now, no position) and returns its id; the position is
     * filled in later from a fresh fix or a recent last-known one. Never waits on GPS.
     */
    suspend fun logFind(itemId: String, via: Via = Via.MANUAL): String {
        val id = newId()
        dao.insertFind(Acquisition(id = id, itemId = itemId, acquiredAt = now(), via = via, createdAt = now()))
        log.i("Log", "Find $id for item $itemId")
        scope.launch {
            val fix = location.currentOrRecent()
            if (fix != null) {
                dao.setFindLocation(id, fix.lat, fix.lng, fix.accuracyM)
                log.d("Log", "Position for find $id: ${fix.accuracyM?.let { "±${it.toInt()} m" } ?: "no accuracy"}, ${(now() - fix.timeMs) / 1000} s old")
            } else log.w("Log", "No position for find $id")
        }
        return id
    }

    /**
     * Logs a find from an alert (Done on the notification). Uses [fix], the position that set off the alert, when it
     * is under 10 minutes old; otherwise looks for a position the usual way.
     */
    suspend fun logFindAt(itemId: String, via: Via, fix: io.github.thisisartman.collectibles.location.Fix?): String {
        val fresh = fix?.takeIf { now() - it.timeMs <= 10 * 60_000 }
        if (fresh == null) return logFind(itemId, via)
        val id = newId()
        dao.insertFind(
            Acquisition(
                id = id, itemId = itemId, acquiredAt = now(), lat = fresh.lat, lng = fresh.lng, accuracyM = fresh.accuracyM,
                via = via, createdAt = now(),
            ),
        )
        log.i("Log", "Find $id for item $itemId from an alert")
        return id
    }

    /**
     * Another try at a find's position once the camera has closed: Android stops a "while using the app"
     * location request while the camera is in front. Keeps a position the find already has.
     */
    fun retryPosition(findId: String) {
        scope.launch {
            val fix = location.currentOrRecent()
            if (fix != null) {
                dao.setFindLocation(findId, fix.lat, fix.lng, fix.accuracyM)
                log.i("Log", "Position after the camera for find $findId")
            } else {
                log.w("Log", "No position after the camera for find $findId")
            }
        }
    }

    /** Undo: removes the find (and its photo links; the photos stay in the gallery). */
    suspend fun undoFind(findId: String) {
        dao.deleteFind(findId)
        log.i("Log", "Undo find $findId")
    }

    /**
     * "Not in the list?": a new item and its find together. Returns the item id.
     * [customTypeId] puts it in one of your own collections (type CUSTOM); [place] is a place looked up on
     * OpenStreetMap (else your position is used); [costYen] is what you paid, where the kind has a cost.
     */
    suspend fun addItemWithFind(
        type: ItemType, nameJa: String, nameRomaji: String?,
        customTypeId: String? = null, place: PlaceHit? = null, costYen: Int? = null,
    ): Pair<String, String> {
        val itemId = newId()
        val findId = newId()
        val fix = location.lastKnown()?.takeIf { now() - it.timeMs <= 120_000 }
        db.withTransaction {
            dao.insertItem(
                Item(
                    id = itemId, type = type, nameJa = nameJa.trim(), nameRomaji = nameRomaji?.trim()?.ifEmpty { null },
                    lat = place?.lat ?: fix?.lat, lng = place?.lng ?: fix?.lng,
                    placeName = place?.name, address = place?.address,
                    sourceUrl = place?.osmUrl,
                    origin = Origin.USER, createdAt = now(), updatedAt = now(), customTypeId = customTypeId,
                ),
            )
            dao.insertFind(Acquisition(id = findId, itemId = itemId, acquiredAt = now(), via = Via.MANUAL, createdAt = now(), costYen = costYen))
        }
        log.i("Log", "New item $itemId ($type) with find $findId")
        scope.launch {
            val f = location.currentOrRecent() ?: return@launch
            dao.setFindLocation(findId, f.lat, f.lng, f.accuracyM)
            dao.item(itemId)?.takeIf { it.lat == null }?.let { dao.upsertItem(it.copy(lat = f.lat, lng = f.lng, updatedAt = now())) }
        }
        return itemId to findId
    }

    /**
     * A new item with no find yet (finding 33): "Not in Meguri? Add it" from a notebook page. Saving the page then
     * gives it its find, had before Meguri or on the date chosen, like every other stamp on the page. No position
     * from the phone: the page is photographed at home, so only a place looked up counts.
     */
    suspend fun addItem(type: ItemType, nameJa: String, nameRomaji: String?, customTypeId: String? = null, place: PlaceHit? = null): String {
        val itemId = newId()
        dao.insertItem(
            Item(
                id = itemId, type = type, nameJa = nameJa.trim(), nameRomaji = nameRomaji?.trim()?.ifEmpty { null },
                lat = place?.lat, lng = place?.lng, placeName = place?.name, address = place?.address, sourceUrl = place?.osmUrl,
                origin = Origin.USER, createdAt = now(), updatedAt = now(), customTypeId = customTypeId,
            ),
        )
        log.i("Log", "New item $itemId ($type) from a notebook page, no find yet")
        return itemId
    }

    /** Gives items you added their prefecture from their position ([Prefectures.missing]); only where still empty. */
    suspend fun fillPrefectures(found: List<Pair<String, String>>) {
        if (found.isEmpty()) return
        db.withTransaction {
            found.forEach { (id, pref) ->
                dao.item(id)?.takeIf { it.prefecture.isNullOrBlank() }?.let { dao.upsertItem(it.copy(prefecture = pref, updatedAt = now())) }
            }
        }
        log.i("Log", "Prefecture filled from position for ${found.size} item(s)")
    }

    // Your own collections (#64) and the details of a collection: how many (duplicates) and what you paid
    fun collectionTypes(): kotlinx.coroutines.flow.Flow<List<CollectionType>> = dao.observeCollectionTypes()

    suspend fun addCollectionType(name: String, color: Long, icon: String, hasCost: Boolean): String {
        val id = newId()
        dao.upsertCollectionType(CollectionType(id = id, name = name.trim(), color = color, icon = icon, hasCost = hasCost, createdAt = now()))
        log.i("Log", "New collection $id")
        return id
    }

    suspend fun setQuantity(findId: String, quantity: Int) {
        dao.setFindQuantity(findId, quantity.coerceIn(1, 99))
        log.d("Log", "Find $findId: quantity ${quantity.coerceIn(1, 99)}")
    }

    suspend fun setCost(findId: String, yen: Int?) {
        dao.setFindCost(findId, yen?.coerceAtLeast(0))
        log.d("Log", "Find $findId: cost ${yen?.coerceAtLeast(0) ?: "none"}")
    }

    /**
     * A trip that already happened (#46, #68): a find on that day for each item, no place or photo. Skips items
     * already collected. Returns the new finds, so Undo can take exactly these back.
     */
    suspend fun markOn(itemIds: Collection<String>, atMs: Long): List<String> {
        val made = mutableListOf<String>()
        db.withTransaction {
            for (id in itemIds) {
                if (dao.findCount(id) > 0) continue
                val f = newId()
                dao.insertFind(Acquisition(id = f, itemId = id, acquiredAt = atMs, via = Via.BULK, createdAt = now()))
                made += f
            }
        }
        log.i("Log", "Marked ${made.size} items as got on a past trip")
        return made
    }

    /**
     * Removes every find of an item with its photos (Meguri's copies; camera originals stay in the gallery). An item
     * you added yourself goes too; a catalogue item stays, back to not collected (owner, 2026-11-09: "yes. or remove").
     */
    suspend fun removeItem(itemId: String, alsoItem: Boolean) {
        val findIds = dao.allFinds().filter { it.itemId == itemId }.map { it.id }.toSet()
        val photos = dao.allPhotos().filter { it.acquisitionId in findIds }
        db.withTransaction {
            if (alsoItem) dao.setCover(itemId, null, now())
            photos.forEach { dao.deletePhoto(it.id) }
            findIds.forEach { dao.deleteFind(it) }
            if (alsoItem) dao.deleteItem(itemId)
        }
        photos.forEach { io.github.thisisartman.collectibles.photo.PhotoStore.delete(context, it.contentUri) }
        log.i("Log", if (alsoItem) "Removed item $itemId with ${findIds.size} finds and ${photos.size} photos" else "Took $itemId out of the collection: ${findIds.size} finds, ${photos.size} photos")
    }

    suspend fun removeFinds(findIds: Collection<String>) {
        db.withTransaction { findIds.forEach { dao.deleteFind(it) } }
        log.i("Log", "Removed ${findIds.size} finds")
    }

    /** Marks items as "had before the app": a find with no date, place or photo. Skips items already collected. */
    suspend fun markBeforeApp(itemIds: Collection<String>): Int {
        var added = 0
        db.withTransaction {
            for (id in itemIds) {
                if (dao.findCount(id) > 0) continue
                dao.insertFind(Acquisition(id = newId(), itemId = id, acquiredAt = null, beforeApp = true, via = Via.BULK, createdAt = now()))
                added++
            }
        }
        log.i("Log", "Marked $added items as had before the app")
        return added
    }

    /** Removes only "had before the app" finds of an item; real finds stay. */
    /** The photo that stands for the item in lists, on tiles and on the map (null: the latest one). */
    suspend fun setCover(itemId: String, photoId: String?) {
        dao.setCover(itemId, photoId, System.currentTimeMillis())
        log.i("Photo", "Cover of item $itemId: ${photoId ?: "latest"}")
    }

    /** You checked and there was nothing here (true), or there is after all (false). */
    suspend fun setNoneHere(itemId: String, none: Boolean) {
        dao.setNoneHere(itemId, if (none) System.currentTimeMillis() else null, System.currentTimeMillis())
        log.i("Log", (if (none) "Marked nothing here: item " else "Cleared nothing here: item ") + itemId)
    }

    suspend fun unmarkBeforeApp(itemId: String): Int {
        val removed = dao.deleteBeforeAppFinds(itemId)
        log.i("Log", "Removed had-before-the-app from item $itemId: $removed finds")
        return removed
    }

    /**
     * The kind the next camera photo for a find gets (#54). Held in memory only: if Android stops the app while the
     * camera is open, the photo is still attached, as "other", and its kind can be changed on the item page.
     */
    private val nextKind = java.util.concurrent.ConcurrentHashMap<String, String>()

    fun setNextPhotoKind(findId: String, kind: String) {
        nextKind[findId] = kind
    }

    /**
     * A picture chosen from the gallery (a screenshot of an Ekitag, say) gets only Meguri's own copy (#79), with no
     * second copy in the gallery; the picker's short-lived access does not matter once it is copied.
     */
    suspend fun attachFromGallery(context: android.content.Context, findId: String, picked: android.net.Uri, kind: String): Boolean =
        kotlinx.coroutines.withContext(Dispatchers.IO) {
            val info = pickedInfo(picked)
            val id = newId()
            // An Ekitag screenshot is titled with the station's name on two lines, the app's language first (owner, Telegram 491)
            val title = if (kind == PhotoKind.EKITAG) dao.itemOfFind(findId)?.let { i ->
                val en = i.nameRomaji?.takeIf { it.isNotBlank() && it != i.nameJa }
                val ja = context.resources.configuration.locales[0]?.language == "ja"
                if (en == null) i.nameJa else if (ja) "${i.nameJa}\n$en" else "$en\n${i.nameJa}"
            } else null
            val uri = io.github.thisisartman.collectibles.photo.PhotoStore.importCopy(context, picked, id, circle = kind == PhotoKind.MANHOLE, ekitagTitle = title)
            if (uri == null) {
                log.w("Photo", "Could not copy a gallery photo for find $findId")
                return@withContext false
            }
            nextKind[findId] = kind
            insertPhoto(id, findId, info.copy(uri = uri))
            true
        }

    /** What the picker tells about a picked picture: its name, size and, where it says, when it was taken. */
    private fun pickedInfo(picked: android.net.Uri): PhotoInfo {
        val cols = arrayOf(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, android.provider.MediaStore.MediaColumns.SIZE)
        var name: String? = null
        var size: Long? = null
        runCatching {
            context.contentResolver.query(picked, cols, null, null, null)?.use { c ->
                if (c.moveToFirst()) { name = c.getString(0); size = if (c.isNull(1)) null else c.getLong(1) }
            }
        }
        val taken = runCatching {
            context.contentResolver.query(picked, arrayOf(android.provider.MediaStore.Images.Media.DATE_TAKEN), null, null, null)
                ?.use { c -> if (c.moveToFirst() && !c.isNull(0)) c.getLong(0).takeIf { it > 0 } else null }
        }.getOrNull()
        return PhotoInfo(picked.toString(), null, name, null, size, taken ?: now())
    }

    suspend fun readShared(uri: android.net.Uri): SharedPhoto =
        kotlinx.coroutines.withContext(Dispatchers.IO) { SharedPhotoReader.read(context, uri) }

    /**
     * Photos shared into Meguri for one item (#80): they go on its latest find, or on a new find dated when the first
     * was taken and placed where they were taken. Each gets Meguri's own copy, filed as Other to sort later. Returns
     * how many were copied.
     */
    suspend fun addShared(itemId: String, photos: List<SharedPhoto>, centre: Pair<Double, Double>?, kind: String = PhotoKind.OTHER): Int {
        val findId = dao.latestFindId(itemId) ?: newId().also { f ->
            dao.insertFind(
                Acquisition(
                    id = f, itemId = itemId, acquiredAt = photos.mapNotNull { it.takenAt }.minOrNull() ?: now(),
                    lat = centre?.first, lng = centre?.second, via = Via.MANUAL, createdAt = now(),
                ),
            )
        }
        var copied = 0
        for (p in photos) if (attachFromGallery(context, findId, android.net.Uri.parse(p.uri), kind)) copied++
        log.i("Share", "Added $copied of ${photos.size} shared photos to item $itemId")
        return copied
    }

    /**
     * A stamp from a notebook page (owner, 2026-11-09). Never the photo's time and place: the page is photographed
     * at home long after. An item already collected only gains the photo; otherwise it is "had before Meguri" with no
     * date, or on [acquiredAt] when the person gives one.
     */
    suspend fun addNotebookStamp(itemId: String, picture: android.net.Uri, acquiredAt: Long?): Boolean {
        val findId = dao.latestFindId(itemId) ?: newId().also { f ->
            // A date given is a day without a time (stored at noon): BULK, like "Already went", so no "12:00" shows
            dao.insertFind(
                Acquisition(
                    id = f, itemId = itemId, acquiredAt = acquiredAt, beforeApp = acquiredAt == null,
                    via = if (acquiredAt == null) Via.MANUAL else Via.BULK, createdAt = now(),
                ),
            )
        }
        val ok = attachFromGallery(context, findId, picture, PhotoKind.STAMP)
        log.i("Notebook", "Stamp for item $itemId from a notebook page (photo copied: $ok)")
        return ok
    }

    suspend fun editItem(item: Item) {
        val now = now()
        dao.upsertItem(item.copy(editedAt = now, updatedAt = now))
        log.i("Item", "Edited ${item.id}: name, place or position")
    }

    suspend fun setPhotoKind(photoId: String, kind: String) {
        dao.setPhotoRole(photoId, kind)
        log.i("Photo", "Kind of $photoId is now $kind")
        // Marking a photo as the item's main kind can make it the display picture too
        dao.allPhotos().firstOrNull { it.id == photoId }?.let { autoCover(photoId, it.acquisitionId) }
    }

    suspend fun removePhoto(photoId: String) {
        val p = dao.allPhotos().firstOrNull { it.id == photoId }
        dao.deletePhoto(photoId)
        p?.let { io.github.thisisartman.collectibles.photo.PhotoStore.delete(context, it.contentUri) }
        log.i("Photo", "Removed photo $photoId from its find, with Meguri's copy (a camera original stays in the gallery)")
    }

    /** A camera photo: Meguri keeps its own copy (#79); if the copy fails, the photo points at the gallery and a later start copies it. */
    suspend fun attachPhoto(findId: String, photo: PhotoInfo) = kotlinx.coroutines.withContext(Dispatchers.IO) {
        val id = newId()
        val uri = io.github.thisisartman.collectibles.photo.PhotoStore.importCopy(context, android.net.Uri.parse(photo.uri), id, circle = nextKind[findId] == PhotoKind.MANHOLE)
        insertPhoto(id, findId, if (uri != null) photo.copy(uri = uri) else photo)
    }

    private suspend fun insertPhoto(id: String, findId: String, photo: PhotoInfo) {
        dao.insertPhoto(
            Photo(
                id = id, acquisitionId = findId, contentUri = photo.uri, takenAt = photo.takenAt,
                role = nextKind.remove(findId) ?: PhotoKind.OTHER,
                mediastoreId = photo.mediastoreId, displayName = photo.displayName, relativePath = photo.relativePath,
                sizeBytes = photo.sizeBytes, createdAt = now(),
            ),
        )
        log.i("Photo", "Attached ${photo.displayName} to find $findId")
        autoCover(id, findId)
    }

    /**
     * The first photo of an item's main kind becomes its display picture when it has none (owner, 2026-11-08):
     * a manhole card's cover, a station's Ekitag screenshot, a goshuin itself.
     */
    private suspend fun autoCover(photoId: String, findId: String) {
        val item = dao.itemOfFind(findId) ?: return
        if (item.coverPhotoId != null) return
        val role = dao.allPhotos().firstOrNull { it.id == photoId }?.role ?: return
        if (AutoCover.makesCover(item.type, role)) {
            dao.setCover(item.id, photoId, now())
            log.i("Photo", "Photo $photoId is now the display picture of ${item.id}")
        }
    }
}

/** What the app records about a gallery photo (PRD FR-9, FR-9a). */
data class PhotoInfo(
    val uri: String,
    val mediastoreId: Long?,
    val displayName: String?,
    val relativePath: String?,
    val sizeBytes: Long?,
    val takenAt: Long?,
)
