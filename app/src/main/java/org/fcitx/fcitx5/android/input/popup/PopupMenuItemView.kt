/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.popup

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.StateListDrawable
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.graphics.ColorUtils
import org.fcitx.fcitx5.android.data.theme.Theme
import splitties.dimensions.dp
import kotlin.math.roundToInt

/** Shared by key long-press menus and the bottom-bar editing menu; scale geometry only once. */
@SuppressLint("ViewConstructor")
internal class PopupMenuItemView(context: Context, theme: Theme, icon: Int, scale: Float) : AppCompatImageView(context) {
    init {
        scaleType = ScaleType.CENTER_INSIDE
        setImageResource(icon)
        imageTintList = ColorStateList.valueOf(theme.accentKeyTextColor)
        val padding = (context.dp(12f) * scale).roundToInt()
        fun circle(active: Boolean) = InsetDrawable(GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(if (active) ColorUtils.compositeColors(
                theme.keyPressHighlightColor, theme.accentKeyBackgroundColor
            ) else theme.accentKeyBackgroundColor)
        }, (context.dp(if (active) 7f else 7.5f) * scale).roundToInt())
        background = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_activated), circle(true))
            addState(intArrayOf(), circle(false))
        }
        // InsetDrawable backgrounds supply their own padding; apply icon padding afterwards.
        setPadding(padding, padding, padding, padding)
    }
}
