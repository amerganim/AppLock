package com.example.applock

import android.widget.ImageView
import com.example.applock.databinding.LayoutKeypadBinding
import com.example.applock.databinding.ViewPinDotsBinding

/**
 * Drives a shared PIN entry: wires the numeric keypad and renders the dot
 * indicators. Reused by both [SetPinActivity] and [LockScreenActivity].
 */
class PinPad(
    private val keypad: LayoutKeypadBinding,
    dots: ViewPinDotsBinding,
    private val onComplete: (String) -> Unit
) {
    private val entry = StringBuilder()
    private val dotViews: List<ImageView> = listOf(dots.dot1, dots.dot2, dots.dot3, dots.dot4)

    init {
        val digits = mapOf(
            keypad.btn0 to '0', keypad.btn1 to '1', keypad.btn2 to '2',
            keypad.btn3 to '3', keypad.btn4 to '4', keypad.btn5 to '5',
            keypad.btn6 to '6', keypad.btn7 to '7', keypad.btn8 to '8',
            keypad.btn9 to '9'
        )
        digits.forEach { (button, digit) -> button.setOnClickListener { append(digit) } }
        keypad.btnDelete.setOnClickListener { delete() }
        render()
    }

    private fun append(c: Char) {
        if (entry.length >= PinManager.PIN_LENGTH) return
        entry.append(c)
        render()
        if (entry.length == PinManager.PIN_LENGTH) {
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
