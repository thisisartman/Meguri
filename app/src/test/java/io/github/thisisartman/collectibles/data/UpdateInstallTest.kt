package io.github.thisisartman.collectibles.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Update from inside the app (#222): reading the release, and the checks before anything is installed. */
class UpdateInstallTest {
    private val sha = "9251db7c01be18870c217d7f18817a78cde045b5602d27f6f463668e67f81524"

    private fun release(name: String = "Meguri-0.7.2.apk", url: String = "https://github.com/thisisartman/Meguri/releases/download/v0.7.2/Meguri-0.7.2.apk", digest: String? = "sha256:$sha") =
        JSONObject(
            """{"tag_name":"v0.7.2","html_url":"https://github.com/thisisartman/Meguri/releases/tag/v0.7.2",
               "assets":[{"name":"$name","size":29763635,"browser_download_url":"$url"${digest?.let { ",\"digest\":\"$it\"" } ?: ""}}]}""",
        )

    @Test fun readsTheApkWithItsDigest() {
        val r = UpdateCheck.parse(release())!!
        assertEquals("v0.7.2", r.tag)
        assertEquals(UpdateCheck.Apk("https://github.com/thisisartman/Meguri/releases/download/v0.7.2/Meguri-0.7.2.apk", 29763635, sha), r.apk)
    }

    @Test fun noDigestStillHasTheApk() = assertNull(UpdateCheck.parse(release(digest = null))!!.apk!!.sha256)

    @Test fun onlyTheApkNamedForTheTag() = assertNull(UpdateCheck.parse(release(name = "Meguri-0.7.1.apk"))!!.apk)

    @Test fun onlyFromThisRepositorysReleases() =
        assertNull(UpdateCheck.parse(release(url = "https://example.com/Meguri-0.7.2.apk"))!!.apk)

    @Test fun aBadDigestIsIgnoredNotTrusted() = assertNull(UpdateCheck.parse(release(digest = "sha256:xyz"))!!.apk!!.sha256)

    @Test fun aPageElsewhereIsNoRelease() =
        assertNull(UpdateCheck.parse(JSONObject("""{"tag_name":"v0.7.2","html_url":"https://evil.example/x"}""")))

    @Test fun sizeAndHashMustMatch() {
        val apk = UpdateCheck.Apk("u", 100, sha)
        assertTrue(UpdateInstall.sizeAndHashOk(100, sha, apk))
        assertTrue(UpdateInstall.sizeAndHashOk(100, sha.uppercase(), apk))
        assertFalse(UpdateInstall.sizeAndHashOk(99, sha, apk))
        assertFalse(UpdateInstall.sizeAndHashOk(100, "0".repeat(64), apk))
        // No digest given: the size alone, then the signature
        assertTrue(UpdateInstall.sizeAndHashOk(100, "0".repeat(64), apk.copy(sha256 = null)))
    }

    @Test fun sameSignersOnly() {
        assertTrue(UpdateInstall.sameSigners(setOf("a"), setOf("a")))
        assertFalse(UpdateInstall.sameSigners(setOf("a"), setOf("b")))
        assertFalse(UpdateInstall.sameSigners(setOf("a"), setOf("a", "b")))
        assertFalse(UpdateInstall.sameSigners(emptySet(), emptySet()))
    }

    @Test fun onByDefaultOnlyForAFreshInstall() {
        assertTrue(UpdateCheck.startsOn(1_000L, 1_000L))
        assertFalse(UpdateCheck.startsOn(1_000L, 2_000L))
    }
}
