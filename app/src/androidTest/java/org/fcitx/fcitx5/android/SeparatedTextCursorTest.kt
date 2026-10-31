/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android

import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputConnectionWrapper
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import androidx.appcompat.view.ContextThemeWrapper
import androidx.test.platform.app.InstrumentationRegistry
import org.fcitx.fcitx5.android.data.InputFeedbacks.InputFeedbackMode
import org.fcitx.fcitx5.android.data.prefs.AppPrefs
import org.fcitx.fcitx5.android.input.cursor.CursorRange
import org.fcitx.fcitx5.android.input.cursor.CursorTracker
import org.fcitx.fcitx5.android.input.cursor.TextCursorNavigator
import org.fcitx.fcitx5.android.input.navigation.BottomBarGestureView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import splitties.dimensions.dp

class SeparatedTextCursorTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    private class EditorHost(val editor: EditText, target: InputConnection, val focusTarget: Button) {
        var keyEvents = 0
        var invalidSelections = 0
        val cursor = CursorTracker()
        val connection = object : InputConnectionWrapper(target, false) {
            override fun sendKeyEvent(event: KeyEvent): Boolean {
                keyEvents++
                if (event.keyCode == KeyEvent.KEYCODE_DPAD_LEFT && editor.selectionStart == 0) {
                    focusTarget.requestFocus()
                }
                return super.sendKeyEvent(event)
            }
            override fun setSelection(start: Int, end: Int): Boolean {
                if (start !in 0..editor.length() || end !in 0..editor.length()) {
                    invalidSelections++
                    return false
                }
                return super.setSelection(start, end)
            }
        }
        fun select(start: Int, end: Int = start) {
            editor.setSelection(start, end)
            cursor.resetTo(start, end)
        }
        fun move(left: Boolean) {
            TextCursorNavigator.move(connection, cursor.latest, left)?.let { cursor.predict(it) }
        }
    }

    private fun withEditor(text: String, block: (EditorHost) -> Unit) = instrumentation.runOnMainSync {
        val ctx = ContextThemeWrapper(instrumentation.targetContext, R.style.Theme_FcitxAppTheme)
        val root = LinearLayout(ctx)
        val neighbor = Button(ctx).apply { isFocusableInTouchMode = true }
        val editor = EditText(ctx).apply { isFocusableInTouchMode = true; setText(text) }
        root.addView(neighbor)
        root.addView(editor)
        assertTrue(editor.requestFocus())
        val host = EditorHost(editor, requireNotNull(editor.onCreateInputConnection(EditorInfo())), neighbor)
        host.select(text.length)
        block(host)
        assertTrue("The editor must retain focus", editor.isFocused)
        assertEquals("Navigation must not reach the host's focus-navigation handler", 0, host.keyEvents)
        assertEquals("No out-of-range selection may reach the editor", 0, host.invalidSelections)
        assertEquals("Navigation must preserve the text", text, editor.text.toString())
    }

    @Test
    fun repeatedOutwardMovesStayAtEdgesAndCanReverseWithoutSelectionCallbacks() = withEditor("ab") { host ->
        repeat(40) { host.move(true) }
        assertEquals(0, host.editor.selectionStart)
        host.move(false)
        assertEquals(1, host.editor.selectionStart)
        repeat(40) { host.move(false) }
        assertEquals(2, host.editor.selectionStart)
        host.move(true)
        assertEquals(1, host.editor.selectionStart)
    }

    @Test
    fun emptyEditorAndUnknownSelectionNeverSendFocusKeys() = withEditor("") { host ->
        repeat(10) { host.move(true); host.move(false) }
        assertNull(TextCursorNavigator.move(host.connection, CursorRange(-1, -1), true))
        assertEquals(0, host.editor.selectionStart)
    }

    @Test
    fun emojiSurrogatePairsAreNotSplit() = withEditor("a😀b") { host ->
        listOf(3, 1, 0).forEach { expected -> host.move(true); assertEquals(expected, host.editor.selectionStart) }
        listOf(1, 3, 4).forEach { expected -> host.move(false); assertEquals(expected, host.editor.selectionStart) }
    }

    @Test
    fun anExistingSelectionCollapsesToItsDirectionalEdge() = withEditor("abc") { host ->
        host.select(1, 3)
        host.move(true)
        assertEquals(1, host.editor.selectionStart)
        assertEquals(1, host.editor.selectionEnd)
        host.select(1, 3)
        host.move(false)
        assertEquals(3, host.editor.selectionStart)
        assertEquals(3, host.editor.selectionEnd)
    }

    @Test
    fun unavailableContextOrRejectedSelectionDoesNotFallBackToDpad() = withEditor("abc") { host ->
        val unavailable = object : InputConnectionWrapper(host.connection, false) {
            override fun getTextAfterCursor(n: Int, flags: Int): CharSequence? = null
        }
        assertNull(TextCursorNavigator.move(unavailable, host.cursor.latest, false))
        val rejected = object : InputConnectionWrapper(host.connection, false) {
            override fun setSelection(start: Int, end: Int) = false
        }
        assertNull(TextCursorNavigator.move(rejected, host.cursor.latest, true))
        assertEquals(3, host.editor.selectionStart)
    }

    @Test
    fun continuousBottomSwipeKeepsFeedbackPastTheEdgeAndAfterReversing() = withEditor("ab") { host ->
        val prefs = AppPrefs.getInstance().keyboard
        val oldMode = prefs.hapticOnKeyPress.getValue()
        val oldRepeat = prefs.hapticOnRepeat.getValue()
        val oldDuration = prefs.buttonPressVibrationMilliseconds.getValue()
        val oldUp = prefs.hapticOnKeyUp.getValue()
        try {
            prefs.hapticOnKeyPress.setValue(InputFeedbackMode.Enabled)
            prefs.hapticOnRepeat.setValue(true)
            prefs.buttonPressVibrationMilliseconds.setValue(0)
            prefs.hapticOnKeyUp.setValue(false)
            var taps = 0
            val bar = object : BottomBarGestureView(host.editor.context,
                { count -> repeat(kotlin.math.abs(count)) { host.move(count < 0) } }, {}, {}, { _, _ -> }, {}) {
                override fun performHapticFeedback(feedbackConstant: Int, flags: Int): Boolean {
                    if (feedbackConstant == HapticFeedbackConstants.KEYBOARD_TAP) taps++
                    return true
                }
            }
            val now = SystemClock.uptimeMillis()
            fun touch(type: Int, x: Float) {
                val event = MotionEvent.obtain(now, now + 20, type, bar.context.dp(x), bar.context.dp(20f), 0)
                try { bar.onTouchEvent(event) } finally { event.recycle() }
            }
            touch(MotionEvent.ACTION_DOWN, 200f)
            touch(MotionEvent.ACTION_MOVE, 100f)
            assertEquals(0, host.editor.selectionStart)
            val atLeft = taps
            touch(MotionEvent.ACTION_MOVE, 0f)
            assertEquals(0, host.editor.selectionStart)
            assertTrue("Boundary moves must not cancel the gesture or feedback", taps > atLeft)
            touch(MotionEvent.ACTION_MOVE, 100f)
            assertEquals(2, host.editor.selectionStart)
            assertTrue(taps > atLeft)
            touch(MotionEvent.ACTION_UP, 100f)
        } finally {
            prefs.hapticOnKeyPress.setValue(oldMode)
            prefs.hapticOnRepeat.setValue(oldRepeat)
            prefs.buttonPressVibrationMilliseconds.setValue(oldDuration)
            prefs.hapticOnKeyUp.setValue(oldUp)
        }
    }
}
