package io.github.thisisartman.collectibles.data

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

/** The four built-in kinds, and CUSTOM for an item in one of your own collections (its `customTypeId`, schema v2). */
/** Kinds of collectible. Stored by name, so new kinds go at the end with no migration (dam cards, museum stamps: #55). */
enum class ItemType { MANHOLE_CARD, GOSHUIN, EKI_STAMP, EKITAG, CUSTOM, DAM_CARD, MUSEUM_STAMP, ROADSIDE_STATION, PLACE }

/**
 * One of your own collections (#64, schema v2): a name, a colour and an icon, and whether its collections record
 * what you paid. Items in it have type CUSTOM and point here with `customTypeId`.
 */
@Entity(tableName = "collection_type")
data class CollectionType(
    @PrimaryKey val id: String,
    val name: String,
    @ColumnInfo(name = "name_ja") val nameJa: String? = null,
    val color: Long,
    val icon: String,
    @ColumnInfo(name = "has_cost") val hasCost: Boolean = true,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

/** A photo's kind, kept in `photo.role` (#54): the stamp itself, proof of an Ekitag or a card, or anything else. */
/**
 * What a photo shows, stored as text in photo.role (no schema change to add one). STAMP is the collectible
 * itself (stamp image, card, goshuin page). PROOF is from 0.3 before per-type kinds and reads as the screenshot
 * or card kind; OTHER is anything else.
 */
object PhotoKind {
    const val STAMP = "stamp"
    const val EKITAG = "ekitag"
    const val MANHOLE = "manhole"
    const val PLACE = "place"
    /** A place of interest's goshuin (2026-11-15: Hakkaisan has a stamp and a goshuin). */
    const val GOSHUIN = "goshuin"
    const val PROOF = "proof"
    const val OTHER = "other"
}

/** Which photo kind becomes an item's display picture on its own when it has none (owner, 2026-11-08). */
object AutoCover {
    fun makesCover(type: ItemType, role: String): Boolean = when (type) {
        ItemType.MANHOLE_CARD -> role == PhotoKind.MANHOLE
        ItemType.EKI_STAMP, ItemType.EKITAG -> role == PhotoKind.EKITAG
        ItemType.GOSHUIN -> role == PhotoKind.STAMP
        else -> false
    }
}

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
    // Schema v2: the collection_type an item of type CUSTOM belongs to (#64)
    @ColumnInfo(name = "custom_type_id") val customTypeId: String? = null,
    // Schema v3: when you checked and there was nothing to get here (no stamp at the station); null = not checked
    @ColumnInfo(name = "none_here_at") val noneHereAt: Long? = null,
    // Schema v3: the photo you chose to stand for this item; null = the latest one
    @ColumnInfo(name = "cover_photo_id") val coverPhotoId: String? = null,
    // Schema v4: when you last edited its details (name, place, position...); a catalogue update leaves it alone (owner, 2026-11-08)
    @ColumnInfo(name = "edited_at") val editedAt: Long? = null,
)

/**
 * One find. Every find has a date (#194, owner 2026-11-26: "it's an app, not a journal"): `acquiredAt` and
 * `beforeApp` stay in the schema so older backups still read, and schema v5 gave their undated finds the day they
 * were added. Nothing writes a null `acquiredAt` or `beforeApp = true` any more.
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
    // Schema v2: how many you got this time (duplicates, e.g. a second card to trade); 1 for every older find
    @ColumnInfo(name = "quantity", defaultValue = "1") val quantity: Int = 1,
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
    @ColumnInfo(name = "photo_uri") val photoUri: String?,
) {
    val collected: Boolean get() = findCount > 0

    /** Checked and nothing there, and not collected since: left out of "still to get", the pins and the totals. */
    val noneHere: Boolean get() = !collected && item.noneHereAt != null
}

data class StationRow(
    @Embedded val row: ItemRow,
    val seq: Int,
)

data class FindWithPhotos(
    @Embedded val find: Acquisition,
    @Relation(parentColumn = "id", entityColumn = "acquisition_id") val photos: List<Photo>,
)
