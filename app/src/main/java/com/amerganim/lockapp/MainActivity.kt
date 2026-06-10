package com.amerganim.lockapp

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
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
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var credential: CredentialManager
    private lateinit var prefs: LockPrefs

    /** True while we are the ones launching the lock screen (so onStop must not
     *  reset the authenticated flag). */
    private var launchingLock = false
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
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }
        binding.permOverlay.setOnClickListener {
            allowSettingsTemporarily()
            startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
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

        binding.protectionSwitch.setOnCheckedChangeListener { _, checked -> onProtectionToggled(checked) }
        binding.searchInput.doAfterTextChanged { applyFilter(it?.toString().orEmpty()) }
    }

    override fun onResume() {
        super.onResume()

        if (!credential.isCredentialSet()) {
            startActivity(Intent(this, WelcomeActivity::class.java))
            finish()
            return
        }

        if (!LockState.settingsAuthed) {
            // Require the lock before revealing the app.
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
        ensureServiceRunning()
        if (!appsLoaded) loadApps()
    }

    override fun onStop() {
        super.onStop()
        if (launchingLock) launchingLock = false else LockState.settingsAuthed = false
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

    private fun ensureServiceRunning() {
        if ((prefs.protectionEnabled || prefs.antiUninstallEnabled) && Permissions.hasRequired(this)) {
            AppLockService.start(this)
        }
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
