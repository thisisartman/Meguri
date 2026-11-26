package io.github.thisisartman.collectibles.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

private const val ITEM_ROW = """
    SELECT i.*,
      (SELECT COUNT(*) FROM acquisition a WHERE a.item_id = i.id) AS find_count,
      (SELECT MAX(a.acquired_at) FROM acquisition a WHERE a.item_id = i.id) AS last_at,
      COALESCE(
        (SELECT c.content_uri FROM photo c WHERE c.id = i.cover_photo_id AND c.missing = 0),
        (SELECT p.content_uri FROM photo p JOIN acquisition a ON p.acquisition_id = a.id
           WHERE a.item_id = i.id AND p.missing = 0
           ORDER BY COALESCE(a.acquired_at, 0) DESC, p.created_at DESC LIMIT 1)) AS photo_uri
    FROM item i
"""

@Dao
interface CollectionDao {
    // Items
    @Query("$ITEM_ROW ORDER BY i.type, i.prefecture, i.code, i.name_ja")
    fun observeItemRows(): Flow<List<ItemRow>>

    @Query("$ITEM_ROW WHERE i.id = :id")
    fun observeItemRow(id: String): Flow<ItemRow?>

    @Query("$ITEM_ROW WHERE i.id = :id")
    suspend fun itemRowOnce(id: String): ItemRow?

    @Query("SELECT * FROM item WHERE id = :id")
    suspend fun item(id: String): Item?

    /** Alert candidates: a place, not an Ekitag, and no find yet. */
    @Query(
        """
        SELECT * FROM item i
        WHERE i.lat IS NOT NULL AND i.lng IS NOT NULL AND i.type != 'EKITAG'
          AND NOT EXISTS (SELECT 1 FROM acquisition a WHERE a.item_id = i.id)
          AND i.none_here_at IS NULL
        """,
    )
    suspend fun uncollectedPlaces(): List<Item>

    /** Ekitag still to get that have a place: they alert like station stamps, at stations you get off at (#138). */
    @Query(
        """
        SELECT * FROM item i
        WHERE i.lat IS NOT NULL AND i.lng IS NOT NULL AND i.type = 'EKITAG'
          AND NOT EXISTS (SELECT 1 FROM acquisition a WHERE a.item_id = i.id)
          AND i.none_here_at IS NULL
        """,
    )
    suspend fun uncollectedEkitag(): List<Item>

    /** The types with at least one item on the phone. */
    @Query("SELECT DISTINCT type FROM item")
    suspend fun typesPresent(): List<ItemType>

    @Query("SELECT * FROM item WHERE catalog_key = :key")
    suspend fun itemByCatalogKey(key: String): Item?

    /** Up to 999 keys per call (SQLite's limit): callers chunk. */
    @Query("SELECT * FROM item WHERE catalog_key IN (:keys)")
    suspend fun itemsByCatalogKeys(keys: List<String>): List<Item>

    @Upsert
    suspend fun upsertItems(items: List<Item>)

    @Upsert
    suspend fun upsertLines(lines: List<Line>)

    @Upsert
    suspend fun upsertLineStations(ls: List<LineStation>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertItem(item: Item)

    @Upsert
    suspend fun upsertItem(item: Item)

    @Query("SELECT COUNT(*) FROM item")
    suspend fun itemCount(): Int

    // Finds
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertFind(find: Acquisition)

    @Query("UPDATE acquisition SET lat = :lat, lng = :lng, accuracy_m = :accuracy WHERE id = :id AND lat IS NULL")
    suspend fun setFindLocation(id: String, lat: Double, lng: Double, accuracy: Float?)

    @Query("DELETE FROM acquisition WHERE id = :id")
    suspend fun deleteFind(id: String)

    @Query("DELETE FROM item WHERE id = :id")
    suspend fun deleteItem(id: String)


    /** Finds dated in [from, to), oldest first, for the trip report (#61). Finds with no date are left out. */
    @Transaction
    @Query("SELECT * FROM acquisition WHERE acquired_at >= :from AND acquired_at < :to ORDER BY acquired_at")
    fun observeFindsBetween(from: Long, to: Long): Flow<List<FindWithPhotos>>

    /** When each dated find was made, newest first: the days a report can be made for. */
    @Query("SELECT acquired_at FROM acquisition WHERE acquired_at IS NOT NULL ORDER BY acquired_at DESC")
    fun observeFindTimes(): Flow<List<Long>>

    @Query("SELECT COUNT(*) FROM acquisition WHERE item_id = :itemId")
    suspend fun findCount(itemId: String): Int

    /**
     * Removing a prefecture's pack (0.5): its catalogue items of one type that you have not touched. Items with a find
     * cannot go (the find's key forbids it), and anything you edited, marked as none here or gave a cover stays.
     */
    @Query("""DELETE FROM item WHERE prefecture = :prefecture AND type = :type AND origin = 'CSV_IMPORT'
        AND edited_at IS NULL AND none_here_at IS NULL AND cover_photo_id IS NULL
        AND id NOT IN (SELECT item_id FROM acquisition)""")
    suspend fun deleteUntouched(prefecture: String, type: String): Int

    /** Lines left with no stations after a removal. */
    @Query("DELETE FROM line WHERE id NOT IN (SELECT line_id FROM line_station)")
    suspend fun deleteEmptyLines(): Int

    /** The item's most recent find, where shared photos go (#80). */
    @Query("SELECT id FROM acquisition WHERE item_id = :itemId ORDER BY COALESCE(acquired_at, created_at) DESC LIMIT 1")
    suspend fun latestFindId(itemId: String): String?

    @Transaction
    @Query("SELECT * FROM acquisition WHERE item_id = :itemId ORDER BY COALESCE(acquired_at, 0) DESC, created_at DESC")
    fun observeFinds(itemId: String): Flow<List<FindWithPhotos>>

    // Photos
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPhoto(photo: Photo)

    @Query("UPDATE photo SET role = :role WHERE id = :id")
    suspend fun setPhotoRole(id: String, role: String)

    /** Only the link: the picture itself stays in the gallery. */
    @Query("DELETE FROM photo WHERE id = :id")
    suspend fun deletePhoto(id: String)

    /**
     * Display pictures whose photo is gone (removed, or its find removed) are cleared, so the next photo can become the
     * display picture and the item can go with its region (#168). The column has no foreign key, so this runs after
     * every removal.
     */
    @Query("UPDATE item SET cover_photo_id = NULL WHERE cover_photo_id IS NOT NULL AND cover_photo_id NOT IN (SELECT id FROM photo)")
    suspend fun clearMissingCovers(): Int

    // Lines (eki-stamp book)
    @Query("SELECT * FROM line ORDER BY name_ja")
    fun observeLines(): Flow<List<Line>>

    @Query("SELECT * FROM line WHERE id = :id")
    fun observeLine(id: String): Flow<Line?>

    @Upsert
    suspend fun upsertLine(line: Line)

    @Upsert
    suspend fun upsertLineStation(ls: LineStation)

    @Query("SELECT * FROM line_station ORDER BY line_id, seq")
    fun observeLineStations(): Flow<List<LineStation>>

    @Query(
        """
        SELECT r.*, ls.seq AS seq FROM ($ITEM_ROW) r
        JOIN line_station ls ON ls.item_id = r.id
        WHERE ls.line_id = :lineId ORDER BY ls.seq
        """,
    )
    fun observeStations(lineId: String): Flow<List<StationRow>>

    // Export
    @Query("SELECT * FROM item ORDER BY type, code, name_ja")
    suspend fun allItems(): List<Item>

    @Query("SELECT * FROM acquisition ORDER BY COALESCE(acquired_at, 0), created_at")
    suspend fun allFinds(): List<Acquisition>

    @Query("SELECT * FROM photo ORDER BY created_at")
    suspend fun allPhotos(): List<Photo>

    @Query("SELECT * FROM line ORDER BY id")
    suspend fun allLines(): List<Line>

    @Query("SELECT * FROM line_station ORDER BY line_id, seq")
    suspend fun allLineStations(): List<LineStation>

    // Restore
    @Query("SELECT COUNT(*) FROM acquisition")
    suspend fun findTotal(): Int

    @Query("SELECT COUNT(*) FROM photo")
    suspend fun photoTotal(): Int

    @Query("SELECT COUNT(*) FROM line")
    suspend fun lineCount(): Int

    @Query("DELETE FROM photo")
    suspend fun deleteAllPhotos()

    @Query("DELETE FROM acquisition")
    suspend fun deleteAllFinds()

    @Query("DELETE FROM line_station")
    suspend fun deleteAllLineStations()

    @Query("DELETE FROM line")
    suspend fun deleteAllLines()

    @Query("DELETE FROM item")
    suspend fun deleteAllItems()

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertItems(items: List<Item>)

    // Your own collections (#64, schema v2)
    @Query("SELECT * FROM collection_type ORDER BY created_at")
    fun observeCollectionTypes(): Flow<List<CollectionType>>

    @Query("SELECT * FROM collection_type ORDER BY created_at")
    suspend fun allCollectionTypes(): List<CollectionType>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCollectionType(t: CollectionType)

    @Query("DELETE FROM collection_type")
    suspend fun deleteAllCollectionTypes()

    @Query("DELETE FROM collection_type WHERE id = :id")
    suspend fun deleteCollectionType(id: String)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCollectionTypes(types: List<CollectionType>)

    @Query("UPDATE acquisition SET quantity = :quantity WHERE id = :id")
    suspend fun setFindQuantity(id: String, quantity: Int)

    @Query("UPDATE acquisition SET cost_yen = :yen WHERE id = :id")
    suspend fun setFindCost(id: String, yen: Int?)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertFinds(finds: List<Acquisition>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPhotos(photos: List<Photo>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertLines(lines: List<Line>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertLineStations(ls: List<LineStation>)

    @Query("UPDATE photo SET content_uri = :uri, mediastore_id = :mediastoreId, missing = :missing WHERE id = :id")
    suspend fun setPhotoLink(id: String, uri: String, mediastoreId: Long, missing: Boolean)

    @Query("UPDATE photo SET content_uri = :uri, missing = 0 WHERE id = :id")
    suspend fun setPhotoUri(id: String, uri: String)

    @Query("UPDATE photo SET missing = :missing WHERE id = :id")
    suspend fun setPhotoMissing(id: String, missing: Boolean)

    @Query("UPDATE item SET none_here_at = :at, updated_at = :now WHERE id = :id")
    suspend fun setNoneHere(id: String, at: Long?, now: Long)

    @Query("SELECT i.* FROM item i JOIN acquisition a ON a.item_id = i.id WHERE a.id = :findId")
    suspend fun itemOfFind(findId: String): Item?

    @Query("UPDATE item SET cover_photo_id = :photoId, updated_at = :now WHERE id = :id")
    suspend fun setCover(id: String, photoId: String?, now: Long)
}
