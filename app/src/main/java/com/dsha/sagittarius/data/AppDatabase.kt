package com.dsha.sagittarius.data

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.io.File

@Database(
    entities = [
        ExpenseEntity::class,
        BudgetEntity::class,
        AppStateEntity::class,
        CategoryEntity::class,
        BudgetNodeEntity::class,
        FoodEntity::class,
        DietRecordEntity::class,
        NutritionGoalEntity::class,
        NutritionGoalOverrideEntity::class,
        FoodUnitEntity::class,
        FoodCategoryEntity::class,
        ChatHistoryEntity::class,
        CnFoodEntity::class,
        ChatSessionEntity::class
    ],
    version = 9,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun expenseDao(): ExpenseDao
    abstract fun budgetDao(): BudgetDao
    abstract fun appStateDao(): AppStateDao
    abstract fun categoryDao(): CategoryDao
    abstract fun budgetNodeDao(): BudgetNodeDao
    abstract fun foodDao(): FoodDao
    abstract fun dietRecordDao(): DietRecordDao
    abstract fun nutritionGoalDao(): NutritionGoalDao
    abstract fun nutritionGoalOverrideDao(): NutritionGoalOverrideDao
    abstract fun foodUnitDao(): FoodUnitDao
    abstract fun foodCategoryDao(): FoodCategoryDao
    abstract fun foodAdminDao(): FoodAdminDao
    abstract fun chatHistoryDao(): ChatHistoryDao
    abstract fun chatHistoryDeleteDao(): ChatHistoryDeleteDao
    abstract fun cnFoodDao(): CnFoodDao
    abstract fun chatSessionDao(): ChatSessionDao

    companion object {
        private const val DB_NAME = "sagittarius.db"
        private const val LEGACY_DB_NAME = "budget_left.db"

        @Volatile
        private var instance: AppDatabase? = null

        /**
         * 改名兼容：同包内若还留着旧的 budget_left.db，首次打开新库前原样复制。
         * 跨包名（applicationId 变更）时旧数据在旧包沙箱，系统不允许读取，这里会自然跳过。
         */
        private fun migrateLegacyDatabase(context: Context) {
            runCatching {
                val legacy = context.getDatabasePath(LEGACY_DB_NAME)
                val current = context.getDatabasePath(DB_NAME)
                if (!legacy.exists() || current.exists()) return@runCatching
                current.parentFile?.mkdirs()
                legacy.copyTo(current, overwrite = false)
                listOf("-wal", "-shm").forEach { suffix ->
                    val source = File(legacy.path + suffix)
                    if (source.exists()) {
                        source.copyTo(File(current.path + suffix), overwrite = true)
                    }
                }
            }.onFailure {
                Log.e("Sagittarius", "legacy database migration failed", it)
            }
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `categories` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `systemKey` TEXT,
                        `name` TEXT NOT NULL,
                        `iconKey` TEXT NOT NULL,
                        `color` INTEGER NOT NULL,
                        `isCustom` INTEGER NOT NULL,
                        `sortOrder` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_categories_systemKey` ON `categories` (`systemKey`)"
                )
                val defaults = listOf(
                    Triple(1L, "FOOD", "餐饮"),
                    Triple(2L, "TRANSPORT", "交通"),
                    Triple(3L, "SHOPPING", "购物"),
                    Triple(4L, "ENTERTAINMENT", "娱乐"),
                    Triple(5L, "HOUSING", "居住"),
                    Triple(6L, "MEDICAL", "医疗"),
                    Triple(7L, "EDUCATION", "教育"),
                    Triple(8L, "OTHER", "其他"),
                    Triple(9L, "DAILY", "日用"),
                    Triple(10L, "BOOKS", "书籍"),
                    Triple(11L, "GAMES", "游戏")
                )
                val iconKeys = mapOf(
                    "FOOD" to "food", "TRANSPORT" to "transport", "SHOPPING" to "shopping",
                    "ENTERTAINMENT" to "entertainment", "HOUSING" to "housing",
                    "MEDICAL" to "medical", "EDUCATION" to "education", "OTHER" to "other",
                    "DAILY" to "daily", "BOOKS" to "books", "GAMES" to "games"
                )
                defaults.forEach { (id, key, name) ->
                    db.execSQL(
                        "INSERT OR IGNORE INTO categories " +
                            "(id, systemKey, name, iconKey, color, isCustom, sortOrder, createdAt) " +
                            "VALUES (?, ?, ?, ?, 0, 0, ?, 0)",
                        arrayOf<Any>(id, key, name, iconKeys.getValue(key), id)
                    )
                }

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `expenses_new` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `amountCents` INTEGER NOT NULL,
                        `date` INTEGER NOT NULL,
                        `categoryId` INTEGER NOT NULL,
                        `note` TEXT,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `expenses_new`
                        (`id`, `amountCents`, `date`, `categoryId`, `note`, `createdAt`, `updatedAt`)
                    SELECT `id`, `amountCents`, `date`,
                        CASE `category`
                            WHEN 'FOOD' THEN 1
                            WHEN 'TRANSPORT' THEN 2
                            WHEN 'SHOPPING' THEN 3
                            WHEN 'ENTERTAINMENT' THEN 4
                            WHEN 'HOUSING' THEN 5
                            WHEN 'MEDICAL' THEN 6
                            WHEN 'EDUCATION' THEN 7
                            WHEN 'DAILY' THEN 9
                            WHEN 'BOOKS' THEN 10
                            WHEN 'GAMES' THEN 11
                            ELSE 8
                        END,
                        `note`, `createdAt`, `updatedAt`
                    FROM `expenses`
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE `expenses`")
                db.execSQL("ALTER TABLE `expenses_new` RENAME TO `expenses`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_expenses_date` ON `expenses` (`date`)")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_expenses_categoryId` ON `expenses` (`categoryId`)"
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `budget_nodes` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `parentId` INTEGER,
                        `budgetName` TEXT NOT NULL,
                        `amountCents` INTEGER NOT NULL,
                        `categoryId` INTEGER,
                        `sortOrder` INTEGER NOT NULL,
                        `rangeType` TEXT NOT NULL,
                        `startDate` INTEGER NOT NULL,
                        `endDate` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_budget_nodes_parentId` ON `budget_nodes` (`parentId`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_budget_nodes_startDate_endDate` ON `budget_nodes` (`startDate`, `endDate`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_budget_nodes_categoryId` ON `budget_nodes` (`categoryId`)"
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `foods` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `icon` TEXT NOT NULL,
                        `unit` TEXT NOT NULL,
                        `weightPerUnit` REAL NOT NULL,
                        `caloriesPer100g` REAL NOT NULL,
                        `carbsPer100g` REAL NOT NULL,
                        `fatPer100g` REAL NOT NULL,
                        `proteinPer100g` REAL NOT NULL,
                        `fiberPer100g` REAL NOT NULL,
                        `vitaminPer100g` REAL NOT NULL,
                        `waterPer100g` REAL NOT NULL,
                        `isCustom` INTEGER NOT NULL,
                        `sortOrder` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_foods_name` ON `foods` (`name`)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `diet_records` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `foodId` INTEGER NOT NULL,
                        `foodName` TEXT NOT NULL,
                        `icon` TEXT NOT NULL,
                        `mealType` TEXT NOT NULL,
                        `amount` REAL NOT NULL,
                        `unit` TEXT NOT NULL,
                        `grams` REAL NOT NULL,
                        `calories` REAL NOT NULL,
                        `carbs` REAL NOT NULL,
                        `fat` REAL NOT NULL,
                        `protein` REAL NOT NULL,
                        `fiber` REAL NOT NULL,
                        `vitamin` REAL NOT NULL,
                        `water` REAL NOT NULL,
                        `date` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_diet_records_date` ON `diet_records` (`date`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_diet_records_foodId` ON `diet_records` (`foodId`)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `nutrition_goals` (
                        `id` INTEGER NOT NULL,
                        `calories` REAL NOT NULL,
                        `carbs` REAL NOT NULL,
                        `fat` REAL NOT NULL,
                        `protein` REAL NOT NULL,
                        `fiber` REAL NOT NULL,
                        `vitamin` REAL NOT NULL,
                        `water` REAL NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `foods` ADD COLUMN `category` TEXT NOT NULL DEFAULT 'STAPLE'")
                db.execSQL("ALTER TABLE `foods` ADD COLUMN `favorite` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `foods` ADD COLUMN `imageUri` TEXT")
                db.execSQL(
                    "UPDATE `foods` SET `category` = 'FRUIT_VEG' WHERE `name` IN " +
                        "('苹果','香蕉','西兰花','胡萝卜','番茄','黄瓜','菠菜','西瓜','橙子','葡萄','草莓','红薯','紫薯','土豆','玉米','海带')"
                )
                db.execSQL(
                    "UPDATE `foods` SET `category` = 'MEAT_EGG' WHERE `name` IN " +
                        "('鸡胸肉','鸡蛋','牛奶','酸奶','牛肉','猪肉','三文鱼','虾仁','鸡腿','豆腐','豆腐皮','豆浆')"
                )
                db.execSQL(
                    "UPDATE `foods` SET `category` = 'SNACK' WHERE `name` IN " +
                        "('坚果','花生酱','咖啡','全麦面包')"
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `nutrition_goal_overrides` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `startDate` INTEGER NOT NULL,
                        `endDate` INTEGER NOT NULL,
                        `calories` REAL NOT NULL,
                        `carbs` REAL NOT NULL,
                        `fat` REAL NOT NULL,
                        `protein` REAL NOT NULL,
                        `fiber` REAL NOT NULL,
                        `vitamin` REAL NOT NULL,
                        `water` REAL NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_nutrition_goal_overrides_startDate_endDate` ON `nutrition_goal_overrides` (`startDate`, `endDate`)")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `food_units` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `foodId` INTEGER NOT NULL,
                        `unitName` TEXT NOT NULL,
                        `weightInGrams` REAL NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_food_units_foodId` ON `food_units` (`foodId`)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `food_categories` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `categoryKey` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `isPreset` INTEGER NOT NULL,
                        `sortOrder` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_food_categories_categoryKey` ON `food_categories` (`categoryKey`)")
                db.execSQL("INSERT OR IGNORE INTO food_categories (categoryKey, name, isPreset, sortOrder) VALUES ('FRUIT_VEG', '水果蔬菜', 1, 0)")
                db.execSQL("INSERT OR IGNORE INTO food_categories (categoryKey, name, isPreset, sortOrder) VALUES ('MEAT_EGG', '肉禽蛋', 1, 1)")
                db.execSQL("INSERT OR IGNORE INTO food_categories (categoryKey, name, isPreset, sortOrder) VALUES ('STAPLE', '面食', 1, 2)")
                db.execSQL("INSERT OR IGNORE INTO food_categories (categoryKey, name, isPreset, sortOrder) VALUES ('SNACK', '零食', 1, 3)")
                db.execSQL("UPDATE foods SET unit = 'g', weightPerUnit = 1.0")
                db.execSQL("INSERT INTO food_units (foodId, unitName, weightInGrams) SELECT id, 'g', 1.0 FROM foods")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `chat_history` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `role` TEXT NOT NULL,
                        `content` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_chat_history_createdAt` ON `chat_history` (`createdAt`)")
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `cn_foods` (
                        `id` INTEGER NOT NULL,
                        `name` TEXT NOT NULL,
                        `category` TEXT,
                        `energyKcal` REAL NOT NULL,
                        `proteinG` REAL NOT NULL,
                        `fatG` REAL NOT NULL,
                        `carbohydrateG` REAL NOT NULL,
                        `fiberG` REAL NOT NULL,
                        `waterG` REAL NOT NULL,
                        `ashG` REAL NOT NULL,
                        `vitaminA` REAL NOT NULL,
                        `carotene` REAL NOT NULL,
                        `retinolEquivalent` REAL NOT NULL,
                        `vitaminB1` REAL NOT NULL,
                        `vitaminB2` REAL NOT NULL,
                        `niacin` REAL NOT NULL,
                        `vitaminC` REAL NOT NULL,
                        `vitaminE` REAL NOT NULL,
                        `potassium` REAL NOT NULL,
                        `sodium` REAL NOT NULL,
                        `calcium` REAL NOT NULL,
                        `magnesium` REAL NOT NULL,
                        `iron` REAL NOT NULL,
                        `manganese` REAL NOT NULL,
                        `zinc` REAL NOT NULL,
                        `copper` REAL NOT NULL,
                        `phosphorus` REAL NOT NULL,
                        `selenium` REAL NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_cn_foods_name` ON `cn_foods` (`name`)")
            }
        }


        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `categories` ADD COLUMN `type` TEXT NOT NULL DEFAULT 'expense'")
                db.execSQL("ALTER TABLE `categories` ADD COLUMN `parentId` INTEGER")
                db.execSQL("ALTER TABLE `categories` ADD COLUMN `isSystem` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE `categories` SET `isSystem` = 1 WHERE `systemKey` IS NOT NULL")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_categories_parentId` ON `categories` (`parentId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_categories_type` ON `categories` (`type`)")

                val otherId = db.query(
                    "SELECT `id` FROM `categories` WHERE `systemKey` = 'OTHER' LIMIT 1"
                ).use { cursor -> if (cursor.moveToFirst()) cursor.getLong(0) else 0L }
                if (otherId > 0L) {
                    val unmatched = db.query(
                        "SELECT COUNT(*) FROM `expenses` WHERE `categoryId` IS NULL " +
                            "OR `categoryId` NOT IN (SELECT `id` FROM `categories`)"
                    ).use { cursor -> if (cursor.moveToFirst()) cursor.getInt(0) else 0 }
                    if (unmatched > 0) {
                        db.execSQL(
                            "UPDATE `expenses` SET `categoryId` = ? WHERE `categoryId` IS NULL " +
                                "OR `categoryId` NOT IN (SELECT `id` FROM `categories`)",
                            arrayOf<Any>(otherId)
                        )
                        Log.i(
                            "SagittariusMigration",
                            "8->9 backfilled $unmatched expense rows with missing/unmatched category to OTHER"
                        )
                    }
                }
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `chat_sessions` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `title` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "INSERT OR IGNORE INTO chat_sessions (id, title, createdAt, updatedAt) " +
                        "VALUES (1, '历史对话', " +
                        "CAST(strftime('%s','now') AS INTEGER) * 1000, " +
                        "CAST(strftime('%s','now') AS INTEGER) * 1000)"
                )
                db.execSQL("ALTER TABLE `chat_history` ADD COLUMN `sessionId` INTEGER NOT NULL DEFAULT 1")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_chat_history_sessionId` ON `chat_history` (`sessionId`)")
            }
        }

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: run {
                    migrateLegacyDatabase(context.applicationContext)
                    Room.databaseBuilder(
                        context.applicationContext,
                        AppDatabase::class.java,
                        DB_NAME
                    )
                        .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9)
                        .addCallback(object : androidx.room.RoomDatabase.Callback() {
                            override fun onCreate(db: SupportSQLiteDatabase) {
                                CategoryCatalog.presetEntities.forEach { category ->
                                    db.execSQL(
                                        "INSERT OR IGNORE INTO categories " +
                                            "(id, systemKey, name, iconKey, color, isCustom, sortOrder, createdAt, type, parentId, isSystem) " +
                                            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                                        arrayOf<Any?>(
                                            category.id,
                                            category.systemKey,
                                            category.name,
                                            category.iconKey,
                                            category.color,
                                            if (category.isCustom) 1 else 0,
                                            category.sortOrder,
                                            category.createdAt,
                                            category.type,
                                            category.parentId,
                                            if (category.isSystem) 1 else 0
                                        )
                                    )
                                }
                            }
                        })
                        .build()
                        .also { instance = it }
                }
            }
    }
}
