package com.dsha.sagittarius.ui

import android.content.Context
import android.view.View
import android.widget.Toast
import androidx.appcompat.widget.PopupMenu
import com.dsha.sagittarius.R
import com.dsha.sagittarius.data.BudgetRepository
import com.dsha.sagittarius.data.CategoryEntity
import com.dsha.sagittarius.data.CategoryResult
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * 自定义类别 Chip 的长按菜单：重命名 / 删除。
 * 不新增独立管理页面；所有入口都挂在现有 Chip 上。
 */
object CategoryChipActions {

    private const val MENU_RENAME = 1
    private const val MENU_DELETE = 2

    fun showMenu(
        anchor: View,
        context: Context,
        scope: CoroutineScope,
        repository: BudgetRepository,
        category: CategoryEntity,
        onDeleted: () -> Unit = {}
    ) {
        if (!category.isCustom) return
        val popup = PopupMenu(context, anchor)
        popup.menu.add(0, MENU_RENAME, 0, R.string.category_rename_action)
        popup.menu.add(0, MENU_DELETE, 0, R.string.category_delete_action)
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                MENU_RENAME -> {
                    showRename(context, scope, repository, category)
                    true
                }
                MENU_DELETE -> {
                    confirmDelete(context, scope, repository, category, onDeleted)
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    private fun showRename(
        context: Context,
        scope: CoroutineScope,
        repository: BudgetRepository,
        category: CategoryEntity
    ) {
        CategoryEditDialog.show(context, category) { name, color, iconKey ->
            scope.launch {
                when (repository.updateCategory(category.id, name, color, iconKey)) {
                    is CategoryResult.Success ->
                        Toast.makeText(context, R.string.category_saved, Toast.LENGTH_SHORT).show()
                    CategoryResult.InvalidName ->
                        Toast.makeText(context, R.string.category_name_invalid, Toast.LENGTH_SHORT).show()
                    else -> Unit
                }
            }
        }
    }

    private fun confirmDelete(
        context: Context,
        scope: CoroutineScope,
        repository: BudgetRepository,
        category: CategoryEntity,
        onDeleted: () -> Unit
    ) {
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.category_delete_title)
            .setMessage(R.string.category_delete_confirm)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.category_delete_action) { _, _ ->
                scope.launch {
                    when (repository.deleteCategory(category.id)) {
                        is CategoryResult.Success -> {
                            onDeleted()
                            Toast.makeText(context, R.string.category_deleted, Toast.LENGTH_SHORT).show()
                        }
                        CategoryResult.InUse ->
                            MaterialAlertDialogBuilder(context)
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
