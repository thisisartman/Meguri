package io.github.thisisartman.collectibles.data

import okhttp3.Credentials
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.internal.tls.OkHostnameVerifier
import org.w3c.dom.Element
import java.io.File
import java.io.IOException
import java.net.URLDecoder
import java.security.KeyStore
import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLException
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager
import javax.xml.parsers.DocumentBuilderFactory

/** Why talking to the server failed, in the terms the Backup page shows. */
sealed class ServerError(message: String) : IOException(message) {
    /** The address isn't an https:// address. */
    class NotHttps : ServerError("not https")
    class BadAddress : ServerError("bad address")
    class WrongLogin : ServerError("wrong user name or password")
    class NotFound : ServerError("folder not found")
    /** The server's certificate isn't trusted by the phone; [sha256] is its fingerprint, to show and to trust. */
    class Untrusted(val sha256: String) : ServerError("untrusted certificate")
    class Offline(cause: Throwable?) : ServerError("server unreachable") {
        init { initCause(cause) }
    }
    class Http(val code: Int) : ServerError("HTTP $code")
}

/** A file in the backup folder on the server. */
data class RemoteFile(val name: String, val size: Long)

/**
 * A WebDAV folder (Nextcloud, ownCloud, a NAS, any WebDAV server) for backups. HTTPS only, user name and password.
 * A server with its own certificate (a home server) is trusted only when its SHA-256 fingerprint matches [trusted],
 * which the user has confirmed once; a different certificate is never accepted silently.
 */
class WebDav(folderUrl: String, private val user: String, private val password: String, private val trusted: String? = null, client: OkHttpClient? = null) {
    val folder: HttpUrl = normalize(folderUrl)
    private val http: OkHttpClient = client ?: pinnedClient(trusted)

    /** Creates the folder if it isn't there yet (one level: the folder's parent must exist). */
    fun ensureFolder() {
        val code = call(Request.Builder().url(folder).method("PROPFIND", PROPFIND_BODY.toRequestBody(XML)).header("Depth", "0").build()) { it.code }
        if (code == 404) call(Request.Builder().url(folder).method("MKCOL", null).build()) { check(it.code, "MKCOL") }
        else if (code !in 200..299) throw errorFor(code)
    }

    /** The files directly inside the folder. */
    fun list(): List<RemoteFile> {
        val xml = call(Request.Builder().url(folder).method("PROPFIND", PROPFIND_BODY.toRequestBody(XML)).header("Depth", "1").build()) {
            check(it.code, "PROPFIND"); it.body?.string().orEmpty()
        }
        return parseListing(xml, folder.encodedPath)
    }

    fun upload(name: String, file: File) {
        call(Request.Builder().url(fileUrl(name)).put(file.asRequestBody(ZIP)).build()) { check(it.code, "PUT") }
    }

    fun download(name: String, target: File) {
        call(Request.Builder().url(fileUrl(name)).get().build()) { r ->
            check(r.code, "GET")
            target.outputStream().use { out -> r.body!!.byteStream().use { it.copyTo(out) } }
        }
    }

    fun delete(name: String) {
        call(Request.Builder().url(fileUrl(name)).delete().build()) { if (it.code != 404) check(it.code, "DELETE") }
    }

    private fun fileUrl(name: String) = folder.newBuilder().addPathSegment(name).build()

    private fun <T> call(request: Request, read: (okhttp3.Response) -> T): T {
        val req = request.newBuilder().header("Authorization", Credentials.basic(user, password)).build()
        try {
            return http.newCall(req).execute().use(read)
        } catch (e: SSLException) {
            untrustedIn(e)?.let { throw ServerError.Untrusted(it) }
            throw ServerError.Offline(e)
        } catch (e: ServerError) {
            throw e
        } catch (e: IOException) {
            throw ServerError.Offline(e)
        }
    }

    private fun check(code: Int, what: String) {
        if (code !in 200..299) throw errorFor(code)
    }

    private fun errorFor(code: Int): ServerError = when (code) {
        401, 403 -> ServerError.WrongLogin()
        404, 409 -> ServerError.NotFound()
        else -> ServerError.Http(code)
    }

    /** Thrown inside the TLS handshake when the phone doesn't trust the certificate; carries its fingerprint out. */
    private class UntrustedCertificate(val sha256: String) : CertificateException("untrusted $sha256")

    companion object {
        private val XML = "application/xml; charset=utf-8".toMediaType()
        private val ZIP = "application/zip".toMediaType()
        private const val PROPFIND_BODY =
            """<?xml version="1.0" encoding="utf-8"?><d:propfind xmlns:d="DAV:"><d:prop><d:getcontentlength/><d:resourcetype/></d:prop></d:propfind>"""

        /** The folder address, always ending in "/". Only https:// is accepted. */
        fun normalize(url: String): HttpUrl {
            val trimmed = url.trim()
            if (!trimmed.startsWith("https://", ignoreCase = true)) {
                if (trimmed.startsWith("http://", ignoreCase = true)) throw ServerError.NotHttps()
            }
            val withScheme = if (trimmed.contains("://")) trimmed else "https://$trimmed"
            val parsed = withScheme.toHttpUrlOrNull() ?: throw ServerError.BadAddress()
            if (!parsed.isHttps) throw ServerError.NotHttps()
            return if (parsed.encodedPath.endsWith("/")) parsed else parsed.newBuilder().addPathSegment("").build()
        }

        fun sha256(cert: X509Certificate): String =
            MessageDigest.getInstance("SHA-256").digest(cert.encoded).joinToString(":") { "%02X".format(it) }

        /** The fingerprint carried by an untrusted-certificate failure, if that is what [e] is. */
        private fun untrustedIn(e: Throwable): String? {
            var t: Throwable? = e
            while (t != null) {
                if (t is UntrustedCertificate) return t.sha256
                t = t.cause
            }
            return null
        }

        /** The phone's own trust, plus the one certificate the user confirmed, if any; nothing else. */
        private fun pinnedClient(trusted: String?): OkHttpClient {
            val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply { init(null as KeyStore?) }
            val system = tmf.trustManagers.filterIsInstance<X509TrustManager>().first()
            val tm = object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<out X509Certificate>, authType: String) = system.checkClientTrusted(chain, authType)
                override fun getAcceptedIssuers(): Array<X509Certificate> = system.acceptedIssuers
                override fun checkServerTrusted(chain: Array<out X509Certificate>, authType: String) {
                    try {
                        system.checkServerTrusted(chain, authType)
                    } catch (e: CertificateException) {
                        val sha = sha256(chain[0])
                        if (trusted == null || !trusted.equals(sha, ignoreCase = true)) throw UntrustedCertificate(sha)
                    }
                }
            }
            val ssl = SSLContext.getInstance("TLS").apply { init(null, arrayOf(tm), null) }
            return OkHttpClient.Builder()
                .sslSocketFactory(ssl.socketFactory, tm)
                // A confirmed home-server certificate often names the server by its local address: accept the name
                // only for that exact certificate; any other certificate needs the normal name check.
                .hostnameVerifier { host, session ->
                    OkHostnameVerifier.verify(host, session) || (trusted != null && runCatching {
                        sha256(session.peerCertificates[0] as X509Certificate).equals(trusted, ignoreCase = true)
                    }.getOrDefault(false))
                }
                .connectTimeout(20, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).writeTimeout(120, TimeUnit.SECONDS)
                .build()
        }

        /** The files (not folders) in a PROPFIND Depth 1 answer, without the folder itself. */
        fun parseListing(xml: String, folderPath: String): List<RemoteFile> {
            if (xml.isBlank()) return emptyList()
            val doc = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }.newDocumentBuilder()
                .parse(xml.byteInputStream())
            val responses = doc.getElementsByTagNameNS("DAV:", "response")
            val folderDecoded = URLDecoder.decode(folderPath, "UTF-8").trimEnd('/')
            return (0 until responses.length).mapNotNull { i ->
                val r = responses.item(i) as Element
                val href = r.getElementsByTagNameNS("DAV:", "href").item(0)?.textContent?.trim() ?: return@mapNotNull null
                val path = URLDecoder.decode(href.substringAfter("://").let { if (href.contains("://")) "/" + it.substringAfter('/') else href }, "UTF-8").trimEnd('/')
                if (path == folderDecoded) return@mapNotNull null
                val isFolder = r.getElementsByTagNameNS("DAV:", "collection").length > 0
                if (isFolder) return@mapNotNull null
                val size = r.getElementsByTagNameNS("DAV:", "getcontentlength").item(0)?.textContent?.trim()?.toLongOrNull() ?: -1
                RemoteFile(path.substringAfterLast('/'), size)
            }
        }
    }
}
