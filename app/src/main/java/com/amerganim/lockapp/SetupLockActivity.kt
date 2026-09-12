package com.amerganim.lockapp

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import com.amerganim.lockapp.databinding.ActivitySetupLockBinding

/**
 * Lets the user pick a lock type (PIN or pattern) and set it with a confirmation
 * step. Used during onboarding and when changing/resetting the lock later.
 */
class SetupLockActivity : SecureActivity() {

    private lateinit var binding: ActivitySetupLockBinding
    private lateinit var credential: CredentialManager
    private lateinit var pinPad: PinPad

    private var onboarding = false
    private var type = LockType.PIN
    private var firstValue: String? = null

    /**
     * Changing the lock must not be possible without proving the current one, e.g. when
     * Android restores this screen from Recents after the process died. During first-run
     * setup there is no credential to prove yet.
     */
    override fun requiresAuth(): Boolean = credential.isCredentialSet()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySetupLockBinding.inflate(layoutInflater)
        setContentView(binding.root)
        credential = CredentialManager(this)
        onboarding = intent.getBooleanExtra(EXTRA_ONBOARDING, false)
        type = if (credential.isCredentialSet()) credential.lockType() else LockType.PIN

        // No autoSubmitLength: the length is the user's choice, so the ✓ key submits.
        pinPad = PinPad(binding.keypad, binding.dots) { value -> onValue(value) }
        binding.patternView.onPatternDetected = { indices ->
            if (indices.size < CredentialManager.MIN_PATTERN_DOTS) {
                binding.patternView.showError()
                Toast.makeText(this, R.string.pattern_too_short, Toast.LENGTH_SHORT).show()
            } else {
                onValue(PatternLockView.encode(indices))
            }
        }

        binding.typeToggle.check(if (type == LockType.PIN) R.id.btnTypePin else R.id.btnTypePattern)
        binding.typeToggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            type = if (checkedId == R.id.btnTypePin) LockType.PIN else LockType.PATTERN
            resetEntry()
        }
        resetEntry()
    }

    private fun resetEntry() {
        firstValue = null
        pinPad.reset()
        binding.patternView.clearPattern()
        binding.pinGroup.visibility = if (type == LockType.PIN) View.VISIBLE else View.GONE
        binding.patternView.visibility = if (type == LockType.PATTERN) View.VISIBLE else View.GONE
        binding.typeToggle.visibility = View.VISIBLE
        binding.title.setText(
            if (type == LockType.PIN) R.string.set_pin_title else R.string.set_pattern_title
        )
        binding.subtitle.setText(
            if (type == LockType.PIN) R.string.set_pin_subtitle else R.string.set_pattern_subtitle
        )
    }

    private fun onValue(value: String) {
        val first = firstValue
        if (first == null) {
            firstValue = value
            // Confirmation step — type can no longer change mid-confirm.
            binding.typeToggle.visibility = View.GONE
            binding.title.setText(
                if (type == LockType.PIN) R.string.confirm_pin_title
                else R.string.confirm_pattern_title
            )
            binding.subtitle.setText(
                if (type == LockType.PIN) R.string.confirm_pin_subtitle
                else R.string.set_pattern_subtitle
            )
            pinPad.reset()
            binding.patternView.clearPattern()
        } else if (value == first) {
            credential.setCredential(type, value)
            onSaved()
        } else {
            firstValue = null
            Toast.makeText(
                this,
                if (type == LockType.PIN) R.string.pin_mismatch else R.string.pattern_mismatch,
                Toast.LENGTH_SHORT
            ).show()
            resetEntry()
        }
    }

    private fun onSaved() {
        LockState.settingsAuthed = true
        if (onboarding) {
            startActivity(
                Intent(this, SetupRecoveryActivity::class.java)
                    .putExtra(SetupRecoveryActivity.EXTRA_ONBOARDING, true)
            )
        } else {
            Toast.makeText(this, R.string.lock_saved, Toast.LENGTH_SHORT).show()
        }
        finish()
    }

    companion object {
        const val EXTRA_ONBOARDING = "extra_onboarding"
    }
}
