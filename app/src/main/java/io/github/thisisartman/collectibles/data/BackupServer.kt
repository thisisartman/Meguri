package io.github.thisisartman.collectibles.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/** The backup server: folder address, user name, a certificate the user trusted, and the password, encrypted. */
data class ServerLogin(val url: String, val user: String, val password: String, val trusted: String?) {
    fun client() = WebDav(url, user, password, trusted)
}

/**
 * Where the server login is kept. The password is encrypted with a key that lives in the phone's keystore and never
 * leaves it; Android backup is off for the app, so none of this is copied anywhere.
 */
@Singleton
class BackupServer @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("backup_server", Context.MODE_PRIVATE)

    val login: ServerLogin?
        get() {
            val url = prefs.getString(KEY_URL, null) ?: return null
            val pw = runCatching { decrypt(prefs.getString(KEY_PASSWORD, null) ?: return null) }.getOrNull() ?: return null
            return ServerLogin(url, prefs.getString(KEY_USER, "").orEmpty(), pw, prefs.getString(KEY_TRUSTED, null))
        }

    /** The server's address without the password, for showing on the Backup page. */
    val address: String? get() = prefs.getString(KEY_URL, null)

    fun save(login: ServerLogin) {
        prefs.edit().putString(KEY_URL, WebDav.normalize(login.url).toString()).putString(KEY_USER, login.user)
            .putString(KEY_PASSWORD, encrypt(login.password)).putString(KEY_TRUSTED, login.trusted).apply()
    }

    fun forget() {
        prefs.edit().clear().apply()
        runCatching { KeyStore.getInstance(STORE).apply { load(null) }.deleteEntry(ALIAS) }
    }

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance(STORE).apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, STORE).apply {
            init(
                KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256).build(),
            )
        }.generateKey()
    }

    private fun encrypt(text: String): String {
        val c = Cipher.getInstance(CIPHER).apply { init(Cipher.ENCRYPT_MODE, key()) }
        return Base64.encodeToString(c.iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(c.doFinal(text.toByteArray()), Base64.NO_WRAP)
    }

    private fun decrypt(stored: String): String {
        val (iv, data) = stored.split(":", limit = 2)
        val c = Cipher.getInstance(CIPHER).apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP))) }
        return String(c.doFinal(Base64.decode(data, Base64.NO_WRAP)))
    }

    private companion object {
        const val STORE = "AndroidKeyStore"
        const val ALIAS = "meguri_backup_server"
        const val CIPHER = "AES/GCM/NoPadding"
        const val KEY_URL = "url"
        const val KEY_USER = "user"
        const val KEY_PASSWORD = "password"
        const val KEY_TRUSTED = "trusted"
    }
}
