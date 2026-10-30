/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android

import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import androidx.appcompat.view.ContextThemeWrapper
import androidx.test.platform.app.InstrumentationRegistry
import org.fcitx.fcitx5.android.core.FcitxKeyMapping
import org.fcitx.fcitx5.android.data.prefs.AppPrefs
import org.fcitx.fcitx5.android.data.theme.ThemePreset
import org.fcitx.fcitx5.android.input.keyboard.KeyAction
import org.fcitx.fcitx5.android.input.navigation.BottomBarAction
import org.fcitx.fcitx5.android.input.navigation.ImeBottomBar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import splitties.dimensions.dp

class ImeBottomBarGestureTest {
    private fun checkBar(interactive: Boolean = true, test: (View, MutableList<KeyAction>, MutableList<BottomBarAction>) -> Unit) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val preference = AppPrefs.getInstance().keyboard.bottomBarSwipeMoveCursor
            val oldValue = preference.getValue()
            try {
                preference.setValue(true)
                val context = ContextThemeWrapper(
                    InstrumentationRegistry.getInstrumentation().targetContext, R.style.Theme_FcitxAppTheme
                )
                val cursorActions = mutableListOf<KeyAction>()
                val buttonActions = mutableListOf<BottomBarAction>()
                val bar = ImeBottomBar(
                    context, ThemePreset.PixelDark,
                    BottomBarAction.HideKeyboard, BottomBarAction.SwitchLanguage,
                    onAction = { buttonActions.add(it) }, onPicker = {}, interactive = interactive,
                    onCursorAction = { cursorActions.add(it) },
                )
                val root = bar.root.apply { visibility = View.VISIBLE }
                root.measure(
                    View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(context.dp(ImeBottomBar.HEIGHT), View.MeasureSpec.EXACTLY),
                )
                root.layout(0, 0, root.measuredWidth, root.measuredHeight)
                test(root, cursorActions, buttonActions)
            } finally {
                preference.setValue(oldValue)
            }
        }
    }

    private fun swipe(root: View, distance: Float, cancel: Boolean = false) {
        val time = SystemClock.uptimeMillis()
        val x = root.width / 2f
        val y = root.height / 2f
        listOf(
            Triple(MotionEvent.ACTION_DOWN, x, time),
            Triple(MotionEvent.ACTION_MOVE, x + distance, time + 80),
            Triple(if (cancel) MotionEvent.ACTION_CANCEL else MotionEvent.ACTION_UP, x + distance, time + 100),
        ).forEach { (action, eventX, eventTime) ->
            val event = MotionEvent.obtain(time, eventTime, action, eventX, y, 0)
            try { root.dispatchTouchEvent(event) } finally { event.recycle() }
        }
    }

    @Test
    fun centerSwipesMoveBothDirectionsAndCancelDoesNotTriggerSideButtons() = checkBar { root, cursor, buttons ->
        val step = root.context.dp(10f)
        swipe(root, step * 2.5f, cancel = true)
        swipe(root, -step * 2.5f)
        assertEquals(
            listOf(FcitxKeyMapping.FcitxKey_Right, FcitxKeyMapping.FcitxKey_Right,
                FcitxKeyMapping.FcitxKey_Left, FcitxKeyMapping.FcitxKey_Left),
            cursor.map { (it as KeyAction.SymAction).sym.sym },
        )
        assertTrue(buttons.isEmpty())
    }

    @Test
    fun disablingCenterGestureImmediatelyStopsMovementAndKeepsSideButtons() = checkBar { root, cursor, buttons ->
        AppPrefs.getInstance().keyboard.bottomBarSwipeMoveCursor.setValue(false)
        swipe(root, root.context.dp(35f))
        assertTrue(cursor.isEmpty())
        val time = SystemClock.uptimeMillis()
        listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP).forEach { action ->
            val event = MotionEvent.obtain(time, time + 20, action, root.context.dp(28f), root.height / 2f, 0)
            try { root.dispatchTouchEvent(event) } finally { event.recycle() }
        }
        assertEquals(listOf(BottomBarAction.HideKeyboard), buttons)
        AppPrefs.getInstance().keyboard.bottomBarSwipeMoveCursor.setValue(true)
        swipe(root, root.context.dp(35f))
        assertTrue(cursor.isNotEmpty())
    }

    @Test
    fun themePreviewNeverEmitsCursorActions() = checkBar(interactive = false) { root, cursor, buttons ->
        swipe(root, root.context.dp(35f))
        assertTrue(cursor.isEmpty())
        assertTrue(buttons.isEmpty())
    }
}
