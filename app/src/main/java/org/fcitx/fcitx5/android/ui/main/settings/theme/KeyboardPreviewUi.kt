/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2024 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.ui.main.settings.theme

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import org.fcitx.fcitx5.android.data.prefs.AppPrefs
import org.fcitx.fcitx5.android.data.prefs.ManagedPreference
import org.fcitx.fcitx5.android.data.prefs.ManagedPreferenceProvider
import org.fcitx.fcitx5.android.data.theme.Theme
import org.fcitx.fcitx5.android.data.theme.ThemeManager
import org.fcitx.fcitx5.android.data.theme.ThemePrefs.NavbarBackground
import org.fcitx.fcitx5.android.input.keyboard.TextKeyboard
import org.fcitx.fcitx5.android.input.navigation.ImeBottomBar
import org.fcitx.fcitx5.android.input.navigation.SystemImeNavigation
import org.fcitx.fcitx5.android.utils.navbarFrameHeight
import splitties.dimensions.dp
import splitties.views.backgroundColor
import splitties.views.dsl.constraintlayout.below
import splitties.views.dsl.constraintlayout.centerHorizontally
import splitties.views.dsl.constraintlayout.centerInParent
import splitties.views.dsl.constraintlayout.constraintLayout
import splitties.views.dsl.constraintlayout.lParams
import splitties.views.dsl.constraintlayout.matchConstraints
import splitties.views.dsl.core.Ui
import splitties.views.dsl.core.add
import splitties.views.dsl.core.horizontalMargin
import splitties.views.dsl.core.imageView
import splitties.views.dsl.core.lParams
import splitties.views.dsl.core.view
import splitties.views.imageDrawable

class KeyboardPreviewUi(override val ctx: Context, val theme: Theme) : Ui {

    var intrinsicWidth: Int = -1
        private set

    var intrinsicHeight: Int = -1
        private set

    private val keyboardPrefs = AppPrefs.getInstance().keyboard
    private val keyboardHeightPercent by keyboardPrefs.keyboardHeightPercent
    private val keyboardHeightPercentLandscape by keyboardPrefs.keyboardHeightPercentLandscape
    private val keyboardSidePadding by keyboardPrefs.keyboardSidePadding
    private val keyboardSidePaddingLandscape by keyboardPrefs.keyboardSidePaddingLandscape
    private val keyboardBottomPadding by keyboardPrefs.keyboardBottomPadding
    private val keyboardBottomPaddingLandscape by keyboardPrefs.keyboardBottomPaddingLandscape

    private val keyboardSidePaddingPx: Int
        get() {
            val value = when (ctx.resources.configuration.orientation) {
                Configuration.ORIENTATION_LANDSCAPE -> keyboardSidePaddingLandscape
                else -> keyboardSidePadding
            }
            return ctx.dp(value)
        }

    private val keyboardBottomPaddingPx: Int
        get() {
            val value = when (ctx.resources.configuration.orientation) {
                Configuration.ORIENTATION_LANDSCAPE -> keyboardBottomPaddingLandscape
                else -> keyboardBottomPadding
            }
            return ctx.dp(value)
        }

    private val navbarBackground = ThemeManager.prefs.navbarBackground
    private val keyBorder by ThemeManager.prefs.keyBorder

    private val navbarBkgChangeListener = ManagedPreference.OnChangeListener<Any> { _, _ ->
        recalculateSize()
    }

    private val bkg = imageView {
        scaleType = ImageView.ScaleType.CENTER_CROP
    }

    private val barHeight = ctx.dp(40)
    private val fakeKawaiiBar = view(::View)

    private var keyboardWidth = -1
    private var keyboardHeight = -1
    private lateinit var fakeKeyboardWindow: TextKeyboard
    private var previewTheme = theme
    private var bottomBar: ImeBottomBar? = null
    private val bottomBarPrefs = ThemeManager.prefs.run {
        setOf(bottomBarMode.key, bottomBarLeftAction.key, bottomBarRightAction.key)
    }
    private val bottomBarChangeListener = ManagedPreferenceProvider.OnChangeListener { key ->
        if (key in bottomBarPrefs) {
            updateBottomBar()
            recalculateSize()
        }
    }

    private val fakeInputView = constraintLayout {
        add(bkg, lParams {
            centerInParent()
        })
        add(fakeKawaiiBar, lParams(height = dp(40)) {
            centerHorizontally()
        })
    }

    override val root = object : FrameLayout(ctx) {
        init {
            add(fakeInputView, lParams())
        }

        override fun onAttachedToWindow() {
            super.onAttachedToWindow()
            recalculateSize()
            onSizeMeasured?.invoke(intrinsicWidth, intrinsicHeight)
            navbarBackground.registerOnChangeListener(navbarBkgChangeListener)
            ThemeManager.prefs.registerOnChangeListener(bottomBarChangeListener)
        }

        override fun onConfigurationChanged(newConfig: Configuration?) {
            recalculateSize()
        }

        override fun onDetachedFromWindow() {
            navbarBackground.unregisterOnChangeListener(navbarBkgChangeListener)
            ThemeManager.prefs.unregisterOnChangeListener(bottomBarChangeListener)
            super.onDetachedFromWindow()
        }
    }

    var onSizeMeasured: ((Int, Int) -> Unit)? = null

    private fun keyboardWindowAspectRatio(): Pair<Int, Int> {
        val resources = ctx.resources
        val displayMetrics = resources.displayMetrics
        val w = displayMetrics.widthPixels
        val h = displayMetrics.heightPixels
        val hPercent = when (resources.configuration.orientation) {
            Configuration.ORIENTATION_LANDSCAPE -> keyboardHeightPercentLandscape
            else -> keyboardHeightPercent
        }
        return w to (h * hPercent / 100)
    }

    init {
        val (w, h) = keyboardWindowAspectRatio()
        keyboardWidth = w
        keyboardHeight = h
        setTheme(theme)
    }

    fun recalculateSize() {
        val (w, h) = keyboardWindowAspectRatio()
        keyboardWidth = w
        keyboardHeight = h
        fakeKeyboardWindow.updateLayoutParams<ConstraintLayout.LayoutParams> {
            height = keyboardHeight
            horizontalMargin = keyboardSidePaddingPx
        }
        intrinsicWidth = keyboardWidth
        // KawaiiBar height + WindowManager view height
        intrinsicHeight = barHeight + keyboardHeight
        // extra bottom padding
        intrinsicHeight += keyboardBottomPaddingPx
        bottomBar?.root?.let { bar ->
            val visible = ThemeManager.prefs.bottomBarMode.getValue().shouldShow(
                SystemImeNavigation.previewControlsVisible(root), floating = false
            )
            bar.visibility = if (visible) View.VISIBLE else View.GONE
            bar.updateLayoutParams<ConstraintLayout.LayoutParams> {
                horizontalMargin = keyboardSidePaddingPx
            }
            if (visible) intrinsicHeight += ctx.dp(ImeBottomBar.HEIGHT)
        }
        // windowInsets navbar padding
        if (navbarBackground.getValue() == NavbarBackground.Full) {
            ViewCompat.getRootWindowInsets(root)?.also {
                // IME window has different navbar height when system navigation in "gesture navigation" mode
                // thus the inset from Activity root window is unreliable
                if (it.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom > 0 ||
                    // in case navigation hint was hidden ...
                    it.getInsets(WindowInsetsCompat.Type.mandatorySystemGestures()).bottom > 0
                ) {
                    intrinsicHeight += ctx.navbarFrameHeight()
                }
            }
        }
        fakeInputView.updateLayoutParams<FrameLayout.LayoutParams> {
            width = intrinsicWidth
            height = intrinsicHeight
        }
    }

    fun setBackground(drawable: Drawable) {
        bkg.imageDrawable = drawable
    }

    fun setTheme(theme: Theme, background: Drawable? = null) {
        previewTheme = theme
        setBackground(background ?: theme.backgroundDrawable(keyBorder))
        if (this::fakeKeyboardWindow.isInitialized) {
            fakeInputView.removeView(fakeKeyboardWindow)
        }
        fakeKawaiiBar.backgroundColor = if (keyBorder) Color.TRANSPARENT else theme.barColor
        fakeKeyboardWindow = TextKeyboard(ctx, theme).also {
            it.onAttach()
        }
        fakeInputView.apply {
            add(fakeKeyboardWindow, lParams(matchConstraints, keyboardHeight) {
                below(fakeKawaiiBar)
                centerHorizontally(keyboardSidePaddingPx)
            })
        }
        updateBottomBar()
        recalculateSize()
    }

    private fun updateBottomBar() {
        bottomBar?.root?.let(fakeInputView::removeView)
        // Like the existing preview keyboard, these buttons show the configured
        // layout without executing IME actions against the settings Activity.
        bottomBar = ImeBottomBar(
            ctx, previewTheme,
            ThemeManager.prefs.bottomBarLeftAction.getValue(),
            ThemeManager.prefs.bottomBarRightAction.getValue(),
            onAction = {}, onPicker = {}, interactive = false,
        ).also { bar ->
            fakeInputView.apply {
                add(bar.root, lParams(matchConstraints, ctx.dp(ImeBottomBar.HEIGHT)) {
                    below(fakeKeyboardWindow)
                    centerHorizontally(keyboardSidePaddingPx)
                })
            }
        }
    }
}
