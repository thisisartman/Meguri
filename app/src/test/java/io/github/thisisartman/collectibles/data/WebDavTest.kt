package io.github.thisisartman.collectibles.data

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.File

/** The WebDAV backup target (0.6): HTTPS only, a home server's certificate only once trusted, plain errors. */
class WebDavTest {
    private lateinit var server: MockWebServer
    private lateinit var cert: HeldCertificate

    @Before fun start() {
        // A home server: its own certificate, not signed by anyone the phone (or this JVM) trusts
        cert = HeldCertificate.Builder().addSubjectAlternativeName("localhost").build()
        server = MockWebServer()
        server.useHttps(HandshakeCertificates.Builder().heldCertificate(cert).build().sslSocketFactory(), false)
        server.start()
    }

    @After fun stop() = server.shutdown()

    private fun url() = server.url("/dav/files/me/Meguri/").toString().replace("127.0.0.1", "localhost")
    private val sha get() = WebDav.sha256(cert.certificate)

    @Test fun httpIsRefused() {
        try { WebDav.normalize("http://example.com/dav/"); fail() } catch (e: ServerError.NotHttps) {}
    }

    @Test fun addressWithoutSchemeBecomesHttpsFolder() {
        assertEquals("https://cloud.example.com/remote.php/dav/files/me/Meguri/", WebDav.normalize("cloud.example.com/remote.php/dav/files/me/Meguri").toString())
    }

    @Test fun unknownCertificateIsRefusedWithItsFingerprint() {
        server.enqueue(MockResponse().setResponseCode(207).setBody(LISTING))
        try {
            WebDav(url(), "me", "pw").list(); fail()
        } catch (e: ServerError.Untrusted) {
            assertEquals(sha, e.sha256)
        }
    }

    @Test fun trustedCertificateIsAcceptedAndOnlyThatOne() {
        server.enqueue(MockResponse().setResponseCode(207).setBody(LISTING))
        val files = WebDav(url(), "me", "pw", trusted = sha).list()
        assertEquals(listOf(RemoteFile("meguri-auto-20261120-101500.zip", 1234)), files)
        // A different fingerprint: still refused
        server.enqueue(MockResponse().setResponseCode(207).setBody(LISTING))
        try { WebDav(url(), "me", "pw", trusted = "00:11").list(); fail() } catch (e: ServerError.Untrusted) {}
    }

    @Test fun wrongPasswordAndMissingFolderAreNamed() {
        server.enqueue(MockResponse().setResponseCode(401))
        try { WebDav(url(), "me", "bad", trusted = sha).list(); fail() } catch (e: ServerError.WrongLogin) {}
        server.enqueue(MockResponse().setResponseCode(404))
        server.enqueue(MockResponse().setResponseCode(201))
        WebDav(url(), "me", "pw", trusted = sha).ensureFolder()
        server.takeRequest() // the refused listing above
        assertEquals("PROPFIND", server.takeRequest().method)
        assertEquals("MKCOL", server.takeRequest().method)
    }

    @Test fun uploadSendsTheFileWithBasicAuth() {
        val f = File.createTempFile("backup", ".zip").apply { writeBytes(ByteArray(300) { it.toByte() }); deleteOnExit() }
        server.enqueue(MockResponse().setResponseCode(201))
        WebDav(url(), "me", "pw", trusted = sha).upload("meguri-auto-20261120-101500.zip", f)
        val req = server.takeRequest()
        assertEquals("PUT", req.method)
        assertTrue(req.path!!.endsWith("/Meguri/meguri-auto-20261120-101500.zip"))
        assertEquals(300L, req.bodySize)
        assertTrue(req.getHeader("Authorization")!!.startsWith("Basic "))
    }

    @Test fun listingWithADoctypeIsRefused() {
        val evil = "<?xml version=\"1.0\"?><!DOCTYPE d [<!ENTITY x SYSTEM \"file:///etc/hosts\">]>" + LISTING.substringAfter("?>")
        try { WebDav.parseListing(evil, "/dav/files/me/Meguri/"); fail("a DOCTYPE was accepted") } catch (e: java.io.IOException) { }
    }

    @Test fun listingSkipsTheFolderAndSubfolders() {
        val files = WebDav.parseListing(LISTING, "/dav/files/me/Meguri/")
        assertEquals(listOf("meguri-auto-20261120-101500.zip"), files.map { it.name })
    }

    private companion object {
        const val LISTING = """<?xml version="1.0"?>
<d:multistatus xmlns:d="DAV:">
  <d:response><d:href>/dav/files/me/Meguri/</d:href><d:propstat><d:prop><d:resourcetype><d:collection/></d:resourcetype></d:prop></d:propstat></d:response>
  <d:response><d:href>/dav/files/me/Meguri/meguri-auto-20261120-101500.zip</d:href><d:propstat><d:prop><d:getcontentlength>1234</d:getcontentlength><d:resourcetype/></d:prop></d:propstat></d:response>
  <d:response><d:href>/dav/files/me/Meguri/old/</d:href><d:propstat><d:prop><d:resourcetype><d:collection/></d:resourcetype></d:prop></d:propstat></d:response>
</d:multistatus>"""
    }
}
