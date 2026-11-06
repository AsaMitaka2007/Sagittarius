package com.dsha.sagittarius.agent.tools.ports

import com.dsha.sagittarius.data.ActiveBudgetSnapshot
import com.dsha.sagittarius.data.CategoryEntity
import com.dsha.sagittarius.data.DietRecordEntity
import com.dsha.sagittarius.data.ExpenseEntity
import com.dsha.sagittarius.data.FoodEntity
import com.dsha.sagittarius.data.FoodUnitEntity
import com.dsha.sagittarius.data.NutritionGoalEntity
import com.dsha.sagittarius.data.BudgetNodeEntity
import com.dsha.sagittarius.data.BudgetNodeResult
import com.dsha.sagittarius.data.BudgetRangeType
import com.dsha.sagittarius.data.FoodCategoryEntity
import com.dsha.sagittarius.data.ExpenseEntity as Expense
import java.time.LocalDate

interface ExpensePort {
    suspend fun categories(): List<CategoryEntity>
    suspend fun addExpense(amountCents: Long, date: LocalDate, categoryId: Long, note: String?): Long
    suspend fun expensesBetween(start: LocalDate, end: LocalDate): List<Expense>
    suspend fun activeSnapshot(): ActiveBudgetSnapshot
    suspend fun setBudget(amountCents: Long): Boolean
    suspend fun getExpense(id: Long): ExpenseEntity? = null
    suspend fun updateExpense(expense: ExpenseEntity) = Unit
    suspend fun deleteExpense(expense: ExpenseEntity) = Unit
    suspend fun budgetNodes(): List<BudgetNodeEntity> = emptyList()
    suspend fun addBudgetNode(parentId: Long?, name: String, amountCents: Long, categoryId: Long?): BudgetNodeResult =
        BudgetNodeResult.NoBudget
    suspend fun updateBudgetNode(nodeId: Long, name: String, amountCents: Long, categoryId: Long?): BudgetNodeResult =
        BudgetNodeResult.NoBudget
    suspend fun setActiveRange(type: BudgetRangeType, start: LocalDate?, end: LocalDate?) = Unit
    suspend fun addCategory(name: String): CategoryEntity? = null
}

interface DietPort {
    suspend fun ensureSeeded()
    suspend fun findFoodByName(name: String): FoodEntity?
    suspend fun getUnits(foodId: Long): List<FoodUnitEntity>
    suspend fun addRecord(record: DietRecordEntity): Long
    suspend fun addFood(food: FoodEntity): FoodEntity?
    suspend fun updateFood(food: FoodEntity)
    suspend fun deleteFood(food: FoodEntity): Boolean
    suspend fun recordsBetween(start: LocalDate, end: LocalDate): List<DietRecordEntity>
    suspend fun getGoal(): NutritionGoalEntity?
    suspend fun saveGoal(goal: NutritionGoalEntity)
    suspend fun addUnit(foodId: Long, unitName: String, weightInGrams: Double): Boolean = false
    suspend fun getFoodCategories(): List<FoodCategoryEntity> = emptyList()
    suspend fun addFoodCategory(name: String): FoodCategoryEntity? = null
    suspend fun deleteFoodCategory(name: String): Boolean = false
}

interface SettingsPort {
    suspend fun setTheme(value: String): String
    suspend fun setDailyBudget(amountCents: Long): Boolean
    suspend fun setNutritionGoal(value: String): String
}

interface SystemPort {
    suspend fun toggleNetworkSearch(enabled: Boolean): Boolean
    suspend fun updateTheme(theme: String): String
    suspend fun updateDateColor(color: String): String
    suspend fun toggleFloatingWindow(enabled: Boolean): Boolean
    suspend fun navigateToTab(tab: String): String
    suspend fun controlWeather(type: String): String
    suspend fun setPetVisible(visible: Boolean)
    suspend fun setPetTextColor(color: Int)
    suspend fun setPetScale(percent: Int)
    suspend fun setPetLocked(locked: Boolean)
}

interface QueryPort {
    suspend fun budgetSummary(): String
    suspend fun expensesBetween(start: LocalDate, end: LocalDate): List<ExpenseEntity>
    suspend fun dietBetween(start: LocalDate, end: LocalDate): List<DietRecordEntity>
}
