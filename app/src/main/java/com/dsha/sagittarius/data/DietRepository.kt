package com.dsha.sagittarius.data

import android.content.Context
import androidx.room.withTransaction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate

class DietRepository private constructor(context: Context) {

    private val db = AppDatabase.get(context.applicationContext)
    private val foodDao = db.foodDao()
    private val recordDao = db.dietRecordDao()
    private val goalDao = db.nutritionGoalDao()
    private val overrideDao = db.nutritionGoalOverrideDao()
    private val unitDao = db.foodUnitDao()
    private val categoryDao = db.foodCategoryDao()
    private val foodAdminDao = db.foodAdminDao()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val seedMutex = Mutex()
    @Volatile
    private var seeded = false

    val foods: Flow<List<FoodEntity>> = foodDao.observeAll()
    val goal: Flow<NutritionGoalEntity?> = goalDao.observe()
    val categories: Flow<List<FoodCategoryEntity>> = categoryDao.observeAll()

    init {
        scope.launch {
            seedMutex.withLock { ensureSeededInternal() }
        }
    }

    private suspend fun ensureSeededInternal() {
        if (seeded) return
        if (foodDao.count() == 0) {
            foodDao.insertAll(FoodCatalog.presets)
        }
        val categorySeed = listOf(
            FoodCategoryEntity(categoryKey = "FRUIT_VEG", name = "水果蔬菜", isPreset = true, sortOrder = 0),
            FoodCategoryEntity(categoryKey = "MEAT_EGG", name = "肉禽蛋", isPreset = true, sortOrder = 1),
            FoodCategoryEntity(categoryKey = "STAPLE", name = "面食", isPreset = true, sortOrder = 2),
            FoodCategoryEntity(categoryKey = "SNACK", name = "零食", isPreset = true, sortOrder = 3),
            FoodCategoryEntity(categoryKey = "OTHER", name = "其他", isPreset = true, sortOrder = 4)
        )
        categoryDao.insertAll(categorySeed)
        foodDao.getAll().forEach { food ->
            if (unitDao.getForFood(food.id).isEmpty()) {
                unitDao.insert(FoodUnitEntity(foodId = food.id, unitName = "g", weightInGrams = 1.0))
            }
        }
        if (goalDao.get() == null) {
            goalDao.upsert(NutritionGoalEntity())
        }
        seeded = true
    }

    fun observeActiveGoal(date: LocalDate): Flow<NutritionGoalEntity> =
        combine(goalDao.observe(), overrideDao.observeForDate(date)) { base, override ->
            override?.toGoal() ?: base ?: NutritionGoalEntity()
        }

    fun observeRecords(date: LocalDate): Flow<List<DietRecordEntity>> =
        recordDao.observeByDate(date)

    fun observeAllRecords(): Flow<List<DietRecordEntity>> = recordDao.observeAll()

    fun observeRecordsBetween(start: LocalDate, end: LocalDate): Flow<List<DietRecordEntity>> =
        recordDao.observeBetween(start, end)

    suspend fun getRecordsBetween(start: LocalDate, end: LocalDate): List<DietRecordEntity> =
        recordDao.getBetween(start, end)

    suspend fun getGoal(): NutritionGoalEntity? = goalDao.get()

    suspend fun ensureSeeded() {
        seedMutex.withLock { ensureSeededInternal() }
    }

    suspend fun addRecords(records: List<DietRecordEntity>) {
        if (records.isEmpty()) return
        ensureSeeded()
        db.withTransaction { recordDao.insertAll(records) }
    }

    suspend fun addRecord(record: DietRecordEntity): Long {
        ensureSeeded()
        return recordDao.insert(record)
    }

    suspend fun findFoodByName(name: String): FoodEntity? {
        ensureSeeded()
        val target = name.trim()
        return foodDao.getAll().firstOrNull { it.name == target }
    }

    suspend fun deleteRecord(record: DietRecordEntity) {
        recordDao.delete(record)
    }

    suspend fun updateRecords(records: List<DietRecordEntity>) {
        if (records.isEmpty()) return
        db.withTransaction { recordDao.updateAll(records) }
    }

    suspend fun saveGoal(goal: NutritionGoalEntity) {
        goalDao.upsert(goal.copy(id = 1))
    }

    suspend fun saveGoalOverride(override: NutritionGoalOverrideEntity) {
        overrideDao.insert(override)
    }

    suspend fun deleteGoalOverride(override: NutritionGoalOverrideEntity) {
        overrideDao.delete(override)
    }

    fun observeGoalOverrides(): Flow<List<NutritionGoalOverrideEntity>> = overrideDao.observeAll()

    suspend fun setFavorite(food: FoodEntity, favorite: Boolean) {
        foodDao.setFavorite(food.id, favorite)
    }

    suspend fun reorderFavorites(favorites: List<FoodEntity>) {
        favorites.forEachIndexed { index, food -> foodDao.setSortOrder(food.id, index) }
    }

    suspend fun getFood(id: Long): FoodEntity? = foodDao.getById(id)

    suspend fun updateFood(food: FoodEntity) {
        foodDao.update(food)
    }

    suspend fun addFood(food: FoodEntity): FoodEntity? {
        ensureSeeded()
        val id = foodDao.insert(food.copy(id = 0, isCustom = true))
        val saved = if (id > 0) foodDao.getById(id) else foodDao.getAll().firstOrNull { it.name == food.name }
        if (saved != null) {
            if (unitDao.getForFood(saved.id).isEmpty()) {
                unitDao.insert(FoodUnitEntity(foodId = saved.id, unitName = "g", weightInGrams = 1.0))
            }
        }
        return saved
    }

    fun observeUnits(foodId: Long): Flow<List<FoodUnitEntity>> = unitDao.observeForFood(foodId)

    suspend fun getUnits(foodId: Long): List<FoodUnitEntity> = unitDao.getForFood(foodId)

    suspend fun addUnit(foodId: Long, unitName: String, weightInGrams: Double): Boolean {
        if (unitName.isBlank() || weightInGrams <= 0.0) return false
        val existing = unitDao.getForFood(foodId).firstOrNull { it.unitName == unitName }
        if (existing != null) {
            unitDao.update(existing.copy(weightInGrams = weightInGrams))
        } else {
            unitDao.insert(FoodUnitEntity(foodId = foodId, unitName = unitName, weightInGrams = weightInGrams))
        }
        return true
    }

    suspend fun getFoodCategories(): List<FoodCategoryEntity> = categoryDao.getAll()

    suspend fun addFoodCategory(name: String): FoodCategoryEntity? {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return null
        if (categoryDao.getAll().any { it.name == trimmed }) return null
        val id = categoryDao.insert(
            FoodCategoryEntity(
                categoryKey = "CUSTOM_${System.currentTimeMillis()}",
                name = trimmed,
                isPreset = false,
                sortOrder = (categoryDao.getAll().maxOfOrNull { it.sortOrder } ?: 0) + 1
            )
        )
        return categoryDao.getAll().firstOrNull { it.id == id }
    }

    suspend fun deleteFoodCategory(name: String): Boolean {
        val category = categoryDao.getAll().firstOrNull { it.name == name && !it.isPreset } ?: return false
        if (categoryDao.foodCount(category.categoryKey) > 0) return false
        categoryDao.deleteById(category.id)
        return true
    }

    suspend fun replaceUnits(foodId: Long, units: List<FoodUnitEntity>) {
        unitDao.deleteForFood(foodId)
        unitDao.insert(FoodUnitEntity(foodId = foodId, unitName = "g", weightInGrams = 1.0))
        units.filter { it.unitName != "g" && it.weightInGrams > 0 }
            .forEach { unitDao.insert(it.copy(id = 0, foodId = foodId)) }
    }

    suspend fun deleteFood(food: FoodEntity): Boolean {
        val used = foodAdminDao.recordCount(food.id) > 0
        unitDao.deleteForFood(food.id)
        foodAdminDao.deleteFood(food.id)
        return used
    }

    suspend fun addCategory(name: String, key: String): Long = categoryDao.insert(
        FoodCategoryEntity(categoryKey = key, name = name, isPreset = false, sortOrder = 100)
    )

    suspend fun renameCategory(category: FoodCategoryEntity, name: String) {
        categoryDao.update(category.copy(name = name))
    }

    suspend fun deleteCategory(category: FoodCategoryEntity): Boolean {
        if (categoryDao.foodCount(category.categoryKey) > 0) return false
        categoryDao.deleteById(category.id)
        return true
    }

    suspend fun updateGoal(goal: NutritionGoalEntity) {
        goalDao.upsert(goal)
    }

    companion object {
        @Volatile
        private var instance: DietRepository? = null

        fun getInstance(context: Context): DietRepository =
            instance ?: synchronized(this) {
                instance ?: DietRepository(context.applicationContext).also { instance = it }
            }
    }
}
