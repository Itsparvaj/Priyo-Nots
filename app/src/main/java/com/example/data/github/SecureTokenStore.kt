package com.example.data.github

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SecureTokenStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun saveToken(token: String) {
        val cleaned = token.trim()
        if (cleaned.isEmpty()) {
            clearToken()
            return
        }
        val encrypted = encryptWithKeystore(cleaned)
        if (encrypted != null) {
            prefs.edit()
                .putString(KEY_CIPHER, encrypted.first)
                .putString(KEY_IV, encrypted.second)
                .apply()
        }
    }

    fun getToken(): String? {
        val cipherB64 = prefs.getString(KEY_CIPHER, null) ?: return null
        val ivB64 = prefs.getString(KEY_IV, null) ?: return null
        return decryptWithKeystore(cipherB64, ivB64)
    }

    fun hasToken(): Boolean = !getToken().isNullOrBlank()

    fun clearToken() {
        prefs.edit().remove(KEY_CIPHER).remove(KEY_IV).apply()
    }

    private fun getOrCreateKey(): SecretKey? {
        return runCatching {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
            if (existing != null) return@runCatching existing

            val keyGen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            val spec = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
            keyGen.init(spec)
            keyGen.generateKey()
        }.getOrNull()
    }

    private fun encryptWithKeystore(plain: String): Pair<String, String>? {
        val key = getOrCreateKey() ?: return null
        return runCatching {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key)
            val cipherBytes = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
            val cipherB64 = Base64.encodeToString(cipherBytes, Base64.NO_WRAP)
            val ivB64 = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
            cipherB64 to ivB64
        }.getOrNull()
    }

    private fun decryptWithKeystore(cipherB64: String, ivB64: String): String? {
        val key = getOrCreateKey() ?: return null
        return runCatching {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val iv = Base64.decode(ivB64, Base64.NO_WRAP)
            val cipherBytes = Base64.decode(cipherB64, Base64.NO_WRAP)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
            String(cipher.doFinal(cipherBytes), Charsets.UTF_8)
        }.getOrNull()
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "priyo_nots_github_pat_v1"
        private const val PREFS_NAME = "priyo_secure_tokens"
        private const val KEY_CIPHER = "github_pat_cipher"
        private const val KEY_IV = "github_pat_iv"
    }
}
