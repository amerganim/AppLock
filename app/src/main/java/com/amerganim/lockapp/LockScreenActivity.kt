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
 * Full-screen unlock prompt (PIN or pattern, plus optional biometric). Two modes:
 *  - "self": shown to protect LockApp's own settings (launched by [MainActivity]).
 *  - app lock: shown over a third-party app by [AppLockService].
 *
 * On success we don't pass an activity result (this activity is singleInstance, so
 * results aren't reliable); instead we update [LockState] and finish.
 */
class LockScreenActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLockScreenBinding
    private lateinit var credential: CredentialManager
    private var pinPad: PinPad? = null

    private lateinit var targetPackage: String
    private val isSelf get() = targetPackage == packageName

    private var builtType: LockType? = null
    private var biometricPromptShowing = false
    private var failCount = 0
    private var selfieTaken = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Prevent the credential from showing up in screenshots / the recents thumbnail.
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)

        binding = ActivityLockScreenBinding.inflate(layoutInflater)
        setContentView(binding.root)
        credential = CredentialManager(this)
        LockState.lockScreenActive = true

        targetPackage = intent.getStringExtra(EXTRA_PACKAGE) ?: packageName
        bindHeader()
        buildInput()

        binding.forgotButton.visibility =
            if (credential.isRecoverySet()) View.VISIBLE else View.GONE
        binding.forgotButton.setOnClickListener {
            startActivity(Intent(this, RecoveryActivity::class.java))
        }

        val fakeCover = LockPrefs(this).fakeCoverEnabled
        if (fakeCover) setupFakeCover()

        if (biometricAvailable()) {
            binding.fingerprintButton.visibility = View.VISIBLE
            binding.fingerprintButton.setOnClickListener { showBiometricPrompt() }
            // Don't pop the system prompt while the fake "crash" cover is up.
            if (!fakeCover) showBiometricPrompt()
        }

        // Back must not reveal the protected app; leave to the home screen instead.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                goHome()
            }
        })
    }

    override fun onResume() {
        super.onResume()
        // The lock type may have changed via the "Forgot?" reset flow — rebuild if so.
        if (builtType != credential.lockType()) buildInput()
    }

    /** Show the PIN keypad or the pattern grid depending on the saved lock type. */
    private fun buildInput() {
        val type = credential.lockType()
        builtType = type
        val pin = type == LockType.PIN
        binding.dots.root.visibility = if (pin) View.VISIBLE else View.GONE
        binding.keypad.root.visibility = if (pin) View.VISIBLE else View.GONE
        binding.keypadSpacer.visibility = if (pin) View.VISIBLE else View.GONE
        binding.patternView.visibility = if (pin) View.GONE else View.VISIBLE
        binding.title.setText(if (pin) R.string.enter_pin_title else R.string.enter_pattern_title)

        if (pin) {
            if (pinPad == null) {
                pinPad = PinPad(binding.keypad, binding.dots, LockPrefs(this).scrambleKeypad) { verify(it) }
            }
            pinPad?.reset()
        } else {
            binding.patternView.onPatternDetected = { indices -> verify(PatternLockView.encode(indices)) }
            binding.patternView.clearPattern()
        }
    }

    private fun setupFakeCover() {
        binding.fakeCover.visibility = View.VISIBLE
        binding.fakeTitle.text = getString(R.string.fake_message, coverAppLabel())
        // The secret reveal gesture: long-press anywhere on the fake dialog.
        binding.fakeCover.setOnLongClickListener {
            binding.fakeCover.visibility = View.GONE
            if (biometricAvailable()) showBiometricPrompt()
            true
        }
        binding.fakeClose.setOnClickListener { goHome() }
        binding.fakeInfo.setOnClickListener { goHome() }
    }

    private fun coverAppLabel(): String =
        if (isSelf) {
            getString(DisguiseManager.current(this).labelRes).substringBefore(" (")
        } else {
            binding.appName.text?.toString()?.takeIf { it.isNotEmpty() }
                ?: getString(R.string.app_name)
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
                    // Cancelled / "Use PIN" / lockout — fall back to the keypad or pattern.
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
        buildInput()
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

    private fun verify(value: String) {
        if (credential.verify(value)) {
            unlock()
        } else {
            binding.appName.setText(R.string.wrong_credential)
            if (builtType == LockType.PIN) pinPad?.reset() else binding.patternView.showError()
            onWrongAttempt()
        }
    }

    private fun onWrongAttempt() {
        failCount++
        if (!selfieTaken &&
            failCount >= LockPrefs.INTRUDER_THRESHOLD &&
            LockPrefs(this).intruderSelfieEnabled &&
            IntruderManager.hasCameraPermission(this)
        ) {
            selfieTaken = true
            IntruderManager.capture(this, targetPackage)
        }
    }

    /** Shared success path for PIN, pattern and biometric unlock. */
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
