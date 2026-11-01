package io.github.thisisartman.collectibles.debug

import android.content.ContentResolver
import android.net.Uri
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.thisisartman.collectibles.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class LogsViewModel @Inject constructor(private val log: FileLog) : ViewModel() {
    private val _lines = MutableStateFlow<List<String>>(emptyList())
    val lines: StateFlow<List<String>> = _lines.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _lines.value = withContext(Dispatchers.IO) { log.tail(300) }
        }
    }

    fun exportFileName(): String =
        "collectibles-log-${SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())}.txt"

    fun export(resolver: ContentResolver, uri: Uri) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    resolver.openOutputStream(uri)!!.bufferedWriter().use { out ->
                        out.write(header())
                        out.write(log.readAll())
                    }
                }.onFailure { log.e("Logs", "Export failed", it) }.isSuccess
            }
            if (ok) log.i("Logs", "Exported to $uri")
            _message.value = if (ok) "Logs saved" else "Couldn't save the logs"
            refresh()
        }
    }

    fun clearMessage() {
        _message.value = null
    }

    private fun header(): String =
        "${BuildConfig.APPLICATION_ID} ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\n" +
            "${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT}) · ${Build.DISPLAY}\n\n"
}
