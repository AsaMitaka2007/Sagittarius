package com.dsha.sagittarius.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.dsha.sagittarius.SagittariusApp
import com.dsha.sagittarius.R
import com.dsha.sagittarius.data.ActiveBudgetSnapshot
import com.dsha.sagittarius.data.BudgetMath
import com.dsha.sagittarius.data.BudgetNodeEntity
import com.dsha.sagittarius.data.BudgetNodeResult
import com.dsha.sagittarius.data.BudgetRangeType
import com.dsha.sagittarius.data.BudgetRepository
import com.dsha.sagittarius.data.BudgetTreeDisplayNode
import com.dsha.sagittarius.data.BudgetTreeMath
import com.dsha.sagittarius.data.CategoryCatalog
import com.dsha.sagittarius.data.CategoryEntity
import com.dsha.sagittarius.databinding.FragmentBudgetBinding
import com.dsha.sagittarius.theme.ThemeManager
import com.dsha.sagittarius.util.BudgetPalette
import com.dsha.sagittarius.util.DateText
import com.dsha.sagittarius.util.Money
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.abs

class BudgetFragment : Fragment() {

    private var _binding: FragmentBudgetBinding? = null
    private val binding get() = _binding!!
    private lateinit var repository: BudgetRepository
    private var currentSnapshot: ActiveBudgetSnapshot? = null
    private var currentNodes: List<BudgetNodeEntity> = emptyList()
    private var currentCategories: List<CategoryEntity> = emptyList()
    private var entrancePlayed = false
    private var consoleSynced = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBudgetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        repository = (requireActivity().application as SagittariusApp).repository
        DateConsoleBinding.observe(this, binding.dateConsole, DateConsolePage.BUDGET) { state ->
            applyConsoleRange(state)
        }
        binding.btnSetBudget.setOnClickListener { showBudgetDialog() }
        binding.btnClearBudget.setOnClickListener { confirmClearBudget() }
        binding.btnAddTopLevel.setOnClickListener { showAddTopLevelDialog() }
        binding.budgetTreeView.onNodeLongClick = { node -> showNodeActions(node) }
        observeData()
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

    private fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    repository.observeActiveSnapshot().collect { snapshot ->
                        currentSnapshot = snapshot
                        currentCategories = snapshot.categories.values.toList()
                        if (!consoleSynced) {
                            consoleSynced = true
                            snapshot.state?.let {
                                DateConsoleManager.syncFromRange(
                                    DateConsolePage.BUDGET,
                                    it.activeStartDate,
                                    it.activeEndDate
                                )
                            }
                        }
                        render(snapshot)
                        rebuildTree()
                    }
                }
                launch {
                    repository.observeBudgetNodes().collect { nodes ->
                        currentNodes = nodes
                        rebuildTree()
                    }
                }
                launch {
                    repository.observeCategories().collect { categories ->
                        currentCategories = categories
                        rebuildTree()
                    }
                }
            }
        }
    }

    private fun render(snapshot: ActiveBudgetSnapshot) {
        val state = snapshot.state ?: return
        binding.tvRange.text = DateText.rangeLabel(
            state.activeRangeType,
            state.activeStartDate,
            state.activeEndDate
        )
        val ratio = BudgetMath.remainingRatio(snapshot)
        val palette = BudgetPalette.forRatio(ratio, ThemeManager.isLight)
        binding.tvRemaining.setTextColor(palette.display)
        binding.progressBudget.setPalette(palette.primary, palette.secondary)
        binding.tvUsed.text = Money.formatCents(snapshot.totalCents)
        binding.tvDaily.text = Money.formatCents(
            BudgetMath.summarize(
                snapshot.expenses,
                state.activeStartDate,
                state.activeEndDate,
                snapshot.categories
            ).dailyAverageCents
        )
        if (!snapshot.isConfigured) {
            binding.tvRemaining.text = getString(R.string.not_set_budget)
            binding.tvRemaining.textSize = 30f
            binding.tvPercent.text = getString(R.string.budget_not_set_hint)
            binding.tvBudgetAmount.text = "—"
            binding.tvOverBudget.isVisible = false
            binding.btnClearBudget.isVisible = false
            binding.btnSetBudget.text = getString(R.string.set_budget)
            binding.tvBudgetHint.isVisible = true
            binding.progressBudget.setProgress(0f, true)
            return
        }

        binding.tvRemaining.textSize = 44f
        binding.tvRemaining.text = Money.formatSignedCents(snapshot.remainingCents ?: 0L)
        val remainingPercent = Money.remainingPercent(ratio) ?: 0
        binding.tvPercent.text = if (remainingPercent >= 0) {
            "剩余 $remainingPercent%"
        } else {
            "已超支 ${abs(remainingPercent)}%"
        }
        binding.tvBudgetAmount.text =
            Money.formatCents(snapshot.budget?.amountCents ?: 0L)
        binding.tvOverBudget.isVisible = snapshot.isOverBudget
        binding.btnClearBudget.isVisible = true
        binding.btnSetBudget.text = getString(R.string.change_budget)
        binding.tvBudgetHint.isVisible = false
        binding.progressBudget.setProgress(snapshot.usedRatio ?: 0f, true)
    }

    private fun rebuildTree() {
        if (_binding == null) return
        val snapshot = currentSnapshot
        val total = snapshot?.budget?.amountCents ?: 0L
        val categoryNames = currentCategories.associate {
            it.id to CategoryCatalog.displayName(requireContext(), it)
        }
        val model = BudgetTreeMath.build(
            currentNodes,
            total,
            snapshot?.expenses.orEmpty(),
            categoryNames
        )
        binding.budgetTreeView.setVisualData(
            snapshot?.totalCents ?: 0L,
            currentCategories.associate {
                it.id to CategoryCatalog.defaultColor(requireContext(), it)
            }
        )
        binding.budgetTreeView.setModel(model)
        binding.btnAddTopLevel.isEnabled = total > 0L
    }

    private fun applyConsoleRange(state: DateConsoleState) {
        if (!consoleSynced) return
        val range = DateConsoleManager.rangeOf(state)
        val appState = currentSnapshot?.state ?: return
        if (appState.activeStartDate == range.start && appState.activeEndDate == range.end) return
        viewLifecycleOwner.lifecycleScope.launch {
            repository.setActiveRange(BudgetRangeType.CUSTOM, range.start, range.end)
        }
    }

    private fun showBudgetDialog() {
        val snapshot = currentSnapshot ?: return
        val state = snapshot.state ?: return
        BudgetDialog.show(
            requireContext(),
            rangeLabel = DateText.rangeLabel(
                state.activeRangeType,
                state.activeStartDate,
                state.activeEndDate
            ),
            currentCents = snapshot.budget?.amountCents
        ) { cents ->
            viewLifecycleOwner.lifecycleScope.launch {
                if (repository.setBudgetForActiveRange(cents)) {
                    Toast.makeText(requireContext(), R.string.budget_saved, Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), R.string.budget_below_tree, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun confirmClearBudget() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.remove_budget)
            .setMessage("仅移除当前日期范围的预算金额，历史记账不会被删除。")
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    repository.clearBudgetForActiveRange()
                    Toast.makeText(
                        requireContext(),
                        R.string.remove_budget_success,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            .show()
    }

    private fun showAddTopLevelDialog() {
        val snapshot = currentSnapshot ?: return
        if (snapshot.budget == null) {
            Toast.makeText(requireContext(), R.string.not_set_budget, Toast.LENGTH_SHORT).show()
            return
        }
        BudgetNodeDialog.show(
            requireContext(),
            currentCategories,
            parentLabel = getString(R.string.budget_tree_parent_root),
            titleRes = R.string.budget_tree_add_title
        ) { name, amount, categoryId ->
            viewLifecycleOwner.lifecycleScope.launch {
                handleNodeResult(repository.addBudgetNode(null, name, amount, categoryId))
            }
        }
    }

    private fun showNodeActions(display: BudgetTreeDisplayNode) {
        val labels = mutableListOf<String>()
        if (display.depth == 1) labels += getString(R.string.budget_tree_add_child)
        labels += getString(R.string.budget_tree_edit)
        labels += getString(R.string.budget_tree_delete)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(display.node.budgetName)
            .setItems(labels.toTypedArray()) { _, which ->
                val action = labels[which]
                when (action) {
                    getString(R.string.budget_tree_add_child) -> showAddChildDialog(display)
                    getString(R.string.budget_tree_edit) -> showEditNodeDialog(display)
                    getString(R.string.budget_tree_delete) -> confirmDeleteNode(display)
                }
            }
            .show()
    }

    private fun showAddChildDialog(parent: BudgetTreeDisplayNode) {
        if (parent.depth >= 2) return
        BudgetNodeDialog.show(
            requireContext(),
            currentCategories,
            parentLabel = getString(
                R.string.budget_tree_parent,
                parent.node.budgetName,
                Money.formatCents(parent.node.amountCents)
            ),
            titleRes = R.string.budget_tree_add_title
        ) { name, amount, categoryId ->
            viewLifecycleOwner.lifecycleScope.launch {
                handleNodeResult(
                    repository.addBudgetNode(parent.node.id, name, amount, categoryId)
                )
            }
        }
    }

    private fun showEditNodeDialog(display: BudgetTreeDisplayNode) {
        val parentLabel = if (display.node.parentId == null) {
            getString(R.string.budget_tree_parent_root)
        } else {
            val parent = currentNodes.firstOrNull { it.id == display.node.parentId }
            getString(
                R.string.budget_tree_parent,
                parent?.budgetName ?: "—",
                Money.formatCents(parent?.amountCents ?: 0L)
            )
        }
        BudgetNodeDialog.show(
            requireContext(),
            currentCategories,
            parentLabel = parentLabel,
            existing = display.node,
            titleRes = R.string.budget_tree_edit_title
        ) { name, amount, categoryId ->
            viewLifecycleOwner.lifecycleScope.launch {
                handleNodeResult(
                    repository.updateBudgetNode(display.node.id, name, amount, categoryId)
                )
            }
        }
    }

    private fun confirmDeleteNode(display: BudgetTreeDisplayNode) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.budget_tree_delete_title)
            .setMessage(R.string.budget_tree_delete_message)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    repository.deleteBudgetNode(display.node.id)
                    Toast.makeText(
                        requireContext(),
                        R.string.budget_tree_deleted,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            .show()
    }

    private fun handleNodeResult(result: BudgetNodeResult) {
        when (result) {
            is BudgetNodeResult.Success ->
                Toast.makeText(requireContext(), R.string.budget_tree_saved, Toast.LENGTH_SHORT).show()
            is BudgetNodeResult.OverAllocated ->
                Toast.makeText(
                    requireContext(),
                    getString(R.string.budget_tree_over) + " 可分配 " +
                        Money.formatCents(result.availableCents),
                    Toast.LENGTH_LONG
                ).show()
            is BudgetNodeResult.BelowChildren ->
                Toast.makeText(
                    requireContext(),
                    "分配金额不能低于子项已分配 " + Money.formatCents(result.requiredCents),
                    Toast.LENGTH_LONG
                ).show()
            BudgetNodeResult.NoBudget ->
                Toast.makeText(requireContext(), R.string.not_set_budget, Toast.LENGTH_SHORT).show()
            BudgetNodeResult.InvalidDepth ->
                Toast.makeText(requireContext(), "预算树最多支持两层分配", Toast.LENGTH_SHORT).show()
            BudgetNodeResult.InvalidAmount ->
                Toast.makeText(requireContext(), R.string.invalid_amount, Toast.LENGTH_SHORT).show()
        }
    }


}
