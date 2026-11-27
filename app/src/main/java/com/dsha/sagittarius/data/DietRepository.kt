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

data class DietSyncResult(
    val changedIds: Set<Long>,
    val missingIds: Set<Long>
)

class DietRepository private constructor(context: Context) {

    private val db = AppDatabase.get(context.applicationContext)
    private val foodDao = db.foodDao()
    private val recordDao = db.dietRecordDao()
    private val goalDao = db.nutritionGoalDao()
    private val overrideDao = db.nutritionGoalOverrideDao()
    private val unitDao = db.foodUnitDao()
    private val categoryDao = db.foodCategoryDao()
    private val foodAdminDao = db.foodAdminDao()
    private val dailyGoalDao = db.dailyGoalDao()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val seedMutex = Mutex()
    @Volatile
    private var seeded = false

    val foods: Flow<List<FoodEntity>> = foodDao.observeAll()
    val goal: Flow<NutritionGoalEntity?> = goalDao.observe()
    val categories: Flow<List<FoodCategoryEntity>> = categoryDao.observeAll()
    val dailyGoals: Flow<List<DailyGoalEntity>> = dailyGoalDao.observeAll()

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
            FoodCategoryEntity(categoryKey = "FRUIT_VEG", name = "果蔬", isPreset = true, sortOrder = 0),
            FoodCategoryEntity(categoryKey = "MEAT_EGG", name = "肉禽蛋", isPreset = true, sortOrder = 1),
            FoodCategoryEntity(categoryKey = "STAPLE", name = "粮油调味", isPreset = true, sortOrder = 2),
            FoodCategoryEntity(categoryKey = "SNACK", name = "零食", isPreset = true, sortOrder = 3),
            FoodCategoryEntity(categoryKey = "SUPPLEMENT", name = "补剂", isPreset = true, sortOrder = 4),
            FoodCategoryEntity(categoryKey = "COMBO", name = "套餐", isPreset = true, sortOrder = 5)
        )
        categoryDao.insertAll(categorySeed)
        // 旧版本的预置分类改名，保持 foods.category 的 key 不变。
        categoryDao.getAll().forEach { category ->
            val newName = when (category.categoryKey) {
                "FRUIT_VEG" -> "果蔬"
                "STAPLE" -> "粮油调味"
                "MEAT_EGG" -> "肉禽蛋"
                "SNACK" -> "零食"
                else -> null
            }
            if (category.isPreset && newName != null && category.name != newName) {
                categoryDao.update(category.copy(name = newName))
            }
        }
        // 旧“其他”没有食材时移除，保证默认分类只有六类。
        categoryDao.getAll().firstOrNull { it.categoryKey == "OTHER" && it.isPreset }
            ?.takeIf { categoryDao.foodCount("OTHER") == 0 }
            ?.let { categoryDao.deleteById(it.id) }
        FoodCatalog.extras.forEach { extra ->
            if (foodDao.getAll().none { it.name == extra.name }) {
                val id = foodDao.insert(extra.copy(id = 0))
                if (id > 0) {
                    unitDao.insert(FoodUnitEntity(foodId = id, unitName = "g", weightInGrams = 1.0))
                    if (extra.unit != "g" && extra.weightPerUnit > 0) {
                        unitDao.insert(
                            FoodUnitEntity(
                                foodId = id,
                                unitName = extra.unit,
                                weightInGrams = extra.weightPerUnit
                            )
                        )
                    }
                }
            }
        }
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

    suspend fun searchFoods(query: String, limit: Int = 20): List<FoodEntity> {
        ensureSeeded()
        val keyword = query.trim()
        return foodDao.getAll()
            .filter { keyword.isEmpty() || it.name.contains(keyword, ignoreCase = true) }
            .take(limit.coerceIn(1, 100))
    }

    suspend fun deleteRecord(record: DietRecordEntity) {
        recordDao.delete(record)
    }

    suspend fun getDietRecord(id: Long): DietRecordEntity? = recordDao.getById(id)

    suspend fun updateDietRecord(record: DietRecordEntity) = recordDao.update(record)

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

    suspend fun foodIds(): Set<Long> = foodDao.getAll().mapTo(mutableSetOf()) { it.id }

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

    /** 拖拽后按顺序写回 sortOrder。 */
    suspend fun reorderFoods(orderedIds: List<Long>) {
        db.withTransaction {
            orderedIds.forEachIndexed { index, id -> foodDao.setSortOrder(id, index) }
        }
    }

    suspend fun moveFood(foodId: Long, targetCategory: String): Boolean {
        val food = foodDao.getById(foodId) ?: return false
        val next = foodDao.getAll()
            .filter { it.category == targetCategory }
            .maxOfOrNull { it.sortOrder }
            ?.plus(1) ?: 0
        foodDao.update(food.copy(category = targetCategory, sortOrder = next))
        return true
    }

    suspend fun copyFood(foodId: Long, newName: String, targetCategory: String? = null): FoodEntity? {
        ensureSeeded()
        val source = foodDao.getById(foodId) ?: return null
        val category = targetCategory ?: source.category
        val next = foodDao.getAll().filter { it.category == category }.maxOfOrNull { it.sortOrder }?.plus(1) ?: 0
        val copy = source.copy(
            id = 0,
            name = newName,
            category = category,
            sortOrder = next,
            isCustom = true,
            favorite = false
        )
        val id = foodDao.insert(copy)
        val saved = if (id > 0) foodDao.getById(id) else null
        if (saved != null) {
            val units = unitDao.getForFood(source.id)
            units.forEach { unitDao.insert(it.copy(id = 0, foodId = saved.id)) }
        }
        return saved
    }

    suspend fun reorderCategories(orderedKeys: List<String>) {
        db.withTransaction {
            orderedKeys.forEachIndexed { index, key -> categoryDao.setSortOrder(key, index) }
        }
    }

    /** 把历史饮食的营养快照同步到当前食材库。 */
    suspend fun syncDietRecords(records: List<DietRecordEntity>): DietSyncResult {
        if (records.isEmpty()) return DietSyncResult(emptySet(), emptySet())
        val foods = foodDao.getAll().associateBy { it.id }
        val changed = mutableSetOf<Long>()
        val missing = mutableSetOf<Long>()
        val updates = mutableListOf<DietRecordEntity>()
        records.forEach { record ->
            val food = foods[record.foodId]
            if (food == null) {
                missing += record.id
            } else {
                val n = NutritionMath.scale(food, record.grams)
                val next = record.copy(
                    foodName = food.name,
                    calories = n.calories,
                    carbs = n.carbs,
                    fat = n.fat,
                    protein = n.protein,
                    fiber = n.fiber,
                    vitamin = n.vitamin,
                    water = n.water,
                    sodium = n.sodium,
                    vitaminA = n.vitaminA,
                    vitaminB1 = n.vitaminB1,
                    vitaminB2 = n.vitaminB2,
                    vitaminB3 = n.vitaminB3,
                    vitaminC = n.vitaminC,
                    vitaminE = n.vitaminE,
                    imageUri = food.imageUri
                )
                if (next != record) {
                    changed += record.id
                    updates += next
                }
            }
        }
        if (updates.isNotEmpty()) {
            db.withTransaction { recordDao.updateAll(updates) }
        }
        return DietSyncResult(changed, missing)
    }

    suspend fun dailyGoalsList(): List<DailyGoalEntity> = dailyGoalDao.getAll()

    suspend fun addDailyGoal(title: String, nutrientKey: String, amount: Double): DailyGoalEntity {        val next = dailyGoalDao.getAll().maxOfOrNull { it.sortOrder }?.plus(1) ?: 0
        val goal = DailyGoalEntity(
            title = title.trim(),
            nutrientKey = nutrientKey,
            amount = amount,
            sortOrder = next
        )
        val id = dailyGoalDao.insert(goal)
        return goal.copy(id = id)
    }

    suspend fun updateDailyGoal(goal: DailyGoalEntity) = dailyGoalDao.update(goal)

    suspend fun deleteDailyGoal(goal: DailyGoalEntity) = dailyGoalDao.delete(goal)

    suspend fun setDailyGoalCompleted(id: Long, completed: Boolean) {
        dailyGoalDao.setCompleted(id, if (completed) LocalDate.now().toString() else null)
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
