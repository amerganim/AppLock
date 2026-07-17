package com.amerganim.lockapp

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Foreground service that polls which app is in the foreground and pops the lock
 * screen whenever a protected app is opened.
 */
class AppLockService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var running = false

    private lateinit var usageStatsManager: UsageStatsManager
    private lateinit var prefs: LockPrefs


    override fun onCreate() {
        super.onCreate()
        usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        prefs = LockPrefs(this)
        startForeground(NOTIF_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!running) {
            running = true
            scope.launch {
                while (isActive) {
                    runCatching { tick() }
                    delay(POLL_INTERVAL_MS)
                }
            }
        }
        return START_STICKY
    }

    private fun tick() {
        val current = foregroundPackage() ?: return

        // Keep the current app's unlock fresh and re-lock apps left longer than the delay.
        LockState.onTick(current, prefs.relockDelayMs)

        // User-chosen app locks honour the master Protection switch (and the optional
        // scheduled pause); the anti-uninstall lock on system screens always applies
        // whenever tamper protection is enabled.
        val userLocked = prefs.protectionEnabled && !prefs.isLockingPausedNow() &&
            prefs.isLocked(current)
        val systemLocked = prefs.antiUninstallEnabled &&
            LockPrefs.PROTECTED_SYSTEM_PACKAGES.contains(current)

        if (shouldShowLockScreen(
                lockScreenActive = LockState.lockScreenActive,
                isOwnPackage = current == packageName,
                userLocked = userLocked,
                systemLocked = systemLocked,
                isUnlocked = LockState.isUnlocked(current),
            )
        ) {
            showLockScreen(current)
        }
    }

    /** The most recently resumed package within the recent time window. */
    private fun foregroundPackage(): String? {
        val end = System.currentTimeMillis()
        val begin = end - LOOKBACK_MS
        val events = usageStatsManager.queryEvents(begin, end)
        val event = UsageEvents.Event()
        var pkg: String? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                pkg = event.packageName
            }
        }
        return pkg
    }

    private fun showLockScreen(pkg: String) {
        LockState.lockScreenActive = true
        val intent = Intent(this, LockScreenActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(LockScreenActivity.EXTRA_PACKAGE, pkg)
        }
        startActivity(intent)
    }

    private fun buildNotification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notif_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
            manager.createNotificationChannel(channel)
        }
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_lock)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(getString(R.string.notif_text))
            .setContentIntent(openApp)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        running = false
        scope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        /**
         * Pure decision for whether the lock screen should be (re)launched over [current]
         * on a given poll tick. Extracted so it can be unit-tested without Android context.
         *
         * [lockScreenActive] must reflect whether the lock screen is *currently visible*
         * (tracked via its onStart/onStop). It is deliberately not a "was ever shown" flag:
         * if it were, switching to the protected app through the Recents/Overview switcher —
         * which stops but does not destroy the lock screen — would keep this returning false
         * and let the app be used unlocked.
         */
        fun shouldShowLockScreen(
            lockScreenActive: Boolean,
            isOwnPackage: Boolean,
            userLocked: Boolean,
            systemLocked: Boolean,
            isUnlocked: Boolean,
        ): Boolean {
            // Ignore our own UI (settings + the lock screen itself run in our package).
            if (isOwnPackage) return false
            // The lock screen is already on top — don't stack duplicates.
            if (lockScreenActive) return false
            return (userLocked || systemLocked) && !isUnlocked
        }

        private const val CHANNEL_ID = "app_lock_service"
        private const val NOTIF_ID = 1
        // Poll fast enough that the lock screen appears near-instantly when a protected app
        // is opened. Lower is snappier but costs more battery; ~200ms is a good balance.
        private const val POLL_INTERVAL_MS = 200L
        private const val LOOKBACK_MS = 10_000L

        /** Start (or no-op if already running) the protection service. */
        fun start(context: Context) {
            val intent = Intent(context, AppLockService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, AppLockService::class.java))
        }
    }
}
