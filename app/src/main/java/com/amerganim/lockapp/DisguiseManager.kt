package com.amerganim.lockapp

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/**
 * Switches the app's launcher icon + name by enabling one of several
 * `<activity-alias>` entries (and disabling the rest) so LockApp can hide in plain
 * sight on the home screen.
 */
object DisguiseManager {

    data class Option(val alias: String, val labelRes: Int, val iconRes: Int)

    val options = listOf(
        Option("LauncherDefault", R.string.disguise_default, R.mipmap.ic_launcher),
        Option("LauncherCalculator", R.string.disguise_calculator, R.drawable.ic_disg_calculator),
        Option("LauncherClock", R.string.disguise_clock, R.drawable.ic_disg_clock),
        Option("LauncherNotes", R.string.disguise_notes, R.drawable.ic_disg_notes)
    )

    fun current(context: Context): Option {
        val active = LockPrefs(context).disguiseAlias
        return options.firstOrNull { it.alias == active } ?: options[0]
    }

    fun apply(context: Context, chosenAlias: String) {
        val pm = context.packageManager
        options.forEach { opt ->
            val state = if (opt.alias == chosenAlias) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            }
            pm.setComponentEnabledSetting(
                ComponentName(context, "${context.packageName}.${opt.alias}"),
                state,
                PackageManager.DONT_KILL_APP
            )
        }
        LockPrefs(context).disguiseAlias = chosenAlias
    }
}
