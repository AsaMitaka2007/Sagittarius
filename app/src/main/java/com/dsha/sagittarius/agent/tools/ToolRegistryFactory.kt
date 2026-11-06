package com.dsha.sagittarius.agent.tools

import android.content.Context
import com.dsha.sagittarius.agent.debug.AgentDebugLogger
import com.dsha.sagittarius.agent.debug.NoOpAgentDebugLogger
import com.dsha.sagittarius.agent.tools.ports.AppSettingsPort
import com.dsha.sagittarius.agent.tools.ports.BudgetRepositoryExpensePort
import com.dsha.sagittarius.agent.tools.ports.DatabaseQueryPort
import com.dsha.sagittarius.agent.tools.ports.DietRepositoryDietPort
import com.dsha.sagittarius.data.BudgetRepository
import com.dsha.sagittarius.data.CnFoodRepository
import com.dsha.sagittarius.data.DietRepository
import java.time.Clock

object ToolRegistryFactory {
    fun default(
        context: Context,
        budgetRepository: BudgetRepository,
        dietRepository: DietRepository,
        clock: Clock = Clock.systemDefaultZone(),
        logger: AgentDebugLogger = NoOpAgentDebugLogger,
        bochaKeyProvider: suspend () -> String? = { null }
    ): ToolRegistry {
        val expense = BudgetRepositoryExpensePort(budgetRepository)
        val diet = DietRepositoryDietPort(dietRepository)
        val prefs = (context.applicationContext as com.dsha.sagittarius.SagittariusApp).sagittariusPrefs
        val systemPort = com.dsha.sagittarius.agent.tools.ports.AndroidSystemPort(context, prefs)
        val queryPort = DatabaseQueryPort(budgetRepository, dietRepository)
        val cnRepository = CnFoodRepository.get(context)
        val cnSource = object : CnFoodSource {
            override suspend fun search(query: String, limit: Int) = cnRepository.search(query, limit)
            override suspend fun getById(id: Long) = cnRepository.getById(id)
        }
        return ToolRegistry().apply {
            register(AddExpenseTool(expense, clock))
            register(UpdateExpenseTool(expense))
            register(ListCategoriesTool(expense))
            register(ManageCategoryTool(expense))
            register(DeleteExpenseTool(expense))
            register(SetBudgetTool(expense))
            register(AllocateBudgetTreeTool(expense))
            register(AddDietTool(diet, clock))
            register(AddFoodToLibraryTool(diet))
            register(ManageFoodCategoryTool(diet))
            register(SearchFoodCnLocalTool(cnSource))
            register(GetCnFoodNutritionTool(cnSource))
            register(WebSearchTool(bochaKeyProvider, logger = logger))
            register(ManageFoodLibraryTool(diet))
            register(UpdateAppSettingsTool(AppSettingsPort(context, budgetRepository, dietRepository)))
            register(QueryAppDataTool(expense, queryPort, clock))
            register(QueryDataTool(expense, queryPort, clock))
            register(ToggleNetworkSearchTool(systemPort))
            register(UpdateThemeTool(systemPort))
            register(UpdateDateColorTool(systemPort))
            register(ToggleFloatingWindowTool(systemPort))
            register(NavigateToTabTool(systemPort))
            register(ControlWeatherDebugTool(systemPort))
            register(TogglePetTool(systemPort))
            register(UpdatePetConfigTool(systemPort))
            // register(WebSearchTool(...))
        }
    }
}
