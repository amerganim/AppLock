package com.amerganim.lockapp

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate

/** Applies the saved theme as early as possible on process start. */
class LockApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppCompatDelegate.setDefaultNightMode(LockPrefs(this).themeMode)
    }
}
