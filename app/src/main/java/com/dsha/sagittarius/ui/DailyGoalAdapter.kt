package com.dsha.sagittarius.ui

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Paint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.dsha.sagittarius.R
import com.dsha.sagittarius.data.DailyGoalEntity
import com.dsha.sagittarius.data.DailyGoalMath
import java.time.LocalDate

/** 当日目标：未完成在上，完成后变暗、加删除线并沉到最下方。 */
class DailyGoalAdapter(
    private val onToggle: (DailyGoalEntity, Boolean) -> Unit,
    private val onDelete: (DailyGoalEntity) -> Unit
) : ListAdapter<DailyGoalEntity, DailyGoalAdapter.Holder>(Diff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_daily_goal, parent, false)
        return Holder(view)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(getItem(position))

    inner class Holder(view: View) : RecyclerView.ViewHolder(view) {
        private val checkbox: CheckBox = view.findViewById(R.id.cbGoal)
        private val title: TextView = view.findViewById(R.id.tvGoalTitle)
        private val effect: TextView = view.findViewById(R.id.tvGoalEffect)

        fun bind(goal: DailyGoalEntity) {
            val completed = DailyGoalMath.isCompleted(goal)
            title.text = goal.title
            effect.text = DailyGoalMath.label(goal.nutrientKey, goal.amount)
            checkbox.setOnCheckedChangeListener(null)
            checkbox.isChecked = completed
            val accent = ButtonAccentManager.current
            checkbox.buttonTintList = ColorStateList.valueOf(
                if (accent.isInk) Color.parseColor("#F3EBDD") else accent.startColor
            )
            checkbox.setOnCheckedChangeListener { _, checked -> onToggle(goal, checked) }
            val alpha = if (completed) 0.45f else 1f
            title.alpha = alpha
            effect.alpha = alpha
            checkbox.alpha = alpha
            title.paintFlags = if (completed) {
                title.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
            } else {
                title.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
            }
            itemView.setOnLongClickListener {
                onDelete(goal)
                true
            }
        }
    }

    private object Diff : DiffUtil.ItemCallback<DailyGoalEntity>() {
        override fun areItemsTheSame(oldItem: DailyGoalEntity, newItem: DailyGoalEntity) =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: DailyGoalEntity, newItem: DailyGoalEntity) =
            oldItem == newItem
    }

    companion object {
        /** 未完成按 sortOrder；今天已完成的排到最后。 */
        fun sortForToday(goals: List<DailyGoalEntity>, today: LocalDate = LocalDate.now()) =
            goals.sortedWith(
                compareBy<DailyGoalEntity> { DailyGoalMath.isCompleted(it, today) }
                    .thenBy { it.sortOrder }
                    .thenBy { it.id }
            )
    }
}
