/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android

import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import androidx.test.platform.app.InstrumentationRegistry
import org.fcitx.fcitx5.android.input.navigation.BottomBarGestureView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import splitties.dimensions.dp

class BottomBarSelectionGestureTest {
    private class Fixture {
        val cursor = mutableListOf<Int>()
        val selection = mutableListOf<Int>()
        val menus = mutableListOf<String>()
        var selectionStarts = 0
        val view = BottomBarGestureView(
            InstrumentationRegistry.getInstrumentation().targetContext,
            moveCursor = { cursor.add(it) }, extendSelection = { selection.add(it) },
            showActions = { menus.add("show") },
            pointAtAction = { _, _ -> menus.add("point") },
            finishActions = { menus.add(if (it) "execute" else "cancel") },
            beginSelection = { selectionStarts++ },
        ).apply {
            measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(180, View.MeasureSpec.EXACTLY))
            layout(0, 0, measuredWidth, measuredHeight)
        }
        var time = SystemClock.uptimeMillis()
        var down = time
        val x = 540f
        val y = 90f
        val step = view.context.dp(10f)

        fun send(action: Int, dx: Float = 0f, dy: Float = 0f) {
            if (action == MotionEvent.ACTION_DOWN) down = time
            val event = MotionEvent.obtain(down, time, action, x + dx, y + dy, 0)
            try { view.dispatchTouchEvent(event) } finally { event.recycle() }
            time += 30
        }
        fun tap() { send(MotionEvent.ACTION_DOWN); send(MotionEvent.ACTION_UP) }
    }

    private fun check(test: Fixture.() -> Unit) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync { Fixture().apply(test) }
    }

    @Test
    fun ordinarySwipeRemainsCursorMovement() = check {
        send(MotionEvent.ACTION_DOWN)
        send(MotionEvent.ACTION_MOVE, -step * 2.5f)
        send(MotionEvent.ACTION_MOVE, step * .5f)
        send(MotionEvent.ACTION_UP, step * .5f)
        assertEquals(listOf(-2, 2), cursor)
        assertTrue(selection.isEmpty())
        assertTrue(menus.isEmpty())
    }

    @Test
    fun tapThenHoldSelectsFromTheAnchorAndCanReverse() = check {
        tap()
        send(MotionEvent.ACTION_DOWN)
        assertEquals(0, selectionStarts)
        send(MotionEvent.ACTION_MOVE, -step * 3.5f)
        send(MotionEvent.ACTION_MOVE, step * 1.5f)
        send(MotionEvent.ACTION_UP, step * 1.5f)
        assertEquals(listOf(-3, 4), selection)
        assertEquals(1, selectionStarts)
        assertTrue(cursor.isEmpty())
        assertTrue(menus.isEmpty())
    }

    @Test
    fun upwardTurnShowsActionsAndExecutesOnlyOnRelease() = check {
        tap()
        send(MotionEvent.ACTION_DOWN)
        send(MotionEvent.ACTION_MOVE, -step * 2)
        send(MotionEvent.ACTION_MOVE, -step * 2, -view.context.dp(32f))
        send(MotionEvent.ACTION_MOVE, step * 5, -view.context.dp(50f))
        assertEquals(listOf(-2), selection)
        assertEquals(listOf("show", "point", "point"), menus)
        send(MotionEvent.ACTION_UP, step * 5, -view.context.dp(50f))
        assertEquals("execute", menus.last())
    }

    @Test
    fun cancelDisableAndExpiredTapNeverExecuteAnAction() = check {
        tap()
        send(MotionEvent.ACTION_DOWN)
        send(MotionEvent.ACTION_MOVE, 0f, -view.context.dp(32f))
        send(MotionEvent.ACTION_CANCEL)
        assertEquals("cancel", menus.last())
        tap()
        send(MotionEvent.ACTION_DOWN)
        send(MotionEvent.ACTION_MOVE, 0f, -view.context.dp(32f))
        view.isEnabled = false
        assertEquals("cancel", menus.last())
        view.isEnabled = true
        tap()
        time += 1000
        send(MotionEvent.ACTION_DOWN)
        send(MotionEvent.ACTION_MOVE, step * 2.5f)
        send(MotionEvent.ACTION_UP, step * 2.5f)
        assertEquals(listOf(2), cursor)
        assertTrue(selection.isEmpty())
        assertTrue("execute" !in menus)
    }
}
