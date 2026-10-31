/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.RectF
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.view.allViews
import androidx.test.platform.app.InstrumentationRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.testing.TestLifecycleOwner
import org.fcitx.fcitx5.android.data.InputFeedbacks.InputFeedbackMode
import org.fcitx.fcitx5.android.data.prefs.AppPrefs
import org.fcitx.fcitx5.android.data.theme.ThemePreset
import org.fcitx.fcitx5.android.input.bar.ui.ToolButton
import org.fcitx.fcitx5.android.input.bar.ui.CandidateUi
import org.fcitx.fcitx5.android.input.bar.ui.TitleUi
import org.fcitx.fcitx5.android.input.status.StatusAreaEntryUi
import org.fcitx.fcitx5.android.input.status.StatusAreaEntry
import org.fcitx.fcitx5.android.input.bar.ui.idle.ButtonsBarUi
import org.fcitx.fcitx5.android.input.handwriting.HandwritingKeyboard
import org.fcitx.fcitx5.android.input.keyboard.KeyAction
import org.fcitx.fcitx5.android.input.keyboard.KeyDef
import org.fcitx.fcitx5.android.input.keyboard.NumberKeyboard
import org.fcitx.fcitx5.android.input.keyboard.TextKeyboard
import org.fcitx.fcitx5.android.input.keyboard.TextKeyView
import org.fcitx.fcitx5.android.input.keyboard.OneHandedMode
import org.fcitx.fcitx5.android.input.keyboard.KeyActionListener
import org.fcitx.fcitx5.android.input.popup.PopupAction
import org.fcitx.fcitx5.android.input.popup.PopupActionListener
import org.fcitx.fcitx5.android.input.navigation.BottomBarGestureView
import org.fcitx.fcitx5.android.input.popup.PopupMenuUi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Test
import splitties.dimensions.dp

class CompactKeyboardUiTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = ContextThemeWrapper(instrumentation.targetContext, R.style.Theme_FcitxAppTheme)
    private val theme = ThemePreset.PixelDark

    private fun layout(view: View, width: Int, height: Int) {
        repeat(2) {
            view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
            view.layout(0, 0, width, height)
        }
    }

    @Test
    fun toolbarIconsResizeContinuouslyWithoutFlexboxFeedback() = instrumentation.runOnMainSync {
        val ctx = context
        val toolbar = ButtonsBarUi(ctx, theme)
        var previous = 0
        for (percent in 65..100) {
            val widthScale = percent / 100f
            val scale = .5f + .5f * widthScale
            toolbar.setContentScale(scale)
            layout(toolbar.root, ctx.dp(390f * widthScale - 80).toInt(), ctx.dp(40))
            toolbar.root.allViews.filterIsInstance<ImageView>().forEach {
                assertEquals(ctx.dp(24f) * scale, it.width.toFloat(), 1f)
                assertEquals(1f, it.scaleX, 0f)
                assertTrue(it.width <= (it.parent as View).width)
            }
            val size = toolbar.root.allViews.filterIsInstance<ImageView>().first().width
            assertTrue("Growing the keyboard cannot shrink its icons", size >= previous)
            if (previous > 0) assertTrue("One resize step must not cause an icon jump", size - previous <= 1)
            previous = size
        }
    }

    @Test
    fun candidateClearIconKeepsItsOriginalSizeAndFollowsScaling() = instrumentation.runOnMainSync {
        val ctx = context
        val candidates = CandidateUi(ctx, theme, View(ctx))
        listOf(1f, .9f, .5f, 1f).forEach { scale ->
            candidates.setContentScale(scale)
            layout(candidates.root, ctx.dp(252), ctx.dp(40))
            val clear = candidates.clearButton.allViews.filterIsInstance<ImageView>().first()
            val expand = candidates.expandButton.allViews.filterIsInstance<ImageView>().first()
            assertEquals(ctx.dp(14f) * scale, clear.width.toFloat(), 1f)
            assertEquals(ctx.dp(24f) * scale, expand.width.toFloat(), 1f)
            assertTrue("Clear must remain smaller than the expand icon", clear.width < expand.width)
        }
    }

    @Test
    fun morePageIconsAndLabelsFitMinimumFloatingSize() = instrumentation.runOnMainSync {
        val ctx = context
        val title = TitleUi(ctx, theme)
        title.setContentScale(.825f, .8f)
        val settings = ToolButton(ctx, R.drawable.ic_baseline_settings_24, theme)
        val extension = android.widget.FrameLayout(ctx).apply {
            addView(settings, android.widget.FrameLayout.LayoutParams(ctx.dp(40), ctx.dp(40)))
        }
        title.addExtension(extension, true)
        layout(title.root, ctx.dp(252), ctx.dp(40))
        title.root.allViews.filterIsInstance<ImageView>().forEach {
            assertEquals(ctx.dp(32f) * .825f, it.width.toFloat(), 1f)
        }
        val configuration = android.content.res.Configuration(ctx.resources.configuration).apply { fontScale = 1.8f }
        val largeFontCtx = ContextThemeWrapper(ctx.createConfigurationContext(configuration), R.style.Theme_FcitxAppTheme)
        val entry = StatusAreaEntryUi(largeFontCtx, theme)
        entry.setEntry(StatusAreaEntry.Android("输入法设置", R.drawable.ic_baseline_language_24, StatusAreaEntry.Android.Type.InputMethod))
        entry.setContentScale(.5f)
        layout(entry.root, largeFontCtx.dp(63), entry.root.layoutParams.height)
        val textLayout = requireNotNull(entry.label.layout)
        for (line in 0 until textLayout.lineCount) assertEquals(0, textLayout.getEllipsisCount(line))
        val bounds = Rect(0, 0, entry.label.width, entry.label.height)
        (entry.root as ViewGroup).offsetDescendantRectToMyCoords(entry.label, bounds)
        assertTrue("The full settings label must remain inside its cell", bounds.bottom <= entry.root.height)
        assertEquals(1f, entry.label.scaleX, 0f)
    }

    @Test
    fun minimumKeyboardsAndLongPressMenuKeepIconsInsideTheirCells() = instrumentation.runOnMainSync {
        val ctx = context
        listOf(TextKeyboard(ctx, theme), NumberKeyboard(ctx, theme), HandwritingKeyboard(ctx, theme)).forEach { keyboard ->
            keyboard.setContentScale(.5f, .65f, .16f)
            layout(keyboard, ctx.dp(252), ctx.dp(128))
            keyboard.allViews.filterIsInstance<ImageView>().filter { it.visibility == View.VISIBLE }.forEach { image ->
                val parent = image.parent as View
                assertTrue("Icon starts outside ${parent.javaClass.simpleName}", image.left >= 0 && image.top >= 0)
                assertTrue("Icon ends outside its key", image.right <= parent.width && image.bottom <= parent.height)
            }
        }
        val menu = PopupMenuUi(ctx, theme, Rect(0, 0, ctx.dp(252), ctx.dp(500)),
            Rect(ctx.dp(100), ctx.dp(300), ctx.dp(130), ctx.dp(340)),
            items = arrayOf(KeyDef.Popup.Menu.Item("Copy", R.drawable.ic_baseline_content_copy_24,
                KeyAction.ShowInputMethodPickerAction)), contentScale = .5f)
        layout(menu.root, ctx.dp(24), ctx.dp(24))
        val image = (menu.root as ViewGroup).getChildAt(0) as ImageView
        val bitmap = Bitmap.createBitmap(image.width, image.height, Bitmap.Config.ARGB_8888)
        try {
            image.draw(Canvas(bitmap))
            val bounds = RectF(0f, 0f, image.drawable.intrinsicWidth.toFloat(), image.drawable.intrinsicHeight.toFloat())
            image.imageMatrix.mapRect(bounds)
            bounds.offset(image.paddingLeft.toFloat(), image.paddingTop.toFloat())
            assertTrue(bounds.left >= 0 && bounds.top >= 0 && bounds.right <= image.width && bounds.bottom <= image.height)
            assertTrue(bounds.width() >= ctx.dp(11f))
            assertTrue("The icon needs space inside its circular background", image.paddingLeft >= ctx.dp(5.5f))
            assertEquals(1f, image.scaleX, 0f)
        } finally { bitmap.recycle() }
    }

    @Test
    fun compactNumberLabelFitsBeforeDrawingAndRemeasuresAfterScaling() = instrumentation.runOnMainSync {
        val ctx = context
        val key = TextKeyView(ctx, theme, KeyDef.Appearance.Text("?123", 16f))
        listOf(.5f, 1f, .5f).forEach { scale ->
            key.setContentScale(scale)
            layout(key, ctx.dp(76f * scale).toInt(), ctx.dp(64f * scale).toInt())
            val text = key.mainText
            assertEquals(ctx.dp(16f) * scale, text.textSize, 1f)
            assertEquals(1f, text.scaleX, 0f)
            assertTrue("The label must fit its measured view before drawing (scale=$scale, width=${text.width}, glyph=${text.paint.measureText(text.text.toString())})",
                text.paint.measureText(text.text.toString()) <= text.width + 1)
            assertTrue(text.left >= 0 && text.right <= (text.parent as View).width)
        }
    }

    @Test
    fun oneHandedShortcutsRequireUpwardSelectionAndCanBeCancelled() = instrumentation.runOnMainSync {
        val ctx = context
        listOf(.5f, 1f).forEach { scale ->
            listOf(OneHandedMode.Left, OneHandedMode.Right).forEach { side ->
                val action = KeyAction.OneHandedModeAction(side)
                val trigger = Rect(ctx.dp(100), ctx.dp(300), ctx.dp(138), ctx.dp(332))
                val menu = PopupMenuUi(ctx, theme, Rect(0, 0, ctx.dp(252), ctx.dp(500)), trigger,
                    items = arrayOf(KeyDef.Popup.Menu.Item("One-handed", R.drawable.ic_material_mobile_hand_24, action)),
                    contentScale = scale)
                val centerX = trigger.width() / 2f
                assertNull("Opening the menu must not select it", menu.onTrigger())
                menu.changeFocus(centerX, trigger.height() / 2f)
                assertNull("Releasing in place must cancel", menu.onTrigger())
                val iconY = menu.offsetY + ctx.dp(24f) * scale
                menu.changeFocus(centerX, iconY)
                assertEquals(action, menu.onTrigger())
                menu.changeFocus(centerX, trigger.height() * 2f)
                assertNull("Moving down must cancel a previously selected shortcut", menu.onTrigger())
                menu.changeFocus(centerX, iconY)
                menu.changeFocus(-ctx.dp(100f), iconY)
                assertNull("Moving sideways out of the icon must cancel", menu.onTrigger())
            }
        }
    }

    @Test
    fun cancelledShortcutDoesNotFallThroughToNumberOrReturnKey() = instrumentation.runOnMainSync {
        val ctx = context
        val keyboard = TextKeyboard(ctx, theme)
        keyboard.setViewTreeLifecycleOwner(TestLifecycleOwner())
        keyboard.setContentScale(.5f, .65f, .16f)
        layout(keyboard, ctx.dp(252), ctx.dp(128))
        val actions = mutableListOf<KeyAction>()
        keyboard.keyActionListener = KeyActionListener { action, _ -> actions.add(action) }
        var menu: PopupMenuUi? = null
        keyboard.popupActionListener = PopupActionListener { event ->
            when (event) {
                is PopupAction.ShowMenuAction -> menu = PopupMenuUi(ctx, theme,
                    Rect(0, 0, ctx.dp(252), ctx.dp(500)), event.bounds,
                    items = event.menu.items, contentScale = .5f)
                is PopupAction.ChangeFocusAction -> event.outResult = menu?.changeFocus(event.x, event.y) ?: false
                is PopupAction.TriggerAction -> event.outAction = menu?.onTrigger()
                is PopupAction.DismissAction -> menu = null
                else -> Unit
            }
        }
        val keys = listOf(keyboard.allViews.filterIsInstance<TextKeyView>().first { it.mainText.text == "?123" },
            keyboard.findViewById<org.fcitx.fcitx5.android.input.keyboard.KeyView>(R.id.button_return))
        keys.forEach { key ->
            fun touch(type: Int, y: Float) {
                val now = SystemClock.uptimeMillis()
                val event = MotionEvent.obtain(now, now, type, key.width / 2f, y, 0)
                try { key.onTouchEvent(event) } finally { event.recycle() }
            }
            touch(MotionEvent.ACTION_DOWN, key.height / 2f)
            key.performLongClick()
            touch(MotionEvent.ACTION_UP, key.height / 2f)
            assertTrue("A cancelled shortcut must not send a normal key action", actions.isEmpty())
            assertNull("A cancelled menu must close", menu)
            touch(MotionEvent.ACTION_DOWN, key.height / 2f)
            key.performLongClick()
            touch(MotionEvent.ACTION_MOVE, -ctx.dp(12f))
            touch(MotionEvent.ACTION_MOVE, key.height * 2f)
            touch(MotionEvent.ACTION_UP, key.height * 2f)
            assertTrue("Sliding back down must not switch modes", actions.isEmpty())
            assertNull(menu)
        }
    }

    @Test
    fun bottomBarRestoresTapHapticsWithoutIgnoringThePreference() = instrumentation.runOnMainSync {
        val prefs = AppPrefs.getInstance().keyboard
        val oldMode = prefs.hapticOnKeyPress.getValue()
        val oldDuration = prefs.buttonPressVibrationMilliseconds.getValue()
        try {
            prefs.hapticOnKeyPress.setValue(InputFeedbackMode.Enabled)
            prefs.buttonPressVibrationMilliseconds.setValue(0)
            val feedback = mutableListOf<Int>()
            val view = object : BottomBarGestureView(context, {}, {}, {}, { _, _ -> }, {}) {
                override fun performHapticFeedback(feedbackConstant: Int, flags: Int): Boolean {
                    feedback.add(feedbackConstant)
                    return true
                }
            }
            fun down() {
                val now = SystemClock.uptimeMillis()
                val event = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, 100f, 20f, 0)
                try { view.onTouchEvent(event) } finally { event.recycle() }
                view.cancelGestures()
            }
            down()
            assertEquals(listOf(HapticFeedbackConstants.KEYBOARD_TAP), feedback)
            prefs.hapticOnKeyPress.setValue(InputFeedbackMode.Disabled)
            down()
            assertEquals(1, feedback.size)
        } finally {
            prefs.hapticOnKeyPress.setValue(oldMode)
            prefs.buttonPressVibrationMilliseconds.setValue(oldDuration)
        }
    }
}
