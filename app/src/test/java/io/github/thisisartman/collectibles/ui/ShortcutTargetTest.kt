package io.github.thisisartman.collectibles.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

/** App shortcuts (#205): the id in the launch intent picks the screen. */
class ShortcutTargetTest {
    @Test fun eachIdOpensItsScreen() {
        assertEquals(ShortcutTarget.ADD, ShortcutTarget.from("add"))
        assertEquals(ShortcutTarget.BOOK, ShortcutTarget.from("book"))
        assertEquals(ShortcutTarget.TRIP, ShortcutTarget.from("trip"))
    }

    @Test fun noOrUnknownIdOpensNothing() {
        assertNull(ShortcutTarget.from(null))
        assertNull(ShortcutTarget.from(""))
        assertNull(ShortcutTarget.from("settings"))
        assertNull(ShortcutTarget.from("ADD"))
    }

    /** Every shortcut in each build's shortcuts.xml names an id the app knows, and the right package for that build. */
    @Test fun shortcutsXmlMatchesTheApp() {
        val builds = mapOf(
            "src/main/res/xml/shortcuts.xml" to "io.github.thisisartman.collectibles",
            "src/debug/res/xml/shortcuts.xml" to "io.github.thisisartman.collectibles.dev",
            "src/devRelease/res/xml/shortcuts.xml" to "io.github.thisisartman.collectibles.dev",
        )
        for ((path, pkg) in builds) {
            val text = File(path).readText()
            val ids = Regex("""android:name="shortcut" android:value="([^"]+)"""").findAll(text).map { it.groupValues[1] }.toList()
            assertEquals(path, ShortcutTarget.entries.map { it.id }, ids)
            ids.forEach { assertEquals(path, ShortcutTarget.from(it)?.id, it) }
            val packages = Regex("""android:targetPackage="([^"]+)"""").findAll(text).map { it.groupValues[1] }.toSet()
            assertEquals(path, setOf(pkg), packages)
            assertEquals(path, 3, Regex("""android:action="android.intent.action.VIEW"""").findAll(text).count())
        }
    }
}
