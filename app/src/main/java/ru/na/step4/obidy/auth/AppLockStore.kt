package ru.na.step4.obidy.auth

import android.content.Context
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

class AppLockStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    val isConfigured: Boolean
        get() = prefs.contains(KEY_HASH) && prefs.contains(KEY_SALT)

    var biometricEnabled: Boolean
        get() = prefs.getBoolean(KEY_BIOMETRIC, true)
        set(value) = prefs.edit().putBoolean(KEY_BIOMETRIC, value).apply()

    fun setPassword(password: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val hash = hashPassword(password, salt)
        prefs.edit()
            .putString(KEY_SALT, encode(salt))
            .putString(KEY_HASH, encode(hash))
            .apply()
    }

    fun verifyPassword(password: String): Boolean {
        val salt = decode(prefs.getString(KEY_SALT, null) ?: return false)
        val expected = decode(prefs.getString(KEY_HASH, null) ?: return false)
        val actual = hashPassword(password, salt)
        return MessageDigest.isEqual(expected, actual)
    }

    companion object {
        private const val PREFS = "app_lock"
        private const val KEY_SALT = "salt"
        private const val KEY_HASH = "hash"
        private const val KEY_BIOMETRIC = "biometric_enabled"
        private const val ITERATIONS = 120_000
        private const val KEY_LENGTH = 256

        private fun hashPassword(password: String, salt: ByteArray): ByteArray {
            val spec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_LENGTH)
            return try {
                SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(spec)
                    .encoded
            } finally {
                spec.clearPassword()
            }
        }

        private fun encode(bytes: ByteArray): String =
            Base64.encodeToString(bytes, Base64.NO_WRAP)

        private fun decode(value: String): ByteArray =
            Base64.decode(value, Base64.NO_WRAP)
    }
}
