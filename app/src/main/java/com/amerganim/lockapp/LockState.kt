package com.amerganim.lockapp

/**
 * In-memory runtime state shared between the monitoring service and the activities.
 *
 * Lives only while the process is alive — intentionally, so everything re-locks if
 * the app is killed and restarted.
 */
object LockState {

    /** True while the user is authenticated inside our own settings UI. */
    @Volatile
    var settingsAuthed: Boolean = false

    /** True while a [LockScreenActivity] is on screen, to avoid launching duplicates. */
    @Volatile
    var lockScreenActive: Boolean = false

    /** Unlocked package -> last time it was seen in the foreground (ms). */
    private val unlocked = HashMap<String, Long>()

    @Synchronized
    fun isUnlocked(pkg: String): Boolean = unlocked.containsKey(pkg)

    @Synchronized
    fun markUnlocked(pkg: String) {
        unlocked[pkg] = System.currentTimeMillis()
    }

    /**
     * Per-tick bookkeeping: keep [current] fresh while it stays in the foreground, and
     * re-lock any other unlocked app that has been away longer than [relockDelayMs].
     */
    @Synchronized
    fun onTick(current: String, relockDelayMs: Long) {
        val now = System.currentTimeMillis()
        if (unlocked.containsKey(current)) unlocked[current] = now
        val it = unlocked.entries.iterator()
        while (it.hasNext()) {
            val e = it.next()
            if (e.key != current && now - e.value > relockDelayMs) it.remove()
        }
    }
}
