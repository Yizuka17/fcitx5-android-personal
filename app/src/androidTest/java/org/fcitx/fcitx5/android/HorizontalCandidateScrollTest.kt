/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android

import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import androidx.appcompat.view.ContextThemeWrapper
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.testing.TestLifecycleOwner
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.platform.app.InstrumentationRegistry
import org.fcitx.fcitx5.android.core.CandidateWord
import org.fcitx.fcitx5.android.data.theme.ThemePreset
import org.fcitx.fcitx5.android.input.candidates.CandidateViewHolder
import org.fcitx.fcitx5.android.input.candidates.horizontal.HorizontalCandidateViewAdapter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import splitties.dimensions.dp

class HorizontalCandidateScrollTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    private fun withList(test: (RecyclerView, HorizontalCandidateViewAdapter, MutableList<Int>) -> Unit) {
        instrumentation.runOnMainSync {
            val context = ContextThemeWrapper(instrumentation.targetContext, R.style.Theme_FcitxAppTheme)
            val clicks = mutableListOf<Int>()
            val adapter = object : HorizontalCandidateViewAdapter(ThemePreset.PixelDark) {
                override fun onBindViewHolder(holder: CandidateViewHolder, position: Int) {
                    super.onBindViewHolder(holder, position)
                    holder.itemView.setOnClickListener { clicks.add(holder.idx) }
                    holder.itemView.setOnLongClickListener { clicks.add(-1); true }
                }
            }
            val list = RecyclerView(context).apply {
                setViewTreeLifecycleOwner(TestLifecycleOwner())
                layoutManager = LinearLayoutManager(context, RecyclerView.HORIZONTAL, false)
                itemAnimator = null
                overScrollMode = View.OVER_SCROLL_NEVER
                this.adapter = adapter
            }
            FrameLayout(context).addView(list)
            adapter.configureLayout(context.dp(300), 0, false, 0)
            adapter.updateCandidates(Array(48) { CandidateWord("", "候选词$it", "") }, 120)
            layout(list)
            try { test(list, adapter, clicks) } finally {
                send(list, MotionEvent.ACTION_CANCEL, 0f, 0f)
                list.stopScroll()
                list.adapter = null
            }
        }
    }

    private fun layout(list: RecyclerView, width: Int = list.context.dp(300)) {
        list.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(list.context.dp(40), View.MeasureSpec.EXACTLY),
        )
        list.layout(0, 0, list.measuredWidth, list.measuredHeight)
    }

    private var downTime = 0L
    private var eventTime = 0L
    private fun send(list: RecyclerView, action: Int, x: Float, y: Float) {
        if (action == MotionEvent.ACTION_DOWN) {
            downTime = SystemClock.uptimeMillis()
            eventTime = downTime
        }
        val event = MotionEvent.obtain(downTime, eventTime, action, x, y, 0)
        try { list.dispatchTouchEvent(event) } finally { event.recycle() }
        eventTime += 80
    }

    private fun offset(list: RecyclerView): Int {
        val first = list.getChildAt(0)
        return list.getChildAdapterPosition(first) * 10000 - first.left
    }

    private fun drag(list: RecyclerView, left: Boolean) {
        val start = list.width * if (left) .85f else .15f
        val distance = list.width * if (left) -.6f else .6f
        val y = list.height / 2f
        send(list, MotionEvent.ACTION_DOWN, start, y)
        for (step in 1..6) send(list, MotionEvent.ACTION_MOVE, start + distance * step / 6, y)
        send(list, MotionEvent.ACTION_UP, start + distance, y)
        list.stopScroll()
    }

    @Test
    fun firstDragAndEveryFollowingDragScrollWithoutSelecting() = withList { list, adapter, clicks ->
        repeat(5) {
            val before = offset(list)
            drag(list, left = true)
            assertTrue("Left drag $it must move immediately", offset(list) > before)
        }
        val beforeAppend = offset(list)
        adapter.appendCandidates(Array(48) { CandidateWord("", "后续候选$it", "") })
        layout(list)
        assertEquals("Loading more must retain the scroll position", beforeAppend, offset(list))
        repeat(5) {
            val before = offset(list)
            drag(list, left = false)
            assertTrue("Right drag $it must move immediately", offset(list) < before)
        }
        assertTrue(clicks.isEmpty())
    }

    @Test
    fun turningDownDuringHorizontalDragKeepsCandidateGeometryStable() = withList { list, _, clicks ->
        val x = list.width * .8f
        val y = list.height / 2f
        send(list, MotionEvent.ACTION_DOWN, x, y)
        send(list, MotionEvent.ACTION_MOVE, x - list.context.dp(30), y)
        send(list, MotionEvent.ACTION_MOVE, x - list.context.dp(100), y)
        val before = offset(list)
        val tops = (0 until list.childCount).map { list.getChildAt(it).top }
        for (step in 1..5) {
            send(list, MotionEvent.ACTION_MOVE, x - list.context.dp(100), y + list.height * step / 2)
            assertEquals(before, offset(list))
            assertEquals(tops, (0 until list.childCount).map { list.getChildAt(it).top })
        }
        send(list, MotionEvent.ACTION_UP, x - list.context.dp(100), list.height * 3f)
        assertTrue(clicks.isEmpty())
    }

    @Test
    fun verticalDragInsideCandidateCancelsPressAndNextTapStillSelects() = withList { list, _, clicks ->
        val item = list.getChildAt(0)
        val x = item.width / 2f
        val y = list.context.dp(4f)
        send(list, MotionEvent.ACTION_DOWN, x, y)
        send(list, MotionEvent.ACTION_MOVE, x, y + list.context.dp(25f))
        assertFalse(item.isPressed)
        send(list, MotionEvent.ACTION_UP, x, y + list.context.dp(25f))
        assertTrue(clicks.isEmpty())
        send(list, MotionEvent.ACTION_DOWN, x, y)
        send(list, MotionEvent.ACTION_UP, x, y)
        assertEquals(listOf(0), clicks)
    }

    @Test
    fun shortListsFillWidthAndResizeOrAppendCanBecomeScrollable() = withList { list, adapter, _ ->
        adapter.configureLayout(list.width, list.width / 3, true, 0)
        adapter.updateCandidates(arrayOf(CandidateWord("", "甲", ""), CandidateWord("", "乙", "")), 120)
        layout(list)
        assertEquals(list.width, list.getChildAt(1).right)
        adapter.configureLayout(list.width, 0, false, 0)
        layout(list)
        assertTrue(list.getChildAt(1).right < list.width)
        adapter.configureLayout(list.width, 0, true, 0)
        layout(list)
        adapter.appendCandidates(Array(48) { CandidateWord("", "追加候选$it", "") })
        layout(list)
        drag(list, left = true)
        assertTrue(offset(list) > 0)
        assertTrue(list.childCount < adapter.itemCount)
        val narrowWidth = list.context.dp(200)
        adapter.configureLayout(narrowWidth, narrowWidth / 3, true, 0)
        layout(list, narrowWidth)
        val before = offset(list)
        drag(list, left = true)
        assertTrue(offset(list) > before)
    }
}
