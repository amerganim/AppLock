package com.amerganim.lockapp

/** Which secret a wrong attempt was made against. Each is throttled separately. */
enum class AttemptScope { UNLOCK, RECOVERY }

/**
 * Brute-force protection. A four-digit PIN is only 10 000 combinations, so wrong
 * attempts must cost time: after [FREE_ATTEMPTS] failures each further one starts a
 * cooldown that doubles up to [MAX_COOLDOWN_MS].
 *
 * The counters live in [LockPrefs] rather than in an activity, so force-stopping the
 * app, pressing Back or letting the process die does not reset them.
 */
object AttemptGuard {

    /** Wrong attempts allowed before the first cooldown kicks in. */
    const val FREE_ATTEMPTS = 5

    /** Cooldown for the first offending attempt; doubles per further failure. */
    const val BASE_COOLDOWN_MS = 30_000L

    const val MAX_COOLDOWN_MS = 300_000L

    /** Show "n tries left" from this many failures on. */
    const val WARN_FROM_FAILURES = 3

    /** Cooldown that applies once [failures] wrong attempts have been made. */
    fun cooldownMsFor(failures: Int): Long {
        if (failures < FREE_ATTEMPTS) return 0L
        val doublings = (failures - FREE_ATTEMPTS).coerceIn(0, 16)
        val cooldown = BASE_COOLDOWN_MS shl doublings
        return if (cooldown <= 0L || cooldown > MAX_COOLDOWN_MS) MAX_COOLDOWN_MS else cooldown
    }

    fun failures(prefs: LockPrefs, scope: AttemptScope): Int = prefs.failedAttempts(scope)

    /** Attempts still allowed before a cooldown starts. */
    fun triesLeft(prefs: LockPrefs, scope: AttemptScope): Int =
        (FREE_ATTEMPTS - prefs.failedAttempts(scope)).coerceAtLeast(0)

    /** Record a wrong attempt and arm the cooldown. Returns the new failure count. */
    fun registerFailure(
        prefs: LockPrefs,
        scope: AttemptScope,
        now: Long = System.currentTimeMillis()
    ): Int {
        val failures = prefs.failedAttempts(scope) + 1
        prefs.setFailedAttempts(scope, failures)
        val cooldown = cooldownMsFor(failures)
        prefs.setCooldownUntil(scope, if (cooldown > 0L) now + cooldown else 0L)
        return failures
    }

    fun remainingCooldownMs(
        prefs: LockPrefs,
        scope: AttemptScope,
        now: Long = System.currentTimeMillis()
    ): Long = remaining(prefs.cooldownUntil(scope), now)

    /**
     * Time left on a cooldown that ends at [cooldownUntil]. Clamped to
     * [MAX_COOLDOWN_MS] so moving the device clock backwards cannot create a lockout
     * that never ends.
     */
    fun remaining(cooldownUntil: Long, now: Long): Long {
        val left = cooldownUntil - now
        return when {
            left <= 0L -> 0L
            left > MAX_COOLDOWN_MS -> MAX_COOLDOWN_MS
            else -> left
        }
    }

    /** Clear the counters after a successful authentication. */
    fun reset(prefs: LockPrefs, scope: AttemptScope) {
        prefs.setFailedAttempts(scope, 0)
        prefs.setCooldownUntil(scope, 0L)
    }

    /** "1:05" / "0:30" for the countdown shown on the lock screen. */
    fun formatCountdown(remainingMs: Long): String {
        val totalSeconds = ((remainingMs + 999L) / 1000L).toInt()
        return "${totalSeconds / 60}:${(totalSeconds % 60).toString().padStart(2, '0')}"
    }
}
