package com.dsha.sagittarius.agent.tools

import com.dsha.sagittarius.data.CnFoodEntity
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CnFoodToolTest {
    private val tofu = CnFoodEntity(
        id = 285,
        name = "豆腐",
        energyKcal = 81.0,
        proteinG = 8.1,
        fatG = 3.7,
        carbohydrateG = 3.8,
        fiberG = 0.4,
        waterG = 82.8
    )
    private val source = object : CnFoodSource {
        override suspend fun search(query: String, limit: Int) =
            if ("豆腐".contains(query)) listOf(tofu) else emptyList()
        override suspend fun getById(id: Long) = tofu.takeIf { it.id == id }
    }

    @Test fun searchReturnsLocalNutritionWithoutHttp() = runBlocking {
        val result = SearchFoodCnLocalTool(source).execute(
            Json.parseToJsonElement("""{"query":"豆腐"}""").jsonObject
        )
        assertTrue(result.ok)
        assertTrue(result.contentForModel.contains("豆腐"))
        assertTrue(result.contentForModel.contains("8.1"))
    }

    @Test fun searchMissReturnsSystemPromptAndFullNutritionWorks() = runBlocking {
        val miss = SearchFoodCnLocalTool(source).execute(
            Json.parseToJsonElement("""{"query":"不存在"}""").jsonObject
        )
        assertFalse(miss.ok)
        assertTrue(miss.contentForModel.contains("本地中文营养库未找到"))
        val full = GetCnFoodNutritionTool(source).execute(
            Json.parseToJsonElement("""{"food_id":285}""").jsonObject
        )
        assertTrue(full.ok)
        assertTrue(full.contentForModel.contains("selenium"))
    }
}
