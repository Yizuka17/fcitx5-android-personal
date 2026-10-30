/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.core.graphics.ColorUtils
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.data.theme.Theme
import splitties.dimensions.dp
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

/** Transparent outer gutter. Both strokes run GAP dp outside the keyboard surface. */
@SuppressLint("ViewConstructor") // Created in code with the current theme and corner geometry.
internal class FloatingKeyboardControls(
    context: Context,
    theme: Theme,
    private val cornerRadius: () -> Float,
) : ViewGroup(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = if (ColorUtils.calculateLuminance(theme.accentKeyBackgroundColor) < .4) {
            ColorUtils.blendARGB(theme.accentKeyBackgroundColor, Color.WHITE, .35f)
        } else theme.accentKeyBackgroundColor
        style = Paint.Style.STROKE
        strokeWidth = context.dp(5f)
        strokeCap = Paint.Cap.ROUND
    }
    private val gap = context.dp(GAP_DP)
    private val hit = context.dp(12f)
    private var radius = 0f
    private val arcBounds = RectF()
    val dragHandle = object : View(context) {
        override fun onDraw(canvas: Canvas) {
            canvas.drawLine(width / 2f - dp(24f), gap, width / 2f + dp(24f), gap, paint)
        }
    }.apply {
        isClickable = true
        contentDescription = context.getString(R.string.floating_keyboard_drag)
    }
    val resizeHandle = object : View(context) {
        override fun onDraw(canvas: Canvas) {
            val outer = radius + gap
            arcBounds.set(hit - outer, hit - outer, hit + outer, hit + outer)
            canvas.drawArc(arcBounds, 15f, 60f, false, paint)
        }

        override fun dispatchTouchEvent(event: MotionEvent): Boolean {
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                val x = event.x - hit
                val y = event.y - hit
                // Exclude the inside of the arc: the keyboard's bottom-right button remains usable.
                if (x < -hit || y < -hit || abs(hypot(x, y) - radius - gap) > hit) return false
            }
            return super.dispatchTouchEvent(event)
        }
    }.apply {
        isClickable = true
        contentDescription = context.getString(R.string.floating_keyboard_resize)
    }

    init {
        clipChildren = false
        clipToPadding = false
        addView(dragHandle)
        addView(resizeHandle)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.getSize(heightMeasureSpec))
        radius = cornerRadius()
        val arcSize = (radius + gap + 2 * hit).roundToInt()
        dragHandle.measure(MeasureSpec.makeMeasureSpec(dp(80), MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(dp(GUTTER_DP), MeasureSpec.EXACTLY))
        resizeHandle.measure(MeasureSpec.makeMeasureSpec(arcSize, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(arcSize, MeasureSpec.EXACTLY))
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val bodyRight = width - dp(GUTTER_DP)
        val bodyBottom = height - dp(GUTTER_DP)
        val dragLeft = (bodyRight - dragHandle.measuredWidth) / 2
        dragHandle.layout(dragLeft, bodyBottom, dragLeft + dragHandle.measuredWidth, height)
        val arcLeft = (bodyRight - radius - hit).roundToInt()
        val arcTop = (bodyBottom - radius - hit).roundToInt()
        resizeHandle.layout(arcLeft, arcTop, arcLeft + resizeHandle.measuredWidth, arcTop + resizeHandle.measuredHeight)
        dragHandle.invalidate()
        resizeHandle.invalidate()
    }

    companion object {
        const val GUTTER_DP = 28
        const val GAP_DP = 12f
    }
}
