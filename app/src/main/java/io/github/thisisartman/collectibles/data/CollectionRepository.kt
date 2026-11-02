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
    suspend fun unmarkBeforeApp(itemId: String): Int = dao.deleteBeforeAppFinds(itemId)

    suspend fun attachPhoto(findId: String, photo: PhotoInfo) {
        dao.insertPhoto(
            Photo(
                id = newId(), acquisitionId = findId, contentUri = photo.uri, takenAt = photo.takenAt,
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
