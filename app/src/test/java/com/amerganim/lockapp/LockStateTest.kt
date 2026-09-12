package com.amerganim.lockapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for the in-memory unlock/relock bookkeeping in [LockState].
 *
 * [LockState] is a process-wide singleton, so each test resets it first. Timestamps are
 * passed in explicitly rather than read from the clock, so nothing here depends on
 * timing.
 */
class LockStateTest {

    private val t0 = 1_000_000L

    @Before
    fun reset() {
        LockState.resetForTest()
    }

    @Test
    fun packageIsLockedByDefault() {
        assertFalse(LockState.isUnlocked("com.example.app"))
    }

    @Test
    fun markUnlockedMakesPackageUnlocked() {
        LockState.markUnlocked("com.example.app", now = t0)
        assertTrue(LockState.isUnlocked("com.example.app"))
    }

    @Test
    fun currentAppStaysUnlockedWhileInForeground() {
        LockState.markUnlocked("com.example.app", now = t0)
        // Even with a zero relock delay, the foreground app must not be relocked.
        LockState.onTick(current = "com.example.app", relockDelayMs = 0, now = t0 + 60_000)
        assertTrue(LockState.isUnlocked("com.example.app"))
    }

    @Test
    fun backgroundedAppRelocksAfterDelay() {
        LockState.markUnlocked("com.example.app", now = t0)
        // It was actually used, then another app came to the foreground.
        LockState.onTick(current = "com.example.app", relockDelayMs = 10_000, now = t0 + 100)
        LockState.onTick(current = "com.other.app", relockDelayMs = 10_000, now = t0 + 20_000)
        assertFalse(LockState.isUnlocked("com.example.app"))
    }

    @Test
    fun backgroundedAppStaysUnlockedWithinDelay() {
        LockState.markUnlocked("com.example.app", now = t0)
        LockState.onTick(current = "com.example.app", relockDelayMs = 60_000, now = t0 + 100)
        LockState.onTick(current = "com.other.app", relockDelayMs = 60_000, now = t0 + 30_000)
        assertTrue(LockState.isUnlocked("com.example.app"))
    }

    /**
     * The regression this guards: with the "Immediately" relock delay, the poll tick that
     * runs while our own lock screen is still the foreground app used to sweep away the
     * unlock that had just been granted, so the lock screen popped a second time before
     * the protected app was ever shown.
     */
    @Test
    fun freshUnlockSurvivesTicksBeforeTheAppComesForward() {
        LockState.markUnlocked("com.example.app", now = t0)
        LockState.onTick(current = "com.amerganim.lockapp", relockDelayMs = 0, now = t0 + 200)
        LockState.onTick(current = "com.amerganim.lockapp", relockDelayMs = 0, now = t0 + 400)
        assertTrue(LockState.isUnlocked("com.example.app"))
    }

    @Test
    fun freshUnlockIsNotAnIndefiniteBypass() {
        LockState.markUnlocked("com.example.app", now = t0)
        // The app never came to the foreground; once the grace period is over it re-locks.
        LockState.onTick(
            current = "com.amerganim.lockapp",
            relockDelayMs = 0,
            now = t0 + LockState.UNLOCK_GRACE_MS + 1
        )
        assertFalse(LockState.isUnlocked("com.example.app"))
    }

    @Test
    fun graceNeverShortensAConfiguredRelockDelay() {
        LockState.markUnlocked("com.example.app", now = t0)
        val delay = LockState.UNLOCK_GRACE_MS * 4
        LockState.onTick(current = "com.other.app", relockDelayMs = delay, now = t0 + delay - 1)
        assertTrue(LockState.isUnlocked("com.example.app"))
        LockState.onTick(current = "com.other.app", relockDelayMs = delay, now = t0 + delay + 1)
        assertFalse(LockState.isUnlocked("com.example.app"))
    }

    /**
     * Screen off must re-lock everything: the relock delay only counts background time,
     * so an app left unlocked in the foreground would otherwise stay open to whoever
     * picks the device up next.
     */
    @Test
    fun lockAllRelocksEverythingAndEndsTheOwnUiSession() {
        LockState.markUnlocked("com.example.app", now = t0)
        LockState.markUnlocked("com.other.app", now = t0)
        LockState.settingsAuthed = true

        LockState.lockAll()

        assertEquals(emptySet<String>(), LockState.unlockedPackages())
        assertFalse(LockState.settingsAuthed)
    }

    @Test
    fun ownUiSessionSurvivesMovingBetweenOurScreens() {
        LockState.onActivityStarted()          // home screen
        LockState.settingsAuthed = true

        LockState.onActivityStarted()          // settings opens on top
        LockState.onActivityStopped()          // home screen stops behind it

        assertTrue(LockState.settingsAuthed)
    }

    @Test
    fun ownUiSessionEndsWhenTheAppLeavesTheForeground() {
        LockState.onActivityStarted()
        LockState.settingsAuthed = true

        LockState.onActivityStopped()

        assertFalse(LockState.settingsAuthed)
    }
}
