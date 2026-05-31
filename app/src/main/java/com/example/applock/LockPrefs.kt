package com.example.applock

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

    companion object {
        private const val KEY_LOCKED = "locked_apps"
        private const val KEY_ENABLED = "protection_enabled"
    }
}
