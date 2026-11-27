package io.github.thisisartman.collectibles.photo

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.thisisartman.collectibles.data.CatalogAndBackup
import io.github.thisisartman.collectibles.data.CollectiblesDb
import io.github.thisisartman.collectibles.debug.FileLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Copies photos that still point at the gallery into the app (#79), on each start until none are left: the photos
 * from before 0.3.0-beta.6, and any a relink found in the gallery after a restore. A photo whose picture is gone
 * stays as it is (missing). Also removes app copies no photo points at any more.
 */
@Singleton
class PhotoCopier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: CollectiblesDb,
    private val backup: CatalogAndBackup,
    private val log: FileLog,
) {
    data class Result(val copied: Int, val unreadable: Int, val orphansRemoved: Int)

    suspend fun copyAll(): Result = withContext(Dispatchers.IO) {
        val dao = db.dao()
        // The backup lock, so a backup never zips a photo halfway through being moved
        backup.lock.withLock {
            var copied = 0
            var unreadable = 0
            for (p in dao.allPhotos()) {
                if (PhotoStore.isApp(p.contentUri) || p.missing) continue
                val uri = PhotoStore.importCopy(context, Uri.parse(p.contentUri), p.id)
                if (uri != null) { dao.setPhotoUri(p.id, uri); copied++ } else unreadable++
            }
            // A photo's kept original (#208) is wanted as much as its copy
            val wanted = PhotoNames.wanted(dao.allPhotos().flatMap { listOf(it.contentUri, it.originalUri) })
            var orphans = 0
            // A copy written in the last minutes may belong to a photo being added right now: leave it
            val settled = System.currentTimeMillis() - 10 * 60_000
            PhotoStore.dir(context).listFiles()?.forEach { f ->
                if (f.name !in wanted && f.lastModified() < settled) { f.delete(); orphans++ }
            }
            if (copied + unreadable + orphans > 0) log.i("Photo", "Copied $copied photos into the app; $unreadable unreadable; $orphans unused copies removed")
            Result(copied, unreadable, orphans)
        }
    }
}
