/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.navigation

import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.data.prefs.ManagedPreferenceEnum

enum class BottomBarMode(override val stringRes: Int) : ManagedPreferenceEnum {
    Auto(R.string.bottom_bar_auto),
    On(R.string.bottom_bar_on),
    Off(R.string.bottom_bar_off);

    fun shouldShow(systemControlsVisible: Boolean, floating: Boolean): Boolean =
        !floating && when (this) {
            Auto -> !systemControlsVisible
            On -> true
            Off -> false
        }
}
