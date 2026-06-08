package com.example.applock

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.applock.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Home screen. PIN-gated. Lets the user grant the required permissions, turn
 * protection on/off, and pick which apps to lock.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var pinManager: PinManager
    private lateinit var prefs: LockPrefs
    private lateinit var dpm: DevicePolicyManager
    private lateinit var adminComponent: ComponentName

    /** True while we are the ones launching the lock screen (so onStop must not
     *  reset the authenticated flag). */
    private var launchingLock = false
    private var appsLoaded = false

    private val notifPermLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            refreshPermissionUi()
        }

    private val adminEnableLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            // Regardless of the result, re-sync the tamper UI to reflect reality.
            syncTamperUi()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        pinManager = PinManager(this)
        prefs = LockPrefs(this)
        dpm = getSystemService(DevicePolicyManager::class.java)
        adminComponent = AdminReceiver.component(this)

        binding.appsList.layoutManager = LinearLayoutManager(this)

        binding.permUsage.setOnClickListener {
            allowSettingsTemporarily()
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }
        binding.permOverlay.setOnClickListener {
            allowSettingsTemporarily()
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
        }
        binding.permNotifications.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notifPermLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            } else {
                startActivity(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                )
            }
        }

        binding.protectionSwitch.setOnCheckedChangeListener { _, checked ->
            onProtectionToggled(checked)
        }
        binding.tamperSwitch.setOnCheckedChangeListener { _, checked ->
            onTamperToggled(checked)
        }
        binding.biometricSwitch.setOnCheckedChangeListener { _, checked ->
            prefs.biometricEnabled = checked
        }
    }

    override fun onResume() {
        super.onResume()

        if (!pinManager.isPinSet()) {
            startActivity(Intent(this, SetPinActivity::class.java))
            finish()
            return
        }

        if (!LockState.settingsAuthed) {
            // Require the PIN before revealing settings.
            binding.content.visibility = View.INVISIBLE
            launchingLock = true
            startActivity(
                Intent(this, LockScreenActivity::class.java)
                    .putExtra(LockScreenActivity.EXTRA_PACKAGE, packageName)
            )
            return
        }

        binding.content.visibility = View.VISIBLE
        refreshPermissionUi()
        syncProtectionSwitch()
        syncTamperUi()
        syncBiometricUi()
        ensureServiceRunning()
        if (!appsLoaded) loadApps()
    }

    override fun onStop() {
        super.onStop()
        if (launchingLock) {
            // We popped the lock screen ourselves; keep the session.
            launchingLock = false
        } else {
            // Genuinely backgrounded — require the PIN again next time.
            LockState.settingsAuthed = false
        }
    }

    private fun onProtectionToggled(enable: Boolean) {
        if (enable && !Permissions.hasRequired(this)) {
            Toast.makeText(this, R.string.permissions_needed, Toast.LENGTH_SHORT).show()
            binding.protectionSwitch.isChecked = false
            refreshPermissionUi()
            return
        }
        prefs.protectionEnabled = enable
        applyServiceState()
        updateProtectionLabel(enable)
    }

    /** Start the monitor if either protection or tamper-locking needs it; otherwise stop it. */
    private fun applyServiceState() {
        if ((prefs.protectionEnabled || prefs.antiUninstallEnabled) && Permissions.hasRequired(this)) {
            AppLockService.start(this)
        } else {
            AppLockService.stop(this)
        }
    }

    private fun syncProtectionSwitch() {
        val on = prefs.protectionEnabled
        binding.protectionSwitch.setOnCheckedChangeListener(null)
        binding.protectionSwitch.isChecked = on
        binding.protectionSwitch.setOnCheckedChangeListener { _, checked ->
            onProtectionToggled(checked)
        }
        updateProtectionLabel(on)
    }

    private fun updateProtectionLabel(on: Boolean) {
        binding.protectionStatus.setText(
            if (on) R.string.protection_on else R.string.protection_off
        )
    }

    private fun ensureServiceRunning() {
        val needed = prefs.protectionEnabled || prefs.antiUninstallEnabled
        if (needed && Permissions.hasRequired(this)) {
            AppLockService.start(this)
        }
    }

    private fun onTamperToggled(enable: Boolean) {
        prefs.antiUninstallEnabled = enable
        // Anti-uninstall locking only bites while the monitor runs.
        applyServiceState()
        if (enable) {
            if (!dpm.isAdminActive(adminComponent)) {
                // Don't let the freshly-locked Settings block the admin consent screen.
                allowSettingsTemporarily()
                val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                    .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
                    .putExtra(
                        DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                        getString(R.string.admin_explanation)
                    )
                adminEnableLauncher.launch(intent)
            }
        } else {
            if (dpm.isAdminActive(adminComponent)) {
                dpm.removeActiveAdmin(adminComponent)
            }
        }
        syncTamperUi()
    }

    private fun syncTamperUi() {
        val on = prefs.antiUninstallEnabled
        binding.tamperSwitch.setOnCheckedChangeListener(null)
        binding.tamperSwitch.isChecked = on
        binding.tamperSwitch.setOnCheckedChangeListener { _, checked ->
            onTamperToggled(checked)
        }
        val adminActive = dpm.isAdminActive(adminComponent)
        binding.adminStatus.setText(
            if (adminActive) R.string.admin_active else R.string.admin_inactive
        )
    }

    private fun syncBiometricUi() {
        // Only offer the toggle when the device actually has usable biometric hardware.
        val status = BiometricManager.from(this).canAuthenticate(BIOMETRIC_WEAK)
        val hasHardware = status != BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE &&
            status != BiometricManager.BIOMETRIC_STATUS_UNKNOWN
        binding.biometricRow.visibility = if (hasHardware) View.VISIBLE else View.GONE
        binding.biometricSwitch.setOnCheckedChangeListener(null)
        binding.biometricSwitch.isChecked = prefs.biometricEnabled
        binding.biometricSwitch.setOnCheckedChangeListener { _, checked ->
            prefs.biometricEnabled = checked
        }
    }

    /** Briefly mark the system Settings/installer screens as unlocked so our own
     *  permission and device-admin flows aren't interrupted by the PIN prompt. */
    private fun allowSettingsTemporarily() {
        LockPrefs.PROTECTED_SYSTEM_PACKAGES.forEach { LockState.markUnlocked(it) }
    }

    private fun refreshPermissionUi() {
        binding.permUsage.visibility =
            if (Permissions.hasUsageAccess(this)) View.GONE else View.VISIBLE
        binding.permOverlay.visibility =
            if (Permissions.hasOverlay(this)) View.GONE else View.VISIBLE
        binding.permNotifications.visibility =
            if (Permissions.hasNotifications(this)) View.GONE else View.VISIBLE
    }

    private fun loadApps() {
        binding.progress.visibility = View.VISIBLE
        lifecycleScope.launch {
            val entries = withContext(Dispatchers.IO) { queryLaunchableApps() }
            appsLoaded = true
            binding.progress.visibility = View.GONE
            binding.appsList.adapter = AppListAdapter(
                items = entries,
                isLocked = { prefs.isLocked(it) },
                onToggle = { pkg, locked -> prefs.setLocked(pkg, locked) }
            )
        }
    }

    private fun queryLaunchableApps(): List<AppEntry> {
        val pm = packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0)
            .asSequence()
            .map { it.activityInfo.packageName }
            .filter { it != packageName }
            .distinct()
            .mapNotNull { pkg ->
                runCatching {
                    val info = pm.getApplicationInfo(pkg, 0)
                    AppEntry(
                        packageName = pkg,
                        label = pm.getApplicationLabel(info).toString(),
                        icon = pm.getApplicationIcon(info)
                    )
                }.getOrNull()
            }
            .sortedBy { it.label.lowercase() }
            .toList()
    }
}
