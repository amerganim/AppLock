package com.amerganim.lockapp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Restarts protection after the device reboots — and after LockApp itself is updated,
 * which also stops the monitoring service.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> AppLockService.sync(context)
        }
    }
}
