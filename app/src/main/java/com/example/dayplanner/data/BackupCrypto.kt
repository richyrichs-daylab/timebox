package com.example.dayplanner.data

import android.util.Base64
import org.json.JSONObject
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Verschlüsselt Backups mit AES-256-GCM. Der Schlüssel wird per PBKDF2 aus dem Passwort abgeleitet. */
object BackupCrypto {
    private const val ITERATIONS = 120_000

    fun newSalt(): ByteArray = ByteArray(16).also { SecureRandom().nextBytes(it) }

    fun deriveKey(password: String, salt: ByteArray, iterations: Int = ITERATIONS): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, 256)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    }

    fun isEncrypted(text: String): Boolean =
        runCatching { JSONObject(text).optInt("enc", 0) == 1 }.getOrDefault(false)

    fun encrypt(plain: String, salt: ByteArray, key: ByteArray): String {
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        val data = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return JSONObject()
            .put("app", "dayplanner")
            .put("enc", 1)
            .put("kdf", "pbkdf2-sha256")
            .put("iter", ITERATIONS)
            .put("salt", Base64.encodeToString(salt, Base64.NO_WRAP))
            .put("iv", Base64.encodeToString(iv, Base64.NO_WRAP))
            .put("data", Base64.encodeToString(data, Base64.NO_WRAP))
            .toString()
    }

    /** Gibt null zurück, wenn das Passwort falsch oder die Datei beschädigt ist. */
    fun decrypt(text: String, password: String): String? = try {
        val o = JSONObject(text)
        val salt = Base64.decode(o.getString("salt"), Base64.NO_WRAP)
        val iv = Base64.decode(o.getString("iv"), Base64.NO_WRAP)
        val data = Base64.decode(o.getString("data"), Base64.NO_WRAP)
        val iterations = o.optInt("iter", ITERATIONS).coerceIn(10_000, 2_000_000)
        val key = deriveKey(password, salt, iterations)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        String(cipher.doFinal(data), Charsets.UTF_8)
    } catch (e: Exception) {
        null
    }
}
