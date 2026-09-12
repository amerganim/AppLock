package com.amerganim.lockapp

/**
 * In-memory runtime state shared between the monitoring service and the activities.
 *
 * Lives only while the process is alive — intentionally, so everything re-locks if
 * the app is killed and restarted.
 */
object LockState {

    /**
     * How long a just-unlocked app is protected from the re-lock sweep before it has
     * actually been seen in the foreground.
     *
     * Without this, unlocking with the "Immediately" re-lock delay was racy: the poll
     * tick that runs while our own lock screen is still the foreground app would sweep
     * the freshly unlocked package away before the protected app had a chance to come
     * forward, and the lock screen would immediately pop a second time.
     */
    const val UNLOCK_GRACE_MS = 5_000L

    /** One unlocked app: when it was unlocked, when it was last foreground. */
    private class Session(val unlockedAt: Long, var lastSeen: Long, var seenForeground: Boolean)

    private val unlocked = HashMap<String, Session>()

    /** How many of our own activities are currently started (see [LockApp]). */
    private var startedActivities = 0

    /** True while the user is authenticated inside our own UI (home, settings, vault). */
    @Volatile
    var settingsAuthed: Boolean = false

    @Synchronized
    fun isUnlocked(pkg: String): Boolean = unlocked.containsKey(pkg)

    @Synchronized
    fun markUnlocked(pkg: String, now: Long = System.currentTimeMillis()) {
        unlocked[pkg] = Session(unlockedAt = now, lastSeen = now, seenForeground = false)
    }

    /**
     * Per-tick bookkeeping: keep [current] fresh while it stays in the foreground, and
     * re-lock any other unlocked app that has been away longer than [relockDelayMs].
     *
     * An app that has never been seen in the foreground since it was unlocked keeps its
     * unlock for at least [UNLOCK_GRACE_MS] (see the constant for why).
     */
    @Synchronized
    fun onTick(current: String, relockDelayMs: Long, now: Long = System.currentTimeMillis()) {
        unlocked[current]?.let { session ->
            session.lastSeen = now
            session.seenForeground = true
        }
        val iterator = unlocked.entries.iterator()
        while (iterator.hasNext()) {
            val (pkg, session) = iterator.next()
            if (pkg == current) continue
            val expired = if (session.seenForeground) {
                now - session.lastSeen > relockDelayMs
            } else {
                now - session.unlockedAt > maxOf(UNLOCK_GRACE_MS, relockDelayMs)
            }
            if (expired) iterator.remove()
        }
    }

    /**
     * Re-lock everything at once. Used when the display turns off: otherwise an app
     * that was unlocked and left in the foreground would stay unlocked indefinitely,
     * because the re-lock delay only counts time spent in the background.
     */
    @Synchronized
    fun lockAll() {
        unlocked.clear()
        settingsAuthed = false
    }

    // ---- Our own foreground tracking ----
    //
    // The authenticated session covers our whole UI, so moving between the home
    // screen, settings and the vault does not re-prompt; it ends as soon as the app
    // as a whole leaves the foreground.

    @Synchronized
    fun onActivityStarted() {
        startedActivities++
    }

    @Synchronized
    fun onActivityStopped() {
        startedActivities--
        if (startedActivities <= 0) {
            startedActivities = 0
            settingsAuthed = false
        }
    }

    /** Currently unlocked packages (read-only snapshot). */
    @Synchronized
    fun unlockedPackages(): Set<String> = unlocked.keys.toSet()

    /** Drop all runtime state. Only for tests. */
    @Synchronized
    fun resetForTest() {
        unlocked.clear()
        startedActivities = 0
        settingsAuthed = false
    }
}
