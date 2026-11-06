package com.dsha.sagittarius.ui

import android.graphics.Rect
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.appcompat.widget.PopupMenu
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.dsha.sagittarius.SagittariusApp
import com.dsha.sagittarius.R
import com.dsha.sagittarius.data.BudgetRepository
import com.dsha.sagittarius.data.CategoryCatalog
import com.dsha.sagittarius.data.CategoryEntity
import com.dsha.sagittarius.data.CategoryResult
import com.dsha.sagittarius.data.ExpenseSplit
import com.dsha.sagittarius.databinding.FragmentAddExpenseBinding
import com.dsha.sagittarius.util.Money
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import java.time.LocalDate

class AddExpenseFragment : Fragment() {

    private var _binding: FragmentAddExpenseBinding? = null
    private val binding get() = _binding!!
    private lateinit var repository: BudgetRepository

    private var selectedDate: LocalDate = LocalDate.now()
    private var entrancePlayed = false
    private val subItems = mutableListOf<SubItem>()

    private data class SubItem(
        val categoryId: Long,
        val name: String,
        val color: Int,
        val amountCents: Long
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAddExpenseBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        repository = (requireActivity().application as SagittariusApp).repository

        binding.bubbleAmount.doOnAmountChanged {
            binding.bubbleAmount.setError(false)
            updateSaveState()
        }
        binding.bubbleAmount.onUndo = {
            if (subItems.isNotEmpty()) {
                subItems.removeAt(subItems.lastIndex)
                refreshBubble()
                updateSaveState()
            }
        }
        binding.bubbleAmount.onRemoveLine = { index ->
            if (index in subItems.indices) {
                subItems.removeAt(index)
                refreshBubble()
                updateSaveState()
            }
        }
        binding.bubbleAmount.onClear = {
            subItems.clear()
            binding.bubbleAmount.clearAmount()
            refreshBubble()
            updateSaveState()
        }
        binding.bubbleAmount.editText.imeOptions = EditorInfo.IME_ACTION_NEXT
        binding.bubbleAmount.editText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_NEXT || actionId == EditorInfo.IME_ACTION_DONE) {
                binding.etNote.requestFocus()
                binding.etNote.post {
                    binding.etNote.requestRectangleOnScreen(Rect(0, 0, binding.etNote.width, binding.etNote.height), true)
                    val imm = requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.showSoftInput(binding.etNote, InputMethodManager.SHOW_IMPLICIT)
                }
                true
            } else {
                false
            }
        }
        binding.etNote.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                saveExpense()
                true
            } else {
                false
            }
        }
        binding.etNote.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                binding.etNote.post {
                    binding.etNote.requestRectangleOnScreen(Rect(0, 0, binding.etNote.width, binding.etNote.height), true)
                }
            }
        }
        binding.btnSave.setOnClickListener { saveExpense() }
        binding.btnAddCategory.setOnClickListener { showAddCategoryDialog() }
        binding.modeSwitch.setMode(AppMode.ACCOUNT)
        binding.modeSwitch.onModeSelected = { mode ->
            AppModeManager.setMode(requireContext(), mode)
        }
        binding.categoryWheel.onCategoryClick = { category ->
            if (addCategoryAmount(category)) {
                animateFlyIn(category)
            }
        }
        binding.categoryWheel.onCategoryLongPress = { category ->
            if (subItems.any { it.categoryId == category.id }) {
                removeCategory(category)
            } else {
                showCategoryMenu(category)
            }
        }
        binding.categoryWheel.onSwapOrder = { first, second ->
            viewLifecycleOwner.lifecycleScope.launch {
                repository.swapCategoryOrder(first.id, second.id)
            }
        }
        updateSaveState()
        DateConsoleBinding.observe(this, binding.dateConsole, DateConsolePage.ADD) { state ->
            // 记账日期完全跟随顶栏聚焦日期；自定义范围取起始日。
            selectedDate = if (state.isCustom) {
                DateConsoleManager.rangeOf(state).start
            } else {
                state.anchor
            }
        }
        observeCategories()
    }

    override fun onResume() {
        super.onResume()
        maybeAnimateEntrance()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden) maybeAnimateEntrance()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private fun maybeAnimateEntrance() {
        if (entrancePlayed || isHidden || !isResumed) return
        entrancePlayed = true
        binding.root.post { _binding?.let { MotionUtils.animateCardsIn(it.root) } }
    }

    private fun observeCategories() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                repository.observeCategories().collect { list ->
                    val validIds = list.map { it.id }.toSet()
                    subItems.removeAll { it.categoryId !in validIds }
                    binding.categoryWheel.setCategories(list)
                    refreshBubble()
                    updateSaveState()
                }
            }
        }
    }

    private fun addCategoryAmount(category: CategoryEntity): Boolean {
        val text = binding.bubbleAmount.amountText()
        val cents = Money.parseYuanToCents(text)
        val existingIndex = subItems.indexOfFirst { it.categoryId == category.id }
        // 单选覆盖：没有重新输入金额时，再点另一个类别应替换当前选中项，而不是新增/累加。
        if (text.isEmpty() && existingIndex < 0 && subItems.isNotEmpty()) {
            val oldIndex = subItems.indices.last()
            val old = subItems[oldIndex]
            subItems[oldIndex] = SubItem(
                categoryId = category.id,
                name = CategoryCatalog.displayName(requireContext(), category),
                color = CategoryCatalog.defaultColor(requireContext(), category),
                amountCents = old.amountCents
            )
            Haptics.tap(binding.categoryWheel)
            binding.bubbleAmount.setError(false)
            refreshBubble()
            updateSaveState()
            return true
        }
        if (cents == null || cents <= 0L) {
            binding.bubbleAmount.flashError()
            Haptics.reject(binding.bubbleAmount)
            return false
        }
        if (existingIndex >= 0) {
            // 同一个类别再次点击：覆盖为当前输入金额。
            subItems[existingIndex] = subItems[existingIndex].copy(amountCents = cents)
        } else {
            subItems += SubItem(
                categoryId = category.id,
                name = CategoryCatalog.displayName(requireContext(), category),
                color = CategoryCatalog.defaultColor(requireContext(), category),
                amountCents = cents
            )
        }
        binding.bubbleAmount.clearAmount()
        binding.bubbleAmount.setError(false)
        refreshBubble()
        updateSaveState()
        Haptics.tap(binding.categoryWheel)
        return true
    }

    private fun removeCategory(category: CategoryEntity) {
        val index = subItems.indexOfFirst { it.categoryId == category.id }
        if (index < 0) return
        subItems.removeAt(index)
        Haptics.longPress(binding.categoryWheel)
        refreshBubble()
        updateSaveState()
    }

    private fun refreshBubble() {
        binding.bubbleAmount.setLines(
            subItems.map {
                ExpenseBubbleLine(
                    categoryId = it.categoryId,
                    name = it.name,
                    color = it.color,
                    amountCents = it.amountCents
                )
            }
        )
        binding.categoryWheel.setRecordedAmounts(
            subItems.associate { it.categoryId to it.amountCents }
        )
        binding.categoryWheel.setSelectedCategory(subItems.lastOrNull()?.categoryId)
    }

    private fun animateFlyIn(category: CategoryEntity) {
        val container = _binding?.bubbleWheelContainer ?: return
        val bubble = _binding?.bubbleAmount ?: return
        if (container.width == 0 || bubble.width == 0) return
        val startX = binding.categoryWheel.lastTapX
        val startY = binding.categoryWheel.lastTapY
        val targetX = bubble.x + bubble.width / 2f
        val targetY = bubble.y + bubble.height / 2f
        val size = (30 * resources.displayMetrics.density).toInt()
        val fly = android.widget.TextView(requireContext()).apply {
            text = CategoryCatalog.displayName(requireContext(), category).take(1)
            gravity = android.view.Gravity.CENTER
            setTextColor(android.graphics.Color.WHITE)
            textSize = 12f
            elevation = 6f * resources.displayMetrics.density
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(CategoryCatalog.defaultColor(requireContext(), category))
            }
            isClickable = false
        }
        val params = android.widget.FrameLayout.LayoutParams(size, size).apply {
            leftMargin = (startX - size / 2f).toInt()
            topMargin = (startY - size / 2f).toInt()
        }
        container.addView(fly, params)
        fly.animate()
            .translationX(targetX - startX)
            .translationY(targetY - startY)
            .scaleX(0.2f)
            .scaleY(0.2f)
            .alpha(0f)
            .setDuration(280L)
            .withEndAction { container.removeView(fly) }
            .start()
        binding.bubbleAmount.animate().scaleX(1.04f).scaleY(1.04f).setDuration(110L)
            .withEndAction {
                binding.bubbleAmount.animate().scaleX(1f).scaleY(1f).setDuration(120L).start()
            }
            .start()
    }

    private fun showCategoryMenu(category: CategoryEntity) {
        if (!category.isCustom) {
            Toast.makeText(requireContext(), R.string.category_preset_readonly, Toast.LENGTH_SHORT).show()
            return
        }
        val popup = PopupMenu(requireContext(), binding.categoryWheel)
        popup.menu.add(0, 1, 0, R.string.category_rename_action)
        popup.menu.add(0, 2, 1, R.string.category_delete_action)
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> {
                    CategoryEditDialog.show(requireContext(), category) { name, color, iconKey ->
                        viewLifecycleOwner.lifecycleScope.launch {
                            when (repository.updateCategory(category.id, name, color, iconKey)) {
                                is CategoryResult.Success ->
                                    Toast.makeText(requireContext(), R.string.category_saved, Toast.LENGTH_SHORT).show()
                                CategoryResult.InvalidName ->
                                    Toast.makeText(requireContext(), R.string.category_name_invalid, Toast.LENGTH_SHORT).show()
                                else -> Unit
                            }
                        }
                    }
                    true
                }
                2 -> {
                    confirmDeleteCategory(category)
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    private fun confirmDeleteCategory(category: CategoryEntity) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.category_delete_title)
            .setMessage(R.string.category_delete_confirm)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.category_delete_action) { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    when (repository.deleteCategory(category.id)) {
                        is CategoryResult.Success -> {
                            Toast.makeText(requireContext(), R.string.category_deleted, Toast.LENGTH_SHORT).show()
                        }
                        else -> Unit
                    }
                }
            }
            .show()
    }

    private fun showAddCategoryDialog() {
        CategoryEditDialog.show(requireContext()) { name, color, iconKey ->
            viewLifecycleOwner.lifecycleScope.launch {
                when (val result = repository.addCategory(name, color, iconKey)) {
                    is CategoryResult.Success -> {
                        Toast.makeText(requireContext(), R.string.category_saved, Toast.LENGTH_SHORT).show()
                    }
                    CategoryResult.InvalidName ->
                        Toast.makeText(requireContext(), R.string.category_name_invalid, Toast.LENGTH_SHORT).show()
                    else -> Unit
                }
            }
        }
    }

    private fun updateSaveState() {
        // 保留可点击，空金额时用气泡红闪 + 触觉反馈拦截，避免禁用按钮让用户无从得知原因。
        binding.btnSave.isEnabled = true
    }

    private fun saveExpense() {
        if (subItems.isEmpty() || binding.bubbleAmount.amountText().isNotEmpty()) {
            binding.bubbleAmount.flashError()
            Haptics.reject(binding.bubbleAmount)
            binding.bubbleAmount.requestAmountFocus()
            return
        }
        val note = binding.etNote.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }
        val splits = subItems.map { ExpenseSplit(it.amountCents, it.categoryId) }
        binding.bubbleAmount.hideKeyboard()
        viewLifecycleOwner.lifecycleScope.launch {
            repository.addExpenses(splits, selectedDate, note)
            subItems.clear()
            binding.bubbleAmount.clearAmount()
            refreshBubble()
            binding.etNote.text?.clear()
            binding.etNote.clearFocus()
            binding.bubbleAmount.hideKeyboard()
            updateSaveState()
            Haptics.tap(binding.btnSave)
            Toast.makeText(requireContext(), R.string.expense_saved, Toast.LENGTH_SHORT).show()
        }
    }
}
