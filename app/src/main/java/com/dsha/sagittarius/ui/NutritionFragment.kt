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
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.dsha.sagittarius.SagittariusApp
import com.dsha.sagittarius.R
import com.dsha.sagittarius.data.DietRecordEntity
import com.dsha.sagittarius.data.DietRepository
import com.dsha.sagittarius.data.NutritionGoalEntity
import com.dsha.sagittarius.databinding.FragmentNutritionBinding
import com.dsha.sagittarius.util.DateText
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.Locale

class NutritionFragment : Fragment() {

    private var _binding: FragmentNutritionBinding? = null
    private val binding get() = _binding!!
    private lateinit var repository: DietRepository
    private lateinit var adapter: DietRecordAdapter
    private var selectedDate: LocalDate = LocalDate.now()
    private var goal = NutritionGoalEntity()
    private var recordsJob: Job? = null
    private var currentRecords: List<DietRecordEntity> = emptyList()

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
        adapter = DietRecordAdapter { record -> confirmDelete(record) }
        binding.recyclerDiet.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerDiet.adapter = adapter
        attachSwipe()
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
                    repository.observeActiveGoal(selectedDate)
                ) { records, activeGoal -> records to activeGoal }.collect { (records, activeGoal) ->
                    currentRecords = records
                    goal = activeGoal
                    adapter.submit(records)
                    binding.tvDietEmpty.isVisible = records.isEmpty()
                    render(records)
                }
            }
        }
    }

    private fun render(records: List<DietRecordEntity> = currentRecords) {
        val calories = records.sumOf { it.calories }
        val carbs = records.sumOf { it.carbs }
        val fat = records.sumOf { it.fat }
        val protein = records.sumOf { it.protein }
        val fiber = records.sumOf { it.fiber }
        val water = records.sumOf { it.water }
        binding.nutritionRings.setData(calories, carbs, fat, protein, fiber, water, goal)
        binding.nutritionLegend.setData(calories, protein, carbs, fat, fiber, water, goal)
        binding.tvNutritionDate.text = DateText.full(selectedDate)
        binding.tvNutritionTotal.text = getString(
            R.string.nutrition_total, calories, goal.calories
        )
    }

    private fun attachSwipe() {
        ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ) = false

            override fun getSwipeThreshold(viewHolder: RecyclerView.ViewHolder): Float = 0.3f

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val record = adapter.recordAt(viewHolder.bindingAdapterPosition) ?: return
                deleteWithUndo(record)
            }
        }).attachToRecyclerView(binding.recyclerDiet)
    }

    private fun confirmDelete(record: DietRecordEntity) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.diet_delete_title)
            .setMessage(getString(R.string.diet_delete_message, record.foodName, record.calories))
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ -> deleteWithUndo(record) }
            .show()
    }

    private fun deleteWithUndo(record: DietRecordEntity) {
        Haptics.longPress(binding.root)
        viewLifecycleOwner.lifecycleScope.launch {
            repository.deleteRecord(record)
            Snackbar.make(binding.root, R.string.diet_deleted, Snackbar.LENGTH_LONG)
                .setAction(R.string.undo) {
                    Haptics.tap(binding.root)
                    viewLifecycleOwner.lifecycleScope.launch { repository.addRecords(listOf(record)) }
                }
                .show()
        }
    }

    override fun onDestroyView() {
        recordsJob?.cancel()
        _binding = null
        super.onDestroyView()
    }
}
