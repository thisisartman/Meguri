package io.github.thisisartman.collectibles.ui

import io.github.thisisartman.collectibles.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Long work started from Settings (card pictures, Back up now, restore, loading a CSV) runs here, for the life of the
 * app, not of the Settings page: leaving Settings used to cancel it halfway (sweep 2026-11-09, finding 32). Its
 * results come back as messages that the app frame shows in its snackbar, wherever the person is by then.
 */
@Singleton
class SettingsWork @Inject constructor() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _messages = Channel<String>(Channel.BUFFERED)

    /** Messages to show once, in order; kept until the app frame takes them. */
    val messages: Flow<String> = _messages.receiveAsFlow()

    fun launch(block: suspend () -> Unit): Job = scope.launch { block() }

    fun say(text: String) {
        _messages.trySend(text)
    }

    private val _cardProgress = MutableStateFlow<Pair<Int, Int>?>(null)

    /** Card pictures being downloaded: done and total, or null when idle. */
    val cardProgress: StateFlow<Pair<Int, Int>?> = _cardProgress.asStateFlow()

    /** Starts the card picture download unless one is already running; [onDone] gets (downloaded, not found). */
    fun downloadCards(download: suspend (onProgress: (Int, Int) -> Unit) -> Pair<Int, Int>, onDone: (Int, Int) -> Unit) {
        if (_cardProgress.value != null) return
        _cardProgress.value = 0 to 0
        launch {
            try {
                val (ok, failed) = download { d, t -> _cardProgress.value = d to t }
                onDone(ok, failed)
            } finally {
                _cardProgress.value = null
            }
        }
    }

    companion object {
        /** What to say when a card picture download ends: nothing was missing, or how many came and how many were not found. */
        fun cardsDoneMessage(downloaded: Int, notFound: Int): Int =
            if (downloaded == 0 && notFound == 0) R.string.cards_all_here else R.string.cards_done
    }
}
