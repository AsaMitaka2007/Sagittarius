package com.dsha.sagittarius.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.dsha.sagittarius.R
import com.dsha.sagittarius.data.DietRecordEntity
import com.dsha.sagittarius.data.MealType
import java.util.Locale

/** 历史饮食分组列表：长按拖拽换餐次/排序，左滑展开红色删除按钮。 */
class DietHistoryAdapter(
    private val onDelete: (DietRecordEntity) -> Unit,
    private val onRecordsChanged: (List<DietRecordEntity>) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private sealed interface Item {
        data class Header(val meal: MealType) : Item
        data class Record(val record: DietRecordEntity) : Item
    }

    private var items: List<Item> = emptyList()
    private var records: List<DietRecordEntity> = emptyList()
    private var openRow: SwipeRevealLayout? = null
    private var dragging = false

    init {
        setHasStableIds(true)
    }

    fun submit(list: List<DietRecordEntity>) {
        records = list.sortedWith(compareBy({ it.mealType.ordinal }, { it.createdAt }, { it.id }))
        rebuild()
        notifyDataSetChanged()
    }

    private fun rebuild() {
        items = MealType.entries.flatMap { meal ->
            val group = records.filter { it.mealType == meal }
            if (group.isEmpty()) emptyList() else listOf(Item.Header(meal)) + group.map(Item::Record)
        }
    }

    fun recordAt(position: Int): DietRecordEntity? =
        (items.getOrNull(position) as? Item.Record)?.record

    fun closeOpenRow() {
        openRow?.close()
        openRow = null
    }

    fun setDragging(value: Boolean) {
        dragging = value
        openRow?.close()
        openRow = null
        notifyDataSetChanged()
    }

    fun move(from: Int, to: Int): Boolean {
        val source = recordAt(from) ?: return false
        if (to !in items.indices) return false
        val target = items[to]
        val targetMeal = when (target) {
            is Item.Header -> target.meal
            is Item.Record -> target.record.mealType
        }
        val flat = items.mapNotNull { (it as? Item.Record)?.record }.toMutableList()
        val sourceIdx = flat.indexOfFirst { it.id == source.id }
        if (sourceIdx < 0) return false
        val moving = flat.removeAt(sourceIdx).copy(mealType = targetMeal)
        val targetIdx = when (target) {
            is Item.Header -> {
                val first = items.indexOfFirst { it is Item.Record && it.record.mealType == target.meal }
                if (first >= 0) flat.indexOfFirst { it.id == (items[first] as Item.Record).record.id }.coerceAtLeast(0)
                else flat.size
            }
            is Item.Record -> flat.indexOfFirst { it.id == target.record.id }.coerceAtLeast(0)
        }.coerceIn(0, flat.size)
        flat.add(targetIdx, moving)
        val base = System.currentTimeMillis()
        records = flat.mapIndexed { index, record -> record.copy(createdAt = base + index) }
        rebuild()
        notifyDataSetChanged()
        return true
    }

    fun finishDrag() {
        setDragging(false)
        onRecordsChanged(records)
    }

    override fun getItemId(position: Int): Long = when (val item = items[position]) {
        is Item.Header -> -1000L - item.meal.ordinal
        is Item.Record -> item.record.id
    }

    override fun getItemViewType(position: Int): Int =
        if (items[position] is Item.Header) TYPE_HEADER else TYPE_RECORD

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_HEADER) {
            HeaderHolder(inflater.inflate(R.layout.item_diet_header, parent, false))
        } else {
            RecordHolder(inflater.inflate(R.layout.item_diet_history, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is Item.Header -> (holder as HeaderHolder).bind(item.meal)
            is Item.Record -> (holder as RecordHolder).bind(item.record)
        }
    }

    override fun getItemCount(): Int = items.size

    private class HeaderHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val text: TextView = view.findViewById(R.id.tvMealHeader)
        fun bind(meal: MealType) {
            text.text = when (meal) {
                MealType.BREAKFAST -> "早餐"
                MealType.LUNCH -> "午餐"
                MealType.DINNER -> "晚餐"
                MealType.SNACK -> "加餐"
            }
        }
    }

    private inner class RecordHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val row: SwipeRevealLayout = view as SwipeRevealLayout
        private val icon: TextView = view.findViewById(R.id.tvFoodIcon)
        private val name: TextView = view.findViewById(R.id.tvFoodName)
        private val amount: TextView = view.findViewById(R.id.tvFoodAmount)
        private val calories: TextView = view.findViewById(R.id.tvFoodCalories)
        private val delete: TextView = view.findViewById(R.id.btnDelete)

        fun bind(record: DietRecordEntity) {
            icon.text = record.icon
            name.text = record.foodName
            amount.text = String.format(
                Locale.CHINA, "%.0f %s · %.1f g", record.amount, record.unit, record.grams
            )
            calories.text = String.format(Locale.CHINA, "%.1f kcal", record.calories)
            row.setSwipeEnabled(!dragging)
            row.close(false)
            row.setOnRevealChanged { changed, revealed ->
                if (revealed) {
                    if (openRow != null && openRow !== changed) openRow?.close()
                    openRow = changed
                } else if (openRow === changed) {
                    openRow = null
                }
            }
            delete.setOnClickListener {
                row.close()
                onDelete(record)
            }
            row.getChildAt(0)?.setOnClickListener {
                closeOpenRow()
            }
        }
    }

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_RECORD = 1
    }
}
