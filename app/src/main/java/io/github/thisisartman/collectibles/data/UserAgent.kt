package io.github.thisisartman.collectibles.data

import io.github.thisisartman.collectibles.BuildConfig

/**
 * How Meguri names itself to the sites it reads (place search, Google Maps links, card pictures, region packs): one
 * string with the real version. Three requests still said "Meguri/0.3" in 0.6 (#171).
 */
object UserAgent {
    val value: String get() = "Meguri/${BuildConfig.VERSION_NAME} (personal collecting app for Android)"
}
