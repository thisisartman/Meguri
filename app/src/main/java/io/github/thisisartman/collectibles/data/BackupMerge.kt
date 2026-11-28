package io.github.thisisartman.collectibles.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlin.math.abs

/** Which of the two to keep for an item where this phone and the backup disagree (#209). */
enum class MergePick { PHONE, BACKUP }

/** What an item's two sides disagree on (owner, 2026-11-28). */
enum class MergeField { COVER_PHOTO, COVER_POSITION, NOTES, HOURS, NONE_HERE }

/**
 * One item that differs between this phone and the backup. [phoneCover] and [backupCover] are the display pictures
 * when they differ; [backupFirstFind] is the backup's first find when this phone says "No stamp here".
 */
data class MergeConflict(
    val phone: Item,
    val backup: Item,
    val fields: List<MergeField>,
    val phoneCover: Photo? = null,
    val backupCover: Photo? = null,
    val backupFirstFind: Acquisition? = null,
)

/** The rows to write, once each conflict has its pick. Inserts never touch a row that is here; updates only fill. */
data class MergeOps(
    val newCategories: List<CollectionType>,
    val newItems: List<Item>,
    val itemUpdates: List<Item>,
    val newLines: List<Line>,
    val newLineStations: List<LineStation>,
    val newFinds: List<Acquisition>,
    val findUpdates: List<Acquisition>,
    val newPhotos: List<Photo>,
) {
    val isEmpty get() = newCategories.isEmpty() && newItems.isEmpty() && itemUpdates.isEmpty() && newLines.isEmpty() &&
        newLineStations.isEmpty() && newFinds.isEmpty() && findUpdates.isEmpty() && newPhotos.isEmpty()
}

/**
 * "Add from a backup" (#209): what a backup adds to this phone, worked out without Android so tests can check it.
 *
 * Rules (backup-format analysis 2026-11-27, owner 2026-11-28):
 *  - Your own categories match by id, then by name (any case); a missing one is added.
 *  - Catalogue items match by catalogue key; your own items by type and name (and category, for your own types).
 *    An item that isn't here is added.
 *  - A find with the same id, or on the same day for the same item, is the same find: the larger quantity is kept and
 *    an empty cost or note is filled. Any other find is added.
 *  - A photo with the same id, or the same name and size on the same item, is already here. Any other is added.
 *  - Display picture, cover position, "No stamp here", notes and hours are filled where this phone has none. Where
 *    both have one and they differ, the item is a [MergeConflict] and the person picks which to keep. Notes and hours
 *    count only when they are yours (your own item, or an edited catalogue item), not two versions of the catalogue.
 *  - "No stamp here" on this phone and a find in the backup is a conflict too: keeping this phone leaves the find out.
 *  - Nothing on this phone is removed, and a row's own values are only replaced by a pick.
 */
object BackupMerge {
    /** About a metre: two readings of the same printed position are the same. */
    private const val SAME_PLACE = 1e-5

    class Plan internal constructor(
        val conflicts: List<MergeConflict>,
        internal val base: MergeOps,
        /** Per conflict (by phone item id): the item as the backup has it, with ids mapped to this phone's. */
        internal val backupSide: Map<String, Item>,
        /** Finds, and their photos, that wait on a "No stamp here" pick, by phone item id. */
        internal val heldFinds: Map<String, List<Acquisition>>,
        internal val heldPhotos: Map<String, List<Photo>>,
        private val phoneItems: Map<String, Item>,
        private val phonePhotoIds: Set<String>,
        /** Items in the backup that are already on this phone. */
        val matchedItems: Int,
        /** Photos in the backup that are already on this phone (same id, or same name and size on the item). */
        val photosAlreadyHere: Int,
    ) {
        /** What would be added with this phone kept everywhere; the dialog's counts. */
        val newItems get() = base.newItems.size
        val newFinds get() = base.newFinds.size + heldFinds.values.sumOf { it.size }
        val newPhotos get() = base.newPhotos.size + heldPhotos.values.sumOf { it.size }
        val nothingNew get() = base.isEmpty && conflicts.isEmpty()

        /** The rows to write with [picks] (by phone item id; an item left out keeps this phone). */
        fun resolve(picks: Map<String, MergePick>, now: Long): MergeOps {
            val updates = base.itemUpdates.associateBy { it.id }.toMutableMap()
            val finds = base.newFinds.toMutableList()
            val photos = base.newPhotos.toMutableList()
            for (c in conflicts) {
                if (picks[c.phone.id] != MergePick.BACKUP) continue
                val from = backupSide.getValue(c.phone.id)
                val cur = updates[c.phone.id] ?: c.phone
                var next = cur
                for (f in c.fields) next = when (f) {
                    MergeField.COVER_PHOTO -> next.copy(coverPhotoId = from.coverPhotoId)
                    MergeField.COVER_POSITION -> next.copy(coverLat = from.coverLat, coverLng = from.coverLng)
                    MergeField.NOTES -> next.copy(notes = from.notes, editedAt = latest(next.editedAt, from.editedAt))
                    MergeField.HOURS -> next.copy(hoursText = from.hoursText, closedDays = from.closedDays, editedAt = latest(next.editedAt, from.editedAt))
                    MergeField.NONE_HERE -> {
                        finds += heldFinds[c.phone.id].orEmpty()
                        photos += heldPhotos[c.phone.id].orEmpty()
                        next.copy(noneHereAt = null)
                    }
                }
                if (next != cur) updates[c.phone.id] = next.copy(updatedAt = maxOf(next.updatedAt, now))
            }
            // A display picture must be a photo that is on the phone after this: one whose find was left out falls back
            val photoIds = phonePhotoIds + photos.map { it.id }
            val checked = updates.values.map { u ->
                val was = phoneItems[u.id]?.coverPhotoId
                if (u.coverPhotoId != null && u.coverPhotoId != was && u.coverPhotoId !in photoIds) u.copy(coverPhotoId = was) else u
            }.filter { it != phoneItems[it.id] }
            return base.copy(itemUpdates = checked, newFinds = finds, newPhotos = photos)
        }
    }

    private fun latest(a: Long?, b: Long?) = listOfNotNull(a, b).maxOrNull()

    private fun text(s: String?) = s?.trim()?.takeIf { it.isNotEmpty() }

    fun plan(phone: AllRows, backup: AllRows, zone: ZoneId = ZoneId.systemDefault(), newId: () -> String = { UUID.randomUUID().toString() }): Plan {
        fun day(a: Acquisition): LocalDate = Instant.ofEpochMilli(a.acquiredAt ?: a.createdAt).atZone(zone).toLocalDate()

        // Categories
        val phoneCats = phone.collectionTypes
        val catIds = phoneCats.map { it.id }.toMutableSet()
        val catMap = HashMap<String, String>()
        val newCats = mutableListOf<CollectionType>()
        for (c in backup.collectionTypes) {
            val match = phoneCats.firstOrNull { it.id == c.id } ?: phoneCats.firstOrNull { it.name.trim().equals(c.name.trim(), ignoreCase = true) }
                ?: newCats.firstOrNull { it.name.trim().equals(c.name.trim(), ignoreCase = true) }
            if (match != null) { catMap[c.id] = match.id; continue }
            val id = if (c.id in catIds) newId() else c.id
            catIds += id
            catMap[c.id] = id
            newCats += c.copy(id = id)
        }

        // Items
        val phoneById = phone.items.associateBy { it.id }
        val byKey = phone.items.filter { it.catalogKey != null }.associateBy { it.catalogKey!! }
        fun ownKey(type: ItemType, name: String, cat: String?) = Triple(type, name.trim(), if (type == ItemType.CUSTOM) cat else null)
        val byOwn = phone.items.filter { it.catalogKey == null }.associateBy { ownKey(it.type, it.nameJa, it.customTypeId) }
        val itemIds = phone.items.map { it.id }.toMutableSet()
        val itemMap = HashMap<String, String>()
        val newItems = mutableListOf<Item>()
        val matched = mutableListOf<Pair<Item, Item>>() // phone, backup
        for (b in backup.items) {
            val cat = b.customTypeId?.let { catMap[it] }
            val p = if (b.catalogKey != null) byKey[b.catalogKey] else byOwn[ownKey(b.type, b.nameJa, cat)]
            if (p != null) { itemMap[b.id] = p.id; matched += p to b; continue }
            val id = if (b.id in itemIds) newId() else b.id
            itemIds += id
            itemMap[b.id] = id
            newItems += b.copy(id = id, customTypeId = cat)
        }
        // Parents point at the item as it is called here, or nowhere
        val newItemsFixed = newItems.map { it.copy(parentItemId = it.parentItemId?.let { p -> itemMap[p] ?: p.takeIf { p in phoneById } }) }

        // Finds
        val phoneFindsByItem = phone.finds.groupBy { it.itemId }
        val phoneFindIds = phone.finds.associateBy { it.id }
        val findMap = HashMap<String, String>()
        val findUpdates = LinkedHashMap<String, Acquisition>()
        val newFinds = mutableListOf<Acquisition>()
        val newFindsByItem = HashMap<String, MutableList<Acquisition>>()
        // An item that is "No stamp here" on this phone with no find of its own: the backup's finds wait on a pick
        val noneHereItems = phone.items.filter { it.noneHereAt != null && phoneFindsByItem[it.id].isNullOrEmpty() }.map { it.id }.toSet()
        val heldFinds = HashMap<String, MutableList<Acquisition>>()
        for (f in backup.finds) {
            val itemId = itemMap[f.itemId] ?: continue
            val same = phoneFindIds[f.id]?.takeIf { it.itemId == itemId }
                ?: phoneFindsByItem[itemId]?.firstOrNull { day(it) == day(f) }
                ?: newFindsByItem[itemId]?.firstOrNull { day(it) == day(f) }
            if (same != null) {
                findMap[f.id] = same.id
                val cur = findUpdates[same.id] ?: newFinds.firstOrNull { it.id == same.id } ?: same
                val filled = cur.copy(
                    quantity = maxOf(cur.quantity, f.quantity),
                    costYen = cur.costYen ?: f.costYen,
                    notes = text(cur.notes) ?: f.notes,
                    lat = if (cur.lat == null) f.lat else cur.lat,
                    lng = if (cur.lat == null) f.lng else cur.lng,
                    accuracyM = if (cur.lat == null) f.accuracyM else cur.accuracyM,
                )
                if (same.id in phoneFindIds) { if (filled != phoneFindIds[same.id]) findUpdates[same.id] = filled }
                else { val i = newFinds.indexOfFirst { it.id == same.id }; newFinds[i] = filled; newFindsByItem[itemId]!!.replaceAll { if (it.id == same.id) filled else it } }
                continue
            }
            val id = if (f.id in phoneFindIds) newId() else f.id
            val row = f.copy(id = id, itemId = itemId, beforeApp = false)
            findMap[f.id] = id
            newFinds += row
            newFindsByItem.getOrPut(itemId) { mutableListOf() } += row
        }
        // Finds held back: on an item this phone marked "No stamp here"
        val held = newFinds.filter { it.itemId in noneHereItems }
        held.forEach { heldFinds.getOrPut(it.itemId) { mutableListOf() } += it }
        newFinds.removeAll(held.toSet())
        val heldFindIds = held.map { it.id }.toSet()

        // Photos
        val phonePhotoIds = phone.photos.map { it.id }.toSet()
        val findItem = (phone.finds + newFinds + held).associate { it.id to it.itemId }
        val phonePhotosByItem = phone.photos.groupBy { findItem[it.acquisitionId] }
        val photoMap = HashMap<String, String>()
        val newPhotos = mutableListOf<Photo>()
        val heldPhotos = HashMap<String, MutableList<Photo>>()
        val addedByItem = HashMap<String, MutableList<Photo>>()
        var already = 0
        for (p in backup.photos) {
            val findId = findMap[p.acquisitionId] ?: continue
            val itemId = findItem[findId] ?: continue
            if (p.id in phonePhotoIds) { photoMap[p.id] = p.id; already++; continue }
            val sameFile = (phonePhotosByItem[itemId].orEmpty() + addedByItem[itemId].orEmpty()).firstOrNull {
                p.displayName != null && p.sizeBytes != null && it.displayName == p.displayName && it.sizeBytes == p.sizeBytes
            }
            if (sameFile != null) { photoMap[p.id] = sameFile.id; already++; continue }
            val row = p.copy(acquisitionId = findId, missing = false)
            photoMap[p.id] = p.id
            addedByItem.getOrPut(itemId) { mutableListOf() } += row
            if (findId in heldFindIds) heldPhotos.getOrPut(itemId) { mutableListOf() } += row else newPhotos += row
        }

        // A new item's display picture is one of its photos here, or none (the column has no key to catch it)
        val allPhotoIds = phonePhotoIds + newPhotos.map { it.id } + heldPhotos.values.flatten().map { it.id }
        val newItemsFinal = newItemsFixed.map { i -> i.copy(coverPhotoId = i.coverPhotoId?.let { photoMap[it] }?.takeIf { it in allPhotoIds }) }

        // Matched items: fill what is empty here, and list what differs
        val backupPhotos = backup.photos.associateBy { it.id }
        val phonePhotos = phone.photos.associateBy { it.id }
        val backupFindsByItem = backup.finds.groupBy { it.itemId }
        val updates = mutableListOf<Item>()
        val conflicts = mutableListOf<MergeConflict>()
        val backupSide = HashMap<String, Item>()
        for ((p, b) in matched) {
            val yours = p.origin == Origin.USER || p.editedAt != null || b.editedAt != null
            val backupYours = b.origin == Origin.USER || b.editedAt != null
            val bCover = b.coverPhotoId?.takeIf { it in backupPhotos }?.let { photoMap[it] }
            val pCover = p.coverPhotoId?.takeIf { it in phonePhotos }
            val fields = mutableListOf<MergeField>()
            var fill = p
            // Display picture
            if (bCover != null) {
                if (pCover == null) fill = fill.copy(coverPhotoId = bCover)
                else if (pCover != bCover) fields += MergeField.COVER_PHOTO
            }
            // Cover position
            if (b.coverLat != null && b.coverLng != null) {
                if (p.coverLat == null || p.coverLng == null) fill = fill.copy(coverLat = b.coverLat, coverLng = b.coverLng)
                else if (abs(p.coverLat - b.coverLat) > SAME_PLACE || abs(p.coverLng - b.coverLng) > SAME_PLACE) fields += MergeField.COVER_POSITION
            }
            // Notes and hours: only your own words
            if (text(b.notes) != null && text(b.notes) != text(p.notes)) {
                if (text(p.notes) == null) { if (backupYours) fill = fill.copy(notes = b.notes, editedAt = latest(fill.editedAt, b.editedAt)) }
                else if (yours) fields += MergeField.NOTES
            }
            val pHours = text(p.hoursText) to text(p.closedDays)
            val bHours = text(b.hoursText) to text(b.closedDays)
            if ((bHours.first != null || bHours.second != null) && bHours != pHours) {
                if (pHours.first == null && pHours.second == null) {
                    if (backupYours) fill = fill.copy(hoursText = b.hoursText, closedDays = b.closedDays, editedAt = latest(fill.editedAt, b.editedAt))
                } else if (yours) fields += MergeField.HOURS
            }
            // No stamp here
            if (p.id in heldFinds) fields += MergeField.NONE_HERE
            else if (p.noneHereAt == null && b.noneHereAt != null && phoneFindsByItem[p.id].isNullOrEmpty() && backupFindsByItem[b.id].isNullOrEmpty()) {
                fill = fill.copy(noneHereAt = b.noneHereAt)
            }
            if (fill != p) updates += fill.copy(updatedAt = maxOf(p.updatedAt, b.updatedAt))
            if (fields.isNotEmpty()) {
                conflicts += MergeConflict(
                    phone = p, backup = b, fields = fields,
                    phoneCover = if (MergeField.COVER_PHOTO in fields) phonePhotos[pCover] else null,
                    backupCover = if (MergeField.COVER_PHOTO in fields) backupPhotos[b.coverPhotoId] else null,
                    backupFirstFind = if (MergeField.NONE_HERE in fields) backupFindsByItem[b.id]?.minByOrNull { it.acquiredAt ?: it.createdAt } else null,
                )
                backupSide[p.id] = b.copy(id = p.id, coverPhotoId = bCover)
            }
        }

        // Lines and their stations
        val lineIds = phone.lines.map { it.id }.toSet()
        val newLines = backup.lines.filter { it.id !in lineIds }
        val pairs = phone.lineStations.map { it.lineId to it.itemId }.toMutableSet()
        val newStations = backup.lineStations.mapNotNull { s ->
            val itemId = itemMap[s.itemId] ?: return@mapNotNull null
            if (!pairs.add(s.lineId to itemId)) null else s.copy(itemId = itemId)
        }

        return Plan(
            conflicts = conflicts,
            base = MergeOps(newCats, newItemsFinal, updates, newLines, newStations, newFinds, findUpdates.values.toList(), newPhotos),
            backupSide = backupSide,
            heldFinds = heldFinds,
            heldPhotos = heldPhotos,
            phoneItems = phoneById,
            phonePhotoIds = phonePhotoIds,
            matchedItems = matched.size,
            photosAlreadyHere = already,
        )
    }
}
