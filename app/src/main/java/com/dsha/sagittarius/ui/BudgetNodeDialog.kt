package com.dsha.sagittarius.ui

import android.content.Context
import android.view.LayoutInflater
import android.widget.ArrayAdapter
import com.dsha.sagittarius.R
import com.dsha.sagittarius.data.BudgetNodeEntity
import com.dsha.sagittarius.data.CategoryCatalog
import com.dsha.sagittarius.data.CategoryEntity
import com.dsha.sagittarius.databinding.DialogBudgetNodeBinding
import com.dsha.sagittarius.util.Money
import com.google.android.material.dialog.MaterialAlertDialogBuilder

object BudgetNodeDialog {

    fun show(
        context: Context,
        categories: List<CategoryEntity>,
        parentLabel: String,
        existing: BudgetNodeEntity? = null,
        titleRes: Int,
        onSave: (name: String, amountCents: Long, categoryId: Long?) -> Unit
    ) {
        val binding = DialogBudgetNodeBinding.inflate(LayoutInflater.from(context))
        binding.tvParent.text = parentLabel
        binding.etNodeName.setText(existing?.budgetName.orEmpty())
        binding.etNodeAmount.setText(
            existing?.amountCents?.let { Money.formatPlainCents(it) }.orEmpty()
        )

        val ordered = categories.sortedWith(compareBy({ it.sortOrder }, { it.id }))
        val labels = mutableListOf(context.getString(R.string.category_none))
        labels += ordered.map { CategoryCatalog.displayName(context, it) }
        val adapter = ArrayAdapter(context, R.layout.item_spinner, labels)
        adapter.setDropDownViewResource(R.layout.item_spinner)
        binding.spinnerCategory.adapter = adapter
        var selectedCategoryId = existing?.categoryId
        val selectedIndex = ordered.indexOfFirst { it.id == selectedCategoryId }
        binding.spinnerCategory.setSelection(if (selectedIndex >= 0) selectedIndex + 1 else 0)
        binding.spinnerCategory.setOnItemSelectedListener(
            object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: android.widget.AdapterView<*>?,
                    view: android.view.View?,
                    position: Int,
                    id: Long
                ) {
                    selectedCategoryId = if (position == 0) null else ordered[position - 1].id
                }

                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
            }
        )

        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(titleRes)
            .setView(binding.root)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.save, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = binding.etNodeName.text?.toString()?.trim().orEmpty()
                if (name.isEmpty()) {
                    binding.etNodeName.error = "请输入分配名称"
                    return@setOnClickListener
                }
                val cents = Money.parseYuanToCents(binding.etNodeAmount.text?.toString().orEmpty())
                if (cents == null || cents <= 0L) {
                    binding.etNodeAmount.error = context.getString(R.string.invalid_amount)
                    return@setOnClickListener
                }
                onSave(name, cents, selectedCategoryId)
                dialog.dismiss()
            }
        }
        dialog.show()
    }
}
