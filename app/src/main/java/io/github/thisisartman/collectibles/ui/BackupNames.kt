package io.github.thisisartman.collectibles.ui

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** The suggested name for a Back up now file; one with photos says so. (Named meguri- since 0.4.0, collectibles- before.) */
internal fun backupFileName(photos: Boolean): String {
    val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
    return if (photos) "meguri-backup-photos-$stamp.zip" else "meguri-backup-$stamp.zip"
}
