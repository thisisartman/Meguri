package io.github.thisisartman.collectibles.data

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

enum class ItemType { MANHOLE_CARD, GOSHUIN, EKI_STAMP, EKITAG }

enum class Origin { SEED, CSV_IMPORT, USER }

enum class Via { MANUAL, NOTIFICATION, SUGGESTION, BULK }

/**
 * Something that can be collected. `lat`/`lng` are where you get it (the distribution place,
 * the shrine, the station), which is what distances are measured to.
 * `catalogKey` (for example `MANHOLE_CARD:15-202-A001`) makes catalogue re-imports update in place.
 */
@Entity(
    tableName = "item",
    indices = [
        Index("type"),
        Index("prefecture"),
        Index("lat", "lng"),
        Index(value = ["catalog_key"], unique = true),
    ],
)
data class Item(
    @PrimaryKey val id: String,
    val type: ItemType,
    @ColumnInfo(name = "catalog_key") val catalogKey: String? = null,
    val code: String? = null,
    @ColumnInfo(name = "name_ja") val nameJa: String,
    @ColumnInfo(name = "name_romaji") val nameRomaji: String? = null,
    val series: String? = null,
    @ColumnInfo(name = "release_date") val releaseDate: String? = null,
    val prefecture: String? = null,
    val municipality: String? = null,
    @ColumnInfo(name = "place_name") val placeName: String? = null,
    val address: String? = null,
    @ColumnInfo(name = "place_phone") val placePhone: String? = null,
    val lat: Double? = null,
    val lng: Double? = null,
    @ColumnInfo(name = "hours_text") val hoursText: String? = null,
    @ColumnInfo(name = "closed_days") val closedDays: String? = null,
    val notes: String? = null,
    @ColumnInfo(name = "source_url") val sourceUrl: String? = null,
    val origin: Origin,
    @ColumnInfo(name = "parent_item_id") val parentItemId: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

/**
 * One find. `acquiredAt` is null only for items marked "had before the app" (`beforeApp`).
 * RESTRICT: an item that has finds cannot be deleted, so a find is never lost with its item.
 */
@Entity(
    tableName = "acquisition",
    foreignKeys = [
        ForeignKey(entity = Item::class, parentColumns = ["id"], childColumns = ["item_id"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index("item_id")],
)
data class Acquisition(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "item_id") val itemId: String,
    @ColumnInfo(name = "acquired_at") val acquiredAt: Long?,
    @ColumnInfo(name = "before_app") val beforeApp: Boolean = false,
    val lat: Double? = null,
    val lng: Double? = null,
    @ColumnInfo(name = "accuracy_m") val accuracyM: Float? = null,
    @ColumnInfo(name = "cost_yen") val costYen: Int? = null,
    val notes: String? = null,
    val via: Via,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

/**
 * A photo in the gallery, linked to a find. The fingerprint (MediaStore ID, display name, path,
 * size, date taken) lets a restore on a new phone find the same picture again (PRD FR-9a).
 */
@Entity(
    tableName = "photo",
    foreignKeys = [
        ForeignKey(entity = Acquisition::class, parentColumns = ["id"], childColumns = ["acquisition_id"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("acquisition_id")],
)
data class Photo(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "acquisition_id") val acquisitionId: String,
    @ColumnInfo(name = "content_uri") val contentUri: String,
    val role: String = "other",
    @ColumnInfo(name = "taken_at") val takenAt: Long? = null,
    @ColumnInfo(name = "exif_lat") val exifLat: Double? = null,
    @ColumnInfo(name = "exif_lng") val exifLng: Double? = null,
    val missing: Boolean = false,
    @ColumnInfo(name = "mediastore_id") val mediastoreId: Long? = null,
    @ColumnInfo(name = "display_name") val displayName: String? = null,
    @ColumnInfo(name = "relative_path") val relativePath: String? = null,
    @ColumnInfo(name = "size_bytes") val sizeBytes: Long? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

/** A railway line, for the eki-stamp book. */
@Entity(tableName = "line")
data class Line(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "name_ja") val nameJa: String,
    @ColumnInfo(name = "name_romaji") val nameRomaji: String? = null,
    val operator: String? = null,
)

/** A station's place on a line; a station on two lines has two rows. */
@Entity(
    tableName = "line_station",
    primaryKeys = ["line_id", "item_id"],
    foreignKeys = [
        ForeignKey(entity = Line::class, parentColumns = ["id"], childColumns = ["line_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Item::class, parentColumns = ["id"], childColumns = ["item_id"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("item_id")],
)
data class LineStation(
    @ColumnInfo(name = "line_id") val lineId: String,
    @ColumnInfo(name = "item_id") val itemId: String,
    val seq: Int,
)

/** An item with what the lists need about its finds. */
data class ItemRow(
    @Embedded val item: Item,
    @ColumnInfo(name = "find_count") val findCount: Int,
    @ColumnInfo(name = "last_at") val lastAt: Long?,
    @ColumnInfo(name = "last_before_app") val lastBeforeApp: Boolean?,
    @ColumnInfo(name = "photo_uri") val photoUri: String?,
) {
    val collected: Boolean get() = findCount > 0
}

data class StationRow(
    @Embedded val row: ItemRow,
    val seq: Int,
)

data class FindWithPhotos(
    @Embedded val find: Acquisition,
    @Relation(parentColumn = "id", entityColumn = "acquisition_id") val photos: List<Photo>,
)
