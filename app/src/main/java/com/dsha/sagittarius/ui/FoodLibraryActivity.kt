package com.dsha.sagittarius.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.dsha.sagittarius.R
import com.dsha.sagittarius.SagittariusApp
import com.dsha.sagittarius.data.DietRepository
import com.dsha.sagittarius.data.FoodCategoryEntity
import com.dsha.sagittarius.data.FoodEntity
import com.dsha.sagittarius.data.MealType
import com.dsha.sagittarius.data.NutritionMath
import com.dsha.sagittarius.databinding.ActivityFoodLibraryBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.abs

/** 新版食材库：分类/食材胶囊、长按拖动、点击详情、长按松手删除。 */
class FoodLibraryActivity : ThemedActivity() {

    private lateinit var binding: ActivityFoodLibraryBinding
    private lateinit var repository: DietRepository
    private lateinit var categoryAdapter: FoodCategoryAdapter
    private lateinit var foodAdapter: FoodCapsuleAdapter
    private var categories: List<FoodCategoryEntity> = emptyList()
    private var foods: List<FoodEntity> = emptyList()
    private var selectedKey = "FRUIT_VEG"
    private var date: LocalDate = LocalDate.now()
    private var meal: MealType = MealType.BREAKFAST
    private var dragCategoryOrder: MutableList<String> = mutableListOf()
    private var dragFoodOrder: MutableList<Long> = mutableListOf()
    private lateinit var categoryGesture: GestureDetector

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFoodLibraryBinding.inflate(layoutInflater)
        setContentView(binding.root)
        repository = (application as SagittariusApp).dietRepository
        date = runCatching { LocalDate.parse(intent.getStringExtra(EXTRA_DATE)) }.getOrDefault(LocalDate.now())
        meal = MealType.fromStorage(intent.getStringExtra(EXTRA_MEAL)) ?: MealType.BREAKFAST
        val accent = ButtonAccentManager.current
        binding.btnAddFood.setTextColor(accent.textColor)
        binding.btnManageCategories.setTextColor(accent.textColor)

        setupCategories()
        categoryGesture = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                val start = e1 ?: return false
                val dx = e2.x - start.x
                val dy = e2.y - start.y
                if (!foodAdapter.isDragging() && abs(dx) > abs(dy) * 1.4f &&
                    abs(dx) > 80f * resources.displayMetrics.density
                ) {
                    stepCategory(if (dx < 0) 1 else -1)
                    return true
                }
                return false
            }
        })
        binding.root.isClickable = true
        binding.root.setOnClickListener { foodAdapter.clearPendingDelete() }
        binding.foodGrid.addOnItemTouchListener(object : RecyclerView.SimpleOnItemTouchListener() {
            override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
                categoryGesture.onTouchEvent(e)
                if (e.actionMasked == MotionEvent.ACTION_DOWN &&
                    foodAdapter.hasPendingDelete() &&
                    rv.findChildViewUnder(e.x, e.y) == null
                ) {
                    foodAdapter.clearPendingDelete()
                }
                return false
            }

        })
        setupFoods()
        binding.foodGrid.isClickable = true
        binding.foodGrid.setOnClickListener { foodAdapter.clearPendingDelete() }
        binding.btnAddFood.setOnClickListener { openEditor(null) }
        binding.btnManageCategories.setOnClickListener { showAddCategory() }
        observe()
    }

    private fun setupCategories() {
        categoryAdapter = FoodCategoryAdapter(
            onClick = { category -> selectCategory(category.categoryKey) }
        )
        binding.categoryList.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        binding.categoryList.adapter = categoryAdapter
        var moved = false
        var dragged: FoodCategoryEntity? = null
        val callback = object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT,
            0
        ) {
            override fun isLongPressDragEnabled(): Boolean = true

            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                moved = true
                val from = viewHolder.bindingAdapterPosition
                val to = target.bindingAdapterPosition
                val ok = categoryAdapter.move(from, to)
                if (ok && from in dragCategoryOrder.indices && to in dragCategoryOrder.indices) {
                    dragCategoryOrder.add(to, dragCategoryOrder.removeAt(from))
                }
                return ok
            }

            override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
                super.onSelectedChanged(viewHolder, actionState)
                if (actionState == ItemTouchHelper.ACTION_STATE_DRAG && viewHolder != null) {
                    moved = false
                    dragged = categoryAdapter.currentList.getOrNull(viewHolder.bindingAdapterPosition)
                    dragCategoryOrder = categoryAdapter.currentList.map { it.categoryKey }.toMutableList()
                    Haptics.longPress(viewHolder.itemView)
                    viewHolder.itemView.animate().scaleX(0.94f).scaleY(0.94f).setDuration(100L).start()
                }
            }

            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(recyclerView, viewHolder)
                viewHolder.itemView.animate().scaleX(1f).scaleY(1f).setDuration(120L).start()
                if (moved) {
                    lifecycleScope.launch { repository.reorderCategories(dragCategoryOrder) }
                } else {
                    dragged?.let { showCategoryMenu(it) }
                }
                dragged = null
                moved = false
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) = Unit
        }
        ItemTouchHelper(callback).attachToRecyclerView(binding.categoryList)
    }

    private fun setupFoods() {
        foodAdapter = FoodCapsuleAdapter(
            onClick = { food, _ -> openEditor(food) },
            onQuickAdd = { food -> quickAdd(food) },
            onDelete = { food -> confirmDelete(food) },
            onMove = { food -> showMoveDialog(food) }
        )
        binding.foodGrid.layoutManager = GridLayoutManager(this, 2)
        binding.foodGrid.adapter = foodAdapter
        var moved = false
        var dragged: FoodEntity? = null
        val callback = object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN or ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT,
            0
        ) {
            override fun isLongPressDragEnabled(): Boolean = true

            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                moved = true
                val from = viewHolder.bindingAdapterPosition
                val to = target.bindingAdapterPosition
                val ok = foodAdapter.move(from, to)
                if (ok && from in dragFoodOrder.indices && to in dragFoodOrder.indices) {
                    dragFoodOrder.add(to, dragFoodOrder.removeAt(from))
                }
                return ok
            }

            override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
                super.onSelectedChanged(viewHolder, actionState)
                if (actionState == ItemTouchHelper.ACTION_STATE_DRAG && viewHolder != null) {
                    moved = false
                    dragged = foodAdapter.currentList.getOrNull(viewHolder.bindingAdapterPosition)
                    dragFoodOrder = foodAdapter.currentList.map { it.id }.toMutableList()
                    dragged?.let { foodAdapter.startDrag(it) }
                    (viewHolder as? FoodCapsuleAdapter.Holder)?.enhanceDrag()
                    Haptics.longPress(viewHolder.itemView)
                }
            }

            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(recyclerView, viewHolder)
                foodAdapter.endDrag()
                val food = dragged
                dragged = null
                if (food == null) return
                if (!moved) {
                    foodAdapter.showDelete(foodAdapter.currentList.indexOfFirst { it.id == food.id })
                } else {
                    lifecycleScope.launch { repository.reorderFoods(dragFoodOrder) }
                }
                moved = false
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) = Unit
        }
        ItemTouchHelper(callback).attachToRecyclerView(binding.foodGrid)
    }

    private fun observe() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    repository.categories.collect { list ->
                        categories = list
                        if (categories.none { it.categoryKey == selectedKey }) {
                            selectedKey = categories.firstOrNull()?.categoryKey ?: "FRUIT_VEG"
                        }
                        categoryAdapter.submit(categories, selectedKey)
                        renderFoods()
                    }
                }
                launch {
                    repository.foods.collect { list ->
                        foods = list
                        renderFoods()
                    }
                }
            }
        }
    }

    private fun renderFoods() {
        val list = foods.filter { it.category == selectedKey }
            .sortedBy { it.sortOrder }
        foodAdapter.submitList(list)
        binding.tvFoodEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun stepCategory(delta: Int) {
        if (categories.isEmpty()) return
        val keys = categories.map { it.categoryKey }
        val index = keys.indexOf(selectedKey)
        if (index < 0) return
        val next = (index + delta).coerceIn(0, keys.lastIndex)
        if (next != index) selectCategory(keys[next])
    }

    private fun selectCategory(key: String) {
        foodAdapter.clearPendingDelete()
        if (key == selectedKey) return
        selectedKey = key
        categoryAdapter.setSelected(key)
        renderFoods()
    }

    override fun finish() {
        super.finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(R.anim.fade_in_pure, R.anim.fade_out_pure)
    }

    private fun openEditor(food: FoodEntity?) {
        foodAdapter.clearPendingDelete()
        val intent = Intent(this, FoodEditActivity::class.java)
            .putExtra(FoodEditActivity.EXTRA_CATEGORY, food?.category ?: selectedKey)
        if (food != null) intent.putExtra(FoodEditActivity.EXTRA_FOOD_ID, food.id)
        startActivity(intent)
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    private fun quickAdd(food: FoodEntity) {
        foodAdapter.clearPendingDelete()
        lifecycleScope.launch {
            val units = repository.getUnits(food.id)
            FoodAmountSheet.show(this@FoodLibraryActivity, food, units) { amount, unit ->
                setResult(
                    Activity.RESULT_OK,
                    Intent().apply {
                        putExtra(RESULT_FOOD_ID, food.id)
                        putExtra(RESULT_AMOUNT, amount)
                        putExtra(RESULT_UNIT, unit.unitName)
                        putExtra(RESULT_UNIT_WEIGHT, unit.weightInGrams)
                    }
                )
                finish()
            }
        }
    }

    private fun showMoveDialog(food: FoodEntity) {
        val targets = categories.filter { it.categoryKey != food.category }
        if (targets.isEmpty()) {
            Toast.makeText(this, "没有其他分类可移动", Toast.LENGTH_SHORT).show()
            return
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("移动「${food.name}」至")
            .setItems(targets.map { it.name }.toTypedArray()) { _, which ->
                val target = targets.getOrNull(which) ?: return@setItems
                lifecycleScope.launch {
                    repository.moveFood(food.id, target.categoryKey)
                    selectCategory(target.categoryKey)
                    Toast.makeText(
                        this@FoodLibraryActivity,
                        "已移动到 ${target.name}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun confirmDelete(food: FoodEntity) {
        MaterialAlertDialogBuilder(this)
            .setTitle("删除食材")
            .setMessage("确认删除「${food.name}」？历史记录保留，但会标记为食材缺失。")
            .setNegativeButton("取消", null)
            .setPositiveButton("删除") { _, _ ->
                lifecycleScope.launch {
                    repository.deleteFood(food)
                    Toast.makeText(this@FoodLibraryActivity, "已删除", Toast.LENGTH_SHORT).show()
                }
            }
            .show()
    }

    private fun showCategoryMenu(category: FoodCategoryEntity) {
        val items = mutableListOf("重命名")
        if (!category.isPreset) items += "删除"
        MaterialAlertDialogBuilder(this)
            .setTitle(category.name)
            .setItems(items.toTypedArray()) { _, which ->
                if (which == 0) renameCategory(category) else deleteCategory(category)
            }
            .show()
    }

    private fun showAddCategory() {
        val input = EditText(this).apply {
            hint = "分类名称"
            inputType = InputType.TYPE_CLASS_TEXT
            setTextColor(ThemeTokens.of(this@FoodLibraryActivity).textPrimary)
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("新增分类")
            .setView(input)
            .setNegativeButton("取消", null)
            .setPositiveButton("保存") { _, _ ->
                val name = input.text?.toString()?.trim().orEmpty()
                if (name.isEmpty()) return@setPositiveButton
                lifecycleScope.launch {
                    repository.addCategory(name, "CUSTOM_${System.currentTimeMillis()}")
                }
            }
            .show()
    }

    private fun renameCategory(category: FoodCategoryEntity) {
        val input = EditText(this).apply {
            setText(category.name)
            setTextColor(ThemeTokens.of(this@FoodLibraryActivity).textPrimary)
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("重命名分类")
            .setView(input)
            .setNegativeButton("取消", null)
            .setPositiveButton("保存") { _, _ ->
                val name = input.text?.toString()?.trim().orEmpty()
                if (name.isNotEmpty()) lifecycleScope.launch { repository.renameCategory(category, name) }
            }
            .show()
    }

    private fun deleteCategory(category: FoodCategoryEntity) {
        lifecycleScope.launch {
            val ok = repository.deleteCategory(category)
            Toast.makeText(
                this@FoodLibraryActivity,
                if (ok) "分类已删除" else "请先移除该分类下的食材",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    companion object {
        const val EXTRA_DATE = "food_library_date"
        const val EXTRA_MEAL = "food_library_meal"
        const val RESULT_FOOD_ID = "food_library_food_id"
        const val RESULT_AMOUNT = "food_library_amount"
        const val RESULT_UNIT = "food_library_unit"
        const val RESULT_UNIT_WEIGHT = "food_library_unit_weight"
    }
}
