package com.amerganim.lockapp

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [AppLockService.shouldShowLockScreen], the pure decision behind the
 * poll loop. The regression this guards against: pressing Recents/Overview and switching
 * back to a protected app used to bypass the lock because the "lock screen was shown"
 * flag was never cleared.
 */
class ShouldShowLockScreenTest {

    @Test
    fun showsLockScreenForLockedAppNotYetUnlocked() {
        assertTrue(
            AppLockService.shouldShowLockScreen(
                lockScreenActive = false,
                isOwnPackage = false,
                userLocked = true,
                systemLocked = false,
                isUnlocked = false,
            )
        )
    }

    @Test
    fun skipsWhenLockScreenAlreadyVisible() {
        assertFalse(
            AppLockService.shouldShowLockScreen(
                lockScreenActive = true,
                isOwnPackage = false,
                userLocked = true,
                systemLocked = false,
                isUnlocked = false,
            )
        )
    }

    /**
     * The bug scenario: user reached the protected app via the Recents switcher, which
     * stops (does not destroy) the lock screen. Once its onStop clears lockScreenActive,
     * the next tick must re-launch the lock screen over the app.
     */
    @Test
    fun reLocksAfterRecentsSwitchClearsVisibleFlag() {
        assertTrue(
            AppLockService.shouldShowLockScreen(
                lockScreenActive = false,   // onStop cleared it after the switch
                isOwnPackage = false,
                userLocked = true,
                systemLocked = false,
                isUnlocked = false,         // never authenticated
            )
        )
    }

    @Test
    fun skipsAlreadyUnlockedApp() {
        assertFalse(
            AppLockService.shouldShowLockScreen(
                lockScreenActive = false,
                isOwnPackage = false,
                userLocked = true,
                systemLocked = false,
                isUnlocked = true,
            )
        )
    }

    @Test
    fun skipsOwnPackage() {
        assertFalse(
            AppLockService.shouldShowLockScreen(
                lockScreenActive = false,
                isOwnPackage = true,
                userLocked = true,
                systemLocked = false,
                isUnlocked = false,
            )
        )
    }

    @Test
    fun skipsUnprotectedApp() {
        assertFalse(
            AppLockService.shouldShowLockScreen(
                lockScreenActive = false,
                isOwnPackage = false,
                userLocked = false,
                systemLocked = false,
                isUnlocked = false,
            )
        )
    }

    @Test
    fun showsForSystemLockedPackage() {
        assertTrue(
            AppLockService.shouldShowLockScreen(
                lockScreenActive = false,
                isOwnPackage = false,
                userLocked = false,
                systemLocked = true,
                isUnlocked = false,
            )
        )
    }
}
