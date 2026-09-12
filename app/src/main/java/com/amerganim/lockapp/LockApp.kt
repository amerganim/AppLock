package com.amerganim.lockapp

import android.app.Activity
import android.app.Application
import android.os.Bundle
import androidx.appcompat.app.AppCompatDelegate

/**
 * Applies the saved theme as early as possible on process start, and tracks whether
 * any of our own screens is visible so the authenticated session ([LockState]) can end
 * the moment the app as a whole leaves the foreground.
 */
class LockApp : Application() {

    override fun onCreate() {
        super.onCreate()
        AppCompatDelegate.setDefaultNightMode(LockPrefs(this).themeMode)
        // Vault previews are decrypted into the cache while viewing and wiped on exit;
        // if the process was killed mid-view they would otherwise linger in the clear.
        VaultManager.clearViewCache(this)
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) = LockState.onActivityStarted()
            override fun onActivityStopped(activity: Activity) = LockState.onActivityStopped()

            override fun onActivityCreated(activity: Activity, state: Bundle?) = Unit
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, out: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }
}
