package io.github.thisisartman.collectibles.alerts

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.thisisartman.collectibles.data.CollectiblesDb
import io.github.thisisartman.collectibles.debug.FileLog
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The photo suggestion after every collect (#228), wherever it happens: a tile's quick Collect, the place page,
 * Scan, an alert's Collect. Not after a notebook page, which is a bulk import (owner, 2026-11-28). Skipped when the thing already has a place photo, or when Suggestions
 * is off. Collects within a minute at one place share one notification ([NudgeGroup]).
 */
@Singleton
class CollectNudge @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: CollectiblesDb,
    private val notifier: AlertNotifier,
    private val log: FileLog,
) {
    private var current: Nudge? = null

    suspend fun collected(itemId: String, findId: String) {
        if (!notifier.canPost(NotifyCategory.SUGGESTIONS)) return
        val dao = db.dao()
        val item = dao.item(itemId) ?: return
        if (dao.placePhotoCount(itemId) > 0) { log.d("Nudge", "No photo suggestion for $itemId: it has a place photo"); return }
        val n = synchronized(this) {
            NudgeGroup.add(
                current, notifier.nameOf(item), findId, item.lat, item.lng, System.currentTimeMillis(),
            ).also { current = it }
        }
        log.d("Nudge", "Photo suggestion for ${n.findIds.size} finds (newest $findId)")
        notifier.notifyNudge(n)
    }

    /** Undo or Remove: the find leaves the suggestion, which goes when nothing is left. */
    fun undone(findId: String) {
        val n = synchronized(this) {
            val before = current
            current = NudgeGroup.remove(before, findId)
            if (before == current) return
            current
        }
        if (n == null) notifier.cancelNudge() else notifier.notifyNudge(n)
    }

    /** A place photo came in for one of its finds (Take photo, or the place page's camera): nothing left to suggest. */
    fun placePhotoAdded(findId: String) {
        synchronized(this) { if (current?.findIds?.contains(findId) != true) return; current = null }
        notifier.cancelNudge()
    }

    /** Not now, or Take photo tapped. */
    fun dismiss() {
        synchronized(this) { current = null }
        notifier.cancelNudge()
    }
}
