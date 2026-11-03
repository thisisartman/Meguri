package io.github.thisisartman.collectibles.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.provider.MediaStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.thisisartman.collectibles.debug.FileLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

@RunWith(AndroidJUnit4::class)
class BackupRestoreTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "test-${UUID.randomUUID()}.db"
    private lateinit var db: CollectiblesDb
    private lateinit var io: CatalogAndBackup
    private lateinit var restore: BackupRestore
    private lateinit var work: File
    private val safety = mutableListOf<String>()

    @Before fun setUp() {
        // Shared with other tests: a restore leaves "photos to find" set until the gallery can be read.
        context.getSharedPreferences("backup", Context.MODE_PRIVATE).edit().clear().commit()
        work = File(context.cacheDir, "t-${UUID.randomUUID()}").apply { mkdirs() }
        db = Room.databaseBuilder(context, CollectiblesDb::class.java, name).build()
        val log = FileLog(work)
        io = CatalogAndBackup(context, db, log)
        restore = BackupRestore(context, db, io, log)
    }

    @After fun tearDown() {
        db.close()
        context.deleteDatabase(name)
        work.deleteRecursively()
        safety.forEach { n ->
            context.contentResolver.delete(
                MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
                "${MediaStore.Downloads.DISPLAY_NAME} = ?", arrayOf(n),
            )
        }
    }

    private fun item(id: String, code: String? = id) =
        Item(id = id, type = ItemType.MANHOLE_CARD, catalogKey = "MANHOLE_CARD:$code", code = code, nameJa = "長岡市 $id", nameRomaji = "Nagaoka $id", origin = Origin.CSV_IMPORT, createdAt = 1, updatedAt = 2)

    private suspend fun seed(prefix: String = "a") {
        val d = db.dao()
        d.insertItem(item("${prefix}1"))
        d.insertItem(item("${prefix}2"))
        d.upsertLine(Line("L$prefix", "上越線", "Joetsu"))
        d.upsertLineStation(LineStation("L$prefix", "${prefix}2", 3))
        d.insertFind(Acquisition(id = "${prefix}f1", itemId = "${prefix}1", acquiredAt = 1000, lat = 37.1, lng = 138.2, accuracyM = 5f, notes = "メモ", via = Via.MANUAL, createdAt = 1000))
        d.insertFind(Acquisition(id = "${prefix}f2", itemId = "${prefix}2", acquiredAt = null, beforeApp = true, via = Via.BULK, createdAt = 1001))
        d.insertPhoto(Photo(id = "${prefix}p1", acquisitionId = "${prefix}f1", contentUri = "content://x/1", mediastoreId = 1, displayName = "CT_1.jpg", relativePath = "DCIM/x/", sizeBytes = 9, takenAt = 7, createdAt = 1))
    }

    private fun zipFile(fileName: String = "b-${UUID.randomUUID()}.zip") = File(work, fileName)

    private suspend fun export(): File {
        val f = zipFile()
        io.exportBackup(Uri.fromFile(f))
        return f
    }

    private fun assertBackupRefused(file: File, type: Class<out BackupError>) {
        assertThrows(type) { runBlocking { restore.inspect(Uri.fromFile(file)) } }
    }

    /** Rebuilds a backup zip with its database changed by [edit]. */
    private fun withEditedDb(source: File, edit: (File) -> Unit): File {
        val dbCopy = File(work, "edit-${UUID.randomUUID()}.db")
        ZipInputStream(source.inputStream()).use { z ->
            var e = z.nextEntry
            while (e != null) {
                if (e.name == CollectiblesDb.NAME) dbCopy.outputStream().use { z.copyTo(it) }
                e = z.nextEntry
            }
        }
        edit(dbCopy)
        val out = zipFile()
        ZipOutputStream(out.outputStream()).use { z ->
            z.putNextEntry(ZipEntry(CollectiblesDb.NAME)); dbCopy.inputStream().use { it.copyTo(z) }; z.closeEntry()
        }
        return out
    }

    /** Runs SQL on a copied backup database. */
    private fun sql(file: File, vararg statements: String) {
        java.io.RandomAccessFile(file, "rw").use { it.seek(18); it.write(byteArrayOf(1, 1)) }
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READWRITE).use { d ->
            statements.forEach { d.execSQL(it) }
        }
    }

    @Test fun exportThenInspectCountsEverything() = runBlocking<Unit> {
        seed()
        val info = restore.inspect(Uri.fromFile(export()))
        assertEquals(2, info.items)
        assertEquals(2, info.finds)
        assertEquals(1, info.photos)
        assertEquals(1, info.lines)
        assertEquals(CollectiblesDb.VERSION, info.schema)
        assertNotNull(info.createdMs)
        restore.discard(info)
    }

    @Test fun exportIncludesWritesStillInTheWal() = runBlocking<Unit> {
        seed()
        repeat(50) { db.dao().insertItem(item("w$it")) }
        val info = restore.inspect(Uri.fromFile(export()))
        assertEquals(52, info.items)
        restore.discard(info)
    }

    @Test fun exportWhileWritingIsAlwaysConsistent() = runBlocking<Unit> {
        seed()
        val writer = async(Dispatchers.IO) {
            var n = 0
            while (isActive && n < 400) { db.dao().insertItem(item("c${n++}")); delay(1) }
            n
        }
        repeat(6) {
            val f = export()
            val info = restore.inspect(Uri.fromFile(f))
            val csv = ZipInputStream(f.inputStream()).use { z ->
                var e = z.nextEntry
                while (e != null && e.name != "items.csv") e = z.nextEntry
                z.readBytes().toString(Charsets.UTF_8).trim().lines().size - 1
            }
            assertEquals("database and CSV must match", info.items, csv)
            restore.discard(info)
        }
        writer.await()
        Unit
    }

    @Test fun restoreBringsBackEveryFieldAndRemovesNewerRows() = runBlocking<Unit> {
        seed()
        val before = AllRowsSnapshot.of(db)
        val file = export()
        db.dao().insertItem(item("later"))
        db.dao().insertFind(Acquisition(id = "laterf", itemId = "later", acquiredAt = 5, via = Via.MANUAL, createdAt = 5))

        val result = restore.restore(restore.inspect(Uri.fromFile(file)))
        result.safetyCopy?.let { safety += it }

        assertEquals(before, AllRowsSnapshot.of(db))
        assertNull(db.dao().item("later"))
        assertEquals(2, result.finds)
    }

    @Test fun safetyCopyIsWrittenBeforeReplacingAndHoldsTheOldData() = runBlocking<Unit> {
        seed()
        val file = export()
        db.dao().insertItem(item("only-in-safety-copy"))
        val result = restore.restore(restore.inspect(Uri.fromFile(file)))
        val copy = assertNotNull(result.safetyCopy).let { result.safetyCopy!! }
        safety += copy
        val uri = context.contentResolver.query(
            MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), arrayOf(MediaStore.Downloads._ID),
            "${MediaStore.Downloads.DISPLAY_NAME} = ?", arrayOf(copy), null,
        )!!.use { c ->
            assertTrue(c.moveToFirst())
            android.content.ContentUris.withAppendedId(MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), c.getLong(0))
        }
        val info = restore.inspect(uri)
        assertEquals(3, info.items)
        restore.discard(info)
    }

    @Test fun restoreIntoEmptyAppMakesNoSafetyCopy() = runBlocking<Unit> {
        seed()
        val file = export()
        db.clearAllTables()
        val result = restore.restore(restore.inspect(Uri.fromFile(file)))
        assertNull(result.safetyCopy)
        assertEquals(2, db.dao().findTotal())
    }

    @Test fun safetyCopyProtectsUserItemsWithoutFinds() = runBlocking<Unit> {
        db.dao().insertItem(item("x"))
        val file = export()
        db.dao().insertItem(item("user-only"))
        val result = restore.restore(restore.inspect(Uri.fromFile(file)))
        assertNotNull(result.safetyCopy)
        safety += result.safetyCopy!!
    }

    @Test fun failedCopyRollsBackLeavingEverything() = runBlocking<Unit> {
        seed()
        val before = AllRowsSnapshot.of(db)
        val broken = AllRows(
            items = listOf(item("z")),
            finds = listOf(Acquisition(id = "zf", itemId = "does-not-exist", acquiredAt = 1, via = Via.MANUAL, createdAt = 1)),
            photos = emptyList(), lines = emptyList(), lineStations = emptyList(),
        )
        assertThrows(Exception::class.java) { runBlocking { restore.replaceAll(broken) } }
        assertEquals(before, AllRowsSnapshot.of(db))
    }

    @Test fun refusesFilesThatAreNotBackups() = runBlocking<Unit> {
        seed()
        val before = AllRowsSnapshot.of(db)
        val garbage = zipFile().apply { writeBytes(ByteArray(4096) { it.toByte() }) }
        assertBackupRefused(garbage, BackupError.NotABackup::class.java)
        val noDb = zipFile().also { f ->
            ZipOutputStream(f.outputStream()).use { z -> z.putNextEntry(ZipEntry("items.csv")); z.write(1); z.closeEntry() }
        }
        assertBackupRefused(noDb, BackupError.NotABackup::class.java)
        val notSqlite = zipFile().also { f ->
            ZipOutputStream(f.outputStream()).use { z -> z.putNextEntry(ZipEntry(CollectiblesDb.NAME)); z.write(ByteArray(4096)); z.closeEntry() }
        }
        assertBackupRefused(notSqlite, BackupError.Damaged::class.java)
        assertEquals(before, AllRowsSnapshot.of(db))
    }

    @Test fun refusesADatabaseDamagedInTheMiddle() = runBlocking<Unit> {
        seed()
        repeat(200) { db.dao().insertItem(item("d$it")) }
        val damaged = withEditedDb(export()) { f ->
            java.io.RandomAccessFile(f, "rw").use { r ->
                r.seek(f.length() / 2)
                r.write(ByteArray(8192) { 0x5A })
            }
        }
        assertThrows(BackupError::class.java) { runBlocking { restore.inspect(Uri.fromFile(damaged)) } }
    }

    @Test fun refusesABackupFromANewerSchema() = runBlocking<Unit> {
        seed()
        val newer = withEditedDb(export()) { sql(it, "PRAGMA user_version = ${CollectiblesDb.VERSION + 1}") }
        assertBackupRefused(newer, BackupError.NewerVersion::class.java)
    }

    @Test fun refusesBrokenLinksBetweenRows() = runBlocking<Unit> {
        seed()
        val broken = withEditedDb(export()) {
            sql(it, "PRAGMA foreign_keys = OFF", "DELETE FROM item WHERE id = 'a1'")
        }
        assertBackupRefused(broken, BackupError.Damaged::class.java)
    }

    @Test fun emptyBackupInspectsAsZeroFindsSoTheScreenCanWarn() = runBlocking<Unit> {
        val info = restore.inspect(Uri.fromFile(export()))
        assertEquals(0, info.finds)
        assertFalse(restore.relinkPending)
        restore.discard(info)
    }

    @Test fun repeatedExportRestoreKeepsTheDataIdentical() = runBlocking<Unit> {
        seed()
        val before = AllRowsSnapshot.of(db)
        repeat(3) {
            val r = restore.restore(restore.inspect(Uri.fromFile(export())))
            r.safetyCopy?.let { safety += it }
            assertEquals(before, AllRowsSnapshot.of(db))
        }
    }

    @Test fun inspectLeavesNoTemporaryFilesWhenRefused() = runBlocking<Unit> {
        val garbage = zipFile().apply { writeBytes(ByteArray(100)) }
        val count = { context.noBackupFilesDir.list()?.count { it.startsWith("restore-") } ?: 0 }
        val start = count()
        assertBackupRefused(garbage, BackupError.NotABackup::class.java)
        assertEquals(start, count())
    }
}

/** Compares whole tables, so a dropped field fails the test. */
private data class AllRowsSnapshot(
    val items: List<Item>, val finds: List<Acquisition>, val photos: List<Photo>, val lines: List<Line>, val ls: List<LineStation>,
) {
    companion object {
        suspend fun of(db: CollectiblesDb): AllRowsSnapshot {
            val d = db.dao()
            return AllRowsSnapshot(d.allItems(), d.allFinds(), d.allPhotos(), d.allLines(), d.allLineStations())
        }
    }
}
