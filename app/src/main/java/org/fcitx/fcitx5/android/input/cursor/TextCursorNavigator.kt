/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.cursor

import android.view.inputmethod.InputConnection

/** Navigate editor text without sending DPAD events that can escape its focus at an edge. */
internal object TextCursorNavigator {
    /** Returns the accepted position, or null when no movement was possible. */
    fun move(connection: InputConnection, selection: CursorRange, left: Boolean): Int? {
        val start = minOf(selection.start, selection.end)
        val end = maxOf(selection.start, selection.end)
        if (start < 0) return null
        val target = if (start != end) {
            if (left) start else end
        } else if (left) {
            if (start == 0) return null
            val before = connection.getTextBeforeCursor(2, 0)
            if (before != null && before.isEmpty()) return null
            val step = if (before != null && before.length >= 2 &&
                Character.isSurrogatePair(before[before.length - 2], before[before.length - 1])) 2 else 1
            (start - step).coerceAtLeast(0)
        } else {
            // An empty suffix is the right boundary. An unavailable suffix does not justify
            // guessing past the editor's end or falling back to a focus-navigation key event.
            val after = connection.getTextAfterCursor(2, 0) ?: return null
            if (after.isEmpty()) return null
            val step = if (after.length >= 2 && Character.isSurrogatePair(after[0], after[1])) 2 else 1
            if (end > Int.MAX_VALUE - step) return null
            end + step
        }
        return if (connection.setSelection(target, target)) target else null
    }
}
