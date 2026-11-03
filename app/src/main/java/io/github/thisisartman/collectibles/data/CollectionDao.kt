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
      (SELECT a.before_app FROM acquisition a WHERE a.item_id = i.id
         ORDER BY COALESCE(a.acquired_at, 0) DESC, a.created_at DESC LIMIT 1) AS last_before_app,
      (SELECT p.content_uri FROM photo p JOIN acquisition a ON p.acquisition_id = a.id
         WHERE a.item_id = i.id AND p.missing = 0
         ORDER BY COALESCE(a.acquired_at, 0) DESC, p.created_at DESC LIMIT 1) AS photo_uri
    FROM item i
"""

@Dao
interface CollectionDao {
    // Items
    @Query("$ITEM_ROW ORDER BY i.type, i.prefecture, i.code, i.name_ja")
    fun observeItemRows(): Flow<List<ItemRow>>

    @Query("$ITEM_ROW WHERE i.id = :id")
    fun observeItemRow(id: String): Flow<ItemRow?>

    @Query("SELECT * FROM item WHERE id = :id")
    suspend fun item(id: String): Item?

    @Query("SELECT * FROM item WHERE catalog_key = :key")
    suspend fun itemByCatalogKey(key: String): Item?

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

    @Query("DELETE FROM acquisition WHERE item_id = :itemId AND before_app = 1")
    suspend fun deleteBeforeAppFinds(itemId: String): Int

    @Query("SELECT COUNT(*) FROM acquisition WHERE item_id = :itemId")
    suspend fun findCount(itemId: String): Int

    @Transaction
    @Query("SELECT * FROM acquisition WHERE item_id = :itemId ORDER BY COALESCE(acquired_at, 0) DESC, created_at DESC")
    fun observeFinds(itemId: String): Flow<List<FindWithPhotos>>

    // Photos
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPhoto(photo: Photo)

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

    @Query("UPDATE photo SET missing = :missing WHERE id = :id")
    suspend fun setPhotoMissing(id: String, missing: Boolean)
}
