package io.github.thisisartman.collectibles.ui

import io.github.thisisartman.collectibles.R
import io.github.thisisartman.collectibles.data.DataJob
import io.github.thisisartman.collectibles.data.DataStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Calendar
import java.util.TimeZone

/** Back up now, Restore and Add photos from a file: what their rows say while running and after (#217). */
class DataJobTextTest {
    private val tokyo = TimeZone.getTimeZone("Asia/Tokyo")

    private fun at(y: Int, m: Int, d: Int, h: Int, min: Int) = Calendar.getInstance(tokyo).apply {
        clear(); set(y, m - 1, d, h, min)
    }.timeInMillis

    @Test fun eachJobSaysWhatItIsDoing() {
        assertEquals(R.string.job_backing_up, DataJobText.progress(DataRun(DataJob.BACKUP, DataStep.WORKING)).step)
        assertEquals(R.string.job_restoring, DataJobText.progress(DataRun(DataJob.RESTORE, DataStep.WORKING)).step)
        assertEquals(R.string.job_adding, DataJobText.progress(DataRun(DataJob.PHOTO_FILE, DataStep.PHOTOS, 3, 10)).step)
        // Reading the chosen file and the copy of what is here come first, the same words for each job
        assertEquals(R.string.job_reading, DataJobText.progress(DataRun(DataJob.RESTORE, DataStep.READING)).step)
        assertEquals(R.string.job_copy, DataJobText.progress(DataRun(DataJob.PHOTO_FILE, DataStep.COPY, 1, 4)).step)
    }

    @Test fun photosAreCountedOnlyWhenThereIsATotal() {
        val p = DataJobText.progress(DataRun(DataJob.BACKUP, DataStep.PHOTOS, 45, 120))
        assertEquals(R.string.job_backing_up, p.step)
        assertEquals(45, p.done)
        assertEquals(120, p.total)
        val none = DataJobText.progress(DataRun(DataJob.BACKUP, DataStep.WORKING))
        assertNull(none.done)
        assertNull(none.total)
    }

    @Test fun theBarFillsWithThePhotosAndMovesWithoutThem() {
        assertEquals(0.375f, DataJobText.fraction(DataRun(DataJob.BACKUP, DataStep.PHOTOS, 45, 120))!!, 1e-6f)
        assertEquals(1f, DataJobText.fraction(DataRun(DataJob.BACKUP, DataStep.PHOTOS, 130, 120))!!, 1e-6f)
        assertNull(DataJobText.fraction(DataRun(DataJob.RESTORE, DataStep.WORKING)))
    }

    @Test fun aBackupThatWorkedSaysWhenWhatAndWhere() {
        val now = at(2026, 11, 28, 9, 0)
        val r = DataJobText.result(DataResult(DataJob.BACKUP, true, at(2026, 11, 28, 8, 46), 90, 120, "meguri-backup-photos.zip"), now, tokyo)
        assertEquals(DataJobText.Result(R.string.job_last_backup, DataJobText.Day.TODAY, 90, 120, "meguri-backup-photos.zip"), r)
    }

    @Test fun eachJobHasItsOwnHead() {
        val now = at(2026, 11, 28, 9, 0)
        assertEquals(R.string.job_last_restore, DataJobText.result(DataResult(DataJob.RESTORE, true, now, 1, 0), now, tokyo).head)
        assertEquals(R.string.job_last_added, DataJobText.result(DataResult(DataJob.PHOTO_FILE, true, now, photos = 5), now, tokyo).head)
    }

    @Test fun noPhotosAndNoNameAreLeftOut() {
        val now = at(2026, 11, 28, 9, 0)
        val r = DataJobText.result(DataResult(DataJob.BACKUP, true, now, 90, 0, " "), now, tokyo)
        assertEquals(90, r.finds)
        assertNull(r.photos)
        assertNull(r.file)
        // A photo file has no finds of its own
        assertNull(DataJobText.result(DataResult(DataJob.PHOTO_FILE, true, now, photos = 5), now, tokyo).finds)
    }

    @Test fun aFailureSaysOnlyThatItFailedAndWhen() {
        val now = at(2026, 11, 28, 9, 0)
        val r = DataJobText.result(DataResult(DataJob.RESTORE, false, at(2026, 11, 27, 22, 5), file = "old.zip"), now, tokyo)
        assertEquals(DataJobText.Result(R.string.job_failed, DataJobText.Day.YESTERDAY, null, null, null), r)
    }

    @Test fun todayAndYesterdayFollowTheCalendarNotTwentyFourHours() {
        val now = at(2026, 11, 28, 0, 10)
        assertEquals(DataJobText.Day.TODAY, DataJobText.dayOf(at(2026, 11, 28, 0, 0), now, tokyo))
        assertEquals(DataJobText.Day.YESTERDAY, DataJobText.dayOf(at(2026, 11, 27, 23, 59), now, tokyo))
        assertEquals(DataJobText.Day.YESTERDAY, DataJobText.dayOf(at(2026, 11, 27, 0, 0), now, tokyo))
        assertEquals(DataJobText.Day.EARLIER, DataJobText.dayOf(at(2026, 11, 26, 23, 59), now, tokyo))
        // Across a month and a year
        assertEquals(DataJobText.Day.YESTERDAY, DataJobText.dayOf(at(2026, 12, 31, 20, 0), at(2027, 1, 1, 8, 0), tokyo))
    }

    private fun strings(path: String): Map<String, String> =
        Regex("""<(string|plurals) name="([^"]+)">(.*?)</\1>""", RegexOption.DOT_MATCHES_ALL)
            .findAll(File(path).readText()).associate { it.groupValues[2] to it.groupValues[3] }

    /** English and Japanese say the same things, with the same numbered parts. */
    @Test fun englishAndJapaneseMatch() {
        val en = strings("src/main/res/values/strings_jobs.xml")
        val ja = strings("src/main/res/values-ja/strings_jobs.xml")
        assertEquals(en.keys, ja.keys)
        val slots = Regex("""%(\d)\$[ds]""")
        for (k in en.keys) {
            val e = slots.findAll(en.getValue(k)).map { it.value }.toSortedSet()
            val j = slots.findAll(ja.getValue(k)).map { it.value }.toSortedSet()
            assertEquals(k, e, j)
        }
    }

    /** The Weekly backup row's failure names the weekly backup, so it doesn't read as Back up now failing. */
    @Test fun weeklyFailureSaysWeekly() {
        val en = strings("src/main/res/values/strings.xml") + strings("src/main/res/values/strings_backup.xml")
        val ja = strings("src/main/res/values-ja/strings.xml") + strings("src/main/res/values-ja/strings_backup.xml")
        for (k in listOf("more_auto_sub_failed", "auto_failed_login", "auto_failed_certificate")) {
            assertTrue(k, en.getValue(k).contains("weekly backup"))
            assertTrue(k, ja.getValue(k).contains("毎週のバックアップ"))
        }
    }
}
