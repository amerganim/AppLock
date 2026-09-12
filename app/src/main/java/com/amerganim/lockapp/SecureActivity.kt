package com.amerganim.lockapp

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity

/**
 * Base class for every screen that must not be readable without the user's lock:
 * the home screen, settings, the vault and the intruder gallery.
 *
 * Each of them used to rely on being *reached* through an already-unlocked screen,
 * which left two holes: Android can restore any of them straight from the Recents
 * switcher (or after the process was killed), and the lock only ever guarded the way
 * in from the home screen. Here the check happens in [onResume] of every such screen,
 * so however it is entered the content stays hidden until [LockState.settingsAuthed]
 * is set by a successful unlock.
 *
 * The authenticated session covers the whole app and ends when the app leaves the
 * foreground — see [LockState.onActivityStopped], driven by [LockApp] — so moving
 * between these screens never re-prompts.
 */
abstract class SecureActivity : AppCompatActivity() {

    /** Screens with nothing secret yet (first-run setup) can opt out. */
    protected open fun requiresAuth(): Boolean = true

    /**
     * Runs before the auth check. Return false if this screen is navigating somewhere
     * else entirely (e.g. to onboarding) and should not prompt for the lock.
     */
    protected open fun onBeforeAuthCheck(): Boolean = true

    /** Called on every resume once the user is authenticated. */
    protected open fun onAuthenticated() {}

    /** Keeps the credential and private content out of screenshots and recents. */
    protected open val secureWindow: Boolean = true

    private val contentRoot: View? get() = findViewById(android.R.id.content)

    override fun onCreate(savedInstanceState: Bundle?) {
        if (secureWindow) {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
            )
        }
        super.onCreate(savedInstanceState)
    }

    override fun onResume() {
        super.onResume()
        if (!onBeforeAuthCheck()) return

        if (requiresAuth() && !LockState.settingsAuthed) {
            // Hide whatever is already laid out, then ask for the lock over the top.
            contentRoot?.visibility = View.INVISIBLE
            startActivity(
                Intent(this, LockScreenActivity::class.java)
                    .putExtra(LockScreenActivity.EXTRA_PACKAGE, packageName)
                    .addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
            )
            return
        }

        contentRoot?.visibility = View.VISIBLE
        onAuthenticated()
    }
}
