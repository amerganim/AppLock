package com.example.applock

import java.util.Collections

/**
 * In-memory runtime state shared between the monitoring service and the activities.
 *
 * Lives only while the process is alive — intentionally, so that everything
 * re-locks if the app is killed and restarted.
 */
object LockState {

    /** True while the user is authenticated inside our own settings UI. */
    @Volatile
    var settingsAuthed: Boolean = false

    /** True while a [LockScreenActivity] is on screen, to avoid launching duplicates. */
    @Volatile
    var lockScreenActive: Boolean = false

    private val unlocked: MutableSet<String> =
        Collections.synchronizedSet(mutableSetOf<String>())

    fun isUnlocked(pkg: String): Boolean = unlocked.contains(pkg)

    fun markUnlocked(pkg: String) {
        unlocked.add(pkg)
    }

    /**
     * Re-lock every previously unlocked app except [keep] (the app currently in the
     * foreground). Called when the foreground app changes so that leaving a locked
     * app and returning requires the PIN again.
     */
    fun relockAllExcept(keep: String?) {
        synchronized(unlocked) {
            val it = unlocked.iterator()
            while (it.hasNext()) {
                if (it.next() != keep) it.remove()
            }
        }
    }
}
