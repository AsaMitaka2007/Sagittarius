package com.dsha.sagittarius.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.dsha.sagittarius.SagittariusApp
import com.dsha.sagittarius.R
import com.dsha.sagittarius.data.DailyGoalEntity
import com.dsha.sagittarius.data.DailyGoalMath
import com.dsha.sagittarius.data.DietRecordEntity
import com.dsha.sagittarius.data.DietRepository
import com.dsha.sagittarius.data.NutritionGoalEntity
import com.dsha.sagittarius.data.NutritionMath
import com.dsha.sagittarius.data.NutritionValues
import com.dsha.sagittarius.databinding.FragmentNutritionBinding
import com.dsha.sagittarius.util.DateText
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.Locale
import kotlin.math.roundToInt

class NutritionFragment : Fragment() {

    private var _binding: FragmentNutritionBinding? = null
    private val binding get() = _binding!!
    private lateinit var repository: DietRepository
    private lateinit var goalAdapter: DailyGoalAdapter
    private var selectedDate: LocalDate = LocalDate.now()
    private var goal = NutritionGoalEntity()
    private var recordsJob: Job? = null
    private var currentRecords: List<DietRecordEntity> = emptyList()
    private var currentGoals: List<DailyGoalEntity> = emptyList()
    private var currentTotals: NutritionValues? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNutritionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        repository = (requireActivity().application as SagittariusApp).dietRepository
        goalAdapter = DailyGoalAdapter(
            onToggle = { item, checked ->
                viewLifecycleOwner.lifecycleScope.launch {
                    repository.setDailyGoalCompleted(item.id, checked)
                }
            },
            onDelete = { item ->
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("删除目标")
                    .setMessage("删除「${item.title}」？")
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.delete) { _, _ ->
                        viewLifecycleOwner.lifecycleScope.launch { repository.deleteDailyGoal(item) }
                    }
                    .show()
            }
        )
        binding.recyclerGoals.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerGoals.adapter = goalAdapter
        binding.btnAddGoal.setOnClickListener {
            DailyGoalDialog.show(requireContext()) { title, key, amount ->
                viewLifecycleOwner.lifecycleScope.launch {
                    repository.addDailyGoal(title, key, amount)
                }
            }
        }
        setupRingSelection()
        binding.btnEditGoals.setOnClickListener {
            NutritionGoalSheet.show(requireContext(), goal) { newGoal ->
                viewLifecycleOwner.lifecycleScope.launch { repository.saveGoal(newGoal) }
            }
        }
        binding.btnSpecialGoals.setOnClickListener {
            SpecialGoalSheet.show(requireContext(), selectedDate, goal) { override ->
                viewLifecycleOwner.lifecycleScope.launch {
                    repository.saveGoalOverride(override)
                    Toast.makeText(requireContext(), R.string.nutrition_special_saved, Toast.LENGTH_SHORT).show()
                }
            }
        }
        DateConsoleBinding.observe(
            this,
            binding.dateConsole,
            DateConsolePage.STATS,
            R.string.tab_nutrition
        ) { state ->
            val range = DateConsoleManager.rangeOf(state)
            selectedDate = if (state.isCustom) range.start else state.anchor
            loadRecords()
            render()
        }
        loadRecords()
    }

    private fun loadRecords() {
        recordsJob?.cancel()
        recordsJob = viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(
                    repository.observeRecords(selectedDate),
                    repository.observeActiveGoal(selectedDate),
                    repository.dailyGoals
                ) { records, activeGoal, goals -> Triple(records, activeGoal, goals) }
                    .collect { (records, activeGoal, goals) ->
                        currentRecords = records
                        goal = activeGoal
                        currentGoals = goals
                        goalAdapter.submitList(DailyGoalAdapter.sortForToday(goals))
                        binding.tvGoalEmpty.isVisible = goals.isEmpty()
                        binding.recyclerGoals.isVisible = goals.isNotEmpty()
                        render(records)
                    }
            }
        }
    }

    private fun render(records: List<DietRecordEntity> = currentRecords) {
        val base = NutritionMath.sum(records)
        val todaysGoals = if (selectedDate == LocalDate.now()) currentGoals else emptyList()
        val totals = DailyGoalMath.apply(base, todaysGoals)
        currentTotals = totals
        binding.nutritionRings.setData(
            totals.calories, totals.carbs, totals.fat, totals.protein,
            totals.fiber, totals.water, totals.sodium, totals.vitamin, goal
        )
        binding.nutritionLegend.setData(
            totals.calories, totals.protein, totals.carbs, totals.fat,
            totals.fiber, totals.water, totals.sodium, totals.vitamin, goal
        )
        binding.tvNutrientDetail.text = getString(R.string.nutrition_ring_touch_hint)
        binding.tvNutritionDate.text = DateText.full(selectedDate)
        binding.tvNutritionTotal.text = getString(
            R.string.nutrition_total, totals.calories, goal.calories
        )
    }

    private fun setupRingSelection() {
        binding.nutritionRings.onRingSelected = { index ->
            binding.tvNutrientDetail.text = describeRing(index)
        }
    }

    private fun describeRing(index: Int): String {
        val totals = currentTotals ?: return ""
        return when (index) {
            0 -> {
                val over = goal.sodium > 0.0 && totals.sodium > goal.sodium
                getString(
                    R.string.nutrition_sodium_detail,
                    totals.sodium, goal.sodium, percent(totals.sodium, goal.sodium)
                ) + if (over) "，已超出" else ""
            }
            1 -> getString(
                R.string.nutrition_vitamin_detail,
                totals.vitaminA, totals.vitaminB1, totals.vitaminB2,
                totals.vitaminB3, totals.vitaminC, totals.vitaminE
            )
            2 -> ringDetail("热量", totals.calories, goal.calories, "kcal")
            3 -> ringDetail("碳水", totals.carbs, goal.carbs, "g")
            4 -> ringDetail("脂肪", totals.fat, goal.fat, "g")
            5 -> ringDetail("蛋白质", totals.protein, goal.protein, "g")
            6 -> ringDetail("纤维", totals.fiber, goal.fiber, "g")
            7 -> ringDetail("水", totals.water, goal.water, "g")
            else -> ""
        }
    }

    private fun ringDetail(label: String, value: Double, target: Double, unit: String): String =
        String.format(
            Locale.CHINA, "%s：%.1f / %.1f %s（%d%%）",
            label, value, target, unit, percent(value, target)
        )

    private fun percent(value: Double, target: Double): Int =
        if (target <= 0.0) 0 else (value / target * 100.0).roundToInt()

    override fun onDestroyView() {
        recordsJob?.cancel()
        _binding = null
        super.onDestroyView()
    }
}
