package com.amerganim.lockapp

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [MainActivity.shouldAutoEnableProtection] — the rule that locking your
 * first app also switches Protection on, so the app is never left configured but inert.
 */
class AutoEnableProtectionTest {

    @Test
    fun lockingAnAppWhileProtectionIsOffTurnsItOn() {
        assertTrue(
            MainActivity.shouldAutoEnableProtection(
                justLocked = true,
                protectionEnabled = false,
                hasPermissions = true,
            )
        )
    }

    /** Unlocking an app must never flip the master switch. */
    @Test
    fun unlockingAnAppChangesNothing() {
        assertFalse(
            MainActivity.shouldAutoEnableProtection(
                justLocked = false,
                protectionEnabled = false,
                hasPermissions = true,
            )
        )
    }

    @Test
    fun protectionAlreadyOnNeedsNoChange() {
        assertFalse(
            MainActivity.shouldAutoEnableProtection(
                justLocked = true,
                protectionEnabled = true,
                hasPermissions = true,
            )
        )
    }

    /**
     * Without usage access and the overlay permission the service cannot enforce
     * anything, so claiming protection is on would be a lie.
     */
    @Test
    fun doesNotClaimProtectionWithoutThePermissionsToEnforceIt() {
        assertFalse(
            MainActivity.shouldAutoEnableProtection(
                justLocked = true,
                protectionEnabled = false,
                hasPermissions = false,
            )
        )
    }
}
