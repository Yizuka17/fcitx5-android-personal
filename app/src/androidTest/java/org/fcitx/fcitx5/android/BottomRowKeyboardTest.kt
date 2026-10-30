/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android

import android.view.View
import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.view.allViews
import androidx.core.view.children
import androidx.test.platform.app.InstrumentationRegistry
import org.fcitx.fcitx5.android.data.prefs.AppPrefs
import org.fcitx.fcitx5.android.data.theme.ThemePreset
import org.fcitx.fcitx5.android.input.handwriting.HandwritingKeyboard
import org.fcitx.fcitx5.android.input.keyboard.BottomRowKeyboard
import org.fcitx.fcitx5.android.input.keyboard.KeyAction
import org.fcitx.fcitx5.android.input.keyboard.KeyActionListener
import org.fcitx.fcitx5.android.input.keyboard.NumberKeyboard
import org.fcitx.fcitx5.android.input.keyboard.TextKeyView
import org.fcitx.fcitx5.android.input.keyboard.TextKeyboard
import org.fcitx.fcitx5.android.input.picker.PickerWindow
import org.fcitx.fcitx5.android.input.popup.PopupAction
import org.fcitx.fcitx5.android.input.popup.PopupActionListener
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BottomRowKeyboardTest {
    private fun withKeyboards(test: (List<BottomRowKeyboard>) -> Unit) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val prefs = AppPrefs.getInstance().keyboard
            val originalLanguage = prefs.showLangSwitchKey.getValue()
            val originalEmoji = prefs.showEmojiKey.getValue()
            val context = ContextThemeWrapper(
                InstrumentationRegistry.getInstrumentation().targetContext, R.style.Theme_FcitxAppTheme
            )
            try {
                val keyboards = listOf(
                    TextKeyboard(context, ThemePreset.PixelDark),
                    HandwritingKeyboard(context, ThemePreset.PixelDark),
                )
                test(keyboards)
            } finally {
                prefs.showLangSwitchKey.setValue(originalLanguage)
                prefs.showEmojiKey.setValue(originalEmoji)
            }
        }
    }

    private fun layout(keyboard: BottomRowKeyboard) {
        keyboard.measure(
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(if (keyboard is TextKeyboard) 720 else 180, View.MeasureSpec.EXACTLY),
        )
        keyboard.layout(0, 0, keyboard.measuredWidth, keyboard.measuredHeight)
    }

    private fun numericAction(keyboard: BottomRowKeyboard): KeyAction? {
        var received: KeyAction? = null
        keyboard.keyActionListener = KeyActionListener { action, _ -> received = action }
        keyboard.allViews.filterIsInstance<TextKeyView>().first { it.mainText.text == "?123" }.performClick()
        return received
    }

    private fun commaMenu(keyboard: BottomRowKeyboard): PopupAction.ShowMenuAction {
        var menu: PopupAction.ShowMenuAction? = null
        keyboard.popupActionListener = PopupActionListener { action ->
            if (action is PopupAction.ShowMenuAction) menu = action
        }
        keyboard.findViewById<View>(R.id.button_comma).performLongClick()
        return requireNotNull(menu)
    }

    @Test
    fun preferenceChangesUpdateTypingAndHandwritingWithoutRecreation() = withKeyboards { keyboards ->
        val prefs = AppPrefs.getInstance().keyboard
        prefs.showLangSwitchKey.setValue(true)
        prefs.showEmojiKey.setValue(false)
        val originalWidths = keyboards.map {
            layout(it)
            it.findViewById<View>(R.id.button_space).width
        }
        prefs.showLangSwitchKey.setValue(false)
        keyboards.forEachIndexed { index, keyboard ->
            layout(keyboard)
            assertEquals(View.GONE, keyboard.lang.visibility)
            assertEquals(View.GONE, keyboard.findViewById<View>(R.id.button_emoji).visibility)
            assertTrue(keyboard.findViewById<View>(R.id.button_space).width > originalWidths[index])
        }
        prefs.showEmojiKey.setValue(true)
        val rows = keyboards.map { keyboard ->
            layout(keyboard)
            assertEquals(View.GONE, keyboard.lang.visibility)
            assertEquals(View.VISIBLE, keyboard.findViewById<View>(R.id.button_emoji).visibility)
            val row = keyboard.lang.parent as android.view.ViewGroup
            row.children.filter { it.visibility == View.VISIBLE }.map { it.left to it.width }.toList()
        }
        assertEquals(rows[0], rows[1])
        prefs.showLangSwitchKey.setValue(true)
        keyboards.forEach {
            assertEquals(View.VISIBLE, it.lang.visibility)
            assertEquals(View.GONE, it.findViewById<View>(R.id.button_emoji).visibility)
        }
    }

    @Test
    fun emojiModeSeparatesEmojiSymbolsAndNumbersButKeepsCommaInput() = withKeyboards { keyboards ->
        val prefs = AppPrefs.getInstance().keyboard
        prefs.showLangSwitchKey.setValue(false)
        prefs.showEmojiKey.setValue(true)
        keyboards.forEach { keyboard ->
            assertEquals(KeyAction.LayoutSwitchAction(NumberKeyboard.Name), numericAction(keyboard))
            val first = commaMenu(keyboard).menu.items.first()
            assertEquals("#", first.label)
            assertEquals(KeyAction.PickerSwitchAction(PickerWindow.Key.Symbol), first.action)
            var received: KeyAction? = null
            keyboard.keyActionListener = KeyActionListener { action, _ -> received = action }
            keyboard.findViewById<View>(R.id.button_emoji).performClick()
            assertEquals(KeyAction.PickerSwitchAction(PickerWindow.Key.Emoji), received)
            keyboard.findViewById<View>(R.id.button_comma).performClick()
            assertTrue(received is KeyAction.FcitxKeyAction)
            assertEquals(",", (received as KeyAction.FcitxKeyAction).act)
        }
    }

    @Test
    fun disablingEmojiOrEnablingLanguagePreservesOriginalShortcuts() = withKeyboards { keyboards ->
        val prefs = AppPrefs.getInstance().keyboard
        listOf(false to false, true to false, true to true).forEach { (language, emoji) ->
            prefs.showLangSwitchKey.setValue(language)
            prefs.showEmojiKey.setValue(emoji)
            keyboards.forEach { keyboard ->
                assertEquals(KeyAction.LayoutSwitchAction(), numericAction(keyboard))
                assertEquals(
                    KeyAction.PickerSwitchAction(PickerWindow.Key.Emoji),
                    commaMenu(keyboard).menu.items.first().action,
                )
            }
        }
    }
}
