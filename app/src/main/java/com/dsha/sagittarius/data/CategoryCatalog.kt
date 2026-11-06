package com.dsha.sagittarius.data

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import com.dsha.sagittarius.R

/**
 * 仅负责系统分类的首次种子数据与图标/颜色展示，不参与任何分类校验。
 * 运行时的唯一分类来源是 Room categories 表。
 */
object CategoryCatalog {

    private data class Seed(
        val id: Long,
        val systemKey: String,
        val name: String,
        val iconKey: String,
        val sortOrder: Int
    )

    private val seeds = listOf(
        Seed(1L, "FOOD", "餐饮", "food", 1),
        Seed(2L, "TRANSPORT", "交通", "transport", 2),
        Seed(3L, "SHOPPING", "购物", "shopping", 3),
        Seed(4L, "ENTERTAINMENT", "娱乐", "entertainment", 4),
        Seed(5L, "HOUSING", "居住", "housing", 5),
        Seed(6L, "MEDICAL", "医疗", "medical", 6),
        Seed(7L, "EDUCATION", "教育", "education", 7),
        Seed(8L, "OTHER", "其他", "other", 8),
        Seed(9L, "DAILY", "日用", "daily", 9),
        Seed(10L, "BOOKS", "书籍", "books", 10),
        Seed(11L, "GAMES", "游戏", "games", 11)
    )

    val presetEntities: List<CategoryEntity>
        get() = seeds.map {
            CategoryEntity(
                id = it.id,
                systemKey = it.systemKey,
                name = it.name,
                iconKey = it.iconKey,
                color = 0,
                isCustom = false,
                sortOrder = it.sortOrder,
                createdAt = 0L,
                type = "expense",
                parentId = null,
                isSystem = true
            )
        }

    fun iconKey(systemKey: String): String = seeds.firstOrNull { it.systemKey == systemKey }?.iconKey ?: "other"

    @DrawableRes
    fun iconRes(iconKey: String?): Int = when (iconKey) {
        "food" -> R.drawable.ic_category_food
        "transport" -> R.drawable.ic_category_transport
        "shopping" -> R.drawable.ic_category_shopping
        "entertainment" -> R.drawable.ic_category_entertainment
        "housing" -> R.drawable.ic_category_housing
        "medical" -> R.drawable.ic_category_medical
        "education" -> R.drawable.ic_category_education
        "daily" -> R.drawable.ic_category_daily
        "books" -> R.drawable.ic_category_book
        "games" -> R.drawable.ic_category_game
        else -> R.drawable.ic_category_other
    }

    @StringRes
    private fun nameRes(systemKey: String?): Int? = when (systemKey) {
        "FOOD" -> R.string.category_food
        "TRANSPORT" -> R.string.category_transport
        "SHOPPING" -> R.string.category_shopping
        "ENTERTAINMENT" -> R.string.category_entertainment
        "HOUSING" -> R.string.category_housing
        "MEDICAL" -> R.string.category_medical
        "EDUCATION" -> R.string.category_education
        "DAILY" -> R.string.category_daily
        "BOOKS" -> R.string.category_books
        "GAMES" -> R.string.category_games
        "OTHER" -> R.string.category_other
        else -> null
    }

    fun displayName(context: Context, category: CategoryEntity): String {
        val res = nameRes(category.systemKey)
        return if (res != null) context.getString(res) else category.name
    }

    fun defaultColor(context: Context, category: CategoryEntity): Int {
        if (category.color != 0) return category.color
        return when (category.systemKey) {
            "FOOD" -> ContextCompat.getColor(context, R.color.cat_food)
            "TRANSPORT" -> ContextCompat.getColor(context, R.color.cat_transport)
            "SHOPPING" -> ContextCompat.getColor(context, R.color.cat_shopping)
            "ENTERTAINMENT" -> ContextCompat.getColor(context, R.color.cat_entertainment)
            "HOUSING" -> ContextCompat.getColor(context, R.color.cat_housing)
            "MEDICAL" -> ContextCompat.getColor(context, R.color.cat_medical)
            "EDUCATION" -> ContextCompat.getColor(context, R.color.cat_education)
            "DAILY" -> ContextCompat.getColor(context, R.color.cat_daily)
            "BOOKS" -> ContextCompat.getColor(context, R.color.cat_books)
            "GAMES" -> ContextCompat.getColor(context, R.color.cat_games)
            else -> fallbackColor(context)
        }
    }

    fun emoji(category: CategoryEntity): String = when (category.systemKey) {
        "FOOD" -> "🍜"
        "TRANSPORT" -> "🚇"
        "SHOPPING" -> "🛍"
        "ENTERTAINMENT" -> "🎬"
        "HOUSING" -> "🏠"
        "MEDICAL" -> "💊"
        "EDUCATION" -> "📚"
        "DAILY" -> "🧴"
        "BOOKS" -> "📖"
        "GAMES" -> "🎮"
        else -> "✨"
    }

    fun fallbackColor(context: Context): Int = ContextCompat.getColor(context, R.color.cat_other)

    fun fallbackEntity(): CategoryEntity = CategoryEntity(
        id = 8L,
        systemKey = "OTHER",
        name = "其他",
        iconKey = "other",
        color = 0,
        isCustom = false,
        sortOrder = 8,
        type = "expense",
        parentId = null,
        isSystem = true
    )

    data class IconChoice(val key: String, @DrawableRes val iconRes: Int)

    fun customIconChoices(): List<IconChoice> = listOf(
        IconChoice("other", R.drawable.ic_category_other),
        IconChoice("food", R.drawable.ic_category_food),
        IconChoice("daily", R.drawable.ic_category_daily),
        IconChoice("shopping", R.drawable.ic_category_shopping),
        IconChoice("transport", R.drawable.ic_category_transport),
        IconChoice("entertainment", R.drawable.ic_category_entertainment),
        IconChoice("education", R.drawable.ic_category_education),
        IconChoice("books", R.drawable.ic_category_book),
        IconChoice("games", R.drawable.ic_category_game)
    )

    fun customColorChoices(context: Context): List<Int> = listOf(
        ContextCompat.getColor(context, R.color.cat_food),
        ContextCompat.getColor(context, R.color.cat_transport),
        ContextCompat.getColor(context, R.color.cat_shopping),
        ContextCompat.getColor(context, R.color.cat_entertainment),
        ContextCompat.getColor(context, R.color.cat_housing),
        ContextCompat.getColor(context, R.color.cat_medical),
        ContextCompat.getColor(context, R.color.cat_education),
        ContextCompat.getColor(context, R.color.cat_daily),
        ContextCompat.getColor(context, R.color.cat_books),
        ContextCompat.getColor(context, R.color.cat_games)
    )

    fun seedColors(context: Context): Map<String, Int> =
        seeds.associate { it.systemKey to defaultColor(context, presetEntities.first { e -> e.systemKey == it.systemKey }) }
}
