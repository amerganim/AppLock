package com.amerganim.lockapp

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.hypot
import kotlin.math.min

/**
 * A self-contained 3x3 pattern lock. Reports the connected dot indices (0..8,
 * row-major) once the finger lifts. Supports an error state (red) and clearing.
 */
class PatternLockView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    var onPatternDetected: ((List<Int>) -> Unit)? = null

    private val selected = mutableListOf<Int>()
    private var curX = 0f
    private var curY = 0f
    private var drawing = false
    private var error = false
    private var inputEnabled = true

    private val normalColor = Color.parseColor("#546E7A")
    private val activeColor = Color.parseColor("#5C6BC0")
    private val errorColor = Color.parseColor("#E53935")

    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = 12f
    }

    private fun cellSize() = min(width, height) / 3f
    private fun dotCenterX(index: Int) = (index % 3 + 0.5f) * cellSize() +
        (width - min(width, height)) / 2f
    private fun dotCenterY(index: Int) = (index / 3 + 0.5f) * cellSize() +
        (height - min(width, height)) / 2f

    fun setEnabledInput(enabled: Boolean) {
        inputEnabled = enabled
    }

    fun clearPattern() {
        selected.clear()
        error = false
        drawing = false
        invalidate()
    }

    /** Briefly show the pattern in red to signal a wrong/too-short attempt. */
    fun showError() {
        error = true
        invalidate()
        postDelayed({ clearPattern() }, 600)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!inputEnabled) return false
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                clearPattern()
                drawing = true
                handleTouch(event.x, event.y)
            }
            MotionEvent.ACTION_MOVE -> if (drawing) {
                curX = event.x
                curY = event.y
                handleTouch(event.x, event.y)
                invalidate()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> if (drawing) {
                drawing = false
                if (selected.isNotEmpty()) {
                    curX = dotCenterX(selected.last())
                    curY = dotCenterY(selected.last())
                    onPatternDetected?.invoke(selected.toList())
                }
                invalidate()
            }
        }
        return true
    }

    private fun handleTouch(x: Float, y: Float) {
        val hit = (0..8).firstOrNull { i ->
            hypot(x - dotCenterX(i), y - dotCenterY(i)) < cellSize() * 0.3f
        } ?: return
        if (selected.contains(hit)) return
        // Pull in any dot the line jumps over (e.g. 0 -> 2 also selects 1).
        if (selected.isNotEmpty()) {
            val last = selected.last()
            val mid = middleDot(last, hit)
            if (mid != -1 && !selected.contains(mid)) selected.add(mid)
        }
        selected.add(hit)
    }

    private fun middleDot(a: Int, b: Int): Int {
        val ra = a / 3; val ca = a % 3
        val rb = b / 3; val cb = b % 3
        if ((ra + rb) % 2 == 0 && (ca + cb) % 2 == 0) {
            return (ra + rb) / 2 * 3 + (ca + cb) / 2
        }
        return -1
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val color = if (error) errorColor else activeColor
        linePaint.color = color

        // connecting lines between selected dots
        for (i in 0 until selected.size - 1) {
            canvas.drawLine(
                dotCenterX(selected[i]), dotCenterY(selected[i]),
                dotCenterX(selected[i + 1]), dotCenterY(selected[i + 1]), linePaint
            )
        }
        // trailing segment to the finger
        if (drawing && selected.isNotEmpty()) {
            canvas.drawLine(
                dotCenterX(selected.last()), dotCenterY(selected.last()),
                curX, curY, linePaint
            )
        }

        val r = cellSize() * 0.12f
        for (i in 0..8) {
            val active = selected.contains(i)
            dotPaint.color = if (active) color else normalColor
            canvas.drawCircle(dotCenterX(i), dotCenterY(i), r, dotPaint)
            if (active) {
                ringPaint.color = color
                canvas.drawCircle(dotCenterX(i), dotCenterY(i), r * 2f, ringPaint)
            }
        }
    }

    companion object {
        /** Encode selected indices as the stored credential string, e.g. "0-1-2-5". */
        fun encode(indices: List<Int>): String = indices.joinToString("-")
    }
}
