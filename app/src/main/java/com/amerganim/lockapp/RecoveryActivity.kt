package com.amerganim.lockapp

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.amerganim.lockapp.databinding.ActivityRecoveryBinding

/**
 * "Forgot lock" flow: answer the security question, then set a new PIN/pattern.
 * Reached from the lock screen's "Forgot?" link.
 */
class RecoveryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRecoveryBinding
    private lateinit var credential: CredentialManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRecoveryBinding.inflate(layoutInflater)
        setContentView(binding.root)
        credential = CredentialManager(this)

        binding.question.text = credential.recoveryQuestion() ?: getString(R.string.no_recovery)
        binding.verify.isEnabled = credential.isRecoverySet()

        binding.verify.setOnClickListener {
            val answer = binding.answerInput.text?.toString()?.trim().orEmpty()
            if (credential.verifyRecovery(answer)) {
                // Identity confirmed — let them set a fresh lock.
                startActivity(Intent(this, SetupLockActivity::class.java))
                finish()
            } else {
                binding.answerLayout.error = getString(R.string.recovery_wrong)
            }
        }
        binding.cancel.setOnClickListener { finish() }
    }
}
