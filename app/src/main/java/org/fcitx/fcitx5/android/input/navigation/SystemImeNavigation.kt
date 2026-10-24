/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.navigation

import android.os.Build
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import androidx.core.view.WindowInsetsCompat

/** Inspect our own IME window; no hidden APIs, manufacturer allowlist or accessibility service. */
object SystemImeNavigation {
    var lastControlsVisible: Boolean? = null
        private set

    fun controlsVisible(decor: View, windowInsets: WindowInsets?): Boolean {
        return inspectControls(decor, windowInsets).also { lastControlsVisible = it }
    }

    /** Prefer observations from the IME; Activity navigation insets alone are not equivalent. */
    fun previewControlsVisible(view: View): Boolean =
        lastControlsVisible ?: inspectControls(view, view.rootWindowInsets)

    private fun inspectControls(decor: View, windowInsets: WindowInsets?): Boolean {
        if (hasNavigationControls(decor)) return true
        val insets = windowInsets ?: return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM &&
            insets.isVisible(WindowInsets.Type.captionBar()) &&
            insets.getInsets(WindowInsets.Type.captionBar()).bottom > 0
        ) return true

        // Before gesture navigation the system owns the buttons in a separate window.
        // A gesture handle or mandatory gesture inset alone is NOT evidence of IME buttons.
        val navigationMode = runCatching {
            Settings.Secure.getInt(decor.context.contentResolver, "navigation_mode", -1)
        }.getOrDefault(-1)
        val compat = WindowInsetsCompat.toWindowInsetsCompat(insets, decor)
        val navHeight = compat.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
        val buttonBarHeight = (32 * decor.resources.displayMetrics.density).toInt()
        return (navigationMode == 0 || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) &&
            compat.isVisible(WindowInsetsCompat.Type.navigationBars()) && navHeight >= buttonBarHeight
    }

    private fun hasNavigationControls(view: View): Boolean {
        if (view.visibility != View.VISIBLE || view.alpha == 0f) return false
        if (view.javaClass.name.startsWith("android.inputmethodservice.navigationbar.") &&
            view.javaClass.simpleName == "NavigationBarView" && view.width > 0 && view.height > 0
        ) return true
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                if (hasNavigationControls(view.getChildAt(i))) return true
            }
        }
        return false
    }
}
