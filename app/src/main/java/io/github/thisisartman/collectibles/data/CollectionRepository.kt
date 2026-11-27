package io.github.thisisartman.collectibles.data

import androidx.room.withTransaction
import io.github.thisisartman.collectibles.debug.FileLog
import io.github.thisisartman.collectibles.location.LocationSource
import kotlinx.coroutines.sync.withLock
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
    private val backup: CatalogAndBackup,
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
     * gives it its find, today or on the date chosen, like every other stamp on the page. No position
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
     * A trip that already happened (#46, #68), or things you already have (onboarding, Collection check, My book,
     * the deck; #194: dated now, never "had before Meguri"): a find on that day for each item, no place or photo.
     * Skips items already collected. Returns the new finds, so Undo can take exactly these back.
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
        log.i("Log", "Marked ${made.size} items as collected, without a place")
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
            // Cleared whether the item goes or stays: its photos are all gone either way (#168)
            dao.setCover(itemId, null, now())
            photos.forEach { dao.deletePhoto(it.id) }
            findIds.forEach { dao.deleteFind(it) }
            if (alsoItem) dao.deleteItem(itemId)
        }
        photos.forEach { io.github.thisisartman.collectibles.photo.PhotoStore.delete(context, it.contentUri, it.originalUri) }
        log.i("Log", if (alsoItem) "Removed item $itemId with ${findIds.size} finds and ${photos.size} photos" else "Took $itemId out of the collection: ${findIds.size} finds, ${photos.size} photos")
    }

    suspend fun removeFinds(findIds: Collection<String>) {
        db.withTransaction { findIds.forEach { dao.deleteFind(it) }; dao.clearMissingCovers() }
        log.i("Log", "Removed ${findIds.size} finds")
    }

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
    suspend fun attachFromGallery(
        context: android.content.Context, findId: String, picked: android.net.Uri, kind: String,
        name: String? = null, size: Long? = null, cutStamps: Boolean = false, original: android.net.Uri? = null,
    ): Boolean =
        kotlinx.coroutines.withContext(Dispatchers.IO) {
            // A file from a photo file (#188) has no picker details: its own name and size are kept, to spot it again
            val info = pickedInfo(picked).let { if (name != null) it.copy(displayName = name, sizeBytes = size ?: it.sizeBytes) else it }
            val id = newId()
            val item = dao.itemOfFind(findId)
            // An Ekitag screenshot is titled with the station's name on two lines, the app's language first (owner, Telegram 491)
            val title = item?.let { i ->
                val en = i.nameRomaji?.takeIf { it.isNotBlank() && it != i.nameJa }
                val ja = context.resources.configuration.locales[0]?.language == "ja"
                if (en == null) i.nameJa else if (ja) "${i.nameJa}\n$en" else "$en\n${i.nameJa}"
            }
            // Filed by what the picture shows, not only by what it was filed as (2026-11-27): a card is the card, a screenshot an Ekitag
            val camera = hasCameraExif(picked)
            val folder = pickedFolder(picked)
            val sorted = io.github.thisisartman.collectibles.photo.PhotoStore.importSorted(context, picked, id, decide = { bmp ->
                sort(item?.type, kind, bmp, info.displayName, folder, camera, cutStamps)
            }, ekitagTitle = title)
            val uri = sorted?.uri
            if (uri == null) {
                sorted?.originalUri?.let { io.github.thisisartman.collectibles.photo.PhotoStore.delete(context, it) }
                log.w("Photo", "Could not copy a gallery photo for find $findId")
                return@withContext false
            }
            val d = sorted.decision
            if (d.kind != kind) log.i("Photo", "Filed as $kind, sorted to ${d.kind} (${d.crop})") else log.d("Photo", "Kind $kind, cut ${d.crop}")
            nextKind[findId] = d.kind
            // A scanned card comes in already trimmed: its untrimmed page is the original (#208), in place of any the import kept
            val kept = original?.let { io.github.thisisartman.collectibles.photo.PhotoStore.importOriginal(context, it, id) } ?: sorted.originalUri
            if (original != null) log.d("Photo", "Scan original kept: ${kept != null}")
            insertPhoto(id, findId, info.copy(uri = uri), kept)
            true
        }

    /** [PhotoSort.decide] on a picture as read: a card item's photo is looked at for a card, any photo for a screenshot. */
    private fun sort(
        type: ItemType?, kind: String, bmp: android.graphics.Bitmap, name: String?, path: String?, camera: Boolean, cutStamps: Boolean,
    ): io.github.thisisartman.collectibles.photo.PhotoSort.Decision {
        val sort = io.github.thisisartman.collectibles.photo.PhotoSort
        val card = if (sort.checksCard(type, kind)) runCatching { io.github.thisisartman.collectibles.photo.CardTrim.detect(bmp) }.getOrNull() else null
        val shot = sort.isScreenshot(name, path, bmp.width, bmp.height, camera)
        return sort.decide(type, kind, io.github.thisisartman.collectibles.photo.PhotoSort.Facts(card, shot), cutStamps)
    }

    /** Whether the picture says which camera took it (EXIF make or model); a screenshot doesn't. */
    private fun hasCameraExif(uri: android.net.Uri): Boolean = runCatching {
        context.contentResolver.openInputStream(uri)?.use { s ->
            val exif = android.media.ExifInterface(s)
            !exif.getAttribute(android.media.ExifInterface.TAG_MAKE).isNullOrBlank() ||
                !exif.getAttribute(android.media.ExifInterface.TAG_MODEL).isNullOrBlank()
        } ?: false
    }.getOrDefault(false)

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

    /** The gallery folder a picked picture is in (Pictures/Screenshots/, say), where the picker tells; for sorting only. */
    private fun pickedFolder(picked: android.net.Uri): String? = runCatching {
        context.contentResolver.query(picked, arrayOf(android.provider.MediaStore.MediaColumns.RELATIVE_PATH), null, null, null)
            ?.use { c -> if (c.moveToFirst() && !c.isNull(0)) c.getString(0) else null }
    }.getOrNull()

    suspend fun readShared(uri: android.net.Uri): SharedPhoto =
        kotlinx.coroutines.withContext(Dispatchers.IO) { SharedPhotoReader.read(context, uri) }

    /**
     * Photos shared into Meguri for one item (#80): they go on its latest find, or on a new find dated when the first
     * was taken and placed where they were taken. Each gets Meguri's own copy, filed as Other to sort later. Returns
     * how many were copied.
     */
    suspend fun addShared(
        itemId: String, photos: List<SharedPhoto>, centre: Pair<Double, Double>?, kind: String = PhotoKind.OTHER,
        originals: Map<String, android.net.Uri> = emptyMap(),
    ): Int {
        val findId = dao.latestFindId(itemId) ?: newId().also { f ->
            dao.insertFind(
                Acquisition(
                    id = f, itemId = itemId, acquiredAt = photos.mapNotNull { it.takenAt }.minOrNull() ?: now(),
                    lat = centre?.first, lng = centre?.second, via = Via.MANUAL, createdAt = now(),
                ),
            )
        }
        var copied = 0
        for (p in photos) if (attachFromGallery(context, findId, android.net.Uri.parse(p.uri), kind, original = originals[p.uri])) copied++
        log.i("Share", "Added $copied of ${photos.size} shared photos to item $itemId")
        return copied
    }

    /**
     * A stamp from a notebook page (owner, 2026-11-09). Never the photo's time and place: the page is photographed
     * at home long after. An item already collected only gains the photo; otherwise it is found on [acquiredAt] when
     * the person gives a day, else today (#194: never "had before Meguri").
     */
    suspend fun addNotebookStamp(itemId: String, picture: android.net.Uri, acquiredAt: Long?): Boolean =
        addOldPhoto(itemId, picture, acquiredAt, PhotoKind.STAMP, tag = "Notebook")

    /**
     * A photo of something collected earlier: from a notebook page, or from a photo file (#188). An item already
     * collected only gains the photo; otherwise it is found on [acquiredAt] (a day at noon), or now (#194).
     */
    suspend fun addOldPhoto(
        itemId: String, picture: android.net.Uri, acquiredAt: Long?, kind: String,
        name: String? = null, size: Long? = null, tag: String = "Import", cutStamps: Boolean = false,
    ): Boolean {
        val findId = dao.latestFindId(itemId) ?: newId().also { f ->
            // A day without a time (a date given is stored at noon): BULK, like "Already went", so no time shows
            dao.insertFind(Acquisition(id = f, itemId = itemId, acquiredAt = acquiredAt ?: now(), via = Via.BULK, createdAt = now()))
        }
        val ok = attachFromGallery(context, findId, picture, kind, name, size, cutStamps)
        log.i(tag, "Old $kind photo for item $itemId (photo copied: $ok)")
        return ok
    }

    suspend fun editItem(item: Item) {
        val now = now()
        dao.upsertItem(item.copy(editedAt = now, updatedAt = now))
        log.i("Item", "Edited ${item.id}: name, place or position")
    }

    /**
     * The cover position read off a scanned card (#203). Only fills an empty one, so a position you set in Edit details
     * (or one the catalogue gave) stays; and it is not an edit, so catalogue updates still reach the card.
     */
    suspend fun setScannedCover(itemId: String, lat: Double, lng: Double): Boolean {
        val n = dao.setCoverIfEmpty(itemId, lat, lng, now())
        if (n > 0) log.i("Scan", "Cover position for $itemId read off the card")
        return n > 0
    }

    suspend fun setPhotoKind(photoId: String, kind: String) {
        dao.setPhotoRole(photoId, kind)
        log.i("Photo", "Kind of $photoId is now $kind")
        // Marking a photo as the item's main kind can make it the display picture too
        dao.allPhotos().firstOrNull { it.id == photoId }?.let { autoCover(photoId, it.acquisitionId) }
    }

    suspend fun removePhoto(photoId: String) {
        val p = dao.allPhotos().firstOrNull { it.id == photoId }
        db.withTransaction { dao.deletePhoto(photoId); dao.clearMissingCovers() }
        p?.let { io.github.thisisartman.collectibles.photo.PhotoStore.delete(context, it.contentUri, it.originalUri) }
        log.i("Photo", "Removed photo $photoId from its find, with Meguri's copy (a camera original stays in the gallery)")
    }

    /**
     * Saves an edit of a photo (#208): a new copy cut from its kept original (or, without one, from the photo as it
     * is), under a new name so nothing shows the old picture; the photo keeps its id, kind and display-picture choice,
     * and its original stays. False if the picture couldn't be read or written; then nothing changes.
     */
    suspend fun editPhoto(photoId: String, edit: io.github.thisisartman.collectibles.photo.PhotoEdit): Boolean = kotlinx.coroutines.withContext(Dispatchers.IO) {
        val store = io.github.thisisartman.collectibles.photo.PhotoStore
        // The backup lock, so a backup never zips a photo halfway through being replaced
        backup.lock.withLock {
            val p = dao.allPhotos().firstOrNull { it.id == photoId } ?: return@withLock false
            val source = if (edit.fromOriginal) p.originalUri else p.contentUri
            if (source == null || !store.exists(context, source)) {
                log.w("Photo", "Edit of $photoId: its picture isn't in the app")
                return@withLock false
            }
            val uri = store.saveEdit(context, source, photoId, edit) ?: run {
                log.w("Photo", "Edit of $photoId could not be written")
                return@withLock false
            }
            dao.setPhotoUri(photoId, uri)
            if (p.contentUri != uri && p.contentUri != p.originalUri) store.delete(context, p.contentUri)
            log.i("Photo", "Edited $photoId: ${edit.quarterTurns} turns, ${edit.degrees} degrees, circle ${edit.circle}, from the original ${edit.fromOriginal}")
            true
        }
    }

    /** A camera photo: Meguri keeps its own copy (#79); if the copy fails, the photo points at the gallery and a later start copies it. */
    suspend fun attachPhoto(findId: String, photo: PhotoInfo) = kotlinx.coroutines.withContext(Dispatchers.IO) {
        val id = newId()
        // Sorted like a gallery pick (2026-11-27): a card photographed for the cover slot goes to the card, uncut
        val type = dao.itemOfFind(findId)?.type
        val asked = nextKind[findId] ?: PhotoKind.OTHER
        val source = android.net.Uri.parse(photo.uri)
        val camera = hasCameraExif(source)
        val sorted = io.github.thisisartman.collectibles.photo.PhotoStore.importSorted(context, source, id, decide = { bmp ->
            sort(type, asked, bmp, photo.displayName, photo.relativePath, camera, cutStamps = false)
        })
        val uri = sorted?.uri
        if (uri == null) sorted?.originalUri?.let { io.github.thisisartman.collectibles.photo.PhotoStore.delete(context, it) }
        // A photo that couldn't be read keeps the kind asked for; the copy is tried again later
        sorted?.decision?.let { d ->
            if (d.kind != asked) log.i("Photo", "Camera photo filed as $asked, sorted to ${d.kind} (${d.crop})")
            nextKind[findId] = d.kind
        }
        insertPhoto(id, findId, if (uri != null) photo.copy(uri = uri) else photo, sorted?.originalUri?.takeIf { uri != null })
    }

    private suspend fun insertPhoto(id: String, findId: String, photo: PhotoInfo, originalUri: String? = null) {
        dao.insertPhoto(
            Photo(
                id = id, acquisitionId = findId, contentUri = photo.uri, takenAt = photo.takenAt,
                role = nextKind.remove(findId) ?: PhotoKind.OTHER,
                mediastoreId = photo.mediastoreId, displayName = photo.displayName, relativePath = photo.relativePath,
                sizeBytes = photo.sizeBytes, createdAt = now(), originalUri = originalUri,
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
