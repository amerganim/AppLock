package com.amerganim.lockapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [AppLockService.queryWindowStart], which decides how far back each poll
 * reads usage events.
 *
 * The bug these pin down: the window used to start from the time of the previous *query*.
 * Usage events are not always readable the instant they happen — after a reboot, with the
 * system still busy, one can surface seconds late — and by then a clock-anchored window
 * has swept past that event's timestamp, losing it for good. The service then still
 * believed the launcher was in front, so the app the user had just opened was never
 * locked. Reproduced on an Android 10 device: the first app opened after a reboot stayed
 * unlocked, the next one locked normally.
 */
class QueryWindowTest {

    private val now = 1_000_000_000L

    @Test
    fun firstQueryReachesBackOverTheInitialWindow() {
        assertEquals(
            now - AppLockService.INITIAL_LOOKBACK_MS,
            AppLockService.queryWindowStart(lastEventTime = 0L, now = now)
        )
    }

    @Test
    fun resumesJustAfterTheNewestEventAlreadyProcessed() {
        val lastEvent = now - 5_000
        assertEquals(lastEvent + 1, AppLockService.queryWindowStart(lastEvent, now))
    }

    /**
     * The regression itself: while no new events are being seen, the start of the window
     * must stay put rather than marching along with the clock, or a late-surfacing event
     * falls into the gap between polls and is never read.
     */
    @Test
    fun windowStartDoesNotAdvanceWhileNoEventsAreSeen() {
        val lastEvent = now - 1_000
        val startNow = AppLockService.queryWindowStart(lastEvent, now)
        val startLater = AppLockService.queryWindowStart(lastEvent, now + 10_000)
        assertEquals(startNow, startLater)
    }

    /**
     * The guarantee that matters: an event that happened after the last one processed is
     * still inside the window however late it becomes readable. This is the case that
     * broke on device — the app the user opened was that event.
     */
    @Test
    fun anEventSurfacingLateIsStillInsideTheWindow() {
        val lastEvent = now - 10_000          // newest event already processed
        val openedApp = now - 6_000           // happened after it...
        val polledMuchLater = now + 30_000    // ...but only readable at this poll

        val start = AppLockService.queryWindowStart(lastEvent, polledMuchLater)

        assertTrue("the app the user opened must still be read", openedApp > start)
    }

    @Test
    fun windowIsCappedWhenEventsHaveBeenQuietForAges() {
        val ancient = now - 10 * AppLockService.MAX_LOOKBACK_MS
        assertEquals(
            now - AppLockService.MAX_LOOKBACK_MS,
            AppLockService.queryWindowStart(ancient, now)
        )
    }
}
