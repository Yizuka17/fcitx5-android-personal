/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.navigation

import android.content.Context
import android.view.View
import androidx.core.view.updateLayoutParams
import androidx.appcompat.widget.TooltipCompat
import androidx.annotation.Keep
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.data.prefs.AppPrefs
import org.fcitx.fcitx5.android.data.prefs.ManagedPreference
import org.fcitx.fcitx5.android.data.theme.Theme
import org.fcitx.fcitx5.android.input.bar.ui.ToolButton
import org.fcitx.fcitx5.android.input.keyboard.KeyAction
import org.fcitx.fcitx5.android.core.FcitxKeyMapping
import org.fcitx.fcitx5.android.core.KeyStates
import org.fcitx.fcitx5.android.core.KeySym
import splitties.dimensions.dp
import splitties.views.dsl.constraintlayout.bottomOfParent
import splitties.views.dsl.constraintlayout.constraintLayout
import splitties.views.dsl.constraintlayout.lParams
import splitties.views.dsl.constraintlayout.leftOfParent
import splitties.views.dsl.constraintlayout.leftToRightOf
import splitties.views.dsl.constraintlayout.rightOfParent
import splitties.views.dsl.constraintlayout.rightToLeftOf
import splitties.views.dsl.constraintlayout.topOfParent
import splitties.views.dsl.core.add
import kotlin.math.roundToInt

class ImeBottomBar(
    private val context: Context,
    private val theme: Theme,
    left: BottomBarAction,
    right: BottomBarAction,
    private val onAction: (BottomBarAction) -> Unit,
    private val onPicker: () -> Unit,
    private val interactive: Boolean = true,
    onCursorAction: (KeyAction) -> Unit = {},
    onSelectionStep: (Int) -> Unit = {},
    onEditAction: (Int) -> Unit = {},
    onSelectionStart: () -> Unit = {},
) {
    private val cursorSwipePreference = AppPrefs.getInstance().keyboard.bottomBarSwipeMoveCursor
    private val selectionMenu = SelectionActionMenu(context, theme)
    private val cursorArea: BottomBarGestureView = BottomBarGestureView(
        context,
        moveCursor = { count ->
            val sym = if (count > 0) FcitxKeyMapping.FcitxKey_Right else FcitxKeyMapping.FcitxKey_Left
            repeat(kotlin.math.abs(count)) {
                onCursorAction(KeyAction.SymAction(KeySym(sym), KeyStates.Virtual))
            }
        },
        extendSelection = onSelectionStep,
        showActions = { selectionMenu.show(root) },
        pointAtAction = selectionMenu::pointAt,
        finishActions = { execute -> selectionMenu.finish(execute)?.let(onEditAction) },
        beginSelection = onSelectionStart,
    ).apply {
        id = R.id.bottom_bar_cursor_area
        contentDescription = context.getString(R.string.bottom_bar_swipe_move_cursor)
    }

    @Keep
    private val cursorSwipeChangeListener = ManagedPreference.OnChangeListener<Boolean> { _, _ ->
        updateCursorSwipe()
    }

    init {
        cursorSwipePreference.registerOnChangeListener(cursorSwipeChangeListener)
        updateCursorSwipe()
    }

    private fun updateCursorSwipe() {
        cursorArea.cancelGestures()
        cursorArea.isEnabled = interactive && cursorSwipePreference.getValue()
    }

    fun cancelGestures() = cursorArea.cancelGestures()

    private var contentScale = 1f
    val heightPx: Int get() = (context.dp(HEIGHT) * contentScale).roundToInt()

    fun setContentScale(scale: Float) {
        if (contentScale == scale) return
        cancelGestures()
        contentScale = scale
        cursorArea.contentScale = scale
        selectionMenu.setContentScale(scale)
        listOf(leftButton, rightButton).forEach {
            it.setContentScale(scale)
            it.updateLayoutParams { width = (context.dp(56) * scale).roundToInt(); height = heightPx }
        }
        cursorArea.updateLayoutParams { height = heightPx }
        root.layoutParams?.let { root.updateLayoutParams { height = heightPx } }
    }

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

    private val leftButton = button(left)
    private val rightButton = button(right)

    val root: androidx.constraintlayout.widget.ConstraintLayout = context.constraintLayout {
        // Keep actions on their physical sides, matching the system IME navigation bar.
        add(leftButton, lParams(dp(56), dp(HEIGHT)) {
            leftOfParent()
            topOfParent()
            bottomOfParent()
        })
        add(rightButton, lParams(dp(56), dp(HEIGHT)) {
            rightOfParent()
            topOfParent()
            bottomOfParent()
        })
        add(cursorArea, lParams(0, dp(HEIGHT)) {
            leftToRightOf(leftButton)
            rightToLeftOf(rightButton)
            topOfParent()
            bottomOfParent()
        })
        visibility = View.GONE
    }

    companion object {
        const val HEIGHT = 44
    }
}
