package com.amerganim.lockapp

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.amerganim.lockapp.databinding.ActivityRecoveryBinding
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * "Forgot lock" flow: answer the security question, then set a new PIN/pattern.
 * Reached from the lock screen's "Forgot?" link.
 *
 * This screen can be opened without knowing the credential, so wrong answers are
 * throttled exactly like wrong PINs — otherwise it would be the cheapest way in.
 */
class RecoveryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRecoveryBinding
    private lateinit var credential: CredentialManager
    private lateinit var prefs: LockPrefs
    private var cooldownJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )
        binding = ActivityRecoveryBinding.inflate(layoutInflater)
        setContentView(binding.root)
        credential = CredentialManager(this)
        prefs = LockPrefs(this)

        binding.question.text = credential.recoveryQuestion() ?: getString(R.string.no_recovery)
        binding.verify.setOnClickListener { attempt() }
        binding.cancel.setOnClickListener { finish() }
    }

    override fun onStart() {
        super.onStart()
        applyCooldownState()
    }

    override fun onStop() {
        super.onStop()
        cooldownJob?.cancel()
    }

    private fun attempt() {
        if (AttemptGuard.remainingCooldownMs(prefs, AttemptScope.RECOVERY) > 0L) {
            applyCooldownState()
            return
        }
        val answer = binding.answerInput.text?.toString()?.trim().orEmpty()
        if (answer.isEmpty()) {
            binding.answerLayout.error = getString(R.string.recovery_answer_required)
            return
        }
        if (credential.verifyRecovery(answer)) {
            // Identity confirmed — let them set a fresh lock, and treat the session as
            // authenticated so the setup screen does not ask for the lock they forgot.
            AttemptGuard.reset(prefs, AttemptScope.RECOVERY)
            LockState.settingsAuthed = true
            startActivity(Intent(this, SetupLockActivity::class.java))
            finish()
            return
        }
        val failures = AttemptGuard.registerFailure(prefs, AttemptScope.RECOVERY)
        if (AttemptGuard.remainingCooldownMs(prefs, AttemptScope.RECOVERY) > 0L) {
            applyCooldownState()
            return
        }
        val triesLeft = AttemptGuard.triesLeft(prefs, AttemptScope.RECOVERY)
        binding.answerLayout.error = if (failures >= AttemptGuard.WARN_FROM_FAILURES) {
            resources.getQuantityString(R.plurals.tries_left, triesLeft, triesLeft)
        } else {
            getString(R.string.recovery_wrong)
        }
    }

    /** Disable verification with a live countdown while a cooldown is running. */
    private fun applyCooldownState() {
        cooldownJob?.cancel()
        val hasRecovery = credential.isRecoverySet()
        if (AttemptGuard.remainingCooldownMs(prefs, AttemptScope.RECOVERY) <= 0L) {
            binding.verify.isEnabled = hasRecovery
            return
        }
        binding.verify.isEnabled = false
        cooldownJob = lifecycleScope.launch {
            while (isActive) {
                val left = AttemptGuard.remainingCooldownMs(prefs, AttemptScope.RECOVERY)
                if (left <= 0L) break
                binding.answerLayout.error =
                    getString(R.string.locked_out, AttemptGuard.formatCountdown(left))
                delay(COUNTDOWN_TICK_MS)
            }
            binding.answerLayout.error = null
            binding.verify.isEnabled = hasRecovery
        }
    }

    private companion object {
        const val COUNTDOWN_TICK_MS = 500L
    }
}
