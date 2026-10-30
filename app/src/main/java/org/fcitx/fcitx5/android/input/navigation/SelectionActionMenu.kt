/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.navigation

import android.annotation.SuppressLint
import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.PopupWindow
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.data.theme.Theme
import org.fcitx.fcitx5.android.input.popup.PopupMenuItemView
import splitties.dimensions.dp

internal class SelectionActionMenu(private val context: Context, private val theme: Theme) {
    private data class Action(val id: Int, val label: Int, val icon: Int)
    private val actions = listOf(
        Action(android.R.id.copy, android.R.string.copy, R.drawable.ic_baseline_content_copy_24),
        Action(android.R.id.paste, android.R.string.paste, R.drawable.ic_baseline_content_paste_24),
        Action(android.R.id.cut, android.R.string.cut, R.drawable.ic_baseline_content_cut_24),
        Action(android.R.id.selectAll, android.R.string.selectAll, R.drawable.ic_baseline_select_all_24),
        Action(R.id.bottom_bar_open_clipboard, R.string.clipboard, R.drawable.ic_clipboard),
    )
    private val cells = mutableListOf<PopupMenuItemView>()
    private var contentScale = 1f
    private val row = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        layoutDirection = View.LAYOUT_DIRECTION_LTR
    }
    private val popup = PopupWindow(row, 0, 0, false).apply {
        isTouchable = false
        isFocusable = false
    }
    private val location = IntArray(2)
    private var selected = -1

    fun setContentScale(scale: Float) {
        if (contentScale == scale) return
        dismiss()
        contentScale = scale
    }

    @SuppressLint("RtlHardcoded") // Popup coordinates and finger hit testing are physical screen coordinates.
    fun show(anchor: View) {
        dismiss()
        if (anchor.windowToken == null) return
        val scale = minOf(contentScale, anchor.width / context.dp(48f * actions.size))
        val size = (context.dp(48f) * scale).toInt().coerceAtLeast(1)
        row.removeAllViews()
        cells.clear()
        actions.forEach { action ->
            val cell = PopupMenuItemView(context, theme, action.icon, scale).apply {
                contentDescription = context.getString(action.label)
            }
            cells.add(cell)
            row.addView(cell, LinearLayout.LayoutParams(size, size))
        }
        popup.width = size * actions.size
        popup.height = size
        updateHighlight(-1)
        popup.showAsDropDown(anchor, (anchor.width - popup.width) / 2,
            -anchor.height - popup.height - (context.dp(8f) * scale).toInt(), Gravity.LEFT)
    }

    fun pointAt(rawX: Float, rawY: Float) {
        row.getLocationOnScreen(location)
        val left = location[0]
        val top = location[1]
        val index = if (popup.isShowing && rawX >= left && rawX < left + popup.width &&
            rawY >= top - context.dp(16f) * contentScale && rawY <= top + popup.height + context.dp(16f) * contentScale) {
            ((rawX - left) * actions.size / popup.width).toInt()
        } else -1
        updateHighlight(index)
    }

    private fun updateHighlight(index: Int) {
        selected = index
        cells.forEachIndexed { i, cell -> cell.isActivated = i == index }
    }

    fun finish(execute: Boolean): Int? {
        val action = if (execute) actions.getOrNull(selected)?.id else null
        dismiss()
        return action
    }

    fun dismiss() {
        selected = -1
        popup.dismiss()
    }
}
