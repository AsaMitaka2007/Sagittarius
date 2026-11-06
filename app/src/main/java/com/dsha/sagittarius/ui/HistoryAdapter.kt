package com.dsha.sagittarius.ui

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.dsha.sagittarius.R
import com.dsha.sagittarius.data.CategoryCatalog
import com.dsha.sagittarius.data.CategoryEntity
import com.dsha.sagittarius.data.ExpenseEntity
import com.dsha.sagittarius.databinding.ItemCategoryBarBinding
import com.dsha.sagittarius.databinding.ItemCategorySummaryBinding
import com.dsha.sagittarius.databinding.ItemExpenseBinding
import com.dsha.sagittarius.databinding.ItemExpenseDayHeaderBinding
import com.dsha.sagittarius.databinding.ItemHistoryEmptyBinding
import com.dsha.sagittarius.databinding.ItemHistorySummaryBinding
import com.dsha.sagittarius.util.DateText
import com.dsha.sagittarius.util.Money

class HistoryAdapter(
    private val onExpenseClick: (ExpenseEntity) -> Unit,
    private val onExpenseLongClick: (ExpenseEntity) -> Unit,
    private val onAddClick: () -> Unit
) : ListAdapter<HistoryItem, RecyclerView.ViewHolder>(DIFF) {

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is HistoryItem.SummaryRow -> TYPE_SUMMARY
        is HistoryItem.CategoryRow -> TYPE_CATEGORY
        is HistoryItem.DayHeader -> TYPE_DAY
        is HistoryItem.ExpenseRow -> TYPE_EXPENSE
        HistoryItem.EmptyRow -> TYPE_EMPTY
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_SUMMARY -> SummaryHolder(ItemHistorySummaryBinding.inflate(inflater, parent, false))
            TYPE_CATEGORY -> CategoryHolder(ItemCategorySummaryBinding.inflate(inflater, parent, false))
            TYPE_DAY -> DayHolder(ItemExpenseDayHeaderBinding.inflate(inflater, parent, false))
            TYPE_EXPENSE -> ExpenseHolder(ItemExpenseBinding.inflate(inflater, parent, false))
            else -> EmptyHolder(ItemHistoryEmptyBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is HistoryItem.SummaryRow -> (holder as SummaryHolder).bind(item)
            is HistoryItem.CategoryRow -> (holder as CategoryHolder).bind(item)
            is HistoryItem.DayHeader -> (holder as DayHolder).bind(item)
            is HistoryItem.ExpenseRow -> (holder as ExpenseHolder).bind(item.expense, item.category)
            HistoryItem.EmptyRow -> (holder as EmptyHolder).bind()
        }
        animateIn(holder, position)
    }

    private fun animateIn(holder: RecyclerView.ViewHolder, position: Int) {
        val view = holder.itemView
        if (view.getTag(R.id.item_anim_tag) == true) return
        view.setTag(R.id.item_anim_tag, true)
        if (!view.isShown || MotionUtils.reduceMotion(view.context)) {
            view.alpha = 1f
            view.scaleX = 1f
            view.scaleY = 1f
            view.translationY = 0f
            return
        }
        val offset = 7f * view.resources.displayMetrics.density
        view.animate().cancel()
        view.alpha = 0f
        view.scaleX = 0.988f
        view.scaleY = 0.988f
        view.translationY = offset
        view.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .translationY(0f)
            .setStartDelay((position.coerceAtMost(7)) * 22L)
            .setDuration(200L)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    private class SummaryHolder(
        private val binding: ItemHistorySummaryBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: HistoryItem.SummaryRow) {
            binding.tvSummaryTitle.text = item.title
            binding.tvSummaryTotal.text = Money.formatCents(item.summary.totalCents)
            binding.tvSummaryCount.text = "${item.summary.count} 笔"
            binding.tvSummaryDaily.text = Money.formatCents(item.summary.dailyAverageCents)
            binding.tvSummaryLargest.text = Money.formatCents(item.summary.largestCents)
        }
    }

    private inner class CategoryHolder(
        private val binding: ItemCategorySummaryBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: HistoryItem.CategoryRow) {
            binding.categoryContainer.removeAllViews()
            binding.tvNoCategory.visibility =
                if (item.totals.isEmpty()) View.VISIBLE else View.GONE
            item.totals.forEach { total ->
                val row = ItemCategoryBarBinding.inflate(
                    LayoutInflater.from(binding.root.context),
                    binding.categoryContainer,
                    false
                )
                val color = CategoryCatalog.defaultColor(binding.root.context, total.category)
                row.categoryIcon.setImageResource(CategoryCatalog.iconRes(total.category.iconKey))
                row.categoryIcon.imageTintList = ColorStateList.valueOf(color)
                row.tvCategoryName.text = CategoryCatalog.displayName(binding.root.context, total.category)
                row.tvCategoryAmount.text = Money.formatCents(total.amountCents)
                row.tvCategoryShare.text = "${Money.sharePercent(total.share)}%"
                row.progressCategory.progress = Money.sharePercent(total.share)
                row.progressCategory.progressTintList = ColorStateList.valueOf(color)
                row.progressCategory.progressBackgroundTintList =
                    ColorStateList.valueOf(
                        ThemeTokens.of(binding.root.context).progressTrack
                    )
                binding.categoryContainer.addView(row.root)
            }
        }
    }

    private class DayHolder(
        private val binding: ItemExpenseDayHeaderBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: HistoryItem.DayHeader) {
            binding.tvDayDate.text = DateText.dayHeader(item.date)
            binding.tvDayTotal.text = "合计 ${Money.formatCents(item.totalCents)}"
        }
    }

    private inner class ExpenseHolder(
        private val binding: ItemExpenseBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(expense: ExpenseEntity, category: CategoryEntity) {
            binding.expenseIcon.bindCategory(category)
            val categoryName = CategoryCatalog.displayName(binding.root.context, category)
            binding.tvExpenseNote.text =
                expense.note?.takeIf { it.isNotBlank() } ?: categoryName
            binding.tvExpenseMeta.text = categoryName
            binding.tvExpenseAmount.text = "-${Money.formatCents(expense.amountCents)}"
            binding.expenseRoot.setOnClickListener { onExpenseClick(expense) }
            binding.expenseRoot.setOnLongClickListener {
                onExpenseLongClick(expense)
                true
            }
        }
    }

    private inner class EmptyHolder(
        private val binding: ItemHistoryEmptyBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind() {
            binding.btnEmptyAdd.setOnClickListener { onAddClick() }
        }
    }

    companion object {
        private const val TYPE_SUMMARY = 0
        private const val TYPE_CATEGORY = 1
        private const val TYPE_DAY = 2
        private const val TYPE_EXPENSE = 3
        private const val TYPE_EMPTY = 4

        private val DIFF = object : DiffUtil.ItemCallback<HistoryItem>() {
            override fun areItemsTheSame(oldItem: HistoryItem, newItem: HistoryItem): Boolean {
                return when {
                    oldItem is HistoryItem.SummaryRow && newItem is HistoryItem.SummaryRow -> true
                    oldItem is HistoryItem.CategoryRow && newItem is HistoryItem.CategoryRow -> true
                    oldItem is HistoryItem.DayHeader && newItem is HistoryItem.DayHeader ->
                        oldItem.date == newItem.date
                    oldItem is HistoryItem.ExpenseRow && newItem is HistoryItem.ExpenseRow ->
                        oldItem.expense.id == newItem.expense.id
                    oldItem is HistoryItem.EmptyRow && newItem is HistoryItem.EmptyRow -> true
                    else -> false
                }
            }

            override fun areContentsTheSame(oldItem: HistoryItem, newItem: HistoryItem): Boolean =
                oldItem == newItem
        }
    }
}
