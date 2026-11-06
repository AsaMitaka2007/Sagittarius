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

class DietRecordAdapter(
    private val onDelete: (DietRecordEntity) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private sealed interface Item {
        data class Header(val meal: MealType) : Item
        data class Record(val record: DietRecordEntity) : Item
    }

    private var items: List<Item> = emptyList()

    fun submit(records: List<DietRecordEntity>) {
        val grouped = records.groupBy { it.mealType }
        items = MealType.entries.flatMap { meal ->
            val list = grouped[meal].orEmpty()
            if (list.isEmpty()) emptyList() else listOf(Item.Header(meal)) + list.map(Item::Record)
        }
        notifyDataSetChanged()
    }

    fun recordAt(position: Int): DietRecordEntity? =
        (items.getOrNull(position) as? Item.Record)?.record

    override fun getItemViewType(position: Int): Int =
        if (items[position] is Item.Header) TYPE_HEADER else TYPE_RECORD

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_HEADER) {
            HeaderHolder(inflater.inflate(R.layout.item_diet_header, parent, false))
        } else {
            RecordHolder(inflater.inflate(R.layout.item_diet_record, parent, false))
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
        private val icon: TextView = view.findViewById(R.id.tvFoodIcon)
        private val name: TextView = view.findViewById(R.id.tvFoodName)
        private val amount: TextView = view.findViewById(R.id.tvFoodAmount)
        private val calories: TextView = view.findViewById(R.id.tvFoodCalories)
        fun bind(record: DietRecordEntity) {
            icon.text = record.icon
            name.text = record.foodName
            amount.text = String.format(Locale.CHINA, "%.0f %s · %.0f g", record.amount, record.unit, record.grams)
            calories.text = String.format(Locale.CHINA, "%.0f kcal", record.calories)
            itemView.setOnLongClickListener {
                onDelete(record)
                true
            }
        }
    }

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_RECORD = 1
    }
}
