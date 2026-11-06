package com.dsha.sagittarius.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.dsha.sagittarius.SagittariusApp
import com.dsha.sagittarius.R
import com.dsha.sagittarius.data.BudgetMath
import com.dsha.sagittarius.data.BudgetRepository
import com.dsha.sagittarius.data.CategoryCatalog
import com.dsha.sagittarius.data.CategoryEntity
import com.dsha.sagittarius.data.DateRange
import com.dsha.sagittarius.data.ExpenseEntity
import com.dsha.sagittarius.databinding.FragmentStatsBinding
import com.dsha.sagittarius.theme.ThemeManager
import com.dsha.sagittarius.util.DateText
import com.dsha.sagittarius.util.Money
import java.time.YearMonth
import kotlinx.coroutines.launch

class StatsFragment : Fragment() {

    private var _binding: FragmentStatsBinding? = null
    private val binding get() = _binding!!
    private lateinit var repository: BudgetRepository
    private lateinit var tokens: ThemeTokens

    private var allExpenses: List<ExpenseEntity> = emptyList()
    private var categories: List<CategoryEntity> = emptyList()
    private val selectedCategoryIds = mutableSetOf<Long>()
    private var consoleRange: DateRange =
        DateConsoleManager.rangeOf(DateConsoleManager.stateOf(DateConsolePage.STATS).value)

    private val categoryChips = mutableMapOf<Long?, GlassChipView>()
    private var categorySignature = ""
    private var lastPieSlices: List<PieSlice> = emptyList()
    private var lineProviderSet = false
    private var lastRangeFocusKey: String? = null
    private var lineLocked = ChartLockManager.locked.value

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentStatsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        repository = (requireActivity().application as SagittariusApp).repository
        tokens = ThemeTokens.of(requireContext())
        binding.lineChart.onMonthChanged = { month ->
            binding.tvLineMonth.text = DateText.month(month)
        }
        lineLocked = ChartLockManager.locked.value
        applyChartLock()
        binding.btnLineLock.setOnClickListener {
            lineLocked = !lineLocked
            ChartLockManager.setLocked(requireContext(), lineLocked)
            applyChartLock()
        }
        DateConsoleBinding.observe(this, binding.dateConsole, DateConsolePage.STATS) { state ->
            consoleRange = DateConsoleManager.rangeOf(state)
            render()
        }
        observeData()
    }

    private fun applyChartLock() {
        binding.lineChart.setLocked(lineLocked)
        binding.btnLineLock.isSelected = lineLocked
        binding.btnLineLock.alpha = if (lineLocked) 1f else 0.62f
    }

    override fun onDestroyView() {
        categoryChips.clear()
        _binding = null
        super.onDestroyView()
    }

    private fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    repository.observeAllExpenses().collect {
                        allExpenses = it
                        render()
                    }
                }
                launch {
                    repository.observeCategories().collect {
                        categories = it
                        selectedCategoryIds.retainAll(it.map { category -> category.id }.toSet())
                        renderCategoryChips()
                        render()
                    }
                }
            }
        }
    }

    private fun renderCategoryChips() {
        val signature = categories.joinToString("|") {
            "${it.id}:${it.name}:${it.color}:${it.iconKey}:${it.sortOrder}"
        }
        if (signature == categorySignature && binding.statsCategoryContainer.childCount > 0) {
            updateCategoryChipSelection()
            return
        }
        categorySignature = signature
        categoryChips.clear()
        binding.statsCategoryContainer.removeAllViews()
        val density = resources.displayMetrics.density
        val allChip = createCategoryChip(null, getString(R.string.stats_all_categories), null)
        binding.statsCategoryContainer.addView(allChip, chipParams(density))
        categories.forEach { category ->
            val chip = createCategoryChip(
                category.id,
                CategoryCatalog.displayName(requireContext(), category),
                category
            )
            binding.statsCategoryContainer.addView(chip, chipParams(density))
        }
        updateCategoryChipSelection()
    }

    private fun chipParams(density: Float): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            (38 * density).toInt()
        ).apply { marginEnd = (7 * density).toInt() }

    private fun createCategoryChip(
        categoryId: Long?,
        label: String,
        category: CategoryEntity?
    ): GlassChipView {
        return GlassChipView(requireContext()).apply {
            text = label
            setOnClickListener {
                if (categoryId == null) {
                    selectedCategoryIds.clear()
                } else if (!selectedCategoryIds.add(categoryId)) {
                    selectedCategoryIds.remove(categoryId)
                }
                updateCategoryChipSelection()
                render()
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
                        if (categoryId != null) selectedCategoryIds.remove(categoryId)
                        updateCategoryChipSelection()
                        render()
                    }
                    true
                }
            }
        }.also { categoryChips[categoryId] = it }
    }

    private fun updateCategoryChipSelection() {
        categoryChips.forEach { (id, chip) ->
            chip.isSelected = if (id == null) {
                selectedCategoryIds.isEmpty()
            } else {
                id in selectedCategoryIds
            }
        }
    }

    private fun render() {
        if (_binding == null) return
        val range = consoleRange
        val categoriesById = categories.associateBy { it.id }
        val filtered = allExpenses.filter { expense ->
            !expense.date.isBefore(range.start) &&
                !expense.date.isAfter(range.end) &&
                (selectedCategoryIds.isEmpty() || expense.categoryId in selectedCategoryIds)
        }
        val summary = BudgetMath.summarize(filtered, range.start, range.end, categoriesById)

        binding.tvStatsTotal.text = Money.formatCents(summary.totalCents)
        binding.tvStatsDaily.text = Money.formatCents(summary.dailyAverageCents)
        binding.tvStatsLargest.text = Money.formatCents(summary.largestCents)
        binding.tvStatsCount.text = summary.count.toString()

        val rangeFocusKey = "${range.start}:${range.end}"
        if (!lineProviderSet) {
            binding.lineChart.setDataProvider(
                { month -> linePointsForMonth(month) },
                YearMonth.from(range.start)
            )
            lineProviderSet = true
            lastRangeFocusKey = rangeFocusKey
        } else {
            binding.lineChart.refreshData()
            if (rangeFocusKey != lastRangeFocusKey) {
                binding.lineChart.setFocusMonth(YearMonth.from(range.start))
                lastRangeFocusKey = rangeFocusKey
            }
        }

        val slices = buildPieSlices(summary.categoryTotals)
        if (slices != lastPieSlices) {
            lastPieSlices = slices
            binding.pieChart.setData(slices)
            renderLegend(slices)
        }

        binding.dateConsole.contentDescription =
            "${DateText.short(range.start)}–${DateText.short(range.end)}，总支出 ${Money.formatCents(summary.totalCents)}"
    }

    private fun linePointsForMonth(month: YearMonth): List<DailyPoint> {
        val byDate = allExpenses
            .filter { expense ->
                YearMonth.from(expense.date) == month &&
                    (selectedCategoryIds.isEmpty() || expense.categoryId in selectedCategoryIds)
            }
            .groupBy { it.date }
            .mapValues { (_, list) -> list.sumOf { it.amountCents } }
        return (1..month.lengthOfMonth()).map { day ->
            val date = month.atDay(day)
            DailyPoint(date, byDate[date] ?: 0L)
        }
    }

    private fun buildPieSlices(
        totals: List<com.dsha.sagittarius.data.CategoryTotal>
    ): List<PieSlice> {
        if (totals.isEmpty()) return emptyList()
        val colors = chartColors()
        val major = totals.filter { it.share >= 0.03f }.take(6).toMutableList()
        val rest = totals.filterNot { it in major }
        val slices = mutableListOf<PieSlice>()
        major.forEachIndexed { index, total ->
            slices += PieSlice(
                label = CategoryCatalog.displayName(requireContext(), total.category),
                amountCents = total.amountCents,
                share = total.share,
                color = colors[index % colors.size]
            )
        }
        if (rest.isNotEmpty()) {
            slices += PieSlice(
                label = getString(R.string.stats_other_category),
                amountCents = rest.sumOf { it.amountCents },
                share = rest.sumOf { it.share.toDouble() }.toFloat(),
                color = ColorUtils.setAlphaComponent(tokens.textTertiary, 235)
            )
        }
        return slices
    }

    private fun chartColors(): List<Int> = listOf(
        if (ThemeManager.isLight) tokens.titleStart else tokens.gradientStart,
        if (ThemeManager.isLight) tokens.titleMid else tokens.gradientMid,
        if (ThemeManager.isLight) tokens.titleEnd else tokens.gradientEnd,
        tokens.accent,
        ColorUtils.blendARGB(tokens.accent, tokens.gradientEnd, 0.45f),
        ColorUtils.blendARGB(tokens.gradientStart, tokens.gradientMid, 0.5f),
        tokens.nodeBarAllocation,
        tokens.nodeBarUsed
    )

    private fun renderLegend(slices: List<PieSlice>) {
        binding.pieLegend.removeAllViews()
        if (slices.isEmpty()) return
        val density = resources.displayMetrics.density
        slices.forEach { slice ->
            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(0, (4 * density).toInt(), 0, (4 * density).toInt())
            }
            val dot = View(requireContext()).apply {
                background = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.OVAL
                    setColor(slice.color)
                }
            }
            row.addView(dot, LinearLayout.LayoutParams((9 * density).toInt(), (9 * density).toInt()))
            val name = TextView(requireContext()).apply {
                text = slice.label
                setTextColor(tokens.textSecondary)
                textSize = 11f
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            }
            row.addView(
                name,
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    .apply { marginStart = (7 * density).toInt() }
            )
            val amount = TextView(requireContext()).apply {
                text = Money.formatCents(slice.amountCents)
                setTextColor(tokens.textPrimary)
                textSize = 11f
                gravity = android.view.Gravity.END
            }
            row.addView(
                amount,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            )
            val share = TextView(requireContext()).apply {
                text = "  ${Money.sharePercent(slice.share)}%"
                setTextColor(tokens.textTertiary)
                textSize = 10f
            }
            row.addView(share)
            binding.pieLegend.addView(row)
        }
    }
}
