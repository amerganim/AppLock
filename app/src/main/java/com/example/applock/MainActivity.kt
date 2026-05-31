package com.example.applock

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
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

    /** True while we are the ones launching the lock screen (so onStop must not
     *  reset the authenticated flag). */
    private var launchingLock = false
    private var appsLoaded = false

    private val notifPermLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            refreshPermissionUi()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        pinManager = PinManager(this)
        prefs = LockPrefs(this)

        binding.appsList.layoutManager = LinearLayoutManager(this)

        binding.permUsage.setOnClickListener {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }
        binding.permOverlay.setOnClickListener {
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
        if (enable) {
            if (!Permissions.hasRequired(this)) {
                Toast.makeText(this, R.string.permissions_needed, Toast.LENGTH_SHORT).show()
                binding.protectionSwitch.isChecked = false
                refreshPermissionUi()
                return
            }
            prefs.protectionEnabled = true
            AppLockService.start(this)
        } else {
            prefs.protectionEnabled = false
            AppLockService.stop(this)
        }
        updateProtectionLabel(enable)
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
        if (prefs.protectionEnabled && Permissions.hasRequired(this)) {
            AppLockService.start(this)
        }
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
