package com.dsha.sagittarius.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/** 只读 cn-food-mcp 字典；首次启动从 assets/cn_food.json 批量灌入。 */
class CnFoodRepository private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val dao = AppDatabase.get(appContext).cnFoodDao()
    private val mutex = Mutex()
    @Volatile
    private var seeded = false

    suspend fun ensureSeeded() {
        if (seeded) return
        mutex.withLock {
            if (seeded) return
            if (dao.count() == 0) {
                val items = withContext(Dispatchers.IO) { parseAssets() }
                if (items.isNotEmpty()) dao.insertAll(items)
            }
            seeded = true
        }
    }

    suspend fun search(query: String, limit: Int = 3): List<CnFoodEntity> {
        ensureSeeded()
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()
        return dao.search(trimmed, limit)
    }

    suspend fun getById(id: Long): CnFoodEntity? {
        ensureSeeded()
        return dao.getById(id)
    }

    private fun parseAssets(): List<CnFoodEntity> {
        val raw = runCatching {
            appContext.assets.open("cn_food.json").bufferedReader().use { it.readText() }
        }.getOrNull() ?: return emptyList()
        val array = runCatching { Json.parseToJsonElement(raw) as? JsonArray }.getOrNull() ?: return emptyList()
        return array.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val id = obj["id"]?.jsonPrimitive?.longOrNull ?: return@mapNotNull null
            val name = obj["name"]?.jsonPrimitive?.content?.trim().orEmpty()
            if (name.isEmpty()) return@mapNotNull null
            CnFoodEntity(
                id = id,
                name = name,
                energyKcal = d(obj, "energy"),
                proteinG = d(obj, "protein"),
                fatG = d(obj, "fat"),
                carbohydrateG = d(obj, "carbohydrate"),
                fiberG = d(obj, "fiber"),
                waterG = d(obj, "water"),
                ashG = d(obj, "ash"),
                vitaminA = d(obj, "vitamin_a"),
                carotene = d(obj, "carotene"),
                retinolEquivalent = d(obj, "retinol_equivalent"),
                vitaminB1 = d(obj, "vitamin_b1"),
                vitaminB2 = d(obj, "vitamin_b2"),
                niacin = d(obj, "niacin"),
                vitaminC = d(obj, "vitamin_c"),
                vitaminE = d(obj, "vitamin_e"),
                potassium = d(obj, "potassium"),
                sodium = d(obj, "sodium"),
                calcium = d(obj, "calcium"),
                magnesium = d(obj, "magnesium"),
                iron = d(obj, "iron"),
                manganese = d(obj, "manganese"),
                zinc = d(obj, "zinc"),
                copper = d(obj, "copper"),
                phosphorus = d(obj, "phosphorus"),
                selenium = d(obj, "selenium")
            )
        }
    }

    private fun d(obj: JsonObject, key: String): Double =
        obj[key]?.jsonPrimitive?.doubleOrNull ?: 0.0

    companion object {
        @Volatile
        private var instance: CnFoodRepository? = null

        fun get(context: Context): CnFoodRepository =
            instance ?: synchronized(this) {
                instance ?: CnFoodRepository(context).also { instance = it }
            }
    }
}
