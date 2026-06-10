package com.amerganim.lockapp

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.amerganim.lockapp.databinding.ActivityWelcomeBinding

/** First-run welcome screen explaining the value before asking for anything. */
class WelcomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWelcomeBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWelcomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.getStarted.setOnClickListener {
            startActivity(
                Intent(this, SetupLockActivity::class.java)
                    .putExtra(SetupLockActivity.EXTRA_ONBOARDING, true)
            )
            finish()
        }
    }
}
