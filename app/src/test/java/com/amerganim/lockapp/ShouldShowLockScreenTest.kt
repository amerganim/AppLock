package com.amerganim.lockapp

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [AppLockService.shouldShowLockScreen], the pure decision behind the
 * poll loop.
 *
 * The regression these guard: reaching a protected app by any route that stops the lock
 * screen without destroying it — the Recents/Overview switcher, or switching straight to
 * the app's task — used to leave the app usable unlocked.
 */
class ShouldShowLockScreenTest {

    private fun decide(
        isOwnPackage: Boolean = false,
        userLocked: Boolean = true,
        systemLocked: Boolean = false,
        isUnlocked: Boolean = false,
        justLaunchedFor: Boolean = false,
    ) = AppLockService.shouldShowLockScreen(
        isOwnPackage = isOwnPackage,
        userLocked = userLocked,
        systemLocked = systemLocked,
        isUnlocked = isUnlocked,
        justLaunchedFor = justLaunchedFor,
    )

    @Test
    fun showsLockScreenForLockedAppNotYetUnlocked() {
        assertTrue(decide())
    }

    @Test
    fun skipsAlreadyUnlockedApp() {
        assertFalse(decide(isUnlocked = true))
    }

    @Test
    fun skipsOwnPackage() {
        assertFalse(decide(isOwnPackage = true))
    }

    @Test
    fun skipsUnprotectedApp() {
        assertFalse(decide(userLocked = false))
    }

    @Test
    fun showsForSystemLockedPackage() {
        assertTrue(decide(userLocked = false, systemLocked = true))
    }

    /**
     * The bug this replaced: the decision also consulted a process-wide "the lock screen
     * is visible" flag. Seeing a protected app in the foreground *is* the proof that the
     * lock screen is not on top of it, so no flag may veto re-locking. Found on device —
     * switching to the protected app's task left it open because the flag had stuck true
     * while the biometric prompt was up.
     */
    @Test
    fun reLocksWheneverTheProtectedAppIsForegroundAgain() {
        assertTrue(decide())
    }

    @Test
    fun doesNotRelaunchWhileTheLockScreenIsStillComingForward() {
        assertFalse(decide(justLaunchedFor = true))
    }

    @Test
    fun relaunchesOnceTheDebounceHasPassed() {
        assertTrue(decide(justLaunchedFor = false))
    }

    /** An unlocked app must stay unlocked even right after a launch for another app. */
    @Test
    fun debounceDoesNotOverrideAnUnlockedApp() {
        assertFalse(decide(isUnlocked = true, justLaunchedFor = true))
    }
}
