package com.astrawave.app.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Encrypted credential storage for user-owned personal media connections.
 * Secrets are encrypted with an Android Keystore AES/GCM key before persistence.
 */
class PersonalMediaCredentialStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun saveToken(connectionId: String, token: String) {
        require(connectionId.isNotBlank()) { "Connection id is required" }
        require(token.isNotBlank()) { "Token is required" }
        prefs.edit().putString(tokenKey(connectionId), encrypt(token)).apply()
    }

    fun loadToken(connectionId: String): String? = prefs.getString(tokenKey(connectionId), null)
        ?.let { runCatching { decrypt(it) }.getOrNull() }

    fun saveUsername(connectionId: String, username: String) {
        require(connectionId.isNotBlank()) { "Connection id is required" }
        require(username.isNotBlank()) { "Username is required" }
        prefs.edit().putString(usernameKey(connectionId), encrypt(username)).apply()
    }

    fun loadUsername(connectionId: String): String? = prefs.getString(usernameKey(connectionId), null)
        ?.let { runCatching { decrypt(it) }.getOrNull() }

    fun savePassword(connectionId: String, password: String) {
        require(connectionId.isNotBlank()) { "Connection id is required" }
        require(password.isNotBlank()) { "Password is required" }
        prefs.edit().putString(passwordKey(connectionId), encrypt(password)).apply()
    }

    fun loadPassword(connectionId: String): String? = prefs.getString(passwordKey(connectionId), null)
        ?.let { runCatching { decrypt(it) }.getOrNull() }

    fun hasCredential(connectionId: String): Boolean =
        prefs.contains(tokenKey(connectionId)) ||
            prefs.contains(usernameKey(connectionId)) ||
            prefs.contains(passwordKey(connectionId))

    fun clear(connectionId: String) {
        prefs.edit()
            .remove(tokenKey(connectionId))
            .remove(usernameKey(connectionId))
            .remove(passwordKey(connectionId))
            .apply()
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
        val ciphertext = Base64.encodeToString(cipher.doFinal(value.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
        return "$iv:$ciphertext"
    }

    private fun decrypt(value: String): String {
        val parts = value.split(':', limit = 2)
        require(parts.size == 2) { "Invalid encrypted personal media credential" }
        val iv = Base64.decode(parts[0], Base64.NO_WRAP)
        val ciphertext = Base64.decode(parts[1], Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
        return cipher.doFinal(ciphertext).toString(Charsets.UTF_8)
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build(),
        )
        return generator.generateKey()
    }

    private fun tokenKey(connectionId: String) = "token_$connectionId"
    private fun usernameKey(connectionId: String) = "username_$connectionId"
    private fun passwordKey(connectionId: String) = "password_$connectionId"

    private companion object {
        const val PREFS_NAME = "astrawave_personal_media_credentials"
        const val KEY_ALIAS = "astrawave_personal_media_credentials_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
