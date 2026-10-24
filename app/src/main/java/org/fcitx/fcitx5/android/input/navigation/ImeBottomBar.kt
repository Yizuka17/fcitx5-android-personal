/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.navigation

import android.content.Context
import android.view.View
import androidx.appcompat.widget.TooltipCompat
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.data.theme.Theme
import org.fcitx.fcitx5.android.input.bar.ui.ToolButton
import splitties.dimensions.dp
import splitties.views.dsl.constraintlayout.bottomOfParent
import splitties.views.dsl.constraintlayout.constraintLayout
import splitties.views.dsl.constraintlayout.lParams
import splitties.views.dsl.constraintlayout.leftOfParent
import splitties.views.dsl.constraintlayout.rightOfParent
import splitties.views.dsl.constraintlayout.topOfParent
import splitties.views.dsl.core.add

class ImeBottomBar(
    private val context: Context,
    private val theme: Theme,
    left: BottomBarAction,
    right: BottomBarAction,
    private val onAction: (BottomBarAction) -> Unit,
    private val onPicker: () -> Unit,
    private val interactive: Boolean = true,
) {
    private fun button(action: BottomBarAction) = ToolButton(context, action.icon, theme).apply {
        visibility = if (action == BottomBarAction.None) View.INVISIBLE else View.VISIBLE
        contentDescription = context.getString(action.stringRes)
        isFocusable = true
        isEnabled = interactive
        setOnClickListener { onAction(action) }
        if (action == BottomBarAction.SwitchInputMethod || action == BottomBarAction.SwitchLanguage) {
            TooltipCompat.setTooltipText(this, context.getString(R.string.bottom_bar_picker_hint))
            setOnLongClickListener {
                onPicker()
                true
            }
        }
    }

    val root = context.constraintLayout {
        // Keep actions on their physical sides, matching the system IME navigation bar.
        add(button(left), lParams(dp(56), dp(HEIGHT)) {
            leftOfParent()
            topOfParent()
            bottomOfParent()
        })
        add(button(right), lParams(dp(56), dp(HEIGHT)) {
            rightOfParent()
            topOfParent()
            bottomOfParent()
        })
        visibility = View.GONE
    }

    companion object {
        const val HEIGHT = 44
    }
}
