package com.amerganim.lockapp

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.MessageDigest
import java.security.SecureRandom

/** Whether the user unlocks with a numeric PIN or a swipe pattern. */
enum class LockType { PIN, PATTERN }

/**
 * Stores and verifies the user's unlock credential (PIN or pattern) and an
 * optional recovery question/answer used to reset a forgotten credential.
 *
 * Nothing is stored in the clear: each secret is kept as a random-salted SHA-256
 * hash, and the whole file is additionally encrypted at rest via
 * [EncryptedSharedPreferences].
 *
 * A PIN is 4 to 8 digits; a pattern is encoded as its connected dot indices joined by
 * '-', e.g. "0-1-2-5".
 */
class CredentialManager(context: Context) {

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

    // ---- Unlock credential ----

    fun isCredentialSet(): Boolean = prefs.contains(KEY_HASH)

    fun lockType(): LockType =
        runCatching { LockType.valueOf(prefs.getString(KEY_TYPE, null) ?: "") }
            .getOrDefault(LockType.PIN)

    fun setCredential(type: LockType, value: String) {
        val salt = newSalt()
        val editor = prefs.edit()
            .putString(KEY_TYPE, type.name)
            .putString(KEY_SALT, salt.toBase64())
            .putString(KEY_HASH, hash(value, salt))
        // Remember how long the PIN is, so unlocking can check it the moment it is
        // complete instead of needing an extra key. It is written into the same
        // encrypted file as the hash, so the length is not readable off the device.
        if (type == LockType.PIN) editor.putInt(KEY_PIN_LENGTH, value.length)
        editor.apply()
    }

    /**
     * Digits in the saved PIN. Credentials saved before PINs could vary in length are
     * all [PIN_MIN_LENGTH] digits, which is exactly what the default gives.
     */
    fun pinLength(): Int = prefs.getInt(KEY_PIN_LENGTH, PIN_MIN_LENGTH)
        .coerceIn(PIN_MIN_LENGTH, PIN_MAX_LENGTH)

    fun verify(value: String): Boolean {
        val salt = prefs.getString(KEY_SALT, null)?.fromBase64() ?: return false
        val expected = prefs.getString(KEY_HASH, null) ?: return false
        return MessageDigest.isEqual(hash(value, salt).toByteArray(), expected.toByteArray())
    }

    // ---- Recovery ----

    fun isRecoverySet(): Boolean = prefs.contains(KEY_REC_HASH)

    fun recoveryQuestion(): String? = prefs.getString(KEY_REC_Q, null)

    fun setRecovery(question: String, answer: String) {
        val salt = newSalt()
        prefs.edit()
            .putString(KEY_REC_Q, question)
            .putString(KEY_REC_SALT, salt.toBase64())
            .putString(KEY_REC_HASH, hash(answer.normalize(), salt))
            .apply()
    }

    fun verifyRecovery(answer: String): Boolean {
        val salt = prefs.getString(KEY_REC_SALT, null)?.fromBase64() ?: return false
        val expected = prefs.getString(KEY_REC_HASH, null) ?: return false
        return MessageDigest.isEqual(
            hash(answer.normalize(), salt).toByteArray(),
            expected.toByteArray()
        )
    }

    // ---- internals ----

    private fun hash(value: String, salt: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(salt)
        return md.digest(value.toByteArray(Charsets.UTF_8)).toBase64()
    }

    private fun newSalt() = ByteArray(16).also { SecureRandom().nextBytes(it) }
    private fun String.normalize() = trim().lowercase()
    private fun ByteArray.toBase64() = Base64.encodeToString(this, Base64.NO_WRAP)
    private fun String.fromBase64() = Base64.decode(this, Base64.NO_WRAP)

    companion object {
        private const val KEY_TYPE = "lock_type"
        private const val KEY_SALT = "pin_salt"
        private const val KEY_HASH = "pin_hash"
        private const val KEY_REC_Q = "recovery_question"
        private const val KEY_REC_SALT = "recovery_salt"
        private const val KEY_REC_HASH = "recovery_hash"
        private const val KEY_PIN_LENGTH = "pin_length"

        const val PIN_MIN_LENGTH = 4
        const val PIN_MAX_LENGTH = 8
        const val MIN_PATTERN_DOTS = 4

        fun isValidPinLength(length: Int): Boolean =
            length in PIN_MIN_LENGTH..PIN_MAX_LENGTH
    }
}
