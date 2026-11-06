package com.dsha.sagittarius.data

import androidx.room.TypeConverter
import java.time.LocalDate

class Converters {
    @TypeConverter
    fun localDateToEpochDay(value: LocalDate?): Long? = value?.toEpochDay()

    @TypeConverter
    fun epochDayToLocalDate(value: Long?): LocalDate? = value?.let(LocalDate::ofEpochDay)

    @TypeConverter
    fun rangeTypeToString(value: BudgetRangeType?): String? = value?.name

    @TypeConverter
    fun stringToRangeType(value: String?): BudgetRangeType? =
        value?.let(BudgetRangeType::fromStorage)

    @TypeConverter
    fun mealTypeToString(value: MealType?): String? = value?.name

    @TypeConverter
    fun stringToMealType(value: String?): MealType? = MealType.fromStorage(value)
}
