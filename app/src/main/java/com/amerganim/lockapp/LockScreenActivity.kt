package com.amerganim.lockapp

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import com.amerganim.lockapp.databinding.ActivityLockScreenBinding

/**
 * Full-screen PIN prompt. Two modes:
 *  - "self": shown to protect AppLock's own settings (launched by [MainActivity]).
 *  - app lock: shown over a third-party app by [AppLockService].
 *
 * On success we don't pass an activity result (this activity is singleInstance, so
 * results aren't reliable); instead we update [LockState] and finish.
 */
class LockScreenActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLockScreenBinding
    private lateinit var pinManager: PinManager
    private lateinit var pinPad: PinPad

    private lateinit var targetPackage: String
    private val isSelf get() = targetPackage == packageName

    private var biometricPromptShowing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Prevent the PIN from showing up in screenshots / the recents thumbnail.
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)

        binding = ActivityLockScreenBinding.inflate(layoutInflater)
        setContentView(binding.root)
        pinManager = PinManager(this)
        LockState.lockScreenActive = true

        targetPackage = intent.getStringExtra(EXTRA_PACKAGE) ?: packageName
        bindHeader()

        pinPad = PinPad(binding.keypad, binding.dots) { pin -> verify(pin) }

        if (biometricAvailable()) {
            binding.fingerprintButton.visibility = View.VISIBLE
            binding.fingerprintButton.setOnClickListener { showBiometricPrompt() }
            showBiometricPrompt()
        }

        // Back must not reveal the protected app; leave to the home screen instead.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                goHome()
            }
        })
    }

    private fun biometricAvailable(): Boolean {
        if (!LockPrefs(this).biometricEnabled) return false
        return BiometricManager.from(this).canAuthenticate(BIOMETRIC_WEAK) ==
            BiometricManager.BIOMETRIC_SUCCESS
    }

    private fun showBiometricPrompt() {
        if (biometricPromptShowing) return
        biometricPromptShowing = true
        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    biometricPromptShowing = false
                    unlock()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    // User cancelled / chose "Use PIN" / hit a lockout — fall back to the keypad.
                    biometricPromptShowing = false
                }

                override fun onAuthenticationFailed() {
                    // A single non-match; the prompt stays up for another try.
                }
            }
        )
        val title = if (isSelf) getString(R.string.app_name)
        else binding.appName.text.ifEmpty { getString(R.string.biometric_prompt_title) }
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title.toString())
            .setSubtitle(getString(R.string.biometric_prompt_subtitle))
            .setNegativeButtonText(getString(R.string.use_pin))
            .setAllowedAuthenticators(BIOMETRIC_WEAK)
            .build()
        prompt.authenticate(info)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        targetPackage = intent.getStringExtra(EXTRA_PACKAGE) ?: packageName
        bindHeader()
        pinPad.reset()
    }

    private fun bindHeader() {
        if (isSelf) {
            binding.appName.text = ""
            binding.appIcon.setImageResource(R.drawable.ic_lock)
        } else {
            val pm = packageManager
            try {
                val info = pm.getApplicationInfo(targetPackage, 0)
                binding.appName.text = pm.getApplicationLabel(info)
                binding.appIcon.setImageDrawable(pm.getApplicationIcon(info))
            } catch (e: Exception) {
                binding.appName.text = targetPackage
                binding.appIcon.setImageResource(R.drawable.ic_lock)
            }
        }
    }

    private fun verify(pin: String) {
        if (pinManager.verify(pin)) {
            unlock()
        } else {
            binding.appName.setText(R.string.wrong_pin)
            pinPad.reset()
        }
    }

    /** Shared success path for both PIN and biometric unlock. */
    private fun unlock() {
        if (isSelf) {
            LockState.settingsAuthed = true
        } else {
            LockState.markUnlocked(targetPackage)
        }
        finish()
    }

    private fun goHome() {
        val home = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(home)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        LockState.lockScreenActive = false
    }

    companion object {
        const val EXTRA_PACKAGE = "extra_package"
    }
}
