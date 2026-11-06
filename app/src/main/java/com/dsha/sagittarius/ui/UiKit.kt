package com.dsha.sagittarius.ui

import android.content.Context
import android.content.res.ColorStateList
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import com.dsha.sagittarius.R
import com.dsha.sagittarius.data.CategoryCatalog
import com.dsha.sagittarius.data.CategoryEntity
import com.dsha.sagittarius.data.ExpenseEntity
import com.dsha.sagittarius.util.DateText
import com.dsha.sagittarius.util.Money

fun Context.dp(value: Float): Int = (value * resources.displayMetrics.density).toInt()

fun View.showIf(visible: Boolean) {
    visibility = if (visible) View.VISIBLE else View.GONE
}

fun ImageView.bindCategory(category: CategoryEntity) {
    setImageResource(CategoryCatalog.iconRes(category.iconKey))
    imageTintList = ColorStateList.valueOf(
        CategoryCatalog.defaultColor(context, category)
    )
}

fun bindRecentRow(
    row: View,
    expense: ExpenseEntity,
    category: CategoryEntity?
) {
    val resolved = category ?: CategoryCatalog.fallbackEntity()
    val icon = row.findViewById<ImageView>(R.id.recentIcon)
    val note = row.findViewById<TextView>(R.id.recentNote)
    val meta = row.findViewById<TextView>(R.id.recentMeta)
    val amount = row.findViewById<TextView>(R.id.recentAmount)
    icon.bindCategory(resolved)
    val categoryName = CategoryCatalog.displayName(row.context, resolved)
    note.text = expense.note?.takeIf { it.isNotBlank() } ?: categoryName
    meta.text = "$categoryName · ${DateText.relative(expense.date)}"
    amount.text = "-${Money.formatCents(expense.amountCents)}"
}
