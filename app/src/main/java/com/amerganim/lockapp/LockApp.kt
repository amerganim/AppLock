package com.amerganim.lockapp

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.UserManager
import androidx.appcompat.app.AppCompatDelegate

/**
 * Applies the saved theme as early as possible on process start, and tracks whether
 * any of our own screens is visible so the authenticated session ([LockState]) can end
 * the moment the app as a whole leaves the foreground.
 */
class LockApp : Application() {

    private var startedWithStorage = false

    override fun onCreate() {
        super.onCreate()
        // After a reboot this process can start before the user has unlocked the device,
        // to get protection running early. Credential-encrypted storage is unreadable
        // until they do, so anything that touches settings or the cache waits.
        initWhenStorageIsReady()
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) = LockState.onActivityStarted()
            override fun onActivityStopped(activity: Activity) = LockState.onActivityStopped()

            override fun onActivityCreated(activity: Activity, state: Bundle?) {
                // An activity means the user is past the keyguard, so if the process
                // started during direct boot this is the moment the theme can be applied.
                initWhenStorageIsReady()
                activity.applySystemBarInsets()
            }
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, out: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }

    private fun initWhenStorageIsReady() {
        if (startedWithStorage || !isUserUnlocked()) return
        startedWithStorage = true
        val prefs = LockPrefs(this)
        AppCompatDelegate.setDefaultNightMode(prefs.themeMode)
        // Catches settings changed by an older version that had no device-protected copy.
        prefs.syncBootMirror()
        // Vault previews are decrypted into the cache while viewing and wiped on exit;
        // if the process was killed mid-view they would otherwise linger in the clear.
        VaultManager.clearViewCache(this)
    }

    private fun isUserUnlocked(): Boolean =
        getSystemService(UserManager::class.java)?.isUserUnlocked ?: true
}
