/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.navigation

import androidx.annotation.DrawableRes
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.data.prefs.ManagedPreferenceEnum

enum class BottomBarAction(
    override val stringRes: Int,
    @DrawableRes val icon: Int,
) : ManagedPreferenceEnum {
    HideKeyboard(R.string.hide_keyboard, R.drawable.ic_baseline_expand_more_24),
    SwitchInputMethod(R.string.bottom_bar_switch_input_method, R.drawable.ic_baseline_keyboard_24),
    SwitchLanguage(R.string.bottom_bar_switch_language, R.drawable.ic_baseline_language_24),
    Clipboard(R.string.clipboard, R.drawable.ic_clipboard),
    Symbols(R.string.bottom_bar_symbols, R.drawable.ic_baseline_emoji_symbols_24),
    Emoji(R.string.bottom_bar_emoji, R.drawable.ic_baseline_emoji_symbols_24),
    None(R.string.bottom_bar_no_action, R.drawable.ic_baseline_expand_more_24),
}
