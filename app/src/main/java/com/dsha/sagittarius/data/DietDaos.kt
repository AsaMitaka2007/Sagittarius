package com.dsha.sagittarius.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface FoodDao {
    @Query("SELECT * FROM foods ORDER BY sortOrder ASC, id ASC")
    fun observeAll(): Flow<List<FoodEntity>>

    @Query("SELECT * FROM foods ORDER BY sortOrder ASC, id ASC")
    suspend fun getAll(): List<FoodEntity>

    @Query("SELECT COUNT(*) FROM foods")
    suspend fun count(): Int

    @Query("SELECT * FROM foods WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): FoodEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(foods: List<FoodEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(food: FoodEntity): Long

    @Query("UPDATE foods SET favorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: Long, favorite: Boolean)

    @Query("UPDATE foods SET sortOrder = :order WHERE id = :id")
    suspend fun setSortOrder(id: Long, order: Int)

    @Update
    suspend fun update(food: FoodEntity)

    @Delete
    suspend fun delete(food: FoodEntity)
}

@Dao
interface DietRecordDao {
    @Query("SELECT * FROM diet_records WHERE date = :date ORDER BY mealType ASC, createdAt ASC")
    fun observeByDate(date: LocalDate): Flow<List<DietRecordEntity>>

    @Query("SELECT * FROM diet_records ORDER BY date DESC, createdAt DESC")
    fun observeAll(): Flow<List<DietRecordEntity>>

    @Query("SELECT * FROM diet_records WHERE date BETWEEN :start AND :end ORDER BY date DESC, createdAt DESC")
    fun observeBetween(start: LocalDate, end: LocalDate): Flow<List<DietRecordEntity>>

    @Query("SELECT * FROM diet_records WHERE date BETWEEN :start AND :end ORDER BY date DESC, createdAt DESC")
    suspend fun getBetween(start: LocalDate, end: LocalDate): List<DietRecordEntity>

    @Query("SELECT * FROM diet_records WHERE date = :date ORDER BY mealType ASC, createdAt ASC")
    suspend fun getByDate(date: LocalDate): List<DietRecordEntity>

    @Insert
    suspend fun insertAll(records: List<DietRecordEntity>)

    @Insert
    suspend fun insert(record: DietRecordEntity): Long

    @Update
    suspend fun update(record: DietRecordEntity)

    @Update
    suspend fun updateAll(records: List<DietRecordEntity>)

    @Delete
    suspend fun delete(record: DietRecordEntity)
}

@Dao
interface NutritionGoalDao {
    @Query("SELECT * FROM nutrition_goals WHERE id = 1 LIMIT 1")
    fun observe(): Flow<NutritionGoalEntity?>

    @Query("SELECT * FROM nutrition_goals WHERE id = 1 LIMIT 1")
    suspend fun get(): NutritionGoalEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(goal: NutritionGoalEntity)
}


@Dao
interface NutritionGoalOverrideDao {
    @Query(
        "SELECT * FROM nutrition_goal_overrides " +
            "WHERE startDate <= :date AND endDate >= :date ORDER BY startDate DESC LIMIT 1"
    )
    fun observeForDate(date: LocalDate): Flow<NutritionGoalOverrideEntity?>

    @Query(
        "SELECT * FROM nutrition_goal_overrides " +
            "WHERE startDate <= :date AND endDate >= :date ORDER BY startDate DESC LIMIT 1"
    )
    suspend fun getForDate(date: LocalDate): NutritionGoalOverrideEntity?

    @Query("SELECT * FROM nutrition_goal_overrides ORDER BY startDate ASC")
    fun observeAll(): Flow<List<NutritionGoalOverrideEntity>>

    @Insert
    suspend fun insert(override: NutritionGoalOverrideEntity): Long

    @Delete
    suspend fun delete(override: NutritionGoalOverrideEntity)
}

@Dao
interface FoodUnitDao {
    @Query("SELECT * FROM food_units WHERE foodId = :foodId ORDER BY id ASC")
    fun observeForFood(foodId: Long): Flow<List<FoodUnitEntity>>

    @Query("SELECT * FROM food_units WHERE foodId = :foodId ORDER BY id ASC")
    suspend fun getForFood(foodId: Long): List<FoodUnitEntity>

    @Insert
    suspend fun insert(unit: FoodUnitEntity): Long

    @Update
    suspend fun update(unit: FoodUnitEntity)

    @Query("DELETE FROM food_units WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM food_units WHERE foodId = :foodId")
    suspend fun deleteForFood(foodId: Long)
}

@Dao
interface FoodCategoryDao {
    @Query("SELECT * FROM food_categories ORDER BY sortOrder ASC, id ASC")
    fun observeAll(): Flow<List<FoodCategoryEntity>>

    @Query("SELECT * FROM food_categories ORDER BY sortOrder ASC, id ASC")
    suspend fun getAll(): List<FoodCategoryEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(categories: List<FoodCategoryEntity>)

    @Insert
    suspend fun insert(category: FoodCategoryEntity): Long

    @Update
    suspend fun update(category: FoodCategoryEntity)

    @Query("DELETE FROM food_categories WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COUNT(*) FROM foods WHERE category = :categoryKey")
    suspend fun foodCount(categoryKey: String): Int
}

@Dao
interface FoodAdminDao {
    @Query("DELETE FROM foods WHERE id = :id")
    suspend fun deleteFood(id: Long)

    @Query("SELECT COUNT(*) FROM diet_records WHERE foodId = :foodId")
    suspend fun recordCount(foodId: Long): Int
}
