package com.dsha.sagittarius.ui

import android.content.res.ColorStateList
import android.os.Bundle
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.dsha.sagittarius.SagittariusApp
import com.dsha.sagittarius.R
import com.dsha.sagittarius.data.BudgetRepository
import com.dsha.sagittarius.data.CategoryCatalog
import com.dsha.sagittarius.data.CategoryEntity
import com.dsha.sagittarius.data.CategoryResult
import com.dsha.sagittarius.databinding.ActivityCategoriesBinding
import com.dsha.sagittarius.databinding.ItemCategoryManageBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

class CategoryActivity : ThemedActivity() {

    private lateinit var binding: ActivityCategoriesBinding
    private lateinit var repository: BudgetRepository
    private var categories: List<CategoryEntity> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCategoriesBinding.inflate(layoutInflater)
        setContentView(binding.root)
        repository = (application as SagittariusApp).repository
        binding.btnBack.setOnClickListener { finish() }
        binding.btnAdd.setOnClickListener { showAddDialog() }
        observeCategories()
    }

    private fun observeCategories() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                repository.observeCategories().collect {
                    categories = it
                    render(it)
                }
            }
        }
    }

    private fun render(list: List<CategoryEntity>) {
        binding.categoriesContainer.removeAllViews()
        binding.tvEmpty.isVisible = list.isEmpty()
        list.forEach { category ->
            val row = ItemCategoryManageBinding.inflate(
                layoutInflater,
                binding.categoriesContainer,
                false
            )
            row.categoryIcon.setImageResource(CategoryCatalog.iconRes(category.iconKey))
            row.categoryIcon.imageTintList = ColorStateList.valueOf(
                CategoryCatalog.defaultColor(this, category)
            )
            row.tvCategoryName.text = CategoryCatalog.displayName(this, category)
            row.tvCategoryType.text = getString(
                if (category.isCustom) R.string.category_custom_badge
                else R.string.category_system_badge
            )
            row.btnDelete.isVisible = category.isCustom
            row.categoryRow.setOnClickListener {
                if (category.isCustom) showEditDialog(category)
                else Toast.makeText(this, R.string.category_system_readonly, Toast.LENGTH_SHORT).show()
            }
            row.btnDelete.setOnClickListener { confirmDelete(category) }
            binding.categoriesContainer.addView(row.root)
        }
    }

    private fun showAddDialog() {
        CategoryEditDialog.show(this) { name, color, iconKey ->
            lifecycleScope.launch {
                when (repository.addCategory(name, color, iconKey)) {
                    is CategoryResult.Success ->
                        Toast.makeText(this@CategoryActivity, R.string.category_saved, Toast.LENGTH_SHORT).show()
                    CategoryResult.InvalidName ->
                        Toast.makeText(this@CategoryActivity, R.string.category_name_invalid, Toast.LENGTH_SHORT).show()
                    else -> Unit
                }
            }
        }
    }

    private fun showEditDialog(category: CategoryEntity) {
        CategoryEditDialog.show(this, category) { name, color, iconKey ->
            lifecycleScope.launch {
                when (repository.updateCategory(category.id, name, color, iconKey)) {
                    is CategoryResult.Success ->
                        Toast.makeText(this@CategoryActivity, R.string.category_saved, Toast.LENGTH_SHORT).show()
                    CategoryResult.InvalidName ->
                        Toast.makeText(this@CategoryActivity, R.string.category_name_invalid, Toast.LENGTH_SHORT).show()
                    else -> Unit
                }
            }
        }
    }

    private fun confirmDelete(category: CategoryEntity) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.category_delete_title)
            .setMessage(R.string.category_delete_confirm)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ ->
                lifecycleScope.launch {
                    when (repository.deleteCategory(category.id)) {
                        is CategoryResult.Success ->
                            Toast.makeText(this@CategoryActivity, R.string.category_deleted, Toast.LENGTH_SHORT).show()
                        CategoryResult.InUse ->
                            MaterialAlertDialogBuilder(this@CategoryActivity)
                                .setTitle(R.string.category_delete_title)
                                .setMessage(R.string.category_delete_in_use)
                                .setPositiveButton(R.string.close, null)
                                .show()
                        else -> Unit
                    }
                }
            }
            .show()
    }
}
