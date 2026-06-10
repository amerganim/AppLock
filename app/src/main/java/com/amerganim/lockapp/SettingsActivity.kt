package com.amerganim.lockapp

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import com.amerganim.lockapp.databinding.ActivitySettingsBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/** Central settings: change lock, recovery, auto-lock timing, fingerprint, tamper, theme. */
class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var prefs: LockPrefs
    private lateinit var credential: CredentialManager
    private lateinit var dpm: DevicePolicyManager
    private lateinit var adminComponent: ComponentName

    private val autolockValues = longArrayOf(0L, 10_000L, 30_000L, 60_000L)
    private val themeValues = intArrayOf(
        AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM,
        AppCompatDelegate.MODE_NIGHT_NO,
        AppCompatDelegate.MODE_NIGHT_YES
    )

    private val adminEnableLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { syncTamper() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = LockPrefs(this)
        credential = CredentialManager(this)
        dpm = getSystemService(DevicePolicyManager::class.java)
        adminComponent = AdminReceiver.component(this)

        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.rowChangeLock.setOnClickListener {
            startActivity(Intent(this, SetupLockActivity::class.java))
        }
        binding.rowRecovery.setOnClickListener {
            startActivity(Intent(this, SetupRecoveryActivity::class.java))
        }
        binding.rowAutolock.setOnClickListener { showAutolockDialog() }
        binding.rowTheme.setOnClickListener { showThemeDialog() }
        binding.rowPrivacy.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.privacy_policy_url))))
        }

        binding.biometricSwitch.setOnCheckedChangeListener { _, checked ->
            prefs.biometricEnabled = checked
        }
        binding.tamperSwitch.setOnCheckedChangeListener { _, checked -> onTamperToggled(checked) }

        binding.versionSubtitle.text = runCatching {
            packageManager.getPackageInfo(packageName, 0).versionName
        }.getOrDefault("")
    }

    override fun onResume() {
        super.onResume()
        binding.lockTypeSubtitle.text = getString(
            if (credential.lockType() == LockType.PIN) R.string.lock_type_pin else R.string.lock_type_pattern
        )
        binding.recoverySubtitle.text = credential.recoveryQuestion()
            ?: getString(R.string.recovery_not_set)
        binding.autolockSubtitle.text = autolockLabel(prefs.relockDelayMs)
        binding.themeSubtitle.text = themeLabel(prefs.themeMode)
        syncBiometric()
        syncTamper()
    }

    private fun showAutolockDialog() {
        val labels = resources.getStringArray(R.array.autolock_labels)
        val checked = autolockValues.indexOf(prefs.relockDelayMs).coerceAtLeast(0)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.autolock_title)
            .setSingleChoiceItems(labels, checked) { dialog, which ->
                prefs.relockDelayMs = autolockValues[which]
                binding.autolockSubtitle.text = labels[which]
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showThemeDialog() {
        val labels = resources.getStringArray(R.array.theme_labels)
        val checked = themeValues.indexOf(prefs.themeMode).coerceAtLeast(0)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.theme_title)
            .setSingleChoiceItems(labels, checked) { dialog, which ->
                prefs.themeMode = themeValues[which]
                AppCompatDelegate.setDefaultNightMode(themeValues[which])
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun autolockLabel(value: Long): String {
        val labels = resources.getStringArray(R.array.autolock_labels)
        val i = autolockValues.indexOf(value).coerceAtLeast(0)
        return labels[i]
    }

    private fun themeLabel(value: Int): String {
        val labels = resources.getStringArray(R.array.theme_labels)
        val i = themeValues.indexOf(value).coerceAtLeast(0)
        return labels[i]
    }

    private fun syncBiometric() {
        val status = BiometricManager.from(this).canAuthenticate(BIOMETRIC_WEAK)
        val hasHardware = status != BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE &&
            status != BiometricManager.BIOMETRIC_STATUS_UNKNOWN
        binding.biometricRow.visibility = if (hasHardware) View.VISIBLE else View.GONE
        binding.biometricSwitch.setOnCheckedChangeListener(null)
        binding.biometricSwitch.isChecked = prefs.biometricEnabled
        binding.biometricSwitch.setOnCheckedChangeListener { _, checked -> prefs.biometricEnabled = checked }
    }

    private fun onTamperToggled(enable: Boolean) {
        prefs.antiUninstallEnabled = enable
        if (prefs.protectionEnabled || enable) {
            if (Permissions.hasRequired(this)) AppLockService.start(this)
        }
        if (enable) {
            if (!dpm.isAdminActive(adminComponent)) {
                LockPrefs.PROTECTED_SYSTEM_PACKAGES.forEach { LockState.markUnlocked(it) }
                val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                    .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
                    .putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, getString(R.string.admin_explanation))
                adminEnableLauncher.launch(intent)
            }
        } else if (dpm.isAdminActive(adminComponent)) {
            dpm.removeActiveAdmin(adminComponent)
        }
        syncTamper()
    }

    private fun syncTamper() {
        binding.tamperSwitch.setOnCheckedChangeListener(null)
        binding.tamperSwitch.isChecked = prefs.antiUninstallEnabled
        binding.tamperSwitch.setOnCheckedChangeListener { _, checked -> onTamperToggled(checked) }
        binding.adminStatus.setText(
            if (dpm.isAdminActive(adminComponent)) R.string.admin_active else R.string.admin_inactive
        )
    }
}
