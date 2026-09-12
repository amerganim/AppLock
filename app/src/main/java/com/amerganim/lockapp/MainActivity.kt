package com.amerganim.lockapp

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.amerganim.lockapp.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Home screen, gated behind the user's lock. Grant permissions, turn protection
 * on/off, search and pick which apps to lock. Everything else lives in Settings.
 */
class MainActivity : SecureActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var credential: CredentialManager
    private lateinit var prefs: LockPrefs

    private var appsLoaded = false

    private var allApps: List<AppEntry> = emptyList()
    private var adapter: AppListAdapter? = null

    private val notifPermLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            refreshPermissionUi()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        credential = CredentialManager(this)
        prefs = LockPrefs(this)

        binding.appsList.layoutManager = LinearLayoutManager(this)
        binding.toolbar.setOnMenuItemClickListener { item ->
            if (item.itemId == R.id.action_settings) {
                startActivity(Intent(this, SettingsActivity::class.java)); true
            } else false
        }

        binding.permUsage.setOnClickListener {
            allowSettingsTemporarily()
            openSettingsScreen(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }
        binding.permOverlay.setOnClickListener {
            allowSettingsTemporarily()
            openSettingsScreen(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            )
        }
        binding.permNotifications.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notifPermLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            } else {
                openSettingsScreen(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                )
            }
        }
        binding.permBattery.setOnClickListener {
            allowSettingsTemporarily()
            openSettingsScreen(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }

        binding.protectionSwitch.setOnCheckedChangeListener { _, checked -> onProtectionToggled(checked) }
        binding.searchInput.doAfterTextChanged { applyFilter(it?.toString().orEmpty()) }
    }

    /** Send first-run users to onboarding instead of prompting for a lock they have not set. */
    override fun onBeforeAuthCheck(): Boolean {
        if (credential.isCredentialSet()) return true
        startActivity(Intent(this, WelcomeActivity::class.java))
        finish()
        return false
    }

    override fun onAuthenticated() {
        refreshPermissionUi()
        syncProtectionSwitch()
        AppLockService.sync(this)
        if (appsLoaded) {
            // The locked-app list may have changed while we were away (auto-lock on
            // install, or a lock toggled from another screen).
            applyFilter(binding.searchInput.text?.toString().orEmpty())
        } else {
            loadApps()
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
        AppLockService.sync(this)
        updateProtectionLabel(enable)
    }

    private fun syncProtectionSwitch() {
        val on = prefs.protectionEnabled
        binding.protectionSwitch.setOnCheckedChangeListener(null)
        binding.protectionSwitch.isChecked = on
        binding.protectionSwitch.setOnCheckedChangeListener { _, checked -> onProtectionToggled(checked) }
        updateProtectionLabel(on)
    }

    private fun updateProtectionLabel(on: Boolean) {
        binding.protectionStatus.setText(if (on) R.string.protection_on else R.string.protection_off)
    }

    /** Briefly mark the system Settings/installer screens as unlocked so our own
     *  permission flows aren't interrupted by the PIN prompt. */
    private fun allowSettingsTemporarily() {
        LockPrefs.PROTECTED_SYSTEM_PACKAGES.forEach { LockState.markUnlocked(it) }
    }

    /** Not every OEM ships every settings screen; never crash because one is missing. */
    private fun openSettingsScreen(intent: Intent) {
        runCatching { startActivity(intent) }.onFailure {
            Toast.makeText(this, R.string.settings_screen_unavailable, Toast.LENGTH_LONG).show()
        }
    }

    private fun refreshPermissionUi() {
        binding.permUsage.visibility =
            if (Permissions.hasUsageAccess(this)) View.GONE else View.VISIBLE
        binding.permOverlay.visibility =
            if (Permissions.hasOverlay(this)) View.GONE else View.VISIBLE
        binding.permNotifications.visibility =
            if (Permissions.hasNotifications(this)) View.GONE else View.VISIBLE
        // Battery optimisation is the usual reason protection "randomly stops" on
        // aggressive OEMs: the system kills the monitoring service in the background.
        binding.permBattery.visibility =
            if (isBatteryOptimized()) View.VISIBLE else View.GONE
    }

    private fun isBatteryOptimized(): Boolean {
        val power = getSystemService(PowerManager::class.java) ?: return false
        return !power.isIgnoringBatteryOptimizations(packageName)
    }

    private fun loadApps() {
        binding.progress.visibility = View.VISIBLE
        lifecycleScope.launch {
            allApps = withContext(Dispatchers.IO) { queryLaunchableApps() }
            appsLoaded = true
            binding.progress.visibility = View.GONE
            adapter = AppListAdapter(
                items = allApps.toMutableList(),
                isLocked = { prefs.isLocked(it) },
                onToggle = { pkg, locked -> prefs.setLocked(pkg, locked) }
            )
            binding.appsList.adapter = adapter
            applyFilter(binding.searchInput.text?.toString().orEmpty())
        }
    }

    private fun applyFilter(query: String) {
        val q = query.trim().lowercase()
        val filtered = if (q.isEmpty()) allApps
        else allApps.filter { it.label.lowercase().contains(q) }
        adapter?.submit(filtered)
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
