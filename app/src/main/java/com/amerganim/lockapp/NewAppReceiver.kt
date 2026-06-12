package com.amerganim.lockapp

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat

/** When auto-lock is on, locks any newly installed launchable app. */
class NewAppReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_PACKAGE_ADDED) return
        // Ignore app updates (a replace), only act on genuinely new installs.
        if (intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) return

        val pkg = intent.data?.schemeSpecificPart ?: return
        if (pkg == context.packageName) return

        val prefs = LockPrefs(context)
        if (!prefs.autoLockNewApps) return
        // Only lock apps that can actually be launched (skip libraries/services).
        if (context.packageManager.getLaunchIntentForPackage(pkg) == null) return

        prefs.setLocked(pkg, true)
        notifyLocked(context, pkg)
    }

    private fun notifyLocked(context: Context, pkg: String) {
        val label = runCatching {
            val pm = context.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        }.getOrDefault(pkg)

        val manager = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.autolock_channel_name),
                    NotificationManager.IMPORTANCE_DEFAULT
                )
            )
        }
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_lock)
            .setContentTitle(context.getString(R.string.autolock_notif_title))
            .setContentText(context.getString(R.string.autolock_notif_text, label))
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        manager.notify(pkg.hashCode(), notification)
    }

    companion object {
        private const val CHANNEL_ID = "auto_lock_new"
    }
}
