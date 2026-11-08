package io.github.thisisartman.collectibles.photo

import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.thisisartman.collectibles.alerts.AlertNotifier
import io.github.thisisartman.collectibles.alerts.AlertSettings
import io.github.thisisartman.collectibles.data.CollectiblesDb
import io.github.thisisartman.collectibles.data.Item
import io.github.thisisartman.collectibles.data.SharedPhotoReader
import io.github.thisisartman.collectibles.debug.FileLog
import io.github.thisisartman.collectibles.location.LocationSource
import io.github.thisisartman.collectibles.location.distanceM
import java.util.concurrent.TimeUnit

/**
 * The new-photo hint (M7, FR-26; owner 2026-11-08: "yes, allow to disable"): when a photo taken with the phone's own
 * camera lands in the gallery near something not yet collected, a quiet notification offers to attach it.
 */
object PhotoHints {
    /** How near a photo must be to an item to suggest it. */
    const val NEAR_M = 300.0
    /** A photo with no position counts as taken where you are only if it is this fresh and your position too. */
    const val FRESH_MS = 10 * 60_000L

    /** The nearest uncollected item within [NEAR_M] of where the photo was taken, or null. */
    fun match(lat: Double, lng: Double, candidates: List<Item>, nearM: Double = NEAR_M): Pair<Item, Double>? =
        candidates.mapNotNull { i -> val a = i.lat ?: return@mapNotNull null; val b = i.lng ?: return@mapNotNull null; i to distanceM(lat, lng, a, b) }
            .filter { it.second <= nearM }.minByOrNull { it.second }

    /** Only the camera's own photos: screenshots, downloads and other apps' pictures are not visits. */
    fun isCameraPath(relativePath: String?): Boolean =
        relativePath != null && (relativePath.startsWith("DCIM/") && !relativePath.contains("CollectiblesTracker", ignoreCase = true))
}

@dagger.hilt.EntryPoint
@dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
interface NewPhotoEntryPoint {
    fun settings(): AlertSettings
    fun notifier(): AlertNotifier
    fun db(): CollectiblesDb
    fun location(): LocationSource
    fun log(): FileLog
}

class NewPhotoWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    private val deps = dagger.hilt.android.EntryPointAccessors.fromApplication(context.applicationContext, NewPhotoEntryPoint::class.java)
    private val settings = deps.settings()
    private val notifier = deps.notifier()
    private val db = deps.db()
    private val location = deps.location()
    private val log = deps.log()

    override suspend fun doWork(): Result {
        // A content trigger fires once: wait for the next photo straight away
        if (settings.photoHintsOn) NewPhotoWatcher.schedule(applicationContext, ExistingWorkPolicy.APPEND_OR_REPLACE) else return Result.success()
        val since = settings.photoHintsSeenSec
        val now = System.currentTimeMillis() / 1000
        if (since == 0L) { settings.photoHintsSeenSec = now; return Result.success() }
        val cr = applicationContext.contentResolver
        val cols = arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.RELATIVE_PATH, MediaStore.Images.Media.DATE_TAKEN, MediaStore.Images.Media.DATE_ADDED)
        val fresh = mutableListOf<Pair<android.net.Uri, Long?>>()
        runCatching {
            cr.query(
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), cols,
                "${MediaStore.Images.Media.DATE_ADDED} > ?", arrayOf(since.toString()), "${MediaStore.Images.Media.DATE_ADDED} DESC",
            )?.use { c ->
                while (c.moveToNext() && fresh.size < 5) {
                    if (!PhotoHints.isCameraPath(c.getString(1))) continue
                    val uri = ContentUris.withAppendedId(MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), c.getLong(0))
                    fresh += uri to (if (c.isNull(2)) null else c.getLong(2))
                }
            }
        }.onFailure { log.w("PhotoHint", "Could not read the gallery: ${it.message}") }
        settings.photoHintsSeenSec = now
        if (fresh.isEmpty()) return Result.success()
        val candidates = db.dao().uncollectedPlaces()
        for ((uri, taken) in fresh) {
            val p = SharedPhotoReader.read(applicationContext, uri)
            val here = location.lastKnown()
            val at = when {
                p.hasPlace -> p.lat!! to p.lng!!
                // No position in the file: where you were, if both the photo and the position are recent
                here != null && taken != null && System.currentTimeMillis() - taken < PhotoHints.FRESH_MS &&
                    System.currentTimeMillis() - here.timeMs < PhotoHints.FRESH_MS -> here.lat to here.lng
                else -> null
            } ?: continue
            val hit = PhotoHints.match(at.first, at.second, candidates) ?: continue
            notifier.notifyPhoto(hit.first, uri, hit.second)
            log.i("PhotoHint", "Photo near ${hit.first.id}, ${hit.second.toInt()} m")
        }
        return Result.success()
    }
}

object NewPhotoWatcher {
    private const val WORK = "new-photo-hint"

    /** Wakes [NewPhotoWorker] when the gallery gets a new picture (within about half a minute). */
    /**
     * [policy]: REPLACE when switched on; KEEP at app start (a watcher already waiting stays); APPEND_OR_REPLACE from
     * inside the worker, since replacing would cancel the run in progress (seen on the emulator, 2026-11-08).
     */
    fun schedule(context: Context, policy: ExistingWorkPolicy = ExistingWorkPolicy.REPLACE) {
        val constraints = Constraints.Builder()
            .addContentUriTrigger(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true)
            .setTriggerContentUpdateDelay(5, TimeUnit.SECONDS)
            .setTriggerContentMaxDelay(30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            WORK, policy, OneTimeWorkRequestBuilder<NewPhotoWorker>().setConstraints(constraints).build(),
        )
    }

    fun cancel(context: Context) = WorkManager.getInstance(context).cancelUniqueWork(WORK)

    /** What the hint needs: seeing the gallery and where its photos were taken, and posting notifications. */
    fun permissions(): Array<String> = buildList {
        addAll(SharedPhotoReader.placePermissions())
        if (Build.VERSION.SDK_INT >= 33) add(android.Manifest.permission.POST_NOTIFICATIONS)
    }.toTypedArray()
}
