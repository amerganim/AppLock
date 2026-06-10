package com.amerganim.lockapp

import android.app.admin.DeviceAdminReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent

/**
 * Device administrator receiver. While AppLock is an active device admin, Android
 * blocks it from being uninstalled until the admin is deactivated — and the
 * "deactivate" / app-info screens live in Settings, which anti-uninstall locks.
 */
class AdminReceiver : DeviceAdminReceiver() {

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        // Shown on the system "deactivate admin" confirmation screen.
        return context.getString(R.string.admin_disable_warning)
    }

    companion object {
        fun component(context: Context): ComponentName =
            ComponentName(context.applicationContext, AdminReceiver::class.java)
    }
}
