package com.amerganim.lockapp

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import com.amerganim.lockapp.databinding.ActivitySetupRecoveryBinding

/**
 * Sets a security question + answer used to reset a forgotten lock. Shown at the
 * end of onboarding and reachable later from Settings.
 */
class SetupRecoveryActivity : SecureActivity() {

    private lateinit var binding: ActivitySetupRecoveryBinding
    private lateinit var credential: CredentialManager
    private var onboarding = false

    /** The recovery answer can reset the lock, so it is as sensitive as the lock itself. */
    override fun requiresAuth(): Boolean = credential.isCredentialSet()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySetupRecoveryBinding.inflate(layoutInflater)
        setContentView(binding.root)
        credential = CredentialManager(this)
        onboarding = intent.getBooleanExtra(EXTRA_ONBOARDING, false)

        val questions = resources.getStringArray(R.array.security_questions)
        binding.questionInput.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_list_item_1, questions)
        )
        binding.questionInput.setText(credential.recoveryQuestion() ?: questions.first(), false)

        binding.skip.visibility = if (onboarding) View.VISIBLE else View.GONE
        binding.skip.setOnClickListener { proceed() }

        binding.save.setOnClickListener {
            val question = binding.questionInput.text?.toString()?.trim().orEmpty()
            val answer = binding.answerInput.text?.toString()?.trim().orEmpty()
            if (question.isEmpty()) {
                binding.questionInput.error = getString(R.string.recovery_question_required)
                return@setOnClickListener
            }
            if (answer.isEmpty()) {
                binding.answerLayout.error = getString(R.string.recovery_answer_required)
                return@setOnClickListener
            }
            credential.setRecovery(question, answer)
            Toast.makeText(this, R.string.recovery_saved, Toast.LENGTH_SHORT).show()
            proceed()
        }
    }

    private fun proceed() {
        if (onboarding) {
            LockState.settingsAuthed = true
            startActivity(Intent(this, MainActivity::class.java))
        }
        finish()
    }

    companion object {
        const val EXTRA_ONBOARDING = "extra_onboarding"
    }
}
