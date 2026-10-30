/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.navigation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BottomBarModeTest {
    @Test
    fun missingSystemButtonsOnGestureDeviceGetFallback() {
        assertTrue(BottomBarMode.Auto.shouldShow(systemControlsVisible = false, floating = false))
        assertFalse(BottomBarMode.Auto.shouldShow(systemControlsVisible = true, floating = false))
    }

    @Test
    fun manualOverridesHandleOemDetectionExceptions() {
        assertTrue(BottomBarMode.On.shouldShow(systemControlsVisible = true, floating = false))
        assertFalse(BottomBarMode.Off.shouldShow(systemControlsVisible = false, floating = false))
    }

    @Test
    fun floatingKeyboardAlwaysIncludesItsOwnBottomBar() {
        BottomBarMode.entries.forEach {
            assertTrue(it.shouldShow(systemControlsVisible = false, floating = true))
            assertTrue(it.shouldShow(systemControlsVisible = true, floating = true))
        }
    }
}
