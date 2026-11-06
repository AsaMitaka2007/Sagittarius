package com.dsha.sagittarius.data

sealed class CategoryResult {
    data class Success(val category: CategoryEntity) : CategoryResult()
    data object InvalidName : CategoryResult()
    data object ReadOnly : CategoryResult()
    data object InUse : CategoryResult()
}

sealed class BudgetNodeResult {
    data class Success(val id: Long) : BudgetNodeResult()
    data object NoBudget : BudgetNodeResult()
    data object InvalidAmount : BudgetNodeResult()
    data object InvalidDepth : BudgetNodeResult()
    data class OverAllocated(val availableCents: Long) : BudgetNodeResult()
    data class BelowChildren(val requiredCents: Long) : BudgetNodeResult()
}
