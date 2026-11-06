package io.github.thisisartman.collectibles.data

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.thisisartman.collectibles.debug.FileLog
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID
import java.util.zip.ZipInputStream

/** "Back up with photos" (#66): the pictures travel inside the backup and come back on a phone that lacks them. */
@RunWith(AndroidJUnit4::class)
class PhotoBackupTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val resolver = context.contentResolver
    private val name = "photo-test-${UUID.randomUUID()}.db"
    private lateinit var db: CollectiblesDb
    private lateinit var io: CatalogAndBackup
    private lateinit var restore: BackupRestore
    private lateinit var work: File
    private val bytes = ByteArray(4096) { (it * 31 % 251).toByte() }
    private val marker = "meguri-test-${UUID.randomUUID()}"

    @Before fun setUp() {
        context.getSharedPreferences("backup", Context.MODE_PRIVATE).edit().clear().commit()
        work = File(context.cacheDir, "pt-${UUID.randomUUID()}").apply { mkdirs() }
        db = Room.databaseBuilder(context, CollectiblesDb::class.java, name).addMigrations(*CollectiblesDb.MIGRATIONS).build()
        val log = FileLog(work)
        io = CatalogAndBackup(context, db, log)
        restore = BackupRestore(context, db, io, log)
    }

    @After fun tearDown() {
        db.close()
        context.deleteDatabase(name)
        work.deleteRecursively()
        val images = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        resolver.delete(images, "${MediaStore.Images.Media.DISPLAY_NAME} LIKE ?", arrayOf("$marker%"))
        resolver.delete(MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), "${MediaStore.Downloads.DISPLAY_NAME} LIKE 'collectibles-pre-restore-%'", null)
    }

    /** A picture in the gallery, as the camera would leave it. */
    private fun galleryImage(): Uri {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$marker.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/MeguriTest")
        }
        val uri = resolver.insert(MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values)!!
        resolver.openOutputStream(uri)!!.use { it.write(bytes) }
        return uri
    }

    private suspend fun seed(image: Uri) {
        val d = db.dao()
        d.insertItem(Item(id = "i1", type = ItemType.GOSHUIN, nameJa = "豪徳寺", origin = Origin.USER, createdAt = 1, updatedAt = 1))
        d.insertFind(Acquisition(id = "f1", itemId = "i1", acquiredAt = 1000, via = Via.MANUAL, createdAt = 1000))
        d.insertPhoto(Photo(id = "p1", acquisitionId = "f1", contentUri = image.toString(), mediastoreId = ContentUris.parseId(image), displayName = "$marker.jpg", createdAt = 1))
    }

    private fun zipNames(f: File): List<String> = ZipInputStream(f.inputStream()).use { z ->
        generateSequence { z.nextEntry }.map { it.name }.toList()
    }

    @Test fun photosTravelInsideAndComeBackWhenThePhoneLacksThem() = runBlocking {
        val image = galleryImage()
        seed(image)
        val backup = File(work, "with-photos.zip")
        val result = io.exportBackup(Uri.fromFile(backup), withPhotos = true)
        assertEquals(1, result.photoFiles)
        assertTrue("photos/p1" in zipNames(backup))

        // The new phone: the picture is gone from the gallery
        resolver.delete(image, null, null)
        val info = restore.inspect(Uri.fromFile(backup))
        restore.restore(info)

        val p = db.dao().allPhotos().single()
        assertNotEquals("points at a new gallery entry", image.toString(), p.contentUri)
        val back = resolver.openInputStream(Uri.parse(p.contentUri))!!.use { it.readBytes() }
        assertArrayEquals(bytes, back)
    }

    @Test fun samePhoneRestoreAddsNoDuplicate() = runBlocking {
        val image = galleryImage()
        seed(image)
        val backup = File(work, "with-photos.zip")
        io.exportBackup(Uri.fromFile(backup), withPhotos = true)
        val images = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        fun count() = resolver.query(images, arrayOf(MediaStore.Images.Media._ID), "${MediaStore.Images.Media.DISPLAY_NAME} LIKE ?", arrayOf("$marker%"), null)!!.use { it.count }
        val before = count()
        restore.restore(restore.inspect(Uri.fromFile(backup)))
        assertEquals(before, count())
        assertEquals(image.toString(), db.dao().allPhotos().single().contentUri)
    }

    @Test fun plainBackupCarriesNoPictures() = runBlocking {
        seed(galleryImage())
        val backup = File(work, "plain.zip")
        assertEquals(0, io.exportBackup(Uri.fromFile(backup)).photoFiles)
        assertTrue(zipNames(backup).none { it.startsWith("photos/") })
    }
}
