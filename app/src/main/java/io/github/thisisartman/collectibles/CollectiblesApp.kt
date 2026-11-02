package io.github.thisisartman.collectibles

import android.app.Application
import android.os.Build
import dagger.hilt.android.HiltAndroidApp
import io.github.thisisartman.collectibles.debug.CrashLogger
import io.github.thisisartman.collectibles.debug.FileLog
import io.github.thisisartman.collectibles.ui.AppLanguage
import javax.inject.Inject

@HiltAndroidApp
class CollectiblesApp : Application() {
    @Inject lateinit var log: FileLog

    override fun onCreate() {
        super.onCreate()
        CrashLogger.install(log)
        AppLanguage.ensureDefault(this)
        log.i(
            "App",
            "Start ${BuildConfig.APPLICATION_ID} ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) · " +
                "${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})",
        )
    }
}
