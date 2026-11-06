package com.dsha.sagittarius.ui

import android.content.Context
import android.view.LayoutInflater
import com.dsha.sagittarius.R
import com.dsha.sagittarius.databinding.DialogBudgetBinding
import com.dsha.sagittarius.util.Money
import com.google.android.material.dialog.MaterialAlertDialogBuilder

object BudgetDialog {

    fun show(
        context: Context,
        rangeLabel: String,
        currentCents: Long?,
        onSave: (Long) -> Unit
    ) {
        val binding = DialogBudgetBinding.inflate(LayoutInflater.from(context))
        binding.tvBudgetRange.text =
            context.getString(R.string.budget_dialog_message, rangeLabel)
        binding.etBudgetAmount.setText(
            currentCents?.let(Money::formatPlainCents).orEmpty()
        )

        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(R.string.budget_dialog_title)
            .setView(binding.root)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.save, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val cents = Money.parseYuanToCents(
                    binding.etBudgetAmount.text?.toString().orEmpty()
                )
                if (cents == null || cents <= 0L) {
                    binding.etBudgetAmount.error = context.getString(R.string.budget_invalid)
                    return@setOnClickListener
                }
                onSave(cents)
                dialog.dismiss()
            }
        }
        dialog.show()
    }
}
