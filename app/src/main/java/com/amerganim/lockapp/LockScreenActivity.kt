package com.amerganim.lockapp

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.amerganim.lockapp.databinding.ActivityLockScreenBinding
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Full-screen unlock prompt (PIN or pattern, plus optional biometric). Two modes:
 *  - "self": shown to protect LockApp's own settings (launched by [SecureActivity]).
 *  - app lock: shown over a third-party app by [AppLockService].
 *
 * On success we don't pass an activity result (this activity is singleInstance, so
 * results aren't reliable); instead we update [LockState] and finish.
 */
class LockScreenActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLockScreenBinding
    private lateinit var credential: CredentialManager
    private lateinit var prefs: LockPrefs
    private var pinPad: PinPad? = null

    private lateinit var targetPackage: String
    private val isSelf get() = targetPackage == packageName

    /** Shown over the task switcher rather than over an app (opt-in setting). */
    private var isRecents = false

    private var builtType: LockType? = null
    private var builtPinLength = 0
    private var biometricPromptShowing = false
    private var cooldownJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Prevent the credential from showing up in screenshots / the recents thumbnail.
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)

        binding = ActivityLockScreenBinding.inflate(layoutInflater)
        setContentView(binding.root)
        credential = CredentialManager(this)
        prefs = LockPrefs(this)

        targetPackage = intent.getStringExtra(EXTRA_PACKAGE) ?: packageName
        isRecents = intent.getBooleanExtra(EXTRA_RECENTS, false)

        // Nothing to verify against (app data cleared, setup never finished): fail open
        // rather than trapping the user behind a prompt no input can satisfy.
        if (!credential.isCredentialSet()) {
            unlock()
            return
        }

        bindHeader()
        buildInput()

        binding.forgotButton.visibility =
            if (credential.isRecoverySet()) View.VISIBLE else View.GONE
        binding.forgotButton.setOnClickListener {
            startActivity(Intent(this, RecoveryActivity::class.java))
        }

        val fakeCover = prefs.fakeCoverEnabled
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

    override fun onStart() {
        super.onStart()
        applyCooldownState()
    }

    override fun onStop() {
        super.onStop()
        cooldownJob?.cancel()
        // Nothing to release here: the service decides whether we are needed from the
        // foreground package, so however this activity is left (Recents switch, Home,
        // another app) the protected app is detected again and we are relaunched over it.
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
            // The saved PIN is checked as soon as it is that many digits long. Rebuild if
            // the length changed under us, e.g. via the "Forgot?" reset flow.
            val length = credential.pinLength()
            if (pinPad == null || builtPinLength != length) {
                pinPad = PinPad(
                    keypad = binding.keypad,
                    dots = binding.dots,
                    scramble = prefs.scrambleKeypad,
                    autoSubmitLength = length,
                ) { verify(it) }
                builtPinLength = length
            }
            pinPad?.reset()
        } else {
            binding.patternView.onPatternDetected = { indices -> verify(PatternLockView.encode(indices)) }
            binding.patternView.clearPattern()
        }
        applyCooldownState()
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
        if (!prefs.biometricEnabled) return false
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
        val requested = intent.getStringExtra(EXTRA_PACKAGE) ?: packageName
        val requestedRecents = intent.getBooleanExtra(EXTRA_RECENTS, false)
        // Re-delivery for the app we are already prompting for (the service can launch us
        // again while we come forward) must not wipe a PIN the user is halfway through.
        if (requested == targetPackage && requestedRecents == isRecents) return
        targetPackage = requested
        isRecents = requestedRecents
        bindHeader()
        buildInput()
        showStatus(null)
    }

    private fun bindHeader() {
        if (isRecents) {
            // The package here is the launcher, whose name and icon would be misleading.
            binding.appName.setText(R.string.recents_screen)
            showPadlockIcon()
        } else if (isSelf) {
            binding.appName.text = ""
            showPadlockIcon()
        } else {
            val pm = packageManager
            try {
                val info = pm.getApplicationInfo(targetPackage, 0)
                binding.appName.text = pm.getApplicationLabel(info)
                // Drop the white tint the padlock placeholder needs, or the app icon is
                // painted over into a solid white blob.
                binding.appIcon.imageTintList = null
                binding.appIcon.setImageDrawable(pm.getApplicationIcon(info))
            } catch (e: Exception) {
                binding.appName.text = targetPackage
                showPadlockIcon()
            }
        }
    }

    private fun showPadlockIcon() {
        binding.appIcon.imageTintList = ColorStateList.valueOf(
            ContextCompat.getColor(this, R.color.white)
        )
        binding.appIcon.setImageResource(R.drawable.ic_lock)
    }

    private fun verify(value: String) {
        // Belt and braces: input is disabled during a cooldown, but never check a
        // credential while one is running.
        if (AttemptGuard.remainingCooldownMs(prefs, AttemptScope.UNLOCK) > 0L) {
            applyCooldownState()
            return
        }
        if (credential.verify(value)) {
            unlock()
        } else {
            if (builtType == LockType.PIN) pinPad?.reset() else binding.patternView.showError()
            onWrongAttempt()
        }
    }

    private fun onWrongAttempt() {
        val failures = AttemptGuard.registerFailure(prefs, AttemptScope.UNLOCK)
        maybeCaptureIntruder(failures)

        if (AttemptGuard.remainingCooldownMs(prefs, AttemptScope.UNLOCK) > 0L) {
            applyCooldownState()
            return
        }
        val triesLeft = AttemptGuard.triesLeft(prefs, AttemptScope.UNLOCK)
        showStatus(
            if (failures >= AttemptGuard.WARN_FROM_FAILURES) {
                resources.getQuantityString(R.plurals.tries_left, triesLeft, triesLeft)
            } else {
                getString(R.string.wrong_credential)
            }
        )
    }

    /**
     * Capture an intruder selfie every [LockPrefs.INTRUDER_THRESHOLD] wrong attempts.
     * The counter is persisted, so closing and reopening the lock screen between guesses
     * no longer resets it (which used to make the capture avoidable).
     */
    private fun maybeCaptureIntruder(failures: Int) {
        if (failures <= 0 || failures % LockPrefs.INTRUDER_THRESHOLD != 0) return
        if (!prefs.intruderSelfieEnabled || !IntruderManager.hasCameraPermission(this)) return
        IntruderManager.capture(this, targetPackage)
    }

    /** Lock the input out (with a live countdown) while a cooldown is running. */
    private fun applyCooldownState() {
        cooldownJob?.cancel()
        if (AttemptGuard.remainingCooldownMs(prefs, AttemptScope.UNLOCK) <= 0L) {
            setInputEnabled(true)
            return
        }
        setInputEnabled(false)
        cooldownJob = lifecycleScope.launch {
            while (isActive) {
                val left = AttemptGuard.remainingCooldownMs(prefs, AttemptScope.UNLOCK)
                if (left <= 0L) break
                showStatus(getString(R.string.locked_out, AttemptGuard.formatCountdown(left)))
                delay(COUNTDOWN_TICK_MS)
            }
            setInputEnabled(true)
            showStatus(null)
        }
    }

    private fun setInputEnabled(enabled: Boolean) {
        pinPad?.setInputEnabled(enabled)
        binding.patternView.setEnabledInput(enabled)
        val alpha = if (enabled) 1f else DISABLED_ALPHA
        binding.keypad.root.alpha = alpha
        binding.patternView.alpha = alpha
        if (enabled) {
            pinPad?.reset()
            binding.patternView.clearPattern()
        }
    }

    private fun showStatus(message: String?) {
        binding.statusMessage.text = message.orEmpty()
        binding.statusMessage.visibility = if (message.isNullOrEmpty()) View.INVISIBLE else View.VISIBLE
    }

    /** Shared success path for PIN, pattern and biometric unlock. */
    private fun unlock() {
        AttemptGuard.reset(prefs, AttemptScope.UNLOCK)
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
        runCatching { startActivity(home) }
        finish()
    }

    companion object {
        const val EXTRA_PACKAGE = "extra_package"
        const val EXTRA_RECENTS = "extra_recents"
        private const val COUNTDOWN_TICK_MS = 500L
        private const val DISABLED_ALPHA = 0.35f
    }
}
