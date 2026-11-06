package com.dsha.sagittarius.ui

import android.content.Context
import android.graphics.Color
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.dsha.sagittarius.R
import com.dsha.sagittarius.data.CategoryCatalog
import com.dsha.sagittarius.data.CategoryEntity
import com.dsha.sagittarius.databinding.DialogCategoryEditBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

object CategoryEditDialog {

    fun show(
        context: Context,
        existing: CategoryEntity? = null,
        onSave: (name: String, color: Int, iconKey: String) -> Unit
    ) {
        val binding = DialogCategoryEditBinding.inflate(LayoutInflater.from(context))
        val tokens = ThemeTokens.of(context)
        val colors = CategoryCatalog.customColorChoices(context)
        var selectedColor = existing?.color?.takeIf { it != 0 }
            ?: colors.firstOrNull()
            ?: ContextCompat.getColor(context, R.color.cat_other)
        var selectedIconKey = existing?.iconKey?.takeIf { it.isNotBlank() } ?: "other"
        binding.etCategoryName.setText(existing?.name.orEmpty())

        fun renderIcons() {
            binding.iconContainer.removeAllViews()
            CategoryCatalog.customIconChoices().forEach { choice ->
                val selected = choice.key == selectedIconKey
                val icon = ImageView(context).apply {
                    setImageResource(choice.iconRes)
                    contentDescription = choice.key
                    scaleType = ImageView.ScaleType.CENTER_INSIDE
                    setPadding(dp(context, 9f), dp(context, 9f), dp(context, 9f), dp(context, 9f))
                    imageTintList = ColorStateList.valueOf(
                        if (selected) tokens.accent else tokens.textSecondary
                    )
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(if (selected) tokens.iconCircle else Color.TRANSPARENT)
                        setStroke(
                            ((if (selected) 2f else 1f) * context.resources.displayMetrics.density).toInt(),
                            if (selected) tokens.accent else tokens.divider
                        )
                    }
                    setOnClickListener {
                        selectedIconKey = choice.key
                        renderIcons()
                    }
                }
                val params = LinearLayout.LayoutParams(dp(context, 40f), dp(context, 40f)).apply {
                    marginEnd = dp(context, 8f)
                }
                binding.iconContainer.addView(icon, params)
            }
        }

        fun renderColors() {
            binding.colorContainer.removeAllViews()
            colors.forEach { color ->
                val dot = View(context).apply {
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(color)
                        setStroke(
                            ((if (color == selectedColor) 2.5f else 1f) *
                                resources.displayMetrics.density).toInt(),
                            if (color == selectedColor) tokens.textPrimary
                            else ColorUtils.setAlphaComponent(tokens.textPrimary, 70)
                        )
                    }
                    setOnClickListener {
                        selectedColor = color
                        renderColors()
                    }
                }
                val params = LinearLayout.LayoutParams(dp(context, 34f), dp(context, 34f)).apply {
                    marginEnd = dp(context, 8f)
                }
                binding.colorContainer.addView(dot, params)
            }
        }
        renderIcons()
        renderColors()

        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(
                if (existing == null) R.string.category_add_title
                else R.string.category_edit_title
            )
            .setView(binding.root)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.save, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = binding.etCategoryName.text?.toString()?.trim().orEmpty()
                if (name.isEmpty()) {
                    binding.etCategoryName.error = context.getString(R.string.category_name_invalid)
                    return@setOnClickListener
                }
                onSave(name, selectedColor, selectedIconKey)
                dialog.dismiss()
            }
        }
        binding.etCategoryName.requestFocus()
        dialog.window?.setGravity(Gravity.CENTER)
        dialog.show()
    }

    private fun dp(context: Context, value: Float): Int =
        (value * context.resources.displayMetrics.density).toInt()
}
