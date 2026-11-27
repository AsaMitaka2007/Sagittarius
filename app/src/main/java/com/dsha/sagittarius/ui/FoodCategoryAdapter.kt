package com.dsha.sagittarius.ui

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.dsha.sagittarius.R
import com.dsha.sagittarius.data.FoodCategoryEntity

/** 食材分类胶囊：横向排列，可拖拽排序。 */
class FoodCategoryAdapter(
    private val onClick: (FoodCategoryEntity) -> Unit
) : ListAdapter<FoodCategoryEntity, FoodCategoryAdapter.Holder>(Diff) {

    private var selectedKey: String = ""

    fun submit(categories: List<FoodCategoryEntity>, selected: String) {
        selectedKey = selected
        submitList(categories)
    }

    fun setSelected(key: String) {
        if (selectedKey == key) return
        selectedKey = key
        notifyDataSetChanged()
    }

    fun keys(): List<String> = currentList.map { it.categoryKey }

    fun move(from: Int, to: Int): Boolean {
        if (from !in currentList.indices || to !in currentList.indices) return false
        val list = currentList.toMutableList()
        list.add(to, list.removeAt(from))
        submitList(list)
        return true
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_food_category, parent, false)
        return Holder(view as TextView)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(getItem(position))

    inner class Holder(private val view: TextView) : RecyclerView.ViewHolder(view) {
        fun bind(category: FoodCategoryEntity) {
            val context = view.context
            val tokens = ThemeTokens.of(context)
            val density = context.resources.displayMetrics.density
            val radius = 20f * density
            val selected = category.categoryKey == selectedKey
            view.text = category.name
            view.typeface = Typeface.create("sans-serif", if (selected) Typeface.BOLD else Typeface.NORMAL)
            view.setTextColor(if (selected) Color.WHITE else tokens.textSecondary)
            view.background = if (selected) {
                GradientDrawable(
                    GradientDrawable.Orientation.LEFT_RIGHT,
                    intArrayOf(tokens.gradientStart, tokens.gradientMid)
                ).apply { cornerRadius = radius }
            } else {
                GradientDrawable().apply {
                    cornerRadius = radius
                    setColor(tokens.chipUnselectedFill)
                    setStroke((1f * density).toInt().coerceAtLeast(1), tokens.chipUnselectedStroke)
                }
            }
            view.setOnClickListener { onClick(category) }
            // 长按交给 ItemTouchHelper 启动拖动；原地松手由 clearView 弹菜单。
        }
    }

    private object Diff : DiffUtil.ItemCallback<FoodCategoryEntity>() {
        override fun areItemsTheSame(oldItem: FoodCategoryEntity, newItem: FoodCategoryEntity) =
            oldItem.categoryKey == newItem.categoryKey

        override fun areContentsTheSame(oldItem: FoodCategoryEntity, newItem: FoodCategoryEntity) =
            oldItem == newItem
    }
}
