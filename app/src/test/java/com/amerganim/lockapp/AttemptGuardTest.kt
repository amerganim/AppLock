package com.amerganim.lockapp

import org.junit.Assert.assertEquals
import org.junit.Test

/** Unit tests for the wrong-attempt throttling policy in [AttemptGuard]. */
class AttemptGuardTest {

    @Test
    fun noCooldownWhileWithinTheFreeAttempts() {
        for (failures in 0 until AttemptGuard.FREE_ATTEMPTS) {
            assertEquals(0L, AttemptGuard.cooldownMsFor(failures))
        }
    }

    @Test
    fun firstOffendingAttemptStartsTheBaseCooldown() {
        assertEquals(
            AttemptGuard.BASE_COOLDOWN_MS,
            AttemptGuard.cooldownMsFor(AttemptGuard.FREE_ATTEMPTS)
        )
    }

    @Test
    fun cooldownDoublesWithEachFurtherAttempt() {
        assertEquals(
            AttemptGuard.BASE_COOLDOWN_MS * 2,
            AttemptGuard.cooldownMsFor(AttemptGuard.FREE_ATTEMPTS + 1)
        )
        assertEquals(
            AttemptGuard.BASE_COOLDOWN_MS * 4,
            AttemptGuard.cooldownMsFor(AttemptGuard.FREE_ATTEMPTS + 2)
        )
    }

    @Test
    fun cooldownIsCappedAndNeverOverflows() {
        assertEquals(AttemptGuard.MAX_COOLDOWN_MS, AttemptGuard.cooldownMsFor(50))
        assertEquals(AttemptGuard.MAX_COOLDOWN_MS, AttemptGuard.cooldownMsFor(Int.MAX_VALUE))
    }

    @Test
    fun remainingIsZeroOnceTheDeadlinePasses() {
        assertEquals(0L, AttemptGuard.remaining(cooldownUntil = 1_000L, now = 1_000L))
        assertEquals(0L, AttemptGuard.remaining(cooldownUntil = 1_000L, now = 5_000L))
    }

    @Test
    fun remainingCountsDown() {
        assertEquals(4_000L, AttemptGuard.remaining(cooldownUntil = 9_000L, now = 5_000L))
    }

    /** Winding the device clock backwards must not create a lockout that never ends. */
    @Test
    fun remainingIsClampedAgainstABackwardsClock() {
        val farFuture = 10L * 365 * 24 * 60 * 60 * 1000
        assertEquals(
            AttemptGuard.MAX_COOLDOWN_MS,
            AttemptGuard.remaining(cooldownUntil = farFuture, now = 0L)
        )
    }

    @Test
    fun countdownIsFormattedAsMinutesAndSeconds() {
        assertEquals("0:30", AttemptGuard.formatCountdown(30_000L))
        assertEquals("1:00", AttemptGuard.formatCountdown(60_000L))
        assertEquals("2:05", AttemptGuard.formatCountdown(125_000L))
        // Part-seconds round up, so the countdown never displays 0:00 while still locked.
        assertEquals("0:01", AttemptGuard.formatCountdown(1L))
    }
}
