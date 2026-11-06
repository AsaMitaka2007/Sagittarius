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
import com.dsha.sagittarius.SagittariusApp
import com.dsha.sagittarius.R
import com.dsha.sagittarius.data.DietRecordEntity
import com.dsha.sagittarius.data.DietRepository
import com.dsha.sagittarius.data.FoodEntity
import com.dsha.sagittarius.data.MealType
import com.dsha.sagittarius.data.NutritionMath
import com.dsha.sagittarius.databinding.FragmentDietAddBinding
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.Locale

class DietAddFragment : Fragment() {

    private var _binding: FragmentDietAddBinding? = null
    private val binding get() = _binding!!
    private lateinit var repository: DietRepository
    private var selectedDate: LocalDate = LocalDate.now()
    private var mealType: MealType = MealType.BREAKFAST
    private val pending = mutableListOf<DietRecordEntity>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDietAddBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        repository = (requireActivity().application as SagittariusApp).dietRepository
        binding.modeSwitch.setMode(AppMode.DIET)
        binding.modeSwitch.onModeSelected = { mode ->
            AppModeManager.setMode(requireContext(), mode)
        }
        setupMeals()
        binding.foodTrack.onFoodClick = { food -> askAmount(food) }
        binding.foodTrack.onFoodLongPress = { food ->
            viewLifecycleOwner.lifecycleScope.launch {
                repository.setFavorite(food, false)
                Toast.makeText(requireContext(), R.string.diet_favorite_removed, Toast.LENGTH_SHORT).show()
            }
        }
        binding.foodTrack.onSwapOrder = { from, to ->
            val list = binding.foodTrack.favoriteSnapshot().toMutableList()
            val fromIndex = list.indexOfFirst { it.id == from.id }
            val toIndex = list.indexOfFirst { it.id == to.id }
            if (fromIndex >= 0 && toIndex >= 0) {
                val moved = list.removeAt(fromIndex)
                list.add(toIndex, moved)
                viewLifecycleOwner.lifecycleScope.launch { repository.reorderFavorites(list) }
            }
        }
        binding.btnCustomFood.setOnClickListener {
            FoodLibrarySheet.show(requireContext(), repository, selectedDate, mealType) { record ->
                if (_binding != null) {
                    pending += record
                    refresh()
                }
            }
        }
        binding.btnClearDiet.setOnClickListener {
            pending.clear()
            refresh()
        }
        binding.btnSaveDiet.setOnClickListener { saveDiet() }
        DateConsoleBinding.observe(
            this,
            binding.dateConsole,
            DateConsolePage.ADD,
            R.string.tab_diet_add
        ) { state ->
            val range = DateConsoleManager.rangeOf(state)
            selectedDate = if (state.isCustom) range.start else state.anchor
            refresh()
        }
        observeFoods()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private fun setupMeals() {
        val chips = listOf(
            MealType.BREAKFAST to binding.chipBreakfast,
            MealType.LUNCH to binding.chipLunch,
            MealType.DINNER to binding.chipDinner,
            MealType.SNACK to binding.chipSnack
        )
        chips.forEach { (meal, chip) ->
            chip.setOnClickListener {
                mealType = meal
                chips.forEach { (m, c) -> c.isSelected = m == meal }
            }
        }
        chips.forEach { (m, c) -> c.isSelected = m == mealType }
    }

    private fun observeFoods() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                repository.foods.collect { foods ->
                    val favorites = foods.filter { it.favorite }
                    binding.foodTrack.setFoods(favorites)
                    binding.tvFoodEmpty.isVisible = favorites.isEmpty()
                }
            }
        }
    }

    private fun askAmount(food: FoodEntity) {
        viewLifecycleOwner.lifecycleScope.launch {
            val units = repository.getUnits(food.id)
            FoodAmountSheet.show(requireContext(), food, units) { amount, unit ->
                pending += NutritionMath.recordFrom(
                    food, mealType, amount, unit.unitName, unit.weightInGrams, selectedDate
                )
                refresh()
            }
        }
    }

    private fun refresh() {
        val calories = pending.sumOf { it.calories }
        val grams = pending.sumOf { it.grams }
        binding.dietBubble.setSummary(
            calories,
            grams,
            pending.map { "${it.foodName} ${String.format(Locale.CHINA, "%.0f", it.calories)}kcal" }
        )
        binding.btnSaveDiet.isEnabled = true
    }

    private fun saveDiet() {
        if (pending.isEmpty()) {
            binding.dietBubble.flashError()
            Haptics.reject(binding.dietBubble)
            return
        }
        val records = pending.map { it.copy(date = selectedDate) }
        viewLifecycleOwner.lifecycleScope.launch {
            repository.addRecords(records)
            pending.clear()
            refresh()
            Haptics.tap(binding.btnSaveDiet)
            Toast.makeText(requireContext(), R.string.expense_saved, Toast.LENGTH_SHORT).show()
        }
    }
}
