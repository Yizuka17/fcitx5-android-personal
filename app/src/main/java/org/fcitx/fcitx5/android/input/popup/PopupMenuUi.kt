/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2023 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.popup

import android.content.Context
import android.graphics.Rect
import org.fcitx.fcitx5.android.data.theme.Theme
import org.fcitx.fcitx5.android.input.keyboard.KeyDef
import org.fcitx.fcitx5.android.input.keyboard.KeyAction
import splitties.dimensions.dp
import splitties.views.dsl.core.add
import splitties.views.dsl.core.horizontalLayout
import splitties.views.dsl.core.lParams
import kotlin.math.floor
import kotlin.math.roundToInt

class PopupMenuUi(
    override val ctx: Context,
    theme: Theme,
    outerBounds: Rect,
    triggerBounds: Rect,
    onDismissSelf: PopupContainerUi.() -> Unit = {},
    private val items: Array<KeyDef.Popup.Menu.Item>,
    private val contentScale: Float
) : PopupContainerUi(ctx, theme, outerBounds, triggerBounds, onDismissSelf) {

    private val keySize = (ctx.dp(48) * contentScale).roundToInt()

    private val columnCount = items.size
    private val focusColumn =
        calcInitialFocusedColumn(columnCount, keySize, outerBounds, triggerBounds)

    override val offsetX = ((triggerBounds.width() - keySize) / 2) - (keySize * focusColumn)
    override val offsetY = (ctx.dp(-52) * contentScale).roundToInt()

    private val columnOrder = createColumnOrder(columnCount, focusColumn)

    private val requiresUpwardSelection = items.all { it.action is KeyAction.OneHandedModeAction }
    private var focusedIndex = if (requiresUpwardSelection) -1 else columnOrder[focusColumn]

    private val keyViews = items.map {
        PopupMenuItemView(ctx, theme, it.icon, contentScale)
    }

    init {
        markFocus(focusedIndex)
    }

    override val root = horizontalLayout root@{
        for (i in 0 until columnCount) {
            val view = keyViews[columnOrder[i]]
            add(view, lParams(keySize, keySize))
        }
    }

    private fun markFocus(index: Int) {
        keyViews.getOrNull(index)?.apply {
            isActivated = true
        }
    }

    private fun markInactive(index: Int) {
        keyViews.getOrNull(index)?.apply {
            isActivated = false
        }
    }

    override fun onChangeFocus(x: Float, y: Float): Boolean {
        if (requiresUpwardSelection) {
            // A long press only opens the shortcut. Enter the icon above the key to select it;
            // returning to the key or moving below it cancels the selection again.
            val index = if (x >= 0 && x < keySize * columnCount && y >= 0 && y < keySize) {
                columnOrder[floor(x / keySize).toInt()]
            } else -1
            markInactive(focusedIndex)
            focusedIndex = index
            markFocus(index)
            return false
        }
        var newColumn = floor(x / keySize).toInt()
        if (newColumn < -2 || newColumn > columnCount + 1) {
            onDismissSelf(this)
            return true
        }
        newColumn = limitIndex(newColumn, columnCount)
        val newFocus = columnOrder[newColumn]
        if (newFocus < keyViews.size) {
            markInactive(focusedIndex)
            markFocus(newFocus)
            focusedIndex = newFocus
        }
        return false
    }

    override fun onTrigger() = items.getOrNull(focusedIndex)?.action
}
