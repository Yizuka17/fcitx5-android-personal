/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.navigation

import android.annotation.SuppressLint
import android.content.Context
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import org.fcitx.fcitx5.android.data.InputFeedbacks
import org.fcitx.fcitx5.android.data.prefs.AppPrefs
import splitties.dimensions.dp
import kotlin.math.abs
import kotlin.math.hypot

/** A tap arms only the next touch. Selection and the action strip share that touch until release. */
@SuppressLint("ViewConstructor") // Created in code with gesture callbacks.
internal open class BottomBarGestureView(
    context: Context,
    private val moveCursor: (Int) -> Unit,
    private val extendSelection: (Int) -> Unit,
    private val showActions: () -> Unit,
    private val pointAtAction: (Float, Float) -> Unit,
    private val finishActions: (Boolean) -> Unit,
    private val beginSelection: () -> Unit = {},
) : View(context) {
    private enum class Mode { Idle, Cursor, Selection, Actions }
    private var mode = Mode.Idle
    private var lastTapUp = Long.MIN_VALUE
    private var tapX = 0f
    private var tapY = 0f
    private var downX = 0f
    private var downY = 0f
    private var stepX = 0f
    private var dragged = false
    private var selectionStarted = false
    private val config = ViewConfiguration.get(context)
    var contentScale = 1f

    init {
        isClickable = true
        isSoundEffectsEnabled = false
        isHapticFeedbackEnabled = false
    }

    fun cancelGestures() {
        if (mode == Mode.Actions) finishActions(false)
        mode = Mode.Idle
        lastTapUp = Long.MIN_VALUE
        parent?.requestDisallowInterceptTouchEvent(false)
    }

    override fun setEnabled(enabled: Boolean) {
        if (!enabled) cancelGestures()
        super.setEnabled(enabled)
    }

    override fun onDetachedFromWindow() {
        cancelGestures()
        super.onDetachedFromWindow()
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                InputFeedbacks.hapticFeedback(this)
                val sinceTap = if (lastTapUp == Long.MIN_VALUE) Long.MAX_VALUE else event.eventTime - lastTapUp
                val secondTouch = sinceTap in 0..ViewConfiguration.getDoubleTapTimeout().toLong() &&
                    hypot(event.x - tapX, event.y - tapY) <= config.scaledDoubleTapSlop
                lastTapUp = Long.MIN_VALUE
                mode = if (secondTouch) Mode.Selection else Mode.Cursor
                downX = event.x
                downY = event.y
                stepX = event.x
                dragged = false
                selectionStarted = false
                parent?.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_MOVE -> {
                if (mode == Mode.Idle) return true
                if (hypot(event.x - downX, event.y - downY) > config.scaledTouchSlop) dragged = true
                if (mode == Mode.Selection && dragged && !selectionStarted) {
                    selectionStarted = true
                    beginSelection()
                }
                if (mode == Mode.Selection && event.y < downY - context.dp(24f) * contentScale) {
                    mode = Mode.Actions
                    showActions()
                }
                if (mode == Mode.Actions) {
                    pointAtAction(event.rawX, event.rawY)
                } else {
                    val step = context.dp(10f)
                    val count = ((event.x - stepX) / step).toInt()
                    if (count != 0) {
                        stepX += count * step
                        if (mode == Mode.Selection) extendSelection(count) else moveCursor(count)
                        if (AppPrefs.getInstance().keyboard.hapticOnRepeat.getValue()) {
                            repeat(abs(count)) { InputFeedbacks.hapticFeedback(this) }
                        }
                    }
                }
            }
            MotionEvent.ACTION_UP -> {
                InputFeedbacks.hapticFeedback(this, longPress = true, keyUp = true)
                if (mode == Mode.Actions) {
                    pointAtAction(event.rawX, event.rawY)
                    finishActions(true)
                } else if (mode == Mode.Cursor && !dragged &&
                    event.eventTime - event.downTime < ViewConfiguration.getLongPressTimeout()) {
                    lastTapUp = event.eventTime
                    tapX = event.x
                    tapY = event.y
                    performClick()
                }
                mode = Mode.Idle
                parent?.requestDisallowInterceptTouchEvent(false)
            }
            MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_DOWN -> {
                cancelGestures()
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
        return true
    }
}
