/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android

import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import androidx.test.platform.app.InstrumentationRegistry
import org.fcitx.fcitx5.android.data.prefs.AppPrefs
import org.fcitx.fcitx5.android.input.FloatingKeyboardController
import org.fcitx.fcitx5.android.input.FloatingKeyboardSessionState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import splitties.dimensions.dp

class FloatingDragDoubleTapTest {
    @Test
    fun doubleTapDocksButDragLongPressAndCancelDoNot() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val ctx = InstrumentationRegistry.getInstrumentation().targetContext
            val host = FrameLayout(ctx)
            val panel = View(ctx)
            val controls = FrameLayout(ctx)
            val drag = View(ctx).apply { isClickable = true }
            val resize = View(ctx).apply { isClickable = true }
            controls.addView(drag)
            controls.addView(resize)
            host.addView(panel, FrameLayout.LayoutParams(ctx.dp(300), ctx.dp(300)))
            host.addView(controls)
            host.measure(View.MeasureSpec.makeMeasureSpec(ctx.dp(400), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(ctx.dp(800), View.MeasureSpec.EXACTLY))
            host.layout(0, 0, host.measuredWidth, host.measuredHeight)
            val prefs = AppPrefs.getInstance().internal
            val x = prefs.floatingKeyboardX.getValue()
            val y = prefs.floatingKeyboardY.getValue()
            val controller = FloatingKeyboardController(host, panel, controls, drag, resize,
                FloatingKeyboardSessionState(), {}, {}, {}, {})
            var now = SystemClock.uptimeMillis()
            fun send(type: Int, down: Long, at: Long, pos: Float = ctx.dp(30f)) {
                val event = MotionEvent.obtain(down, at, type, pos, pos, 0)
                try { drag.dispatchTouchEvent(event) } finally { event.recycle() }
            }
            fun tap(duration: Long = 20) {
                send(MotionEvent.ACTION_DOWN, now, now)
                send(MotionEvent.ACTION_UP, now, now + duration)
                now += duration + 70
            }
            try {
                controller.start()
                controller.setFloating(true)
                tap()
                assertTrue(controller.isFloating)
                tap()
                assertFalse("Only the second completed tap docks the keyboard", controller.isFloating)

                controller.setFloating(true)
                send(MotionEvent.ACTION_DOWN, now, now)
                send(MotionEvent.ACTION_MOVE, now, now + 20, ctx.dp(80f))
                send(MotionEvent.ACTION_UP, now, now + 40, ctx.dp(80f))
                now += 80
                tap()
                assertTrue("A drag followed by a tap is not a double tap", controller.isFloating)
                now += ViewConfiguration.getDoubleTapTimeout() + 20
                tap(ViewConfiguration.getLongPressTimeout().toLong() + 20)
                tap()
                assertTrue("A long hold cannot arm a double tap", controller.isFloating)
                send(MotionEvent.ACTION_DOWN, now, now)
                send(MotionEvent.ACTION_CANCEL, now, now + 20)
                now += 80
                tap()
                assertTrue("A cancelled touch cannot arm a double tap", controller.isFloating)
            } finally {
                controller.destroy()
                prefs.floatingKeyboardX.setValue(x)
                prefs.floatingKeyboardY.setValue(y)
            }
        }
    }
}
