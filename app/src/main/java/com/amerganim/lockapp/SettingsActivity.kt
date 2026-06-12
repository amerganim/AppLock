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

    private val cameraPermLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            prefs.intruderSelfieEnabled = granted
            binding.intruderSwitch.isChecked = granted
        }

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
        binding.rowDisguise.setOnClickListener { showDisguiseDialog() }
        binding.scheduleSwitch.setOnCheckedChangeListener { _, checked -> prefs.scheduleEnabled = checked }
        binding.scheduleWindowRow.setOnClickListener { pickScheduleWindow() }
        binding.fakeCoverSwitch.setOnCheckedChangeListener { _, checked -> prefs.fakeCoverEnabled = checked }
        binding.rowPrivacy.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.privacy_policy_url))))
        }

        binding.biometricSwitch.setOnCheckedChangeListener { _, checked ->
            prefs.biometricEnabled = checked
        }
        binding.scrambleSwitch.setOnCheckedChangeListener { _, checked ->
            prefs.scrambleKeypad = checked
        }
        binding.autolockNewSwitch.setOnCheckedChangeListener { _, checked ->
            prefs.autoLockNewApps = checked
        }
        binding.intruderSwitch.setOnCheckedChangeListener { _, checked -> onIntruderToggled(checked) }
        binding.intruderPhotosRow.setOnClickListener {
            startActivity(Intent(this, IntrudersActivity::class.java))
        }
        binding.vaultRow.setOnClickListener {
            startActivity(Intent(this, VaultActivity::class.java))
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
        binding.scrambleSwitch.isChecked = prefs.scrambleKeypad
        binding.autolockNewSwitch.isChecked = prefs.autoLockNewApps
        binding.intruderSwitch.setOnCheckedChangeListener(null)
        binding.intruderSwitch.isChecked =
            prefs.intruderSelfieEnabled && IntruderManager.hasCameraPermission(this)
        binding.intruderSwitch.setOnCheckedChangeListener { _, checked -> onIntruderToggled(checked) }
        binding.intruderPhotosSubtitle.text =
            getString(R.string.intruder_photos_count, IntruderManager.list(this).size)
        binding.vaultSubtitle.text = getString(R.string.vault_count, VaultManager.count(this))
        binding.disguiseSubtitle.text = getString(DisguiseManager.current(this).labelRes)
        binding.fakeCoverSwitch.isChecked = prefs.fakeCoverEnabled
        binding.scheduleSwitch.isChecked = prefs.scheduleEnabled
        binding.scheduleWindowSubtitle.text = getString(
            R.string.schedule_window_value,
            minutesToText(prefs.scheduleStartMinutes),
            minutesToText(prefs.scheduleEndMinutes)
        )
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

    private fun minutesToText(minutes: Int): String =
        String.format(java.util.Locale.getDefault(), "%02d:%02d", minutes / 60, minutes % 60)

    private fun pickScheduleWindow() {
        val is24h = android.text.format.DateFormat.is24HourFormat(this)
        val start = prefs.scheduleStartMinutes
        android.app.TimePickerDialog(this, { _, h, m ->
            prefs.scheduleStartMinutes = h * 60 + m
            val end = prefs.scheduleEndMinutes
            android.app.TimePickerDialog(this, { _, h2, m2 ->
                prefs.scheduleEndMinutes = h2 * 60 + m2
                binding.scheduleWindowSubtitle.text = getString(
                    R.string.schedule_window_value,
                    minutesToText(prefs.scheduleStartMinutes),
                    minutesToText(prefs.scheduleEndMinutes)
                )
            }, end / 60, end % 60, is24h).show()
        }, start / 60, start % 60, is24h).show()
    }

    private fun showDisguiseDialog() {
        val labels = DisguiseManager.options.map { getString(it.labelRes) }.toTypedArray()
        val checked = DisguiseManager.options.indexOfFirst {
            it.alias == DisguiseManager.current(this).alias
        }.coerceAtLeast(0)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.disguise_title)
            .setSingleChoiceItems(labels, checked) { dialog, which ->
                DisguiseManager.apply(this, DisguiseManager.options[which].alias)
                binding.disguiseSubtitle.text = labels[which]
                dialog.dismiss()
                android.widget.Toast.makeText(this, R.string.disguise_applied, android.widget.Toast.LENGTH_LONG).show()
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

    private fun onIntruderToggled(enable: Boolean) {
        if (enable && !IntruderManager.hasCameraPermission(this)) {
            cameraPermLauncher.launch(android.Manifest.permission.CAMERA)
            return
        }
        prefs.intruderSelfieEnabled = enable
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
