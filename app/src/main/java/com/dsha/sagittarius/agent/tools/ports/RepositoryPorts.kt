package com.dsha.sagittarius.agent.tools.ports

import android.content.Context
import com.dsha.sagittarius.data.ActiveBudgetSnapshot
import com.dsha.sagittarius.data.BudgetRepository
import com.dsha.sagittarius.data.CategoryEntity
import com.dsha.sagittarius.data.DietRecordEntity
import com.dsha.sagittarius.data.DietRepository
import com.dsha.sagittarius.data.ExpenseEntity
import com.dsha.sagittarius.data.FoodEntity
import com.dsha.sagittarius.data.FoodUnitEntity
import com.dsha.sagittarius.data.NutritionGoalEntity
import com.dsha.sagittarius.data.BudgetNodeEntity
import com.dsha.sagittarius.data.BudgetNodeResult
import com.dsha.sagittarius.data.BudgetRangeType
import com.dsha.sagittarius.data.FoodCategoryEntity
import com.dsha.sagittarius.theme.ThemeManager
import com.dsha.sagittarius.theme.ThemeMode
import com.dsha.sagittarius.util.Money
import kotlinx.coroutines.flow.first
import android.content.Intent
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.dsha.sagittarius.agent.data.SagittariusPrefs
import com.dsha.sagittarius.data.weather.WeatherRepository
import com.dsha.sagittarius.data.weather.WeatherType
import com.dsha.sagittarius.ui.AppMode
import com.dsha.sagittarius.ui.AppModeManager
import com.dsha.sagittarius.ui.DateConsoleStyleManager
import com.dsha.sagittarius.ui.FloatingWindowService
import com.dsha.sagittarius.ui.MainActivity
import java.time.LocalDate

class BudgetRepositoryExpensePort(private val repository: BudgetRepository) : ExpensePort {
    override suspend fun categories(): List<CategoryEntity> = repository.observeCategories().first()
    override suspend fun addExpense(amountCents: Long, date: LocalDate, categoryId: Long, note: String?): Long =
        repository.addExpense(amountCents, date, categoryId, note)
    override suspend fun expensesBetween(start: LocalDate, end: LocalDate): List<ExpenseEntity> =
        repository.getExpensesBetween(start, end)
    override suspend fun activeSnapshot(): ActiveBudgetSnapshot = repository.getActiveSnapshot()
    override suspend fun setBudget(amountCents: Long): Boolean = repository.setBudgetForActiveRange(amountCents)
    override suspend fun getExpense(id: Long): ExpenseEntity? = repository.getExpense(id)
    override suspend fun updateExpense(expense: ExpenseEntity) = repository.updateExpense(expense)
    override suspend fun deleteExpense(expense: ExpenseEntity) = repository.deleteExpense(expense)
    override suspend fun budgetNodes(): List<BudgetNodeEntity> = repository.getBudgetNodes()
    override suspend fun addBudgetNode(
        parentId: Long?, name: String, amountCents: Long, categoryId: Long?
    ): BudgetNodeResult = repository.addBudgetNode(parentId, name, amountCents, categoryId)
    override suspend fun updateBudgetNode(
        nodeId: Long, name: String, amountCents: Long, categoryId: Long?
    ): BudgetNodeResult = repository.updateBudgetNode(nodeId, name, amountCents, categoryId)
    override suspend fun setActiveRange(type: BudgetRangeType, start: java.time.LocalDate?, end: java.time.LocalDate?) =
        repository.setActiveRange(type, start, end)

    override suspend fun addCategory(name: String): CategoryEntity? =
        (repository.addCategory(name, 0) as? com.dsha.sagittarius.data.CategoryResult.Success)?.category
}

class DietRepositoryDietPort(private val repository: DietRepository) : DietPort {
    override suspend fun ensureSeeded() = repository.ensureSeeded()
    override suspend fun findFoodByName(name: String): FoodEntity? = repository.findFoodByName(name)
    override suspend fun getUnits(foodId: Long): List<FoodUnitEntity> = repository.getUnits(foodId)
    override suspend fun addRecord(record: DietRecordEntity): Long = repository.addRecord(record)
    override suspend fun addFood(food: FoodEntity): FoodEntity? = repository.addFood(food)
    override suspend fun updateFood(food: FoodEntity) = repository.updateFood(food)
    override suspend fun deleteFood(food: FoodEntity): Boolean = repository.deleteFood(food)
    override suspend fun recordsBetween(start: LocalDate, end: LocalDate): List<DietRecordEntity> =
        repository.getRecordsBetween(start, end)
    override suspend fun getGoal(): NutritionGoalEntity? = repository.getGoal()
    override suspend fun saveGoal(goal: NutritionGoalEntity) = repository.saveGoal(goal)
    override suspend fun addUnit(foodId: Long, unitName: String, weightInGrams: Double): Boolean =
        repository.addUnit(foodId, unitName, weightInGrams)
    override suspend fun getFoodCategories(): List<FoodCategoryEntity> = repository.getFoodCategories()
    override suspend fun addFoodCategory(name: String): FoodCategoryEntity? = repository.addFoodCategory(name)
    override suspend fun deleteFoodCategory(name: String): Boolean = repository.deleteFoodCategory(name)
}

class AppSettingsPort(
    private val context: Context,
    private val budget: BudgetRepository,
    private val diet: DietRepository
) : SettingsPort {
    override suspend fun setTheme(value: String): String {
        val mode = when (value.trim().lowercase()) {
            "dark", "black" -> ThemeMode.BLACK
            "light" -> ThemeMode.LIGHT
            "glass" -> ThemeMode.GLASS
            else -> throw IllegalArgumentException("主题只能是 dark/light/glass")
        }
        ThemeManager.setMode(context, mode)
        return mode.storageValue
    }

    override suspend fun setDailyBudget(amountCents: Long): Boolean = budget.setBudgetForActiveRange(amountCents)

    override suspend fun setNutritionGoal(value: String): String {
        val text = value.trim().lowercase()
        val numeric = Regex("[-+]?\\d+(\\.\\d+)?").find(text)?.value?.toDoubleOrNull()
            ?: throw IllegalArgumentException("营养目标必须是数字")
        val goal = diet.getGoal() ?: NutritionGoalEntity()
        val result = when {
            text.contains("protein") || text.endsWith("g") -> goal.copy(protein = numeric).also { diet.saveGoal(it) }
            text.contains("carb") -> goal.copy(carbs = numeric).also { diet.saveGoal(it) }
            text.contains("fat") -> goal.copy(fat = numeric).also { diet.saveGoal(it) }
            text.contains("fiber") -> goal.copy(fiber = numeric).also { diet.saveGoal(it) }
            text.contains("water") -> goal.copy(water = numeric).also { diet.saveGoal(it) }
            else -> goal.copy(calories = numeric).also { diet.saveGoal(it) }
        }
        return "营养目标已更新（热量 %.0f kcal / 蛋白质 %.0f g）".format(result.calories, result.protein)
    }
}

class AndroidSystemPort(
    private val context: Context,
    private val prefs: SagittariusPrefs
) : SystemPort {
    override suspend fun toggleNetworkSearch(enabled: Boolean): Boolean {
        prefs.setWebSearchEnabled(enabled)
        return enabled
    }

    override suspend fun updateTheme(theme: String): String {
        val mode = when (theme.trim().lowercase()) {
            "light" -> ThemeMode.LIGHT
            "dark", "black" -> ThemeMode.BLACK
            "glass" -> ThemeMode.GLASS
            else -> throw IllegalArgumentException("主题只能是 light/dark/glass")
        }
        ThemeManager.setMode(context, mode)
        return mode.storageValue
    }

    override suspend fun updateDateColor(color: String): String {
        val style = DateConsoleStyleManager.presets.firstOrNull {
            it.label == color || it.id == color.lowercase()
        } ?: throw IllegalArgumentException("没有这个颜色")
        DateConsoleStyleManager.setStyle(context, style)
        return style.label
    }

    override suspend fun toggleFloatingWindow(enabled: Boolean): Boolean {
        if (enabled && !Settings.canDrawOverlays(context)) return false
        val intent = Intent(context, FloatingWindowService::class.java).apply {
            action = if (enabled) FloatingWindowService.ACTION_SHOW else FloatingWindowService.ACTION_STOP
        }
        if (enabled) ContextCompat.startForegroundService(context, intent) else context.startService(intent)
        return true
    }

    override suspend fun navigateToTab(tab: String): String {
        val current = AppModeManager.mode.value
        val (mode, index) = when (tab.trim()) {
            "记一笔", "记账" -> AppMode.ACCOUNT to 0
            "历史记账", "历史" -> AppMode.ACCOUNT to 1
            "预算" -> AppMode.ACCOUNT to 2
            "统计面板", "统计" -> AppMode.ACCOUNT to 3
            "饮食" -> AppMode.DIET to 0
            "饮食历史" -> AppMode.DIET to 1
            "营养" -> AppMode.DIET to 2
            "天气" -> current to if (current == AppMode.ACCOUNT) 4 else 3
            "系统设置", "设置" -> current to if (current == AppMode.ACCOUNT) 5 else 4
            else -> throw IllegalArgumentException("未知页面")
        }
        AppModeManager.setMode(context, mode)
        context.startActivity(
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(MainActivity.EXTRA_TAB, index)
                putExtra(MainActivity.EXTRA_TAB_NAME, tab)
            }
        )
        return tab
    }

    override suspend fun setPetVisible(visible: Boolean) {
        prefs.setPetVisible(visible)
    }

    override suspend fun setPetTextColor(color: Int) = prefs.setPetTextColor(color)
    override suspend fun setPetScale(percent: Int) = prefs.setPetScale(percent)
    override suspend fun setPetLocked(locked: Boolean) = prefs.setPetLocked(locked)

    override suspend fun controlWeather(type: String): String {
        val weather = when (type.trim().lowercase()) {
            "real", "真实" -> null
            "sunny", "晴" -> WeatherType.SUNNY
            "rainy", "雨" -> WeatherType.RAINY
            "snowy", "雪" -> WeatherType.SNOWY
            "cloudy", "云" -> WeatherType.CLOUDY
            else -> throw IllegalArgumentException("天气特效只能是 sunny/rainy/snowy/cloudy/real")
        }
        WeatherRepository.getInstance(context).setDebugOverride(weather)
        return weather?.name?.lowercase() ?: "real"
    }
}

class DatabaseQueryPort(
    private val budget: BudgetRepository,
    private val diet: DietRepository
) : QueryPort {
    override suspend fun budgetSummary(): String {
        val s = budget.getActiveSnapshot()
        if (!s.isConfigured) return "当前没有设置预算。"
        return buildString {
            append("当前预算：")
            append(Money.formatCents(s.budget?.amountCents ?: 0L))
            append("，已用：")
            append(Money.formatCents(s.totalCents))
            append("，剩余：")
            append(s.remainingCents?.let(Money::formatCents) ?: "未知")
            append("；范围：")
            append(s.startDate)
            append(" 至 ")
            append(s.endDate)
        }
    }

    override suspend fun expensesBetween(start: LocalDate, end: LocalDate): List<ExpenseEntity> =
        budget.getExpensesBetween(start, end)

    override suspend fun dietBetween(start: LocalDate, end: LocalDate): List<DietRecordEntity> =
        diet.getRecordsBetween(start, end)
}
