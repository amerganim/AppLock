package com.amerganim.lockapp

import android.app.Activity
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * Keeps content clear of the status bar, navigation bar and display cutout.
 *
 * The app used to sidestep this with `windowOptOutEdgeToEdgeEnforcement`, but that
 * opt-out is ignored from Android 16 for apps targeting SDK 36: the window is drawn
 * edge to edge whether we ask for it or not. Without this the bottom row of the lock
 * screen — "Use fingerprint" and "Forgot?" — sits underneath the navigation bar.
 *
 * Padding the content view rather than the individual layouts means the window
 * background still runs edge to edge behind the bars, which is the look Android wants.
 */
fun Activity.applySystemBarInsets() {
    val content = findViewById<View>(android.R.id.content) ?: return
    ViewCompat.setOnApplyWindowInsetsListener(content) { view, windowInsets ->
        val bars = windowInsets.getInsets(
            WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
        )
        view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
        WindowInsetsCompat.CONSUMED
    }
}
