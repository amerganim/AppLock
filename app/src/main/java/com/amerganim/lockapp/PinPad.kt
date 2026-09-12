package com.amerganim.lockapp

import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import com.amerganim.lockapp.databinding.LayoutKeypadBinding
import com.amerganim.lockapp.databinding.ViewPinDotsBinding

/**
 * Drives a shared PIN entry: wires the numeric keypad and renders the dot
 * indicators. Reused by both [SetupLockActivity] and [LockScreenActivity].
 *
 * A PIN is [CredentialManager.PIN_MIN_LENGTH] to [CredentialManager.PIN_MAX_LENGTH]
 * digits, which gives two ways to submit:
 *  - [autoSubmitLength] set — unlocking, where the saved length is known: the PIN is
 *    checked the moment it reaches that length, so there is nothing extra to press.
 *  - null — choosing a PIN, where the length is the user's to pick: the ✓ key submits,
 *    and stays disabled until the minimum number of digits has been entered.
 *
 * One dot is shown per digit typed rather than a row of empty slots, so the length of
 * the PIN is never on display for someone watching over a shoulder.
 */
class PinPad(
    private val keypad: LayoutKeypadBinding,
    private val dots: ViewPinDotsBinding,
    scramble: Boolean = false,
    private val autoSubmitLength: Int? = null,
    private val onComplete: (String) -> Unit
) {
    private val entry = StringBuilder()
    private val keys: List<View>
    private val dotSizePx: Int
    private val dotMarginPx: Int
    private var inputEnabled = true

    init {
        val density = dots.root.resources.displayMetrics.density
        dotSizePx = (DOT_SIZE_DP * density).toInt()
        dotMarginPx = (DOT_MARGIN_DP * density).toInt()

        // Button positions in visual order; the digits they carry can be shuffled.
        val buttons = listOf(
            keypad.btn1, keypad.btn2, keypad.btn3,
            keypad.btn4, keypad.btn5, keypad.btn6,
            keypad.btn7, keypad.btn8, keypad.btn9, keypad.btn0
        )
        val digits = (1..9).toList() + 0
        val assigned = if (scramble) digits.shuffled() else digits
        buttons.forEachIndexed { i, button ->
            val digit = assigned[i]
            button.text = digit.toString()
            button.setOnClickListener {
                it.tap()
                append('0' + digit)
            }
        }
        keypad.btnDelete.setOnClickListener {
            it.tap()
            delete()
        }
        keypad.btnOk.visibility = if (autoSubmitLength == null) View.VISIBLE else View.INVISIBLE
        keypad.btnOk.setOnClickListener {
            it.tap()
            submit()
        }
        keys = buttons + keypad.btnDelete + keypad.btnOk
        render()
    }

    /** Block entry (used while a wrong-attempt cooldown is running). */
    fun setInputEnabled(enabled: Boolean) {
        inputEnabled = enabled
        keys.forEach { it.isEnabled = enabled }
        render()
    }

    private fun append(c: Char) {
        if (!inputEnabled) return
        if (entry.length >= CredentialManager.PIN_MAX_LENGTH) return
        entry.append(c)
        render()
        if (autoSubmitLength != null && entry.length == autoSubmitLength) {
            onComplete(entry.toString())
        }
    }

    private fun delete() {
        if (!inputEnabled) return
        if (entry.isNotEmpty()) {
            entry.deleteCharAt(entry.length - 1)
            render()
        }
    }

    private fun submit() {
        if (!inputEnabled) return
        val value = entry.toString()
        if (!CredentialManager.isValidPinLength(value.length)) return
        onComplete(value)
    }

    /** Clear the current entry (e.g. after a wrong PIN). */
    fun reset() {
        entry.setLength(0)
        render()
    }

    private fun render() {
        val row = dots.root
        while (row.childCount < entry.length) row.addView(newDot())
        while (row.childCount > entry.length) row.removeViewAt(row.childCount - 1)
        keypad.btnOk.isEnabled = inputEnabled && CredentialManager.isValidPinLength(entry.length)
    }

    private fun newDot(): ImageView = ImageView(dots.root.context).apply {
        layoutParams = LinearLayout.LayoutParams(dotSizePx, dotSizePx).apply {
            setMargins(dotMarginPx, dotMarginPx, dotMarginPx, dotMarginPx)
        }
        setImageResource(R.drawable.dot_filled)
    }

    /** Confirm each keypress by touch, the way the system keyguard does. */
    private fun View.tap() {
        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    private companion object {
        const val DOT_SIZE_DP = 18
        const val DOT_MARGIN_DP = 10
    }
}
