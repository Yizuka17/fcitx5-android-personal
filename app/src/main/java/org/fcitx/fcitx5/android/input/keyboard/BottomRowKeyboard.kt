/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.keyboard

import android.content.Context
import android.view.View
import androidx.annotation.Keep
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.data.prefs.AppPrefs
import org.fcitx.fcitx5.android.data.prefs.ManagedPreference
import org.fcitx.fcitx5.android.data.theme.Theme
import org.fcitx.fcitx5.android.input.picker.PickerWindow
import org.fcitx.fcitx5.android.input.popup.PopupAction
import splitties.views.imageResource

/** Shared bottom-row preferences and shortcuts for typing, handwriting and theme previews. */
abstract class BottomRowKeyboard(
    context: Context,
    theme: Theme,
    layout: List<List<KeyDef>>,
) : BaseKeyboard(context, theme, layout) {
    companion object {
        val BottomRow: List<KeyDef> = listOf(
            LayoutSwitchKey(
                "?123",
                popup = arrayOf(
                    KeyDef.Popup.Menu(
                        arrayOf(
                            KeyDef.Popup.Menu.Item(
                                "Left-handed mode",
                                R.drawable.ic_material_mobile_hand_left_24,
                                KeyAction.OneHandedModeAction(OneHandedMode.Left),
                            )
                        )
                    )
                ),
            ),
            CommaKey(0.1f, KeyDef.Appearance.Variant.Alternative),
            LanguageKey(),
            ImagePickerSwitchKey(
                R.drawable.ic_baseline_tag_faces_24,
                PickerWindow.Key.Emoji,
                viewId = R.id.button_emoji,
            ),
            SpaceKey(),
            SymbolKey(".", 0.1f, KeyDef.Appearance.Variant.Alternative),
            ReturnKey(),
        )
    }

    private val prefs = AppPrefs.getInstance().keyboard
    val lang: ImageKeyView by lazy { findViewById(R.id.button_lang) }
    private val emoji: ImageKeyView by lazy { findViewById(R.id.button_emoji) }
    private val comma: ImageTextKeyView by lazy { findViewById(R.id.button_comma) }

    private val useEmojiKey: Boolean
        get() = !prefs.showLangSwitchKey.getValue() && prefs.showEmojiKey.getValue()

    @Keep
    private val bottomRowChangeListener = ManagedPreference.OnChangeListener<Boolean> { _, _ ->
        updateBottomRow()
    }

    init {
        // Preferences hold weak listeners; keep cached keyboards and previews in sync too.
        prefs.showLangSwitchKey.registerOnChangeListener(bottomRowChangeListener)
        prefs.showEmojiKey.registerOnChangeListener(bottomRowChangeListener)
        updateBottomRow()
    }

    private fun updateBottomRow() {
        lang.visibility = if (prefs.showLangSwitchKey.getValue()) View.VISIBLE else View.GONE
        emoji.visibility = if (useEmojiKey) View.VISIBLE else View.GONE
        emoji.contentDescription = context.getString(R.string.bottom_bar_emoji)
        comma.img.imageResource = if (useEmojiKey) R.drawable.ic_baseline_tag_24
        else R.drawable.ic_baseline_tag_faces_24
    }

    override fun onAction(action: KeyAction, source: KeyActionListener.Source) {
        val actual = if (useEmojiKey && action is KeyAction.LayoutSwitchAction && action.act.isEmpty()) {
            KeyAction.LayoutSwitchAction(NumberKeyboard.Name)
        } else action
        super.onAction(actual, source)
    }

    override fun onPopupAction(action: PopupAction) {
        val actual = if (useEmojiKey && action is PopupAction.ShowMenuAction &&
            action.viewId == R.id.button_comma
        ) {
            action.copy(menu = KeyDef.Popup.Menu(arrayOf(
                KeyDef.Popup.Menu.Item("#", R.drawable.ic_baseline_tag_24,
                    KeyAction.PickerSwitchAction(PickerWindow.Key.Symbol)),
                *action.menu.items.drop(1).toTypedArray(),
            )))
        } else action
        super.onPopupAction(actual)
    }
}
