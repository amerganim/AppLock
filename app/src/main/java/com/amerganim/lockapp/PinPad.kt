package com.amerganim.lockapp

import android.widget.ImageView
import com.amerganim.lockapp.databinding.LayoutKeypadBinding
import com.amerganim.lockapp.databinding.ViewPinDotsBinding

/**
 * Drives a shared PIN entry: wires the numeric keypad and renders the dot
 * indicators. Reused by both [SetupLockActivity] and [LockScreenActivity].
 */
class PinPad(
    private val keypad: LayoutKeypadBinding,
    dots: ViewPinDotsBinding,
    scramble: Boolean = false,
    private val onComplete: (String) -> Unit
) {
    private val entry = StringBuilder()
    private val dotViews: List<ImageView> = listOf(dots.dot1, dots.dot2, dots.dot3, dots.dot4)

    init {
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
            button.setOnClickListener { append('0' + digit) }
        }
        keypad.btnDelete.setOnClickListener { delete() }
        render()
    }

    private fun append(c: Char) {
        if (entry.length >= CredentialManager.PIN_LENGTH) return
        entry.append(c)
        render()
        if (entry.length == CredentialManager.PIN_LENGTH) {
            val value = entry.toString()
            onComplete(value)
        }
    }

    private fun delete() {
        if (entry.isNotEmpty()) {
            entry.deleteCharAt(entry.length - 1)
            render()
        }
    }

    /** Clear the current entry (e.g. after a wrong PIN). */
    fun reset() {
        entry.setLength(0)
        render()
    }

    private fun render() {
        dotViews.forEachIndexed { i, dot ->
            dot.setImageResource(if (i < entry.length) R.drawable.dot_filled else R.drawable.dot_empty)
        }
    }
}
