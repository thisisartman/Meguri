package io.github.thisisartman.collectibles

import android.app.Application
import android.os.Build
import dagger.hilt.android.HiltAndroidApp
import io.github.thisisartman.collectibles.data.AutoBackup
import io.github.thisisartman.collectibles.data.BackupRestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import io.github.thisisartman.collectibles.debug.CrashLogger
import io.github.thisisartman.collectibles.debug.FileLog
import io.github.thisisartman.collectibles.ui.AppLanguage
import javax.inject.Inject

@HiltAndroidApp
class CollectiblesApp : Application() {
    @Inject lateinit var log: FileLog
    @Inject lateinit var restore: BackupRestore
    @Inject lateinit var autoBackup: AutoBackup
    @Inject lateinit var notifier: io.github.thisisartman.collectibles.alerts.AlertNotifier
    @Inject lateinit var geofences: io.github.thisisartman.collectibles.alerts.Geofences
    @Inject lateinit var catalogue: io.github.thisisartman.collectibles.data.CatalogAndBackup
    @Inject lateinit var photoCopier: io.github.thisisartman.collectibles.photo.PhotoCopier

    override fun onCreate() {
        super.onCreate()
        CrashLogger.install(log)
        AppLanguage.ensureDefault(this)
        log.i(
            "App",
            "Start ${BuildConfig.APPLICATION_ID} ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) · " +
                "${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})",
        )
        autoBackup.catchUpIfOverdue()
        notifier.createChannels()
        geofences.refreshInBackground()
        CoroutineScope(Dispatchers.IO).launch {
            runCatching { restore.resumeRelinkIfPending() }.onFailure { log.e("Restore", "Relink failed", it) }
            runCatching { photoCopier.copyAll() }.onFailure { log.e("Photo", "Copying photos into the app failed", it) }
            runCatching { catalogue.refreshBundledIfChanged() }.onFailure { log.e("Catalog", "Bundled refresh failed", it) }
        }
    }
}
