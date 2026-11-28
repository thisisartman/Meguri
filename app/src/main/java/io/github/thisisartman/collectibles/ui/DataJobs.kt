package io.github.thisisartman.collectibles.ui

import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.thisisartman.collectibles.MainActivity
import io.github.thisisartman.collectibles.R
import io.github.thisisartman.collectibles.alerts.AlertNotifier
import io.github.thisisartman.collectibles.data.DataJob
import io.github.thisisartman.collectibles.data.DataStep
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Back up now, Restore and Add photos from a file (#217): which one is running and how far, for the row that started
 * it; the last result of each, kept on the phone so the row can say it until the next run (a restore restarts the app);
 * and a progress notification once a run takes longer than a few seconds, so it can be followed from anywhere.
 */
@Singleton
@Suppress("MissingPermission") // each notify is behind AlertNotifier.canPost(), or cancels
class DataJobs @Inject constructor(
    @ApplicationContext private val context: Context,
    private val notifier: AlertNotifier,
) {
    private val prefs = context.getSharedPreferences("data_jobs", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _running = MutableStateFlow<DataRun?>(null)
    /** The job running now, or null. */
    val running: StateFlow<DataRun?> = _running.asStateFlow()

    private val _results = MutableStateFlow(DataJob.entries.mapNotNull { read(it) }.associateBy { it.job })
    /** The last result of each job that has run. */
    val results: StateFlow<Map<DataJob, DataResult>> = _results.asStateFlow()

    @Volatile private var shown = false
    @Volatile private var lastShownMs = 0L
    @Volatile private var lastStateMs = 0L
    private var notifyLater: Job? = null

    /** Marks [job] as running from [step]; false, with nothing changed, while another one runs. */
    @Synchronized
    fun start(job: DataJob, step: DataStep = DataStep.WORKING): Boolean {
        if (_running.value != null) return false
        _running.value = DataRun(job, step)
        shown = false
        notifyLater?.cancel()
        notifyLater = scope.launch {
            delay(NOTIFY_AFTER_MS)
            if (_running.value != null) { shown = true; post() }
        }
        return true
    }

    /** How far the running job is. Called for each photo, so the row and the notification only follow a few times a second. */
    fun report(step: DataStep, done: Int, total: Int) {
        val now = _running.value ?: return
        val next = now.copy(step = step, done = done, total = total)
        val t = System.currentTimeMillis()
        val ends = total > 0 && done >= total
        if (step != now.step || ends || t - lastStateMs >= STATE_EVERY_MS) {
            lastStateMs = t
            _running.value = next
        }
        if (shown && (step != now.step || t - lastShownMs >= NOTIFY_EVERY_MS)) post()
    }

    /** Ends the running job without a result: the file was read and now waits on the person's OK. */
    @Synchronized
    fun pause() = stop()

    /** Ends the running job with how it went, kept until the next one. */
    @Synchronized
    fun finish(result: DataResult) {
        val wasShown = shown
        stop()
        write(result)
        _results.value = _results.value + (result.job to result)
        // A long run told about in a notification ends in one too, as the person may be in another app by now
        if (wasShown && notifier.canPost(io.github.thisisartman.collectibles.alerts.NotifyCategory.BACKUPS)) {
            NotificationManagerCompat.from(context).notify(
                NOTIFICATION_ID,
                base().setContentTitle(context.getString(titleOf(result.job)))
                    .setContentText(DataJobText.resultText(context.resources, result))
                    .setAutoCancel(true)
                    .build(),
            )
        }
    }

    private fun stop() {
        notifyLater?.cancel()
        notifyLater = null
        if (shown) NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
        shown = false
        _running.value = null
    }

    private fun post() {
        val run = _running.value ?: return
        if (!notifier.canPost(io.github.thisisartman.collectibles.alerts.NotifyCategory.BACKUPS)) return
        lastShownMs = System.currentTimeMillis()
        val fraction = DataJobText.fraction(run)
        NotificationManagerCompat.from(context).notify(
            NOTIFICATION_ID,
            base().setContentTitle(context.getString(titleOf(run.job)))
                .setContentText(DataJobText.progressText(context.resources, run))
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setProgress(100, ((fraction ?: 0f) * 100).toInt(), fraction == null)
                .build(),
        )
    }

    private fun base() = NotificationCompat.Builder(context, AlertNotifier.CH_DATA)
        .setSmallIcon(R.drawable.ic_notification)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .setContentIntent(
            PendingIntent.getActivity(
                context, NOTIFICATION_ID, MainActivity.launchIntent(context),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            ),
        )

    private fun titleOf(job: DataJob) = when (job) {
        DataJob.BACKUP -> R.string.more_backup
        DataJob.RESTORE -> R.string.more_restore
        DataJob.PHOTO_FILE -> R.string.photo_file
    }

    private fun read(job: DataJob): DataResult? {
        val k = job.name.lowercase()
        val at = prefs.getLong("$k.at", 0L).takeIf { it > 0L } ?: return null
        return DataResult(
            job, prefs.getBoolean("$k.ok", false), at,
            prefs.getInt("$k.finds", -1).takeIf { it >= 0 },
            prefs.getInt("$k.photos", -1).takeIf { it >= 0 },
            prefs.getString("$k.file", null),
        )
    }

    private fun write(r: DataResult) {
        val k = r.job.name.lowercase()
        prefs.edit()
            .putLong("$k.at", r.atMs)
            .putBoolean("$k.ok", r.ok)
            .putInt("$k.finds", r.finds ?: -1)
            .putInt("$k.photos", r.photos ?: -1)
            .putString("$k.file", r.file)
            .commit()
    }

    private companion object {
        const val NOTIFICATION_ID = 7217
        const val NOTIFY_AFTER_MS = 3_000L
        const val NOTIFY_EVERY_MS = 500L
        const val STATE_EVERY_MS = 100L
    }
}
