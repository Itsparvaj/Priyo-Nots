package com.example.data.backup

import android.util.Base64
import org.json.JSONObject
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object BackupCrypto {

    private const val MAGIC = "PRIYO_NOTS_ENC_V1"
    private const val ITERATIONS = 120_000
    private const val KEY_LENGTH_BITS = 256
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val GCM_TAG_BITS = 128

    fun encryptPayload(plainJson: String, password: String): String {
        require(password.isNotEmpty()) { "Password cannot be empty for encrypted backup" }
        val random = SecureRandom()
        val salt = ByteArray(SALT_BYTES).also { random.nextBytes(it) }
        val iv = ByteArray(IV_BYTES).also { random.nextBytes(it) }

        val keySpec = deriveKey(password, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, GCMParameterSpec(GCM_TAG_BITS, iv))
        val cipherBytes = cipher.doFinal(plainJson.toByteArray(Charsets.UTF_8))

        val envelope = JSONObject().apply {
            put("format", MAGIC)
            put("encrypted", true)
            put("kdf", "PBKDF2WithHmacSHA256")
            put("iterations", ITERATIONS)
            put("salt", Base64.encodeToString(salt, Base64.NO_WRAP))
            put("iv", Base64.encodeToString(iv, Base64.NO_WRAP))
            put("ciphertext", Base64.encodeToString(cipherBytes, Base64.NO_WRAP))
        }
        return envelope.toString(2)
    }

    fun isEncryptedEnvelope(rawContent: String): Boolean {
        return runCatching {
            val obj = JSONObject(rawContent)
            obj.optBoolean("encrypted", false) && obj.optString("format") == MAGIC
        }.getOrDefault(false)
    }

    fun decryptPayload(rawEnvelope: String, password: String): String {
        val obj = JSONObject(rawEnvelope)
        if (obj.optString("format") != MAGIC || !obj.optBoolean("encrypted", false)) {
            throw IllegalArgumentException("Unsupported encrypted backup format")
        }
        val iterations = obj.optInt("iterations", ITERATIONS).coerceIn(10_000, 500_000)
        val salt = Base64.decode(obj.getString("salt"), Base64.NO_WRAP)
        val iv = Base64.decode(obj.getString("iv"), Base64.NO_WRAP)
        val cipherBytes = Base64.decode(obj.getString("ciphertext"), Base64.NO_WRAP)

        val keySpec = deriveKey(password, salt, iterations)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, keySpec, GCMParameterSpec(GCM_TAG_BITS, iv))
        val plainBytes = cipher.doFinal(cipherBytes)
        return String(plainBytes, Charsets.UTF_8)
    }

    private fun deriveKey(password: String, salt: ByteArray, iterations: Int = ITERATIONS): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, KEY_LENGTH_BITS)
        val keyBytes = factory.generateSecret(spec).encoded
        return SecretKeySpec(keyBytes, "AES")
    }
}
