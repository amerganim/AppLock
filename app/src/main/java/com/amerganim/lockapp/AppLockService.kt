package com.amerganim.lockapp

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.UserManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
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

    /**
     * Null until the user has unlocked the device after a reboot. The service starts
     * before that (see [BootReceiver]) so protection is ready the instant they do, but
     * the settings live in credential-encrypted storage and cannot be read until then.
     */
    private var prefs: LockPrefs? = null

    /**
     * Polling is pointless while the display is off, and costs battery. Kept only to
     * pace the loop — whether to do any work is read from the power manager on each
     * tick, because a cached flag can stick: if the service starts during boot in the
     * instant before the display is interactive, no ACTION_SCREEN_ON ever follows (the
     * screen was already coming on) and protection would stay asleep for the session.
     */
    @Volatile
    private var screenOn = true

    private val powerManager: PowerManager? by lazy { getSystemService(PowerManager::class.java) }

    private fun isInteractive(): Boolean = powerManager?.isInteractive ?: true

    /**
     * Timestamp of the newest usage event already processed, which is what each poll
     * reads forward from.
     *
     * Deliberately not the time of the last *query*. Events do not always become
     * readable the instant they happen — after a reboot, with the system still busy,
     * one can surface seconds late — and a window anchored to the clock will by then
     * have swept past that event's timestamp and lose it permanently, leaving the
     * service convinced the launcher is still in front. Anchoring to the last event
     * actually seen means the window simply widens until the event turns up.
     */
    private var lastEventTime = 0L

    /**
     * Last package seen moving to the foreground. Usage events only fire on a *change*,
     * so a poll that reads no event means "still the same app" — not "unknown". Relying
     * on a fixed lookback window instead used to stall the re-lock bookkeeping whenever
     * the user stayed in one app for longer than that window, which let a backgrounded
     * app keep its unlock past the configured delay.
     */
    private var lastForegroundPkg: String? = null
    private var lastForegroundClass: String? = null

    /** The home/launcher package, which also hosts the task switcher on most devices. */
    private var launcherPackage: String? = null

    /** Last lock-screen launch, so it is not launched again before it can come forward. */
    private var lastLaunchPkg: String? = null
    private var lastLaunchAt = 0L

    private var permissionsOk = true
    private var ticksSincePermissionCheck = PERMISSION_CHECK_TICKS

    /** Fires once, when the user first unlocks the device after a reboot. */
    private val userUnlockedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_USER_UNLOCKED) openCredentialStorage()
        }
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    screenOn = false
                    // Re-lock everything: an app left unlocked in the foreground when the
                    // display goes off would otherwise still be unlocked for whoever
                    // picks the device up next, because the re-lock delay only counts
                    // time spent in the background.
                    LockState.lockAll()
                }

                Intent.ACTION_SCREEN_ON -> {
                    screenOn = true
                    // Re-derive the foreground app from a fresh window of events.
                    lastEventTime = 0L
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        screenOn = isInteractive()
        openCredentialStorage()
        if (prefs == null) {
            ContextCompat.registerReceiver(
                this,
                userUnlockedReceiver,
                IntentFilter(Intent.ACTION_USER_UNLOCKED),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        }
        launcherPackage = resolveLauncherPackage()
        ContextCompat.registerReceiver(
            this,
            screenReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        // If the system refuses the foreground service there is nothing to keep alive;
        // stop cleanly instead of letting the process crash.
        val started = runCatching { startForeground(NOTIF_ID, buildNotification(healthy = true)) }
        if (started.isFailure) {
            Log.w(TAG, "Could not start in the foreground", started.exceptionOrNull())
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!running) {
            running = true
            scope.launch {
                while (isActive) {
                    runCatching { tick() }.onFailure { Log.w(TAG, "Poll failed", it) }
                    delay(if (screenOn) POLL_INTERVAL_MS else IDLE_POLL_INTERVAL_MS)
                }
            }
        }
        return START_STICKY
    }

    private fun tick() {
        // Still waiting for the user to unlock the device after a reboot. Nothing can be
        // opened behind the keyguard, so there is nothing to protect yet either. Retry
        // here as well as on the broadcast, so a missed ACTION_USER_UNLOCKED cannot leave
        // protection switched off for the rest of the session.
        val prefs = prefs ?: run {
            openCredentialStorage()
            return
        }
        // Nothing left to protect (both switches off) — stop instead of polling forever.
        if (!prefs.protectionEnabled && !prefs.antiUninstallEnabled) {
            stopSelf()
            return
        }
        screenOn = isInteractive()
        if (!screenOn) return
        // Usage access or the overlay permission can be revoked while we run; without
        // them the lock screen silently never appears, so surface it in the notification.
        if (!permissionsHealthy()) return

        val now = System.currentTimeMillis()
        val current = foregroundPackage(now) ?: return

        // Keep the current app's unlock fresh and re-lock apps left longer than the delay.
        LockState.onTick(current, prefs.relockDelayMs, now)

        // User-chosen app locks honour the master Protection switch (and the optional
        // scheduled pause); the anti-uninstall lock on system screens always applies
        // whenever tamper protection is enabled.
        val userLocked = prefs.protectionEnabled && !prefs.isLockingPausedNow() &&
            prefs.isLocked(current)
        val systemLocked = prefs.antiUninstallEnabled &&
            LockPrefs.PROTECTED_SYSTEM_PACKAGES.contains(current)
        // Opt-in: the task switcher shows a preview of every app, and Android will not
        // let us blank the preview of a locked one, so the only lever we have is the
        // switcher as a whole.
        val recentsLocked = prefs.protectionEnabled && !prefs.isLockingPausedNow() &&
            prefs.lockRecentsScreen &&
            isRecentsScreen(current, lastForegroundClass, launcherPackage)

        if (shouldShowLockScreen(
                isOwnPackage = current == packageName,
                userLocked = userLocked,
                systemLocked = systemLocked,
                recentsLocked = recentsLocked,
                isUnlocked = LockState.isUnlocked(current),
                justLaunchedFor = current == lastLaunchPkg &&
                    now - lastLaunchAt < RELAUNCH_DEBOUNCE_MS,
            )
        ) {
            showLockScreen(current, now, recents = recentsLocked)
        }
    }

    /**
     * The package that most recently moved to the foreground, remembered across polls.
     * Only events newer than the previous query are read (with a small overlap so none
     * are missed), which keeps this cheap at the poll rate.
     */
    // MOVE_TO_FOREGROUND is deprecated in favour of ACTIVITY_RESUMED, which is the same
    // constant and only exists from API 29 — this app supports API 26.
    @Suppress("DEPRECATION")
    private fun foregroundPackage(now: Long): String? {
        val begin = queryWindowStart(lastEventTime, now)
        val events = usageStatsManager.queryEvents(begin, now)
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.timeStamp > lastEventTime) lastEventTime = event.timeStamp
            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                lastForegroundPkg = event.packageName
                lastForegroundClass = event.className
            }
        }
        return lastForegroundPkg
    }

    /** Re-check the special permissions every few seconds, cheaply. */
    private fun permissionsHealthy(): Boolean {
        if (ticksSincePermissionCheck < PERMISSION_CHECK_TICKS) {
            ticksSincePermissionCheck++
            return permissionsOk
        }
        ticksSincePermissionCheck = 0
        val ok = Permissions.hasRequired(this)
        if (ok != permissionsOk) {
            permissionsOk = ok
            getSystemService(NotificationManager::class.java)
                ?.notify(NOTIF_ID, buildNotification(healthy = ok))
        }
        return ok
    }

    private fun resolveLauncherPackage(): String? = runCatching {
        packageManager.resolveActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
            PackageManager.MATCH_DEFAULT_ONLY
        )?.activityInfo?.packageName
    }.getOrNull()

    private fun showLockScreen(pkg: String, now: Long, recents: Boolean = false) {
        lastLaunchPkg = pkg
        lastLaunchAt = now
        val intent = Intent(this, LockScreenActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
            putExtra(LockScreenActivity.EXTRA_PACKAGE, pkg)
            putExtra(LockScreenActivity.EXTRA_RECENTS, recents)
        }
        // A background activity start relies on the overlay permission; if it was revoked
        // between our check and here, do not take the process down.
        runCatching { startActivity(intent) }.onFailure {
            // Retry on the next tick rather than leaving the app unprotected.
            lastLaunchAt = 0L
            Log.w(TAG, "Could not show the lock screen", it)
        }
    }

    private fun buildNotification(healthy: Boolean): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notif_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
            manager?.createNotificationChannel(channel)
        }
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(if (healthy) R.drawable.ic_lock else R.drawable.ic_warning)
            .setContentTitle(
                getString(if (healthy) R.string.notif_title else R.string.notif_title_paused)
            )
            .setContentText(
                getString(if (healthy) R.string.notif_text else R.string.notif_text_paused)
            )
            .setContentIntent(openApp)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    /**
     * Read the settings, once credential-encrypted storage is available. Called on start
     * and again when the user unlocks the device, whichever comes first.
     */
    private fun openCredentialStorage() {
        if (prefs != null) return
        val unlocked = getSystemService(UserManager::class.java)?.isUserUnlocked ?: true
        if (!unlocked) return
        prefs = runCatching { LockPrefs(this).also { it.syncBootMirror() } }
            .onFailure { Log.w(TAG, "Settings not readable yet", it) }
            .getOrNull()
    }

    override fun onDestroy() {
        super.onDestroy()
        running = false
        runCatching { unregisterReceiver(screenReceiver) }
        runCatching { unregisterReceiver(userUnlockedReceiver) }
        scope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        /**
         * Pure decision for whether the lock screen should be (re)launched over the current
         * foreground package on a given poll tick. Extracted so it can be unit-tested
         * without Android context.
         *
         * The only state this trusts is the foreground package itself: if a protected app is
         * in the foreground then the lock screen is, by definition, *not* on top of it — our
         * own package would be the foreground one instead. An earlier version also gated on
         * a process-wide "the lock screen is visible" flag maintained by the activity, and
         * whenever that flag got stuck the protected app stayed usable unlocked. It does get
         * stuck: onStop deliberately kept it set while the biometric prompt was up, so
         * switching to the protected app from Recents left it true forever. Verified on
         * device, which is how this was found.
         *
         * [justLaunchedFor] is the only de-duplication needed — it covers the few hundred
         * milliseconds between launching the lock screen and it actually coming forward.
         */
        fun shouldShowLockScreen(
            isOwnPackage: Boolean,
            userLocked: Boolean,
            systemLocked: Boolean,
            recentsLocked: Boolean,
            isUnlocked: Boolean,
            justLaunchedFor: Boolean,
        ): Boolean {
            // Ignore our own UI (settings + the lock screen itself run in our package).
            if (isOwnPackage) return false
            // Launched for this app a moment ago; give it time to appear.
            if (justLaunchedFor) return false
            return (userLocked || systemLocked || recentsLocked) && !isUnlocked
        }

        /**
         * Whether the foreground screen is the task switcher.
         *
         * Both halves matter. Only the launcher (or SystemUI, which hosts the switcher on
         * some devices) may own it, because the *home screen* shares the launcher package
         * and matching on the package alone would lock the home screen too. And the
         * activity name has to read like an overview screen — note "recents" rather than
         * "recent", so Samsung's FromRecentActivity does not match.
         *
         * Where the switcher is a state of the launcher activity rather than an activity
         * of its own (Pixel launchers), no separate event is ever reported and this
         * correctly stays false: the toggle then does nothing on that device.
         */
        fun isRecentsScreen(pkg: String, className: String?, launcherPackage: String?): Boolean {
            if (pkg != launcherPackage && pkg != SYSTEM_UI_PACKAGE) return false
            val name = className?.lowercase() ?: return false
            return RECENTS_CLASS_HINTS.any { name.contains(it) }
        }

        private const val SYSTEM_UI_PACKAGE = "com.android.systemui"
        private val RECENTS_CLASS_HINTS = listOf("recents", "overview", "quickstep")

        /**
         * Where the next usage-events query should start reading from.
         *
         * Anchored to [lastEventTime], the newest event already processed — never to the
         * clock. Events can surface later than they happened (seconds late on a busy
         * device just after boot); a clock-anchored window would already have moved past
         * such an event's timestamp and would miss it for good, leaving the service
         * believing the launcher is still in front and the protected app unlocked.
         * Anchored this way the window simply widens until the event appears.
         */
        fun queryWindowStart(lastEventTime: Long, now: Long): Long =
            if (lastEventTime == 0L) {
                now - INITIAL_LOOKBACK_MS
            } else {
                maxOf(lastEventTime + 1, now - MAX_LOOKBACK_MS)
            }

        private const val TAG = "AppLockService"
        private const val CHANNEL_ID = "app_lock_service"
        private const val NOTIF_ID = 1

        // Poll fast enough that the lock screen appears near-instantly when a protected app
        // is opened. Lower is snappier but costs more battery; ~200ms is a good balance.
        private const val POLL_INTERVAL_MS = 200L

        /** While the display is off the loop only wakes up to notice it came back. */
        private const val IDLE_POLL_INTERVAL_MS = 1_000L

        /**
         * Window for the first query (and the first one after the screen comes back).
         *
         * Generous on purpose: after a reboot the service cannot start until the system
         * wakes it, and a protected app opened during that gap is already in the
         * foreground by then. Its "moved to foreground" event is the only way to notice
         * it, so the first query has to reach back far enough to still find it and lock
         * the app retroactively.
         */
        const val INITIAL_LOOKBACK_MS = 120_000L

        /** Cap on how far back a poll reads when no new events are turning up. */
        const val MAX_LOOKBACK_MS = 120_000L

        /** Re-check usage access / overlay roughly every 5 seconds of polling. */
        private const val PERMISSION_CHECK_TICKS = 25

        /** Grace after launching the lock screen before it may be launched again. */
        private const val RELAUNCH_DEBOUNCE_MS = 1_000L

        /** Start (or no-op if already running) the protection service. */
        fun start(context: Context) {
            val intent = Intent(context, AppLockService::class.java)
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }.onFailure { Log.w(TAG, "Could not start the service", it) }
        }

        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, AppLockService::class.java)) }
        }

        /** Run the service exactly when there is something to protect and we can do it. */
        fun sync(context: Context) {
            val prefs = LockPrefs(context)
            val needed = prefs.protectionEnabled || prefs.antiUninstallEnabled
            if (needed && Permissions.hasRequired(context)) start(context) else stop(context)
        }
    }
}
