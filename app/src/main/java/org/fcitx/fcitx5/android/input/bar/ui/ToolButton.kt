/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2023 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.bar.ui

import android.content.Context
import android.content.res.ColorStateList
import android.view.View
import android.view.ViewPropertyAnimator
import android.widget.ImageView
import androidx.core.view.updateLayoutParams
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import org.fcitx.fcitx5.android.data.prefs.AppPrefs
import org.fcitx.fcitx5.android.data.theme.Theme
import org.fcitx.fcitx5.android.input.keyboard.CustomGestureView
import org.fcitx.fcitx5.android.utils.borderlessRippleDrawable
import org.fcitx.fcitx5.android.utils.circlePressHighlightDrawable
import org.fcitx.fcitx5.android.utils.circleRippleDrawable
import splitties.dimensions.dp
import splitties.views.dsl.core.add
import splitties.views.dsl.core.imageView
import splitties.views.dsl.core.lParams
import splitties.views.dsl.core.wrapContent
import splitties.views.gravityCenter
import splitties.views.imageResource
import kotlin.math.min
import kotlin.math.roundToInt

class ToolButton(context: Context) : CustomGestureView(context) {

    companion object {
        val disableAnimation by AppPrefs.getInstance().advanced.disableAnimation
    }

    private val image = imageView {
        isClickable = false
        isFocusable = false
        scaleType = ImageView.ScaleType.CENTER_INSIDE
    }
    private var contentScale = 1f
    private var compactSizing = false

    var iconRotation: Float
        get() = image.rotation
        set(value) {
            image.rotation = value
        }

    constructor(context: Context, @DrawableRes icon: Int, theme: Theme) : this(context) {
        image.imageTintList = ColorStateList.valueOf(theme.altKeyTextColor)
        setIcon(icon)
        setPressHighlightColor(theme.keyPressHighlightColor)
        add(image, lParams(dp(24), dp(24), gravityCenter))
    }

    fun iconAnimate(): ViewPropertyAnimator = image.animate()

    fun setIcon(@DrawableRes icon: Int) {
        image.imageResource = icon
    }

    fun setContentScale(scale: Float) {
        compactSizing = false
        contentScale = scale
        updateIconSize()
    }

    fun setCompactContentScale(scale: Float) {
        compactSizing = true
        contentScale = scale
        updateIconSize()
    }

    private fun updateIconSize(availableWidth: Int = width, availableHeight: Int = height) {
        var requested = (dp(24f) * contentScale).roundToInt()
        if (compactSizing && availableWidth > 0 && availableHeight > 0) {
            // The old wrap-content image lost 10dp padding on every edge before scaling.
            // Enlarge the 0.1.4 compact icon by another 50%, capped at a normal icon.
            val oldContent = min((availableWidth - dp(20)).coerceAtLeast(1), (availableHeight - dp(20)).coerceAtLeast(1))
            val previousSize = min(requested, (oldContent * contentScale * 1.5f).roundToInt())
            requested = min(requested, (previousSize * 1.5f).roundToInt())
        }
        val size = min(requested, min(availableWidth.takeIf { it > 0 } ?: requested,
            availableHeight.takeIf { it > 0 } ?: requested))
        if (image.layoutParams != null && (image.layoutParams.width != size || image.layoutParams.height != size)) {
            image.updateLayoutParams { width = size; height = size }
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // Flexbox can measure a button again after shrinking its cell. Size its image
        // before that pass so the child is measured with the final available space.
        updateIconSize(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.getSize(heightMeasureSpec))
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateIconSize()
    }

    fun setPressHighlightColor(@ColorInt color: Int) {
        background = if (disableAnimation) {
            circlePressHighlightDrawable(color)
        } else {
            borderlessRippleDrawable(color, dp(20))
        }
    }

    fun setBoundedPressHighlightColor(@ColorInt color: Int) {
        background = if (disableAnimation) {
            circlePressHighlightDrawable(color)
        } else {
            circleRippleDrawable(color)
        }
    }

}
