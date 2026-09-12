package com.amerganim.lockapp

import android.app.Activity
import android.content.res.Configuration
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

/**
 * Keeps content clear of the status bar, navigation bar and display cutout, and keeps
 * the system bar icons readable against whatever is behind them.
 *
 * The app used to sidestep all of this with `windowOptOutEdgeToEdgeEnforcement`, but that
 * opt-out is ignored from Android 16 for apps targeting SDK 36: the window is drawn edge
 * to edge whether we ask for it or not. Without the padding, the bottom row of the lock
 * screen — "Use fingerprint" and "Forgot?" — sits underneath the navigation bar; without
 * the icon appearance, the status bar icons stay white and vanish against a light theme.
 *
 * Padding the content view rather than the individual layouts means the window background
 * still runs edge to edge behind the bars, which is the look Android wants.
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

    // Dark icons on a light background and vice versa. The lock screen and viewer are
    // painted on the dark lock background even in the light theme, and say so through
    // the lockAppDarkBackground theme attribute.
    val darkBackground = obtainStyledAttributes(intArrayOf(R.attr.lockAppDarkBackground)).use {
        it.getBoolean(0, false)
    }
    val nightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
        Configuration.UI_MODE_NIGHT_YES
    val lightBars = !darkBackground && !nightMode
    WindowCompat.getInsetsController(window, window.decorView).apply {
        isAppearanceLightStatusBars = lightBars
        isAppearanceLightNavigationBars = lightBars
    }
}

private inline fun <T> android.content.res.TypedArray.use(block: (android.content.res.TypedArray) -> T): T =
    try {
        block(this)
    } finally {
        recycle()
    }
