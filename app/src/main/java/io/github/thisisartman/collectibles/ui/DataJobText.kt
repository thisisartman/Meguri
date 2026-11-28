package io.github.thisisartman.collectibles.ui

import android.content.res.Resources
import io.github.thisisartman.collectibles.R
import io.github.thisisartman.collectibles.data.DataJob
import io.github.thisisartman.collectibles.data.DataStep
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/** A job running now (#217): which, its step, and photos done of the total when the step counts them. */
data class DataRun(val job: DataJob, val step: DataStep, val done: Int = 0, val total: Int = 0)

/**
 * How the last run of a job ended (#217), kept until the next one: when, whether it worked, what it carried (null
 * leaves a count out) and the file's name.
 */
data class DataResult(
    val job: DataJob,
    val ok: Boolean,
    val atMs: Long,
    val finds: Int? = null,
    val photos: Int? = null,
    val file: String? = null,
)

/**
 * The words on the Backup and data rows while a job runs and after it (#217), picked here without Android so tests can
 * check them: "Backing up… 45 of 120 photos", "Last backup: today 8:46 · 90 finds, 120 photos · meguri-backup.zip",
 * "Failed today 8:46".
 */
object DataJobText {
    enum class Day { TODAY, YESTERDAY, EARLIER }

    /** The step's words and, when it counts photos, how many of how many. */
    data class Progress(val step: Int, val done: Int?, val total: Int?)

    /** The head ("Last backup: %1$s" or "Failed %1$s", given the time) and the parts after it. */
    data class Result(val head: Int, val day: Day, val finds: Int?, val photos: Int?, val file: String?)

    fun progress(run: DataRun): Progress = Progress(
        step = when (run.step) {
            DataStep.READING -> R.string.job_reading
            DataStep.COPY -> R.string.job_copy
            DataStep.WORKING, DataStep.PHOTOS -> when (run.job) {
                DataJob.BACKUP -> R.string.job_backing_up
                DataJob.RESTORE -> R.string.job_restoring
                DataJob.PHOTO_FILE -> R.string.job_adding
                DataJob.MERGE -> R.string.job_merging
            }
        },
        done = run.done.takeIf { run.total > 0 },
        total = run.total.takeIf { it > 0 },
    )

    /** How full the bar is, 0 to 1; null while the step has nothing to count (the bar then just moves). */
    fun fraction(run: DataRun): Float? =
        if (run.total > 0) (run.done.coerceIn(0, run.total).toFloat() / run.total) else null

    fun result(r: DataResult, nowMs: Long, tz: TimeZone = TimeZone.getDefault()): Result {
        val day = dayOf(r.atMs, nowMs, tz)
        if (!r.ok) return Result(R.string.job_failed, day, null, null, null)
        return Result(
            head = when (r.job) {
                DataJob.BACKUP -> R.string.job_last_backup
                DataJob.RESTORE -> R.string.job_last_restore
                DataJob.PHOTO_FILE, DataJob.MERGE -> R.string.job_last_added
            },
            day = day,
            // A backup without photos says so by leaving the photos out; a photo file has no finds of its own
            finds = r.finds,
            photos = r.photos?.takeIf { it > 0 },
            file = r.file?.takeIf { it.isNotBlank() },
        )
    }

    /** Today, yesterday or before, by the phone's calendar. */
    fun dayOf(atMs: Long, nowMs: Long, tz: TimeZone = TimeZone.getDefault()): Day {
        fun start(ms: Long) = Calendar.getInstance(tz).apply {
            timeInMillis = ms; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val today = start(nowMs)
        val at = start(atMs)
        if (at.timeInMillis == today.timeInMillis) return Day.TODAY
        today.add(Calendar.DAY_OF_YEAR, -1)
        return if (at.timeInMillis == today.timeInMillis) Day.YESTERDAY else Day.EARLIER
    }

    // The words themselves, from the app's strings

    fun progressText(res: Resources, run: DataRun): String {
        val p = progress(run)
        val step = res.getString(p.step)
        return if (p.done != null && p.total != null) step + " " + res.getQuantityString(R.plurals.job_photos_of, p.total, p.done, p.total) else step
    }

    fun whenText(res: Resources, day: Day, atMs: Long, locale: Locale): String = when (day) {
        Day.TODAY -> res.getString(R.string.job_today, Dates.time(atMs, locale))
        Day.YESTERDAY -> res.getString(R.string.job_yesterday, Dates.time(atMs, locale))
        Day.EARLIER -> Dates.dayTime(atMs, locale)
    }

    fun resultText(res: Resources, r: DataResult, nowMs: Long = System.currentTimeMillis()): String {
        val line = result(r, nowMs)
        val locale = res.configuration.locales[0] ?: Locale.getDefault()
        val counts = listOfNotNull(
            line.finds?.let { res.getQuantityString(R.plurals.finds, it, it) },
            line.photos?.let { res.getQuantityString(R.plurals.photos, it, it) },
        ).joinToString(res.getString(R.string.count_sep)).ifEmpty { null }
        return listOfNotNull(res.getString(line.head, whenText(res, line.day, r.atMs, locale)), counts, line.file).joinToString(" · ")
    }
}
