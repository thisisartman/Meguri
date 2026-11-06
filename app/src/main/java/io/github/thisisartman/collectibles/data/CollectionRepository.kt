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
            if (fix != null) dao.setFindLocation(id, fix.lat, fix.lng, fix.accuracyM)
            else log.w("Log", "No position for find $id")
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

    /** "Not in the list?": a new item and its find together. Returns the item id. */
    suspend fun addItemWithFind(type: ItemType, nameJa: String, nameRomaji: String?): Pair<String, String> {
        val itemId = newId()
        val findId = newId()
        val fix = location.lastKnown()?.takeIf { now() - it.timeMs <= 120_000 }
        db.withTransaction {
            dao.insertItem(
                Item(
                    id = itemId, type = type, nameJa = nameJa.trim(), nameRomaji = nameRomaji?.trim()?.ifEmpty { null },
                    lat = fix?.lat, lng = fix?.lng, origin = Origin.USER, createdAt = now(), updatedAt = now(),
                ),
            )
            dao.insertFind(Acquisition(id = findId, itemId = itemId, acquiredAt = now(), via = Via.MANUAL, createdAt = now()))
        }
        log.i("Log", "New item $itemId ($type) with find $findId")
        scope.launch {
            val f = location.currentOrRecent() ?: return@launch
            dao.setFindLocation(findId, f.lat, f.lng, f.accuracyM)
            dao.item(itemId)?.takeIf { it.lat == null }?.let { dao.upsertItem(it.copy(lat = f.lat, lng = f.lng, updatedAt = now())) }
        }
        return itemId to findId
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
     * A picture chosen from the gallery (a screenshot of an Ekitag, say) is copied into Meguri's own photo folder, so
     * it relinks after a restore like a camera photo, and the picker's short-lived access does not matter.
     */
    suspend fun attachFromGallery(context: android.content.Context, findId: String, picked: android.net.Uri, kind: String): Boolean =
        kotlinx.coroutines.withContext(Dispatchers.IO) {
            val resolver = context.contentResolver
            val target = io.github.thisisartman.collectibles.photo.CameraCapture.newPhotoUri(resolver) ?: return@withContext false
            val ok = runCatching {
                resolver.openInputStream(picked)!!.use { input -> resolver.openOutputStream(target, "w")!!.use { input.copyTo(it) } }
            }.isSuccess
            val info = if (ok) io.github.thisisartman.collectibles.photo.CameraCapture.describe(resolver, target) else null
            if (info == null) {
                io.github.thisisartman.collectibles.photo.CameraCapture.discardIfEmpty(resolver, target)
                log.w("Photo", "Could not copy a gallery photo for find $findId")
                return@withContext false
            }
            nextKind[findId] = kind
            attachPhoto(findId, info)
            true
        }

    suspend fun setPhotoKind(photoId: String, kind: String) {
        dao.setPhotoRole(photoId, kind)
        log.i("Photo", "Kind of $photoId is now $kind")
    }

    suspend fun removePhoto(photoId: String) {
        dao.deletePhoto(photoId)
        log.i("Photo", "Removed photo $photoId from its find (the picture stays in the gallery)")
    }

    suspend fun attachPhoto(findId: String, photo: PhotoInfo) {
        dao.insertPhoto(
            Photo(
                id = newId(), acquisitionId = findId, contentUri = photo.uri, takenAt = photo.takenAt,
                role = nextKind.remove(findId) ?: PhotoKind.OTHER,
                mediastoreId = photo.mediastoreId, displayName = photo.displayName, relativePath = photo.relativePath,
                sizeBytes = photo.sizeBytes, createdAt = now(),
            ),
        )
        log.i("Photo", "Attached ${photo.displayName} to find $findId")
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
