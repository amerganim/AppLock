package com.amerganim.lockapp

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [AppLockService.shouldLockRecentsOnKey], the decision taken the moment
 * the Recents key is pressed.
 *
 * This path exists because the usage event announcing the task switcher arrives about a
 * second and a half after it is already on screen (measured on a Galaxy A15, Android 16),
 * which is long enough to read a protected app's thumbnail. Acting on the key press
 * instead means the decision is made without knowing what the foreground package is, so
 * every other condition has to be checked here.
 */
class RecentsKeyLockTest {

    private fun decide(
        screenOn: Boolean = true,
        protectionEnabled: Boolean = true,
        lockRecentsScreen: Boolean = true,
        lockingPaused: Boolean = false,
        permissionsOk: Boolean = true,
    ) = AppLockService.shouldLockRecentsOnKey(
        screenOn = screenOn,
        protectionEnabled = protectionEnabled,
        lockRecentsScreen = lockRecentsScreen,
        lockingPaused = lockingPaused,
        permissionsOk = permissionsOk,
    )

    @Test
    fun locksTheSwitcherWhenTheSettingIsOn() {
        assertTrue(decide())
    }

    @Test
    fun doesNothingWhenTheSettingIsOff() {
        // The default. Locking the switcher costs an unlock every time it is opened, for
        // every app, so it is never imposed on someone who did not ask for it.
        assertFalse(decide(lockRecentsScreen = false))
    }

    @Test
    fun doesNothingWhileProtectionIsOff() {
        assertFalse(decide(protectionEnabled = false))
    }

    @Test
    fun doesNothingWhileLockingIsPausedBySchedule() {
        assertFalse(decide(lockingPaused = true))
    }

    @Test
    fun doesNothingWithoutTheSpecialPermissions() {
        // Without usage access and the overlay permission the lock screen cannot be shown
        // anyway; launching it would only flash an activity that is immediately useless.
        assertFalse(decide(permissionsOk = false))
    }

    @Test
    fun doesNothingWhileTheDisplayIsOff() {
        // A stray broadcast with the screen off must not wake the device with a prompt.
        assertFalse(decide(screenOn = false))
    }
}
