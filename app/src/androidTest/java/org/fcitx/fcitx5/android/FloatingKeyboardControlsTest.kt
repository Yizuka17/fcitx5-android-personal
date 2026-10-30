/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import androidx.test.platform.app.InstrumentationRegistry
import org.fcitx.fcitx5.android.data.theme.ThemePreset
import org.fcitx.fcitx5.android.input.FloatingKeyboardControls
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import splitties.dimensions.dp
import kotlin.math.roundToInt

class FloatingKeyboardControlsTest {
    @Test
    fun outerStrokesHaveEqualGapAndOnlyHandlesConsumeTouches() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val radius = context.dp(24f)
            val controls = FloatingKeyboardControls(context, ThemePreset.PixelDark) { radius }
            val width = context.dp(340)
            val height = context.dp(300)
            controls.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
            controls.layout(0, 0, width, height)
            val bodyRight = width - context.dp(FloatingKeyboardControls.GUTTER_DP)
            val bodyBottom = height - context.dp(FloatingKeyboardControls.GUTTER_DP)
            val gap = context.dp(FloatingKeyboardControls.GAP_DP)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            try {
                controls.draw(Canvas(bitmap))
                val barColor = bitmap.getPixel(bodyRight / 2, (bodyBottom + gap).roundToInt())
                val diagonal = (radius + gap) / kotlin.math.sqrt(2f)
                val arcColor = bitmap.getPixel((bodyRight - radius + diagonal).roundToInt(),
                    (bodyBottom - radius + diagonal).roundToInt())
                assertTrue(Color.alpha(barColor) > 0)
                assertEquals(barColor, arcColor)
                assertEquals(Color.TRANSPARENT, bitmap.getPixel(bodyRight / 2, bodyBottom - context.dp(4)))
            } finally { bitmap.recycle() }

            var drags = 0
            var resizes = 0
            controls.dragHandle.setOnTouchListener { _, _ -> drags++; true }
            controls.resizeHandle.setOnTouchListener { _, _ -> resizes++; true }
            fun touch(x: Float, y: Float): Boolean {
                val now = SystemClock.uptimeMillis()
                val down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, x, y, 0)
                val up = MotionEvent.obtain(now, now + 20, MotionEvent.ACTION_UP, x, y, 0)
                return try { controls.dispatchTouchEvent(down).also { controls.dispatchTouchEvent(up) } }
                finally { down.recycle(); up.recycle() }
            }
            assertTrue(touch(bodyRight / 2f, bodyBottom + gap))
            assertTrue(drags > 0)
            assertTrue(touch(bodyRight + gap, bodyBottom - radius))
            assertTrue(resizes > 0)
            assertFalse(touch(bodyRight - context.dp(28f), bodyBottom - context.dp(22f)))
        }
    }
}
