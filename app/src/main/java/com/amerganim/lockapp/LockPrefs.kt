package com.amerganim.lockapp

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

    companion object {
        private const val KEY_LOCKED = "locked_apps"
        private const val KEY_ENABLED = "protection_enabled"
        private const val KEY_ANTI_UNINSTALL = "anti_uninstall"
        private const val KEY_BIOMETRIC = "biometric_enabled"
        private const val KEY_RELOCK_DELAY = "relock_delay_ms"
        private const val KEY_THEME = "theme_mode"

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
