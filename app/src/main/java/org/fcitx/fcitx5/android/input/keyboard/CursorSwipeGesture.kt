/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.keyboard

import org.fcitx.fcitx5.android.core.FcitxKeyMapping
import org.fcitx.fcitx5.android.core.KeyStates
import org.fcitx.fcitx5.android.core.KeySym
import org.fcitx.fcitx5.android.data.InputFeedbacks
import org.fcitx.fcitx5.android.data.prefs.AppPrefs
import splitties.dimensions.dp
import kotlin.math.absoluteValue

/** Use the same cursor step, direction and feedback on the space key and bottom bar. */
internal fun CustomGestureView.configureCursorSwipe(onAction: (KeyAction) -> Unit) {
    swipeRepeatEnabled = true
    swipeThresholdX = context.dp(10f)
    swipeThresholdY = context.dp(800f)
    onGestureListener = CustomGestureView.OnGestureListener { view, event ->
        if (event.type != CustomGestureView.GestureType.Move || event.countX == 0) {
            false
        } else {
            val sym = if (event.countX > 0) FcitxKeyMapping.FcitxKey_Right
            else FcitxKeyMapping.FcitxKey_Left
            repeat(event.countX.absoluteValue) {
                val prefs = AppPrefs.getInstance().keyboard
                onAction(if (prefs.separatePreeditCursor.getValue() && prefs.bottomBarSwipeMoveCursor.getValue()) {
                    KeyAction.PreeditCursorAction(KeySym(sym))
                } else KeyAction.SymAction(KeySym(sym), KeyStates.Virtual))
                if (AppPrefs.getInstance().keyboard.hapticOnRepeat.getValue()) {
                    InputFeedbacks.hapticFeedback(view)
                }
            }
            true
        }
    }
}
