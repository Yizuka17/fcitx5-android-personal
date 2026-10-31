/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2024 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.candidates.horizontal

import android.annotation.SuppressLint
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import androidx.annotation.CallSuper
import androidx.recyclerview.widget.RecyclerView
import org.fcitx.fcitx5.android.core.CandidateWord
import org.fcitx.fcitx5.android.data.theme.Theme
import org.fcitx.fcitx5.android.input.candidates.CandidateItemUi
import org.fcitx.fcitx5.android.input.candidates.CandidateViewHolder
import org.fcitx.fcitx5.android.input.keyboard.CustomGestureView
import splitties.dimensions.dp
import splitties.views.dsl.core.matchParent
import splitties.views.dsl.core.wrapContent
import splitties.views.setPaddingDp
import kotlin.math.roundToInt

open class HorizontalCandidateViewAdapter(val theme: Theme) :
    RecyclerView.Adapter<CandidateViewHolder>() {

    init {
        setHasStableIds(true)
    }

    var candidates: Array<CandidateWord> = arrayOf()
        private set

    var total = -1
        private set

    private var availableWidth = 0
    private var minimumItemWidth = 0
    private var fillWidth = false
    private var dividerWidth = 0
    private var filledItemWidths = intArrayOf()
    private var contentScale = 1f

    @SuppressLint("NotifyDataSetChanged")
    fun setContentScale(scale: Float) {
        if (contentScale == scale) return
        contentScale = scale
        measurementUi?.let(::scaleCandidate)
        updateFilledWidths()
        notifyDataSetChanged()
    }

    private fun scaleCandidate(ui: CandidateItemUi) {
        ui.setContentScale(contentScale)
        val padding = (ui.root.dp(10) * contentScale).roundToInt()
        ui.root.setPadding(padding, 0, padding, 0)
        ui.root.minimumWidth = maxOf((ui.root.dp(40) * contentScale).roundToInt(), minimumItemWidth)
    }

    @SuppressLint("NotifyDataSetChanged")
    fun configureLayout(width: Int, minWidth: Int, fill: Boolean, divider: Int) {
        if (availableWidth == width && minimumItemWidth == minWidth &&
            fillWidth == fill && dividerWidth == divider) return
        availableWidth = width
        minimumItemWidth = minWidth
        fillWidth = fill
        dividerWidth = divider
        updateFilledWidths()
        notifyDataSetChanged()
    }

    // Only measure the short list that fits in the viewport. Overflowing lists retain their
    // natural widths and use LinearLayoutManager's normal scrolling and view recycling.
    private fun updateFilledWidths() {
        filledItemWidths = intArrayOf()
        if (!fillWidth || availableWidth <= 0 || candidates.isEmpty()) return
        val ui = measurementUi ?: return
        val widths = IntArray(candidates.size)
        var used = 0
        for (i in candidates.indices) {
            ui.updateCandidate(candidates[i])
            scaleCandidate(ui)
            ui.root.measure(
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            )
            widths[i] = ui.root.measuredWidth
            used += widths[i] + dividerWidth
            if (used >= availableWidth) return
        }
        val extra = availableWidth - used
        filledItemWidths = widths.mapIndexed { i, width ->
            width + extra / widths.size + if (i < extra % widths.size) 1 else 0
        }.toIntArray()
    }

    private var measurementUi: CandidateItemUi? = null

    @SuppressLint("NotifyDataSetChanged")
    fun updateCandidates(data: Array<CandidateWord>, total: Int) {
        this.candidates = data
        this.total = total
        updateFilledWidths()
        notifyDataSetChanged()
    }

    fun appendCandidates(data: Array<CandidateWord>) {
        if (data.isEmpty()) return
        val start = candidates.size
        candidates += data
        val wasFilled = filledItemWidths.isNotEmpty()
        updateFilledWidths()
        if (wasFilled) notifyItemRangeChanged(0, start)
        notifyItemRangeInserted(start, data.size)
    }

    override fun getItemCount() = candidates.size

    override fun getItemId(position: Int) = candidates.getOrNull(position).hashCode().toLong()

    @CallSuper
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CandidateViewHolder {
        val ui = CandidateItemUi(parent.context, theme)
        if (measurementUi == null) {
            measurementUi = CandidateItemUi(parent.context, theme).apply {
                root.setPaddingDp(10, 0, 10, 0)
            }
            updateFilledWidths()
        }
        ui.root.apply {
            minimumWidth = dp(40)
            setPaddingDp(10, 0, 10, 0)
            layoutParams = RecyclerView.LayoutParams(wrapContent, matchParent)
            // A drag inside a wide candidate must cancel both click and long press even
            // before it leaves the item's bounds (including a purely vertical drag).
            swipeEnabled = true
            val slop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
            swipeThresholdX = slop
            swipeThresholdY = slop
            onGestureListener = CustomGestureView.OnGestureListener { view, event ->
                val dragged = event.type == CustomGestureView.GestureType.Move &&
                    (event.totalX != 0 || event.totalY != 0)
                if (dragged) view.isPressed = false
                dragged
            }
        }
        return CandidateViewHolder(ui)
    }

    @CallSuper
    override fun onBindViewHolder(holder: CandidateViewHolder, position: Int) {
        holder.update(position, candidates[position])
        scaleCandidate(holder.ui)
        holder.itemView.layoutParams.width = filledItemWidths.getOrNull(position) ?: ViewGroup.LayoutParams.WRAP_CONTENT
    }

    @CallSuper
    override fun onViewRecycled(holder: CandidateViewHolder) {
        holder.clear()
    }

}
