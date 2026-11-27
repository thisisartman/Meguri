package io.github.thisisartman.collectibles.ui

/**
 * The app shortcuts on a long press of the Meguri icon (#205): res/xml/shortcuts.xml puts one of these ids in the
 * launch intent's "shortcut" extra, and the app opens on that screen, with Nearby under it for Back.
 */
enum class ShortcutTarget(val id: String) {
    /** The + card: Scan, Search, Add. */
    ADD("add"),
    /** My book. */
    BOOK("book"),
    /** Trip: today's trip, or its start screen when none is planned. */
    TRIP("trip");

    companion object {
        /** The shortcut an intent's extra names; null for none, or an id this version does not know. */
        fun from(id: String?): ShortcutTarget? = entries.firstOrNull { it.id == id?.trim() }
    }
}
