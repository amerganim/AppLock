package com.example.applock

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Stores and verifies the user's PIN.
 *
 * The PIN itself is never stored: we keep a random salt plus a SHA-256 hash of
 * (salt + pin), and the whole file is additionally encrypted at rest via
 * [EncryptedSharedPreferences].
 */
class PinManager(context: Context) {

    private val prefs: SharedPreferences = run {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "secure_pin_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun isPinSet(): Boolean = prefs.contains(KEY_HASH)

    fun setPin(pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        prefs.edit()
            .putString(KEY_SALT, salt.toBase64())
            .putString(KEY_HASH, hash(pin, salt))
            .apply()
    }

    fun verify(pin: String): Boolean {
        val saltStr = prefs.getString(KEY_SALT, null) ?: return false
        val expected = prefs.getString(KEY_HASH, null) ?: return false
        val actual = hash(pin, saltStr.fromBase64())
        // Constant-time comparison.
        return MessageDigest.isEqual(actual.toByteArray(), expected.toByteArray())
    }

    private fun hash(pin: String, salt: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(salt)
        return md.digest(pin.toByteArray(Charsets.UTF_8)).toBase64()
    }

    private fun ByteArray.toBase64() = Base64.encodeToString(this, Base64.NO_WRAP)
    private fun String.fromBase64() = Base64.decode(this, Base64.NO_WRAP)

    companion object {
        private const val KEY_SALT = "pin_salt"
        private const val KEY_HASH = "pin_hash"
        const val PIN_LENGTH = 4
    }
}
