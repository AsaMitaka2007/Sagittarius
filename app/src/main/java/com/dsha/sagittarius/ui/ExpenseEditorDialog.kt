package com.dsha.sagittarius.ui

import android.app.DatePickerDialog
import android.content.Context
import android.view.LayoutInflater
import android.view.WindowManager
import android.widget.ArrayAdapter
import com.dsha.sagittarius.R
import com.dsha.sagittarius.data.CategoryCatalog
import com.dsha.sagittarius.data.CategoryEntity
import com.dsha.sagittarius.data.ExpenseEntity
import com.dsha.sagittarius.databinding.DialogExpenseBinding
import com.dsha.sagittarius.util.DateText
import com.dsha.sagittarius.util.Money
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.time.LocalDate

data class ExpenseDraft(
    val amountCents: Long,
    val date: LocalDate,
    val categoryId: Long,
    val note: String?
)

object ExpenseEditorDialog {

    fun show(
        context: Context,
        categories: List<CategoryEntity>,
        existing: ExpenseEntity? = null,
        onSave: (ExpenseDraft) -> Unit
    ) {
        if (categories.isEmpty()) return
        val binding = DialogExpenseBinding.inflate(LayoutInflater.from(context))
        val ordered = categories.sortedWith(compareBy({ it.sortOrder }, { it.id }))
        var selectedDate = existing?.date ?: LocalDate.now()
        var selectedCategoryId = existing?.categoryId ?: ordered.first().id
        if (ordered.none { it.id == selectedCategoryId }) selectedCategoryId = ordered.first().id

        binding.tvDate.text = DateText.full(selectedDate)
        binding.etAmount.setText(existing?.amountCents?.let { Money.formatPlainCents(it) }.orEmpty())
        binding.etNote.setText(existing?.note.orEmpty())

        val labels = ordered.map {
            val emoji = if (it.systemKey != null) {
                CategoryCatalog.emoji(it)
            } else {
                "●"
            }
            "$emoji  ${CategoryCatalog.displayName(context, it)}"
        }
        val adapter = ArrayAdapter(context, R.layout.item_spinner, labels)
        adapter.setDropDownViewResource(R.layout.item_spinner)
        binding.spinnerCategory.adapter = adapter
        binding.spinnerCategory.setSelection(
            ordered.indexOfFirst { it.id == selectedCategoryId }.coerceAtLeast(0)
        )

        binding.spinnerCategory.setOnItemSelectedListener(
            object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: android.widget.AdapterView<*>?,
                    view: android.view.View?,
                    position: Int,
                    id: Long
                ) {
                    selectedCategoryId = ordered[position].id
                }

                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
            }
        )

        binding.tvDate.setOnClickListener {
            val picker = DatePickerDialog(
                context,
                { _, year, month, day ->
                    selectedDate = LocalDate.of(year, month + 1, day)
                    binding.tvDate.text = DateText.full(selectedDate)
                },
                selectedDate.year,
                selectedDate.monthValue - 1,
                selectedDate.dayOfMonth
            )
            picker.datePicker.maxDate = System.currentTimeMillis()
            picker.show()
        }

        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(
                if (existing == null) R.string.expense_dialog_title
                else R.string.expense_dialog_edit_title
            )
            .setView(binding.root)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.save, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val cents = Money.parseYuanToCents(binding.etAmount.text?.toString().orEmpty())
                if (cents == null || cents <= 0L) {
                    binding.etAmount.error = context.getString(R.string.invalid_amount)
                    return@setOnClickListener
                }
                onSave(
                    ExpenseDraft(
                        amountCents = cents,
                        date = selectedDate,
                        categoryId = selectedCategoryId,
                        note = binding.etNote.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }
                    )
                )
                dialog.dismiss()
            }
            if (existing == null) {
                binding.etAmount.requestFocus()
                dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
            }
        }
        dialog.show()
    }
}
