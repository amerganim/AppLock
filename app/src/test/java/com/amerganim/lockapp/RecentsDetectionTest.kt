package com.amerganim.lockapp

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [AppLockService.isRecentsScreen], which backs the optional
 * "Lock the Recents screen" setting.
 *
 * The dangerous failure here is a false positive: the home screen shares the launcher
 * package, so anything too loose would put the lock prompt over the user's home screen.
 */
class RecentsDetectionTest {

    private val launcher = "com.sec.android.app.launcher"

    @Test
    fun detectsTheSamsungTaskSwitcher() {
        // Observed on a Galaxy A15 (Android 16).
        assertTrue(
            AppLockService.isRecentsScreen(
                pkg = launcher,
                className = "com.android.quickstep.RecentsActivity",
                launcherPackage = launcher,
            )
        )
    }

    @Test
    fun detectsASystemUiHostedSwitcher() {
        assertTrue(
            AppLockService.isRecentsScreen(
                pkg = "com.android.systemui",
                className = "com.android.systemui.recents.RecentsActivity",
                launcherPackage = launcher,
            )
        )
    }

    /** The home screen must never be mistaken for the switcher. */
    @Test
    fun ignoresTheHomeScreen() {
        assertFalse(
            AppLockService.isRecentsScreen(
                pkg = launcher,
                className = "com.sec.android.app.launcher.activities.LauncherActivity",
                launcherPackage = launcher,
            )
        )
    }

    /** Samsung ships this one; "recent" singular must not match "recents". */
    @Test
    fun ignoresOtherLauncherActivitiesThatMentionRecent() {
        assertFalse(
            AppLockService.isRecentsScreen(
                pkg = launcher,
                className = "com.samsung.app.honeyspace.edge.fromrecent.FromRecentActivity",
                launcherPackage = launcher,
            )
        )
    }

    /** A third-party app with an overview-looking screen is not the task switcher. */
    @Test
    fun ignoresOrdinaryAppsWithSimilarClassNames() {
        assertFalse(
            AppLockService.isRecentsScreen(
                pkg = "com.example.notes",
                className = "com.example.notes.RecentsActivity",
                launcherPackage = launcher,
            )
        )
    }

    /**
     * Where the switcher is a state of the launcher activity rather than its own
     * activity, no class name is reported and the feature simply stays inactive.
     */
    @Test
    fun staysFalseWithoutAClassName() {
        assertFalse(
            AppLockService.isRecentsScreen(
                pkg = launcher,
                className = null,
                launcherPackage = launcher,
            )
        )
    }

    @Test
    fun staysFalseWhenTheLauncherCouldNotBeResolved() {
        assertFalse(
            AppLockService.isRecentsScreen(
                pkg = launcher,
                className = "com.android.quickstep.RecentsActivity",
                launcherPackage = null,
            )
        )
    }
}
