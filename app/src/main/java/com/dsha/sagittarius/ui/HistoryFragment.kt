package com.dsha.sagittarius.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.dsha.sagittarius.SagittariusApp
import com.dsha.sagittarius.R
import com.dsha.sagittarius.data.CategoryCatalog
import com.dsha.sagittarius.data.CategoryEntity
import com.dsha.sagittarius.data.BudgetRepository
import com.dsha.sagittarius.data.ExpenseEntity
import com.dsha.sagittarius.databinding.FragmentHistoryBinding
import com.dsha.sagittarius.export.CsvExporter
import com.dsha.sagittarius.util.Money
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

class HistoryFragment : Fragment() {

    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: HistoryAdapter
    private lateinit var repository: BudgetRepository
    private val viewModel: HistoryViewModel by viewModels {
        HistoryViewModel.Factory(
            (requireActivity().application as SagittariusApp).repository,
            requireContext().applicationContext
        )
    }

    private var latestUi: HistoryUiState = HistoryUiState.empty()
    private var pendingExport: List<ExpenseEntity> = emptyList()
    private val categoryChips = mutableMapOf<Long?, GlassChipView>()
    private var categorySignature: String = ""

    private val exportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        val data = pendingExport
        pendingExport = emptyList()
        val appContext = context?.applicationContext ?: return@registerForActivityResult
        val categories = latestUi.categories
        lifecycleScope.launch {
            val result = runCatching { CsvExporter.write(appContext, uri, data, categories) }
            val ctx = context ?: return@launch
            if (result.isSuccess) {
                Toast.makeText(ctx, R.string.export_success, Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(
                    ctx,
                    "${ctx.getString(R.string.export_failed)}：${result.exceptionOrNull()?.message.orEmpty()}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        repository = (requireActivity().application as SagittariusApp).repository
        setupSearch()
        setupRecyclerView()
        DateConsoleBinding.observe(this, binding.dateConsole, DateConsolePage.HISTORY) { state ->
            val range = DateConsoleManager.rangeOf(state)
            viewModel.setConsoleRange(range.start, range.end)
        }
        binding.btnExport.setOnClickListener { exportCsv() }
        binding.fabAdd.setOnClickListener { showAddExpense() }
        observeData()
    }

    override fun onDestroyView() {
        categoryChips.clear()
        _binding = null
        super.onDestroyView()
    }

    private fun setupSearch() {
        binding.etSearch.doAfterTextChanged { viewModel.setQuery(it?.toString().orEmpty()) }
        binding.etSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                binding.etSearch.clearFocus()
                true
            } else {
                false
            }
        }
    }

    private fun renderCategoryChips(categories: List<CategoryEntity>, selectedId: Long?) {
        val signature = categories.joinToString("|") {
            "${it.id}:${it.name}:${it.color}:${it.iconKey}:${it.sortOrder}"
        }
        if (signature == categorySignature && binding.categoryContainer.childCount > 0) {
            categoryChips.forEach { (id, chip) -> chip.isSelected = id == selectedId }
            return
        }
        categorySignature = signature
        categoryChips.clear()
        binding.categoryContainer.removeAllViews()
        val allChip = createCategoryChip(null, getString(R.string.all_categories), null)
        binding.categoryContainer.addView(allChip)
        categories.forEach { category ->
            val chip = createCategoryChip(
                category.id,
                CategoryCatalog.displayName(requireContext(), category),
                category
            )
            binding.categoryContainer.addView(chip)
        }
        categoryChips.forEach { (id, chip) -> chip.isSelected = id == selectedId }
    }

    private fun createCategoryChip(
        categoryId: Long?,
        label: String,
        category: CategoryEntity?
    ): GlassChipView {
        val density = resources.displayMetrics.density
        val chip = GlassChipView(requireContext()).apply {
            text = label
            setOnClickListener {
                viewModel.setCategory(categoryId)
                categoryChips.forEach { (id, itemChip) -> itemChip.isSelected = id == categoryId }
            }
            if (category?.isCustom == true) {
                setOnLongClickListener {
                    CategoryChipActions.showMenu(
                        this,
                        requireContext(),
                        viewLifecycleOwner.lifecycleScope,
                        repository,
                        category
                    ) {
                        if (categoryId != null && latestUi.filter.categoryId == categoryId) {
                            viewModel.setCategory(null)
                        }
                    }
                    true
                }
            }
        }
        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            (38 * density).toInt()
        ).apply { marginEnd = (6 * density).toInt() }
        chip.layoutParams = params
        categoryChips[categoryId] = chip
        return chip
    }

    private fun setupRecyclerView() {
        adapter = HistoryAdapter(
            onExpenseClick = { expense -> showEditExpense(expense) },
            onExpenseLongClick = { expense -> confirmDelete(expense) },
            onAddClick = { showAddExpense() }
        )
        binding.recyclerHistory.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerHistory.adapter = adapter

        val swipeCallback = object : ItemTouchHelper.SimpleCallback(
            0,
            ItemTouchHelper.LEFT
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean = false

            override fun getMovementFlags(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder
            ): Int {
                val position = viewHolder.bindingAdapterPosition
                val swipeable = adapter.currentList.getOrNull(position) is HistoryItem.ExpenseRow
                return if (swipeable) {
                    makeMovementFlags(0, ItemTouchHelper.LEFT)
                } else {
                    makeMovementFlags(0, 0)
                }
            }

            override fun getSwipeThreshold(viewHolder: RecyclerView.ViewHolder): Float = 0.3f

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.bindingAdapterPosition
                val item = adapter.currentList.getOrNull(position)
                if (item is HistoryItem.ExpenseRow) deleteWithUndo(item.expense)
            }
        }
        ItemTouchHelper(swipeCallback).attachToRecyclerView(binding.recyclerHistory)
    }

    private fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { ui ->
                    latestUi = ui
                    adapter.submitList(ui.items)
                    renderCategoryChips(ui.categories, ui.filter.categoryId)
                    if (ui.filter.categoryId != null &&
                        ui.categories.none { it.id == ui.filter.categoryId }
                    ) {
                        viewModel.setCategory(null)
                    }
                }
            }
        }
    }

    private fun showAddExpense() {
        ExpenseEditorDialog.show(requireContext(), latestUi.categories) { draft ->
            viewModel.addExpense(draft)
            Toast.makeText(requireContext(), R.string.expense_saved, Toast.LENGTH_SHORT).show()
        }
    }

    private fun showEditExpense(expense: ExpenseEntity) {
        ExpenseEditorDialog.show(requireContext(), latestUi.categories, expense) { draft ->
            viewModel.updateExpense(expense, draft)
            Toast.makeText(requireContext(), R.string.expense_saved, Toast.LENGTH_SHORT).show()
        }
    }

    private fun confirmDelete(expense: ExpenseEntity) {
        val category = latestUi.categories.firstOrNull { it.id == expense.categoryId }
        val categoryName = category?.let { CategoryCatalog.displayName(requireContext(), it) }
            ?: getString(R.string.category_unknown)
        val title = expense.note?.takeIf { it.isNotBlank() } ?: categoryName
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.delete_title)
            .setMessage(
                getString(
                    R.string.delete_message,
                    title,
                    Money.formatCents(expense.amountCents)
                )
            )
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ -> deleteWithUndo(expense) }
            .show()
    }

    private fun deleteWithUndo(expense: ExpenseEntity) {
        Haptics.longPress(binding.root)
        viewModel.deleteExpense(expense)
        Snackbar.make(
            binding.root,
            R.string.expense_deleted,
            Snackbar.LENGTH_LONG
        ).setAction(R.string.undo) {
            Haptics.tap(binding.root)
            viewModel.restoreExpense(expense)
        }.show()
    }

    private fun exportCsv() {
        val expenses = latestUi.filteredExpenses
        if (expenses.isEmpty()) {
            Toast.makeText(requireContext(), R.string.export_empty, Toast.LENGTH_SHORT).show()
            return
        }
        pendingExport = expenses
        val fileName = CsvExporter.suggestedFileName()
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.export_title)
            .setMessage(getString(R.string.export_message, expenses.size, fileName))
            .setNegativeButton(R.string.cancel) { _, _ -> pendingExport = emptyList() }
            .setPositiveButton(R.string.export_continue) { _, _ ->
                runCatching { exportLauncher.launch(fileName) }
                    .onFailure {
                        pendingExport = emptyList()
                        Toast.makeText(requireContext(), R.string.export_failed, Toast.LENGTH_LONG).show()
                    }
            }
            .show()
    }
}
