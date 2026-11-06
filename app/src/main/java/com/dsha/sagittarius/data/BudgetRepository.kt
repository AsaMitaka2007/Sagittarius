package com.dsha.sagittarius.data

import android.content.Context
import androidx.room.withTransaction
import com.dsha.sagittarius.widget.WidgetUpdateManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * 唯一数据入口。
 *
 * 所有增删改都走这里，并在写入后统一刷新桌面小部件；
 * 悬浮窗、主界面、历史页通过 Room Flow 自动收到同一份数据。
 */
data class ExpenseSplit(
    val amountCents: Long,
    val categoryId: Long
)

class BudgetRepository private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val db = AppDatabase.get(appContext)
    private val expenseDao = db.expenseDao()
    private val budgetDao = db.budgetDao()
    private val stateDao = db.appStateDao()
    private val categoryDao = db.categoryDao()
    private val nodeDao = db.budgetNodeDao()
    private val initMutex = Mutex()

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeActiveSnapshot(): Flow<ActiveBudgetSnapshot> =
        stateDao.observe()
            .filterNotNull()
            .flatMapLatest { state ->
                val budgetFlow = state.activeBudgetId
                    ?.let { budgetDao.observeById(it) }
                    ?: flowOf(null)
                combine(
                    budgetFlow,
                    expenseDao.observeBetween(state.activeStartDate, state.activeEndDate),
                    categoryDao.observeAll()
                ) { budget, expenses, categories ->
                    BudgetMath.createSnapshot(
                        state,
                        budget,
                        expenses,
                        categories.associateBy { it.id }
                    )
                }
            }
            .distinctUntilChanged()

    fun observeState(): Flow<AppStateEntity?> = stateDao.observe()

    fun observeAllExpenses(): Flow<List<ExpenseEntity>> = expenseDao.observeAll()

    suspend fun getExpensesBetween(start: LocalDate, end: LocalDate): List<ExpenseEntity> {
        ensureInitialState()
        return expenseDao.getBetween(start, end)
    }

    suspend fun getExpense(id: Long): ExpenseEntity? {
        ensureInitialState()
        return expenseDao.getById(id)
    }

    fun observeAllBudgets(): Flow<List<BudgetEntity>> = budgetDao.observeAll()

    fun observeCategories(): Flow<List<CategoryEntity>> = categoryDao.observeAll()

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeBudgetNodes(): Flow<List<BudgetNodeEntity>> =
        stateDao.observe()
            .filterNotNull()
            .flatMapLatest { state ->
                nodeDao.observeForRange(state.activeStartDate, state.activeEndDate)
            }
            .distinctUntilChanged()

    suspend fun ensureInitialState() {
        initMutex.withLock {
            categoryDao.insertAll(CategoryCatalog.presetEntities)
            if (stateDao.get() != null) return
            val today = LocalDate.now()
            val range = BudgetRangeCalculator.rangeFor(BudgetRangeType.THIS_MONTH, today)
            stateDao.upsert(
                AppStateEntity(
                    activeRangeType = BudgetRangeType.THIS_MONTH,
                    activeStartDate = range.start,
                    activeEndDate = range.end,
                    activeBudgetId = null,
                    floatingEnabled = false
                )
            )
        }
    }

    suspend fun getActiveSnapshot(): ActiveBudgetSnapshot {
        ensureInitialState()
        val state = stateDao.get() ?: return ActiveBudgetSnapshot.empty()
        val budget = state.activeBudgetId?.let { budgetDao.getById(it) }
        val expenses = expenseDao.getBetween(state.activeStartDate, state.activeEndDate)
        val categories = categoryDao.getAll().associateBy { it.id }
        return BudgetMath.createSnapshot(state, budget, expenses, categories)
    }

    suspend fun getState(): AppStateEntity? {
        ensureInitialState()
        return stateDao.get()
    }

    suspend fun getCategories(): List<CategoryEntity> {
        ensureInitialState()
        return categoryDao.getAll()
    }

    /**
     * 切换日期范围后，只复用“类型 + 起止日期完全相同”的预算。
     * 新范围尚未设置预算时，界面明确显示“未设置预算”，绝不复用其他区间的金额。
     */
    suspend fun setActiveRange(
        type: BudgetRangeType,
        customStart: LocalDate? = null,
        customEnd: LocalDate? = null
    ) {
        ensureInitialState()
        val today = LocalDate.now()
        val range = BudgetRangeCalculator.rangeFor(type, today, customStart, customEnd)
        initMutex.withLock {
            val matchingBudget = budgetDao.findExact(type, range.start, range.end)
            val current = stateDao.get() ?: return@withLock
            stateDao.upsert(
                current.copy(
                    activeRangeType = type,
                    activeStartDate = range.start,
                    activeEndDate = range.end,
                    activeBudgetId = matchingBudget?.id,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
        refreshWidgetsSafely()
    }

    /**
     * 设置总预算。若当前范围已有预算树顶级分配，且新预算低于已分配总额，
     * 返回 false，界面应提示先调整预算树。
     */
    suspend fun setBudgetForActiveRange(amountCents: Long): Boolean {
        require(amountCents > 0L) { "预算金额必须大于 0" }
        ensureInitialState()
        var accepted = false
        initMutex.withLock {
            db.withTransaction {
                val state = stateDao.get() ?: return@withTransaction
                val topAllocated = nodeDao.getForRange(
                    state.activeStartDate,
                    state.activeEndDate
                ).filter { it.parentId == null }.sumOf { it.amountCents }
                if (topAllocated > amountCents) {
                    accepted = false
                    return@withTransaction
                }
                val existing = state.activeBudgetId?.let { budgetDao.getById(it) }
                    ?.takeIf {
                        it.startDate == state.activeStartDate &&
                            it.endDate == state.activeEndDate
                    }
                val exact = existing ?: budgetDao.findExact(
                    state.activeRangeType,
                    state.activeStartDate,
                    state.activeEndDate
                )
                val now = System.currentTimeMillis()
                val budgetId: Long
                if (exact != null) {
                    budgetDao.update(
                        exact.copy(
                            amountCents = amountCents,
                            rangeType = state.activeRangeType,
                            updatedAt = now
                        )
                    )
                    budgetId = exact.id
                } else {
                    budgetId = budgetDao.insert(
                        BudgetEntity(
                            rangeType = state.activeRangeType,
                            startDate = state.activeStartDate,
                            endDate = state.activeEndDate,
                            amountCents = amountCents,
                            createdAt = now,
                            updatedAt = now
                        )
                    )
                }
                stateDao.upsert(
                    state.copy(
                        activeBudgetId = budgetId,
                        updatedAt = now
                    )
                )
                accepted = true
            }
        }
        if (accepted) refreshWidgetsSafely()
        return accepted
    }

    suspend fun clearBudgetForActiveRange() {
        ensureInitialState()
        initMutex.withLock {
            val state = stateDao.get() ?: return@withLock
            state.activeBudgetId?.let { budgetDao.deleteById(it) }
            budgetDao.findExact(
                state.activeRangeType,
                state.activeStartDate,
                state.activeEndDate
            )?.let { budgetDao.deleteById(it.id) }
            stateDao.upsert(
                state.copy(activeBudgetId = null, updatedAt = System.currentTimeMillis())
            )
        }
        refreshWidgetsSafely()
    }

    suspend fun addExpense(
        amountCents: Long,
        date: LocalDate,
        categoryId: Long,
        note: String?
    ): Long {
        require(amountCents > 0L) { "金额必须大于 0" }
        val now = System.currentTimeMillis()
        val id = expenseDao.insert(
            ExpenseEntity(
                amountCents = amountCents,
                date = date,
                categoryId = categoryId,
                note = note?.trim()?.takeIf { it.isNotEmpty() },
                createdAt = now,
                updatedAt = now
            )
        )
        refreshWidgetsSafely()
        return id
    }

    /** 同一气泡内的多个类别子项按当前日期一次性写入；总金额为子项之和。 */
    suspend fun addExpenses(
        splits: List<ExpenseSplit>,
        date: LocalDate,
        note: String?
    ): List<Long> {
        require(splits.isNotEmpty()) { "至少需要一个子项" }
        require(splits.all { it.amountCents > 0L }) { "金额必须大于 0" }
        val now = System.currentTimeMillis()
        val normalizedNote = note?.trim()?.takeIf { it.isNotEmpty() }
        val ids = db.withTransaction {
            splits.mapIndexed { index, split ->
                expenseDao.insert(
                    ExpenseEntity(
                        amountCents = split.amountCents,
                        date = date,
                        categoryId = split.categoryId,
                        note = normalizedNote,
                        createdAt = now + index,
                        updatedAt = now + index
                    )
                )
            }
        }
        refreshWidgetsSafely()
        return ids
    }

    suspend fun updateExpense(expense: ExpenseEntity) {
        require(expense.amountCents > 0L) { "金额必须大于 0" }
        expenseDao.update(
            expense.copy(
                note = expense.note?.trim()?.takeIf { it.isNotEmpty() },
                updatedAt = System.currentTimeMillis()
            )
        )
        refreshWidgetsSafely()
    }

    suspend fun deleteExpense(expense: ExpenseEntity) {
        expenseDao.delete(expense)
        refreshWidgetsSafely()
    }

    suspend fun setFloatingEnabled(enabled: Boolean) {
        ensureInitialState()
        initMutex.withLock {
            val state = stateDao.get() ?: return@withLock
            if (state.floatingEnabled == enabled) return@withLock
            stateDao.upsert(
                state.copy(floatingEnabled = enabled, updatedAt = System.currentTimeMillis())
            )
        }
    }

    // ------------------------------------------------------------
    // Custom categories
    // ------------------------------------------------------------

    suspend fun addCategory(name: String, color: Int, iconKey: String = "other"): CategoryResult {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || trimmed.length > 12) return CategoryResult.InvalidName
        ensureInitialState()
        val existing = categoryDao.getAll()
        val id = categoryDao.insert(
            CategoryEntity(
                systemKey = null,
                name = trimmed,
                iconKey = iconKey.ifBlank { "other" },
                color = color,
                isCustom = true,
                sortOrder = (existing.maxOfOrNull { it.sortOrder } ?: 0) + 1,
                type = "expense",
                parentId = null,
                isSystem = false
            )
        )
        return categoryDao.getById(id)
            ?.let(CategoryResult::Success)
            ?: CategoryResult.InvalidName
    }

    suspend fun updateCategory(
        id: Long,
        name: String,
        color: Int,
        iconKey: String = "other"
    ): CategoryResult {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || trimmed.length > 12) return CategoryResult.InvalidName
        ensureInitialState()
        val category = categoryDao.getById(id) ?: return CategoryResult.InvalidName
        if (!category.isCustom) return CategoryResult.ReadOnly
        val updated = category.copy(
            name = trimmed,
            color = color,
            iconKey = iconKey.ifBlank { category.iconKey }
        )
        categoryDao.update(updated)
        return CategoryResult.Success(updated)
    }

    suspend fun deleteCategory(id: Long): CategoryResult {
        ensureInitialState()
        val category = categoryDao.getById(id) ?: return CategoryResult.InvalidName
        if (!category.isCustom) return CategoryResult.ReadOnly
        val all = categoryDao.getAll()
        val other = all.firstOrNull { it.systemKey == "OTHER" } ?: CategoryCatalog.fallbackEntity()
        db.withTransaction {
            if (categoryDao.countExpenses(id) > 0) {
                expenseDao.reassignCategory(id, other.id)
                nodeDao.reassignCategory(id, other.id)
            } else {
                nodeDao.clearCategory(id)
            }
            categoryDao.deleteById(id)
        }
        return CategoryResult.Success(category)
    }

    /** 交换两个类别的排序值，用于轮盘长按拖拽换位。 */
    suspend fun swapCategoryOrder(firstId: Long, secondId: Long): Boolean {
        if (firstId == secondId) return false
        ensureInitialState()
        val first = categoryDao.getById(firstId) ?: return false
        val second = categoryDao.getById(secondId) ?: return false
        db.withTransaction {
            categoryDao.update(first.copy(sortOrder = second.sortOrder))
            categoryDao.update(second.copy(sortOrder = first.sortOrder))
        }
        return true
    }

    // ------------------------------------------------------------
    // Budget tree allocation
    // ------------------------------------------------------------

    suspend fun getBudgetNodes(): List<BudgetNodeEntity> {
        ensureInitialState()
        val state = stateDao.get() ?: return emptyList()
        return nodeDao.getForRange(state.activeStartDate, state.activeEndDate)
    }

    suspend fun addBudgetNode(
        parentId: Long?,
        name: String,
        amountCents: Long,
        categoryId: Long?
    ): BudgetNodeResult {
        if (amountCents <= 0L) return BudgetNodeResult.InvalidAmount
        val trimmed = name.trim().ifEmpty { "未命名分配" }
        ensureInitialState()
        return db.withTransaction {
            val state = stateDao.get() ?: return@withTransaction BudgetNodeResult.NoBudget
            val nodes = nodeDao.getForRange(state.activeStartDate, state.activeEndDate)
            val parent = parentId?.let { id -> nodes.firstOrNull { it.id == id } }
            if (parentId != null && (parent == null || parent.parentId != null)) {
                return@withTransaction BudgetNodeResult.InvalidDepth
            }
            val budget = state.activeBudgetId?.let { budgetDao.getById(it) }
                ?: return@withTransaction BudgetNodeResult.NoBudget
            val available = if (parent == null) {
                budget.amountCents - nodes.filter { it.parentId == null }.sumOf { it.amountCents }
            } else {
                parent.amountCents - nodes.filter { it.parentId == parent.id }
                    .sumOf { it.amountCents }
            }
            if (amountCents > available) {
                return@withTransaction BudgetNodeResult.OverAllocated(available.coerceAtLeast(0))
            }
            val sortOrder = (nodes.maxOfOrNull { it.sortOrder } ?: 0) + 1
            val id = nodeDao.insert(
                BudgetNodeEntity(
                    parentId = parentId,
                    budgetName = trimmed,
                    amountCents = amountCents,
                    categoryId = categoryId,
                    sortOrder = sortOrder,
                    rangeType = state.activeRangeType,
                    startDate = state.activeStartDate,
                    endDate = state.activeEndDate
                )
            )
            BudgetNodeResult.Success(id)
        }
    }

    suspend fun updateBudgetNode(
        nodeId: Long,
        name: String,
        amountCents: Long,
        categoryId: Long?
    ): BudgetNodeResult {
        if (amountCents <= 0L) return BudgetNodeResult.InvalidAmount
        val trimmed = name.trim().ifEmpty { "未命名分配" }
        ensureInitialState()
        return db.withTransaction {
            val state = stateDao.get() ?: return@withTransaction BudgetNodeResult.NoBudget
            val nodes = nodeDao.getForRange(state.activeStartDate, state.activeEndDate)
            val node = nodes.firstOrNull { it.id == nodeId }
                ?: return@withTransaction BudgetNodeResult.InvalidAmount
            val budget = state.activeBudgetId?.let { budgetDao.getById(it) }
                ?: return@withTransaction BudgetNodeResult.NoBudget
            val siblings = if (node.parentId == null) {
                nodes.filter { it.parentId == null && it.id != node.id }
            } else {
                nodes.filter { it.parentId == node.parentId && it.id != node.id }
            }
            val parentAmount = if (node.parentId == null) {
                budget.amountCents
            } else {
                nodes.firstOrNull { it.id == node.parentId }?.amountCents
                    ?: return@withTransaction BudgetNodeResult.InvalidDepth
            }
            val available = parentAmount - siblings.sumOf { it.amountCents }
            if (amountCents > available) {
                return@withTransaction BudgetNodeResult.OverAllocated(available.coerceAtLeast(0))
            }
            val childAllocated = nodes.filter { it.parentId == node.id }.sumOf { it.amountCents }
            if (amountCents < childAllocated) {
                return@withTransaction BudgetNodeResult.BelowChildren(childAllocated)
            }
            nodeDao.update(
                node.copy(
                    budgetName = trimmed,
                    amountCents = amountCents,
                    categoryId = categoryId,
                    updatedAt = System.currentTimeMillis()
                )
            )
            BudgetNodeResult.Success(node.id)
        }
    }

    suspend fun deleteBudgetNode(nodeId: Long) {
        ensureInitialState()
        db.withTransaction {
            val state = stateDao.get() ?: return@withTransaction
            val nodes = nodeDao.getForRange(state.activeStartDate, state.activeEndDate)
            val ids = mutableSetOf(nodeId)
            var changed: Boolean
            do {
                changed = false
                nodes.forEach { node ->
                    if (node.parentId != null && node.parentId in ids && ids.add(node.id)) {
                        changed = true
                    }
                }
            } while (changed)
            ids.forEach { nodeDao.deleteById(it) }
        }
    }

    private suspend fun refreshWidgetsSafely() {
        withContext(Dispatchers.IO) {
            runCatching { WidgetUpdateManager.updateAll(appContext) }
        }
    }

    companion object {
        @Volatile
        private var instance: BudgetRepository? = null

        fun getInstance(context: Context): BudgetRepository =
            instance ?: synchronized(this) {
                instance ?: BudgetRepository(context.applicationContext).also { instance = it }
            }
    }
}
