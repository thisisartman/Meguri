package io.github.thisisartman.collectibles.data

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.thisisartman.collectibles.debug.FileLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

/** A photo file read and checked, nothing written yet (#188). */
class PhotoFileInfo internal constructor(
    internal val dir: File,
    internal val rows: List<PhotoBundle.Row>,
    val plan: PhotoBundle.Plan,
    /** Lines of the list that couldn't be used, and photos the list names that aren't in the file. */
    val problems: List<String>,
)

data class PhotoFileResult(val added: Int, val newItems: Int, val alreadyThere: Int, val failed: Int, val safetyCopy: String?)

sealed class PhotoFileError(message: String) : Exception(message) {
    class NotAPhotoFile : PhotoFileError("no ${PhotoBundle.MANIFEST} in the file")
    class Empty : PhotoFileError("nothing to add")
}

/**
 * Adds a photo file's photos to what is on the phone (#188); never replaces or removes anything. Each photo goes
 * through the same save as a notebook stamp or a gallery pick, so cards get their cover cut out and Ekitag
 * screenshots their stamp picture. A photo already on its item (same name and size) is skipped, so adding the same
 * file twice changes nothing.
 */
@Singleton
class PhotoImport @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: CollectiblesDb,
    private val repo: CollectionRepository,
    private val restore: BackupRestore,
    private val backup: CatalogAndBackup,
    private val log: FileLog,
) {
    private val dao = db.dao()

    suspend fun inspect(uri: Uri): PhotoFileInfo = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "photo-file").apply { deleteRecursively(); mkdirs() }
        try {
            unzip(uri, dir)
            val manifest = File(dir, PhotoBundle.MANIFEST).takeIf { it.isFile } ?: throw PhotoFileError.NotAPhotoFile()
            val parsed = PhotoBundle.parse(manifest.readText())
            val absent = parsed.rows.filter { !File(dir, PhotoBundle.PHOTOS + it.photo).isFile }
            val rows = parsed.rows - absent.toSet()
            if (rows.isEmpty()) throw PhotoFileError.Empty()
            val keys = rows.mapNotNull { (it.target as? PhotoBundle.Target.Catalogue)?.key }.distinct()
            val onPhone = keys.chunked(500).flatMap { dao.itemsByCatalogKeys(it) }.mapNotNull { it.catalogKey }.toSet()
            val own = dao.ownItems().map { it.type to it.nameJa }.toSet()
            val plan = PhotoBundle.plan(rows, onPhone, own)
            log.i("PhotoFile", "Read: ${rows.size} photos, plan $plan, ${parsed.bad.size} bad lines, ${absent.size} photos not in the file")
            PhotoFileInfo(dir, rows, plan, parsed.bad + absent.map { "${it.photo}: not in the file" })
        } catch (e: Exception) {
            dir.deleteRecursively()
            throw e
        }
    }

    fun discard(info: PhotoFileInfo) {
        info.dir.deleteRecursively()
    }

    suspend fun add(info: PhotoFileInfo): PhotoFileResult = withContext(Dispatchers.IO) {
        try {
            val safety = backup.lock.withLock { restore.saveSafetyCopy("meguri-pre-photos") }
            val keys = info.rows.mapNotNull { (it.target as? PhotoBundle.Target.Catalogue)?.key }.distinct()
            val byKey = keys.chunked(500).flatMap { dao.itemsByCatalogKeys(it) }.associate { it.catalogKey to it.id }
            val own = dao.ownItems().associate { (it.type to it.nameJa) to it.id }.toMutableMap()
            var added = 0; var newItems = 0; var already = 0; var failed = 0
            for (row in info.rows) {
                val itemId = when (val t = row.target) {
                    is PhotoBundle.Target.Catalogue -> byKey[t.key] ?: continue // region not on this phone: in the plan's "missing"
                    is PhotoBundle.Target.Own -> own.getOrPut(t.type to t.name) { newItems++; repo.addItem(t.type, t.name, null) }
                }
                val file = File(info.dir, PhotoBundle.PHOTOS + row.photo)
                if (dao.photosOfItem(itemId).any { it.displayName == row.photo && it.sizeBytes == file.length() }) { already++; continue }
                val ok = repo.addOldPhoto(
                    itemId, Uri.fromFile(file), row.date?.let { PhotoBundle.dayMs(it) }, row.kind,
                    name = row.photo, size = file.length(), tag = "PhotoFile",
                )
                if (ok) added++ else failed++
            }
            log.i("PhotoFile", "Added $added photos, $newItems new items, $already already there, $failed failed; safety copy $safety")
            PhotoFileResult(added, newItems, already, failed, safety)
        } finally {
            discard(info)
        }
    }

    /** Unpacks [uri] into [dir]; only plain names, so nothing can land outside it. */
    private fun unzip(uri: Uri, dir: File) {
        val root = dir.canonicalPath + File.separator
        val input = context.contentResolver.openInputStream(uri) ?: throw PhotoFileError.NotAPhotoFile()
        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                if (e.isDirectory) continue
                val out = File(dir, e.name)
                if (!out.canonicalPath.startsWith(root)) continue
                out.parentFile?.mkdirs()
                out.outputStream().use { zip.copyTo(it) }
            }
        }
    }
}
