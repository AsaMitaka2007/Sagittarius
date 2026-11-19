package com.dsha.sagittarius.agent.ui

import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.recyclerview.widget.RecyclerView
import com.dsha.sagittarius.R
import com.dsha.sagittarius.data.ChatSessionEntity
import com.dsha.sagittarius.ui.ThemeTokens
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 会话胶囊内的列表：普通点击切换，长按进入多选。 */
class SessionAdapter(
    private val tokens: ThemeTokens,
    private val onClick: (ChatSessionEntity) -> Unit,
    private val onLongClick: (ChatSessionEntity) -> Unit
) : RecyclerView.Adapter<SessionAdapter.Holder>() {

    private var sessions: List<ChatSessionEntity> = emptyList()
    private var currentId = -1L
    private var selectionMode = false
    private val selected = linkedSetOf<Long>()
    private val timeFormat = SimpleDateFormat("MM-dd HH:mm", Locale.CHINA)

    fun submit(
        sessions: List<ChatSessionEntity>,
        currentId: Long,
        selectionMode: Boolean = this.selectionMode,
        selectedIds: Set<Long> = selected
    ) {
        this.sessions = sessions
        this.currentId = currentId
        this.selectionMode = selectionMode
        selected.clear()
        selected.addAll(selectedIds.filter { id -> sessions.any { it.id == id } })
        notifyDataSetChanged()
    }

    fun setSelectionMode(mode: Boolean) {
        selectionMode = mode
        if (!mode) selected.clear()
        notifyDataSetChanged()
    }

    fun toggle(id: Long) {
        if (selected.contains(id)) selected.remove(id) else selected.add(id)
        notifyDataSetChanged()
    }

    fun selectAll() {
        selected.clear()
        selected.addAll(sessions.map { it.id })
        notifyDataSetChanged()
    }

    fun selectedIds(): Set<Long> = selected.toSet()

    fun isSelectionMode(): Boolean = selectionMode

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_agent_session, parent, false)
        AgentTypography.apply(view)
        return Holder(view)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(sessions[position])
    }

    override fun getItemCount(): Int = sessions.size

    inner class Holder(view: View) : RecyclerView.ViewHolder(view) {
        private val row: View = view.findViewById(R.id.sessionRow)
        private val dot: View = view.findViewById(R.id.selectDot)
        private val title: TextView = view.findViewById(R.id.tvSessionTitle)
        private val time: TextView = view.findViewById(R.id.tvSessionTime)
        private val current: TextView = view.findViewById(R.id.tvCurrent)

        fun bind(session: ChatSessionEntity) {
            title.text = session.title.ifBlank { "对话" }
            time.text = timeFormat.format(Date(session.updatedAt))
            val isCurrent = session.id == currentId
            current.visibility = if (isCurrent && !selectionMode) View.VISIBLE else View.GONE
            title.setTextColor(if (isCurrent) AgentInkColors.CREAM else AgentInkColors.TEXT_PRIMARY)
            row.background = GradientDrawable().apply {
                cornerRadius = 14f * row.resources.displayMetrics.density
                setColor(
                    if (isCurrent) ColorUtils.setAlphaComponent(tokens.accent, 18) else 0
                )
            }
            dot.visibility = if (selectionMode) View.VISIBLE else View.GONE
            if (selectionMode) {
                val checked = selected.contains(session.id)
                dot.background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(if (checked) AgentInkColors.CREAM else 0)
                    setStroke(
                        (1.2f * row.resources.displayMetrics.density).toInt(),
                        if (checked) AgentInkColors.CREAM else AgentInkColors.TEXT_TERTIARY
                    )
                }
            }
            row.setOnClickListener {
                if (selectionMode) toggle(session.id) else onClick(session)
            }
            row.setOnLongClickListener {
                if (!selectionMode) {
                    onLongClick(session)
                    true
                } else {
                    toggle(session.id)
                    true
                }
            }
        }
    }
}
