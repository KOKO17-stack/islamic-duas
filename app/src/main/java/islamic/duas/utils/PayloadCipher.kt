package islamic.duas.utils

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object PayloadCipher {

    private const val KEY_STRING = "D3vSync!Enc#2024"
    private const val GCM_TAG_LENGTH = 128
    private const val IV_LENGTH = 12

    private val keyBytes: ByteArray by lazy {
        val raw = KEY_STRING.toByteArray(Charsets.UTF_8)
        val sha256 = java.security.MessageDigest.getInstance("SHA-256").digest(raw)
        sha256.copyOf(16)
    }

    fun encrypt(plaintext: String): String {
        val iv = ByteArray(IV_LENGTH).apply { SecureRandom().nextBytes(this) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(keyBytes, "AES"), spec)
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        val combined = iv + ciphertext
        return java.util.Base64.getEncoder().encodeToString(combined)
    }

    fun decrypt(encrypted: String): String? {
        return try {
            val combined = java.util.Base64.getDecoder().decode(encrypted)
            val iv = combined.copyOfRange(0, IV_LENGTH)
            val ciphertext = combined.copyOfRange(IV_LENGTH, combined.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(keyBytes, "AES"), spec)
            String(cipher.doFinal(ciphertext), Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    fun encryptBytes(plaintext: ByteArray): ByteArray {
        val iv = ByteArray(IV_LENGTH).apply { SecureRandom().nextBytes(this) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(keyBytes, "AES"), spec)
        val ciphertext = cipher.doFinal(plaintext)
        return iv + ciphertext
    }

    fun decryptBytes(encrypted: ByteArray): ByteArray? {
        return try {
            val iv = encrypted.copyOfRange(0, IV_LENGTH)
            val ciphertext = encrypted.copyOfRange(IV_LENGTH, encrypted.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(keyBytes, "AES"), spec)
            cipher.doFinal(ciphertext)
        } catch (_: Exception) {
            null
        }
    }
}
