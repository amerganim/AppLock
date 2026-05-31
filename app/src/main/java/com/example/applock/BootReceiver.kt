package com.example.applock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Restarts protection after the device reboots, if it was enabled. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_LOCKED_BOOT_COMPLETED
        ) return

        val prefs = LockPrefs(context)
        val needed = prefs.protectionEnabled || prefs.antiUninstallEnabled
        if (needed && Permissions.hasRequired(context)) {
            AppLockService.start(context)
        }
    }
}
