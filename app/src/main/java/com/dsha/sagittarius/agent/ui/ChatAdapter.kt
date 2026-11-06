package com.dsha.sagittarius.agent.ui

import android.graphics.Color
import android.animation.ValueAnimator
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.dsha.sagittarius.R
import com.dsha.sagittarius.ui.ButtonAccentManager
import com.dsha.sagittarius.ui.ThemeTokens
import kotlin.math.sin

data class ToolTrace(
    val name: String,
    val args: String,
    val display: String? = null,
    val result: String? = null,
    val failed: Boolean = false,
    val done: Boolean = false
)

data class ChatItem(
    val id: Long,
    val role: Role,
    val content: String = "",
    val reasoning: String = "",
    val reasoningExpanded: Boolean = false,
    val toolName: String? = null,
    val toolDisplay: String? = null,
    val toolArgs: String? = null,
    val toolResult: String? = null,
    val toolFailed: Boolean = false,
    val toolTraces: List<ToolTrace> = emptyList(),
    val error: String? = null,
    val streaming: Boolean = false,
    val stopped: Boolean = false
) {
    enum class Role { USER, ASSISTANT, TOOL, ERROR }
}

class ChatAdapter : ListAdapter<ChatItem, ChatAdapter.Holder>(Diff) {

    var onItemLongClick: ((ChatItem) -> Unit)? = null
    var onToolClick: ((ToolTrace) -> Unit)? = null
    var onChoiceSelected: ((ChatItem, ChatChoice) -> Unit)? = null
    private val answeredChoices = mutableSetOf<String>()
    private val expanded = mutableSetOf<Long>()
    private val detailsExpanded = mutableSetOf<Long>()
    private val detailsRegex = Regex("\\[DETAILS\\]([\\s\\S]*?)\\[/DETAILS\\]", RegexOption.IGNORE_CASE)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_chat, parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(getItem(position))

    override fun onViewRecycled(holder: Holder) {
        holder.stopThinkingTips()
        super.onViewRecycled(holder)
    }

    inner class Holder(view: View) : RecyclerView.ViewHolder(view) {
        private val bubble: View = view.findViewById(R.id.bubble)
        private val content: RichTextView = view.findViewById(R.id.tvContent)
        private val reasoning: TextView = view.findViewById(R.id.tvReasoning)
        private val workflowHeader: TextView = view.findViewById(R.id.tvWorkflowHeader)
        private val workflowChips: LinearLayout = view.findViewById(R.id.workflowChips)
        private val error: TextView = view.findViewById(R.id.tvError)
        private val detailsCard: LinearLayout = view.findViewById(R.id.detailsCard)
        private val detailsHeader: TextView = view.findViewById(R.id.tvDetailsHeader)
        private val detailsBody: TextView = view.findViewById(R.id.tvDetailsBody)
        private val tail: RichTextView = view.findViewById(R.id.tvTail)
        private val tipView: TextView = view.findViewById(R.id.tvThinkingTip)
        private val choiceScroll: android.widget.HorizontalScrollView = view.findViewById(R.id.choiceScroll)
        private val choiceRow: LinearLayout = view.findViewById(R.id.choiceRow)
        private val tokens = ThemeTokens.of(view.context)
        private val handler = Handler(Looper.getMainLooper())
        private var tipIndex = 0
        private var tipRunnable: Runnable? = null
        private var tipFloat: ValueAnimator? = null

        fun bind(item: ChatItem) {
            stopThinkingTips()
            val context = itemView.context
            val isUser = item.role == ChatItem.Role.USER
            itemView.setOnLongClickListener {
                if (item.role == ChatItem.Role.USER || item.role == ChatItem.Role.ASSISTANT) {
                    onItemLongClick?.invoke(item)
                    true
                } else false
            }
            val params = bubble.layoutParams as FrameLayout.LayoutParams
            params.gravity = if (isUser) Gravity.END else Gravity.START
            bubble.layoutParams = params
            val maxWidth = (context.resources.displayMetrics.widthPixels * 0.75f).toInt()
            val innerMax = (maxWidth - 24 * context.resources.displayMetrics.density).toInt()
            content.maxWidth = innerMax
            reasoning.maxWidth = innerMax
            error.maxWidth = innerMax
            detailsBody.maxWidth = innerMax
            tail.maxWidth = innerMax

            bubble.background = GradientDrawable().apply {
                cornerRadius = (20 * context.resources.displayMetrics.density).toFloat()
                if (isUser) {
                    setColors(ButtonAccentManager.current.colors)
                } else {
                    setColors(intArrayOf(tokens.cardFillTop, tokens.cardFillBottom))
                    setStroke(1, tokens.cardStrokeStart)
                }
            }
            val pad = (12 * context.resources.displayMetrics.density).toInt()
            bubble.setPadding(pad, pad, pad, pad)
            error.visibility = if (item.error.isNullOrBlank()) View.GONE else View.VISIBLE
            error.setTextColor(tokens.warning)
            error.text = item.error

            if (isUser) {
                detailsCard.visibility = View.GONE
                tail.visibility = View.GONE
                choiceScroll.visibility = View.GONE
                content.visibility = View.VISIBLE
                content.text = item.content
                content.setTextColor(ButtonAccentManager.current.textColor)
            } else {
                val choiceParsed = ChoiceParser.parse(item.content)
                val parsed = parseDetails(item, choiceParsed.text)
                content.visibility = if (parsed.first.isBlank() && !item.streaming) View.GONE else View.VISIBLE
                val finalText = when {
                    item.streaming && item.content.isBlank() -> context.getString(R.string.agent_thinking)
                    item.stopped -> parsed.first + "\n" + context.getString(R.string.agent_stopped)
                    else -> parsed.first
                }
                content.setRichText(finalText)
                if (item.streaming && item.content.isBlank()) startThinkingTips() else stopThinkingTips()
                content.setTextColor(tokens.textPrimary)
                renderDetails(item, parsed.second, parsed.third)
                renderChoices(item, choiceParsed.choices, innerMax)
            }

            val hasWorkflow = item.reasoning.isNotBlank() || item.toolTraces.isNotEmpty()
            val workflowExpanded = expanded.contains(item.id)
            workflowHeader.visibility = if (hasWorkflow) View.VISIBLE else View.GONE
            workflowHeader.text = context.getString(
                if (workflowExpanded) R.string.agent_workflow_expanded else R.string.agent_workflow_collapsed
            )
            workflowHeader.setOnClickListener {
                if (workflowExpanded) expanded.remove(item.id) else expanded.add(item.id)
                notifyItemChanged(bindingAdapterPosition)
            }
            reasoning.visibility = if (workflowExpanded && item.reasoning.isNotBlank()) View.VISIBLE else View.GONE
            reasoning.text = item.reasoning
            reasoning.alpha = 0.72f
            workflowChips.visibility = if (workflowExpanded && item.toolTraces.isNotEmpty()) View.VISIBLE else View.GONE
            workflowChips.removeAllViews()
            if (workflowExpanded) {
                item.toolTraces.forEach { trace ->
                    val chip = TextView(context).apply {
                        text = toolChipLabel(trace)
                        textSize = 11f
                        setTextColor(if (trace.failed) tokens.warning else tokens.textSecondary)
                        background = GradientDrawable().apply {
                            cornerRadius = 12f * context.resources.displayMetrics.density
                            setColor(androidx.core.graphics.ColorUtils.setAlphaComponent(tokens.accent, 18))
                            setStroke(1, androidx.core.graphics.ColorUtils.setAlphaComponent(tokens.accent, 70))
                        }
                        val ph = (9 * context.resources.displayMetrics.density).toInt()
                        val pv = (5 * context.resources.displayMetrics.density).toInt()
                        setPadding(ph, pv, ph, pv)
                        setOnClickListener { onToolClick?.invoke(trace) }
                    }
                    workflowChips.addView(chip, LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply { topMargin = (4 * context.resources.displayMetrics.density).toInt() })
                }
            }
        }

        fun stopThinkingTips() {
            tipRunnable?.let { handler.removeCallbacks(it) }
            tipRunnable = null
            tipFloat?.cancel()
            tipFloat = null
            tipView.visibility = View.GONE
            tipView.translationY = 0f
        }

        private fun startThinkingTips() {
            val tips = itemView.resources.getStringArray(R.array.agent_thinking_tips)
            if (tips.isEmpty()) return
            tipIndex = 0
            tipView.visibility = View.VISIBLE
            tipView.alpha = 0.75f
            tipFloat?.cancel()
            if (!com.dsha.sagittarius.ui.MotionUtils.reduceMotion(itemView.context)) {
                tipFloat = ValueAnimator.ofFloat(0f, 1f).apply {
                    duration = 2200L
                    repeatCount = ValueAnimator.INFINITE
                    addUpdateListener {
                        tipView.translationY = sin((it.animatedValue as Float) * Math.PI * 2).toFloat() * 2f * itemView.resources.displayMetrics.density
                    }
                    start()
                }
            }
            fun applyTip() {
                val text = tips[tipIndex % tips.size]
                if (com.dsha.sagittarius.ui.MotionUtils.reduceMotion(itemView.context)) {
                    tipView.text = text
                } else {
                    tipView.animate().alpha(0f).setDuration(120L).withEndAction {
                        tipView.text = text
                        tipView.animate().alpha(0.75f).setDuration(180L).start()
                    }.start()
                }
                tipIndex++
                val next = Runnable { applyTip() }
                tipRunnable = next
                handler.postDelayed(next, 2600L)
            }
            applyTip()
        }

        private fun parseDetails(item: ChatItem, content: String = item.content): Triple<String, String?, String?> {
            val match = detailsRegex.find(content)
            if (match == null) {
                val source = if (item.streaming) {
                    content.substringBefore("[DETAILS]").trimEnd()
                } else content
                return Triple(source, null, null)
            }
            val before = content.substring(0, match.range.first).trim()
            val detail = match.groupValues[1].trim()
            val after = content.substring(match.range.last + 1).trim()
            return Triple(before, detail, after)
        }

        private fun renderChoices(item: ChatItem, choices: List<ChatChoice>, maxWidth: Int) {
            choiceRow.removeAllViews()
            if (item.streaming || choices.isEmpty()) {
                choiceScroll.visibility = View.GONE
                return
            }
            choiceScroll.visibility = View.VISIBLE
            choiceScroll.layoutParams = choiceScroll.layoutParams.apply { width = maxWidth }
            choices.forEach { choice ->
                val key = "${item.id}:${choice.value}"
                val answered = answeredChoices.contains(key)
                val chip = TextView(itemView.context).apply {
                    text = choice.label
                    textSize = 12f
                    gravity = Gravity.CENTER
                    setTextColor(if (answered) tokens.textTertiary else tokens.accent)
                    background = GradientDrawable().apply {
                        cornerRadius = 16f * resources.displayMetrics.density
                        setColor(androidx.core.graphics.ColorUtils.setAlphaComponent(tokens.accent, if (answered) 8 else 22))
                        setStroke(1, androidx.core.graphics.ColorUtils.setAlphaComponent(tokens.accent, if (answered) 30 else 100))
                    }
                    isEnabled = !answered
                    alpha = if (answered) 0.55f else 1f
                    val ph = (12 * resources.displayMetrics.density).toInt()
                    val pv = (7 * resources.displayMetrics.density).toInt()
                    setPadding(ph, pv, ph, pv)
                    setOnClickListener {
                        if (answeredChoices.add(key)) {
                            onChoiceSelected?.invoke(item, choice)
                            notifyItemChanged(bindingAdapterPosition)
                        }
                    }
                }
                choiceRow.addView(chip, LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { marginEnd = (8 * itemView.resources.displayMetrics.density).toInt() })
            }
        }

        private fun renderDetails(item: ChatItem, detail: String?, after: String?) {
            if (detail.isNullOrBlank()) {
                detailsCard.visibility = View.GONE
                tail.visibility = View.GONE
                return
            }
            val isExpanded = detailsExpanded.contains(item.id)
            detailsCard.visibility = View.VISIBLE
            detailsCard.background = GradientDrawable().apply {
                cornerRadius = 14f * itemView.resources.displayMetrics.density
                setColor(androidx.core.graphics.ColorUtils.setAlphaComponent(tokens.cardFillTop, 210))
                setStroke(1, tokens.cardStrokeStart)
            }
            detailsHeader.text = if (isExpanded) {
                itemView.context.getString(R.string.agent_details_expanded)
            } else {
                itemView.context.getString(R.string.agent_details_collapsed)
            }
            detailsBody.text = detail
            detailsBody.visibility = if (isExpanded) View.VISIBLE else View.GONE
            if (isExpanded) {
                detailsBody.alpha = 0f
                detailsBody.animate().alpha(1f).setDuration(160L).start()
            } else {
                detailsBody.alpha = 1f
            }
            tail.visibility = if (after.isNullOrBlank()) View.GONE else View.VISIBLE
            tail.setRichText(after.orEmpty())
            detailsCard.setOnClickListener {
                if (isExpanded) detailsExpanded.remove(item.id) else detailsExpanded.add(item.id)
                notifyItemChanged(bindingAdapterPosition)
            }
        }

        private fun toolChipLabel(trace: ToolTrace): String {
            val action = when (trace.name) {
                "add_expense" -> "💰 记账"
                "update_expense" -> "✏️ 修改账单"
                "delete_expense" -> "🗑️ 删除账单"
                "set_budget" -> "💳 设置预算"
                "allocate_budget_tree" -> "🌳 分配预算"
                "add_diet" -> "🍎 饮食"
                "add_food_to_library" -> "📦 添加食材"
                "manage_food_category" -> "🏷️ 食材分类"
                "search_food_cn_local", "get_cn_food_nutrition" -> "🔍 营养库"
                "web_search" -> "🌐 联网搜索"
                "manage_food_library" -> "📦 食材库"
                "toggle_network_search" -> "🌐 联网开关"
                "update_theme" -> "🎨 主题"
                "update_date_card_color" -> "🎨 顶栏颜色"
                "toggle_floating_window" -> "🪟 悬浮窗"
                "navigate_to_tab" -> "🧭 页面跳转"
                "control_weather_debug" -> "🌦️ 天气特效"
                "update_app_settings" -> "⚙️ 设置"
                "query_app_data", "query_data" -> "📊 查询"
                else -> "🛠️ 工具"
            }
            val detail = trace.display?.takeIf { it.isNotBlank() }?.let { " · ${it.take(22)}" }.orEmpty()
            val state = when {
                trace.failed -> " · 失败"
                !trace.done -> " …"
                else -> ""
            }
            return action + detail + state
        }
    }

    private object Diff : DiffUtil.ItemCallback<ChatItem>() {
        override fun areItemsTheSame(oldItem: ChatItem, newItem: ChatItem): Boolean = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: ChatItem, newItem: ChatItem): Boolean = oldItem == newItem
    }
}
