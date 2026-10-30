/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2025 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.candidates.horizontal

import android.content.res.Configuration
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.RectShape
import android.view.View
import androidx.core.view.children
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.core.CandidateWord
import org.fcitx.fcitx5.android.core.FcitxEvent
import org.fcitx.fcitx5.android.daemon.launchOnReady
import org.fcitx.fcitx5.android.data.prefs.AppPrefs
import org.fcitx.fcitx5.android.input.bar.ExpandButtonStateMachine.BooleanKey.ExpandedCandidatesEmpty
import org.fcitx.fcitx5.android.input.bar.ExpandButtonStateMachine.TransitionEvent.ExpandedCandidatesUpdated
import org.fcitx.fcitx5.android.input.bar.KawaiiBarComponent
import org.fcitx.fcitx5.android.input.broadcast.InputBroadcastReceiver
import org.fcitx.fcitx5.android.input.candidates.CandidateViewHolder
import org.fcitx.fcitx5.android.input.candidates.horizontal.HorizontalCandidateMode.AlwaysFillWidth
import org.fcitx.fcitx5.android.input.candidates.horizontal.HorizontalCandidateMode.AutoFillWidth
import org.fcitx.fcitx5.android.input.candidates.horizontal.HorizontalCandidateMode.NeverFillWidth
import org.fcitx.fcitx5.android.input.dependency.UniqueViewComponent
import org.fcitx.fcitx5.android.input.dependency.context
import org.fcitx.fcitx5.android.input.dependency.fcitx
import org.fcitx.fcitx5.android.input.dependency.inputView
import org.fcitx.fcitx5.android.input.dependency.inputMethodService
import org.fcitx.fcitx5.android.input.dependency.theme
import org.mechdancer.dependency.manager.must
import splitties.dimensions.dp
import kotlin.math.max

class HorizontalCandidateComponent :
    UniqueViewComponent<HorizontalCandidateComponent, RecyclerView>(), InputBroadcastReceiver {

    private val context by manager.context()
    private val fcitx by manager.fcitx()
    private val theme by manager.theme()
    private val inputView by manager.inputView()
    private val service by manager.inputMethodService()
    private val bar: KawaiiBarComponent by manager.must()

    private val fillStyle by AppPrefs.getInstance().keyboard.horizontalCandidateStyle
    private val maxSpanCountPref by lazy {
        AppPrefs.getInstance().keyboard.run {
            if (context.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT)
                expandedCandidateGridSpanCount
            else
                expandedCandidateGridSpanCountLandscape
        }
    }

    private var layoutMinWidth = 0
    private var layoutFillWidth = true
    private var contentScale = 1f
    private var fcitxCandidates = emptyArray<CandidateWord>()
    private var fcitxCandidateTotal = -1
    private var candidateOverride: CandidateOverride? = null
    private var candidateGeneration = 0
    private var loadMoreJob: Job? = null
    private var reachedCandidateEnd = false

    private data class CandidateOverride(
        val candidates: Array<CandidateWord>,
        val onCandidateClick: (Int) -> Unit,
    )

    // Since expanded candidate window is created once the expand button was clicked,
    // we need to replay the last offset
    private val _expandedCandidateOffset = MutableSharedFlow<Int>(
        replay = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    val expandedCandidateOffset = _expandedCandidateOffset.asSharedFlow()

    private fun refreshExpanded(childCount: Int) {
        _expandedCandidateOffset.tryEmit(childCount)
        bar.expandButtonStateMachine.push(
            ExpandedCandidatesUpdated,
            ExpandedCandidatesEmpty to (adapter.itemCount == 0 ||
                (adapter.total >= 0 && adapter.total <= childCount))
        )
    }

    val adapter: HorizontalCandidateViewAdapter by lazy {
        object : HorizontalCandidateViewAdapter(theme) {
            override fun onBindViewHolder(holder: CandidateViewHolder, position: Int) {
                super.onBindViewHolder(holder, position)
                holder.ui.setContentScale(contentScale)
                holder.itemView.setOnClickListener {
                    candidateOverride?.onCandidateClick?.invoke(holder.idx)
                        ?: fcitx.launchOnReady { it.select(holder.idx) }
                }
                holder.itemView.setOnLongClickListener {
                    if (candidateOverride != null) return@setOnLongClickListener false
                    inputView.showCandidateActionMenu(holder.idx, holder.candidate.text, holder.ui.root)
                    true
                }
            }

            override fun onViewRecycled(holder: CandidateViewHolder) {
                holder.itemView.setOnClickListener(null)
                holder.itemView.setOnLongClickListener(null)
                super.onViewRecycled(holder)
            }
        }
    }

    fun setContentScale(scale: Float) {
        if (contentScale == scale) return
        contentScale = scale
        view.children.forEach {
            (view.getChildViewHolder(it) as? CandidateViewHolder)?.ui?.setContentScale(scale)
        }
    }

    val layoutManager: LinearLayoutManager by lazy {
        object : LinearLayoutManager(context, RecyclerView.HORIZONTAL, false) {
            override fun onLayoutCompleted(state: RecyclerView.State) {
                super.onLayoutCompleted(state)
                val cnt = if (adapter.itemCount > 0) {
                    (findLastCompletelyVisibleItemPosition() + 1).coerceAtLeast(1)
                } else {
                    this.childCount
                }
                refreshExpanded(cnt)
            }
        }
    }

    private val dividerDrawable by lazy {
        ShapeDrawable(RectShape()).apply {
            val intrinsicSize = max(1, context.dp(1))
            intrinsicWidth = intrinsicSize
            intrinsicHeight = intrinsicSize
            paint.color = theme.dividerColor
        }
    }

    private fun updateItemLayout(width: Int) {
        if (candidateOverride == null && fillStyle == AutoFillWidth) {
            layoutMinWidth = (width / maxSpanCountPref.getValue() - dividerDrawable.intrinsicWidth)
                .coerceAtLeast(0)
        }
        adapter.configureLayout(width, layoutMinWidth, layoutFillWidth, dividerDrawable.intrinsicWidth)
    }

    override val view by lazy {
        object : RecyclerView(context) {
            override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
                super.onSizeChanged(w, h, oldw, oldh)
                updateItemLayout(w)
            }
        }.apply {
            id = R.id.candidate_view
            itemAnimator = null
            // Edge stretching changes with the finger's vertical position and makes a
            // held horizontal drag visibly twitch when it turns downwards at an edge.
            overScrollMode = View.OVER_SCROLL_NEVER
            adapter = this@HorizontalCandidateComponent.adapter
            layoutManager = this@HorizontalCandidateComponent.layoutManager
            addItemDecoration(DividerItemDecoration(context, RecyclerView.HORIZONTAL).apply {
                setDrawable(InsetDrawable(dividerDrawable, 0, context.dp(8), 0, context.dp(8)))
            })
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    if (dx == 0) return
                    val last = this@HorizontalCandidateComponent.layoutManager.findLastVisibleItemPosition()
                    if (last >= adapter!!.itemCount - 8) loadMoreCandidates()
                }
            })
        }
    }

    private fun loadMoreCandidates() {
        if (candidateOverride != null || reachedCandidateEnd || loadMoreJob?.isActive == true) return
        val start = adapter.itemCount
        if (start == 0 || (adapter.total >= 0 && start >= adapter.total)) return
        val generation = candidateGeneration
        loadMoreJob = service.lifecycleScope.launch {
            val more = fcitx.runOnReady { getCandidates(start, 48) }
            // Input may have changed while the engine was answering the request.
            if (generation != candidateGeneration || candidateOverride != null) return@launch
            reachedCandidateEnd = more.size < 48
            adapter.appendCandidates(more)
        }
    }

    fun cancelLoading() {
        candidateGeneration++
        loadMoreJob?.cancel()
        loadMoreJob = null
    }

    override fun onCandidateUpdate(data: FcitxEvent.CandidateListEvent.Data) {
        cancelLoading()
        reachedCandidateEnd = data.total >= 0 && data.candidates.size >= data.total
        fcitxCandidates = data.candidates
        fcitxCandidateTotal = data.total
        if (candidateOverride != null) return
        showFcitxCandidates()
    }

    fun setCandidateOverride(
        candidates: Array<CandidateWord>,
        onCandidateClick: (Int) -> Unit,
        onClear: () -> Unit,
    ) {
        cancelLoading()
        candidateOverride = CandidateOverride(candidates, onCandidateClick)
        layoutMinWidth = 0
        layoutFillWidth = false
        updateItemLayout(view.width)
        adapter.updateCandidates(candidates, candidates.size)
        view.scrollToPosition(0)
        bar.updateCandidateOverride(candidates.isEmpty(), onClear)
        if (candidates.isEmpty()) {
            refreshExpanded(0)
        } else {
            // Wait for layout to calculate how many candidates are fully visible.
            // Until then the previous expanded offset must not be actionable.
            bar.expandButtonStateMachine.push(
                ExpandedCandidatesUpdated,
                ExpandedCandidatesEmpty to true,
            )
        }
    }

    val isCandidateOverrideActive: Boolean
        get() = candidateOverride != null

    fun candidateOverrideCandidates(offset: Int): List<CandidateWord>? =
        candidateOverride?.candidates?.drop(offset.coerceAtLeast(0))

    fun selectCandidateOverride(index: Int): Boolean {
        val override = candidateOverride ?: return false
        if (index !in override.candidates.indices) return false
        override.onCandidateClick(index)
        return true
    }

    fun clearCandidateOverride() {
        if (candidateOverride == null) return
        candidateOverride = null
        bar.clearCandidateOverride()
        showFcitxCandidates()
    }

    private fun showFcitxCandidates() {
        val candidates = fcitxCandidates
        val total = fcitxCandidateTotal
        val maxSpanCount = maxSpanCountPref.getValue()
        when (fillStyle) {
            NeverFillWidth -> {
                layoutMinWidth = 0
                layoutFillWidth = false
            }
            AutoFillWidth -> {
                layoutMinWidth = (view.width / maxSpanCount - dividerDrawable.intrinsicWidth).coerceAtLeast(0)
                layoutFillWidth = true
            }
            AlwaysFillWidth -> {
                layoutMinWidth = 0
                layoutFillWidth = true
            }
        }
        updateItemLayout(view.width)
        adapter.updateCandidates(candidates, total)
        view.stopScroll()
        view.scrollToPosition(0)
        // Clear the expanded state immediately rather than waiting for the next layout.
        if (candidates.isEmpty()) {
            refreshExpanded(0)
        }
    }
}
