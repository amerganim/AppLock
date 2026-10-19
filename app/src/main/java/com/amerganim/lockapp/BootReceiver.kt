package com.amerganim.lockapp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Restarts protection after the device reboots — and after LockApp itself is updated,
 * which also stops the monitoring service.
 *
 * The receiver is direct-boot aware so it is woken by ACTION_LOCKED_BOOT_COMPLETED, which
 * reaches the handful of direct-boot-aware apps as soon as the system is up. Waiting for
 * ACTION_BOOT_COMPLETED was measurably too late: every app on the device receives that
 * one, and on a low-end phone our turn came 68 seconds after the system sent it — a full
 * minute during which a locked app opened unprotected, which is exactly what users saw
 * as "the lock does not work the first time after switching the phone on".
 *
 * Whether protection is wanted is read from device-protected storage, because the user
 * may not have unlocked the device yet and the ordinary settings are unreadable until
 * they do.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                // Deliberately start rather than sync: a permission check this early can
                // come back false while PackageManager is still settling (the boot log
                // shows our permissions being un-granted and re-granted), and sync would
                // then stop protection instead of starting it. The service re-checks its
                // own permissions every few seconds and says so in its notification.
                if (LockPrefs.protectionNeededAtBoot(context)) AppLockService.start(context)
            }
        }
    }
}
