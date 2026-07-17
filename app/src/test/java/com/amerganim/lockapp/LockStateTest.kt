package com.amerganim.lockapp

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for the in-memory unlock/relock bookkeeping in [LockState].
 *
 * [LockState] is a process-wide singleton, so each test resets it first via reflection.
 */
class LockStateTest {

    @Before
    fun reset() {
        val field = LockState::class.java.getDeclaredField("unlocked")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        (field.get(LockState) as MutableMap<String, Long>).clear()
        LockState.lockScreenActive = false
        LockState.settingsAuthed = false
    }

    @Test
    fun packageIsLockedByDefault() {
        assertFalse(LockState.isUnlocked("com.example.app"))
    }

    @Test
    fun markUnlockedMakesPackageUnlocked() {
        LockState.markUnlocked("com.example.app")
        assertTrue(LockState.isUnlocked("com.example.app"))
    }

    @Test
    fun lockScreenActiveDefaultsToFalse() {
        assertFalse(LockState.lockScreenActive)
    }

    @Test
    fun currentAppStaysUnlockedWhileInForeground() {
        LockState.markUnlocked("com.example.app")
        // Even with a zero relock delay, the foreground app must not be relocked.
        Thread.sleep(5)
        LockState.onTick(current = "com.example.app", relockDelayMs = 0)
        assertTrue(LockState.isUnlocked("com.example.app"))
    }

    @Test
    fun backgroundedAppRelocksAfterDelay() {
        LockState.markUnlocked("com.example.app")
        // Another app is now in the foreground; the delay has elapsed.
        Thread.sleep(5)
        LockState.onTick(current = "com.other.app", relockDelayMs = 0)
        assertFalse(LockState.isUnlocked("com.example.app"))
    }

    @Test
    fun backgroundedAppStaysUnlockedWithinDelay() {
        LockState.markUnlocked("com.example.app")
        LockState.onTick(current = "com.other.app", relockDelayMs = 60_000)
        assertTrue(LockState.isUnlocked("com.example.app"))
    }
}
