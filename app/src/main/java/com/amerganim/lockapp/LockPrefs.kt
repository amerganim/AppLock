package com.amerganim.lockapp

import android.annotation.SuppressLint
import android.content.Context

/** Non-secret settings: which packages are locked, and whether protection is enabled. */
class LockPrefs(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("app_lock_prefs", Context.MODE_PRIVATE)

    fun getLockedApps(): Set<String> =
        // getStringSet returns a shared instance; copy it before use.
        HashSet(prefs.getStringSet(KEY_LOCKED, emptySet()) ?: emptySet())

    fun isLocked(pkg: String): Boolean = getLockedApps().contains(pkg)

    fun setLocked(pkg: String, locked: Boolean) {
        val current = getLockedApps().toMutableSet()
        if (locked) current.add(pkg) else current.remove(pkg)
        prefs.edit().putStringSet(KEY_LOCKED, current).apply()
    }

    var protectionEnabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_ENABLED, value).apply()

    /** When on, the Settings app and package installers are locked too (anti-tamper). */
    var antiUninstallEnabled: Boolean
        get() = prefs.getBoolean(KEY_ANTI_UNINSTALL, false)
        set(value) = prefs.edit().putBoolean(KEY_ANTI_UNINSTALL, value).apply()

    /** When on (and hardware is enrolled), the lock screen offers fingerprint/face unlock. */
    var biometricEnabled: Boolean
        get() = prefs.getBoolean(KEY_BIOMETRIC, true)
        set(value) = prefs.edit().putBoolean(KEY_BIOMETRIC, value).apply()

    /** How long an unlocked app stays unlocked after you leave it, in milliseconds. */
    var relockDelayMs: Long
        get() = prefs.getLong(KEY_RELOCK_DELAY, 0L)
        set(value) = prefs.edit().putLong(KEY_RELOCK_DELAY, value).apply()

    /** Theme: AppCompatDelegate night mode (-1 follow system, 1 light, 2 dark). */
    var themeMode: Int
        get() = prefs.getInt(KEY_THEME, MODE_FOLLOW_SYSTEM)
        set(value) = prefs.edit().putInt(KEY_THEME, value).apply()

    /** Randomize the PIN keypad layout to resist shoulder-surfing. */
    var scrambleKeypad: Boolean
        get() = prefs.getBoolean(KEY_SCRAMBLE, false)
        set(value) = prefs.edit().putBoolean(KEY_SCRAMBLE, value).apply()

    /** Automatically lock any newly installed app. */
    var autoLockNewApps: Boolean
        get() = prefs.getBoolean(KEY_AUTOLOCK_NEW, false)
        set(value) = prefs.edit().putBoolean(KEY_AUTOLOCK_NEW, value).apply()

    /** Capture a front-camera photo after repeated wrong unlock attempts. */
    var intruderSelfieEnabled: Boolean
        get() = prefs.getBoolean(KEY_INTRUDER, false)
        set(value) = prefs.edit().putBoolean(KEY_INTRUDER, value).apply()

    /** After importing into the vault, offer to delete the originals from the gallery. */
    var vaultRemoveOriginal: Boolean
        get() = prefs.getBoolean(KEY_VAULT_RM_ORIG, false)
        set(value) = prefs.edit().putBoolean(KEY_VAULT_RM_ORIG, value).apply()

    /** Show a fake "app keeps stopping" screen over the lock; long-press reveals it. */
    var fakeCoverEnabled: Boolean
        get() = prefs.getBoolean(KEY_FAKE_COVER, false)
        set(value) = prefs.edit().putBoolean(KEY_FAKE_COVER, value).apply()

    /** Which launcher alias (icon/label disguise) is active. */
    var disguiseAlias: String
        get() = prefs.getString(KEY_DISGUISE, "LauncherDefault") ?: "LauncherDefault"
        set(value) = prefs.edit().putString(KEY_DISGUISE, value).apply()

    /** Pause app locking during a daily time window (e.g. when you're at home). */
    var scheduleEnabled: Boolean
        get() = prefs.getBoolean(KEY_SCHED_ON, false)
        set(value) = prefs.edit().putBoolean(KEY_SCHED_ON, value).apply()

    /** Window start/end as minutes since midnight. */
    var scheduleStartMinutes: Int
        get() = prefs.getInt(KEY_SCHED_START, 22 * 60)
        set(value) = prefs.edit().putInt(KEY_SCHED_START, value).apply()

    var scheduleEndMinutes: Int
        get() = prefs.getInt(KEY_SCHED_END, 6 * 60)
        set(value) = prefs.edit().putInt(KEY_SCHED_END, value).apply()

    // ---- Wrong-attempt throttling (see AttemptGuard) ----

    fun failedAttempts(scope: AttemptScope): Int = prefs.getInt(keyFailures(scope), 0)

    @SuppressLint("ApplySharedPref")
    fun setFailedAttempts(scope: AttemptScope, value: Int) {
        // commit(): the counter must survive an immediate force-stop of the app.
        prefs.edit().putInt(keyFailures(scope), value).commit()
    }

    fun cooldownUntil(scope: AttemptScope): Long = prefs.getLong(keyCooldown(scope), 0L)

    @SuppressLint("ApplySharedPref")
    fun setCooldownUntil(scope: AttemptScope, value: Long) {
        // commit(): a cooldown must survive an immediate force-stop of the app.
        prefs.edit().putLong(keyCooldown(scope), value).commit()
    }

    private fun keyFailures(scope: AttemptScope) = "failed_attempts_${scope.name.lowercase()}"
    private fun keyCooldown(scope: AttemptScope) = "cooldown_until_${scope.name.lowercase()}"

    /** True if locking is currently paused by the schedule (handles overnight windows). */
    fun isLockingPausedNow(): Boolean {
        if (!scheduleEnabled) return false
        val cal = java.util.Calendar.getInstance()
        val now = cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE)
        val start = scheduleStartMinutes
        val end = scheduleEndMinutes
        return if (start <= end) now in start until end else now >= start || now < end
    }

    companion object {
        private const val KEY_LOCKED = "locked_apps"
        private const val KEY_ENABLED = "protection_enabled"
        private const val KEY_ANTI_UNINSTALL = "anti_uninstall"
        private const val KEY_BIOMETRIC = "biometric_enabled"
        private const val KEY_RELOCK_DELAY = "relock_delay_ms"
        private const val KEY_THEME = "theme_mode"
        private const val KEY_SCRAMBLE = "scramble_keypad"
        private const val KEY_AUTOLOCK_NEW = "autolock_new_apps"
        private const val KEY_INTRUDER = "intruder_selfie"
        private const val KEY_DISGUISE = "disguise_alias"
        private const val KEY_FAKE_COVER = "fake_cover"
        private const val KEY_VAULT_RM_ORIG = "vault_remove_original"
        private const val KEY_SCHED_ON = "schedule_enabled"
        private const val KEY_SCHED_START = "schedule_start_min"
        private const val KEY_SCHED_END = "schedule_end_min"

        /** Wrong attempts before an intruder selfie is captured. */
        const val INTRUDER_THRESHOLD = 3

        const val MODE_FOLLOW_SYSTEM = -1

        /**
         * System screens that lead to uninstalling / disabling AppLock. Locking these
         * forces a PIN before someone can reach Uninstall, Force-stop or Clear-data.
         */
        val PROTECTED_SYSTEM_PACKAGES: Set<String> = setOf(
            "com.android.settings",
            "com.android.packageinstaller",
            "com.google.android.packageinstaller",
            "com.android.vending" // Play Store (also offers uninstall)
        )
    }
}
