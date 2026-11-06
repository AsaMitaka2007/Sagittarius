package com.dsha.sagittarius.data

import kotlin.math.max

data class BudgetTreeDisplayNode(
    val node: BudgetNodeEntity,
    val depth: Int,
    val usedCents: Long,
    val allocationShare: Float,
    val usedShare: Float,
    val overAllocated: Boolean,
    val nearLimit: Boolean,
    val children: List<BudgetTreeDisplayNode>
)

data class BudgetTreeModel(
    val totalCents: Long,
    val topAllocatedCents: Long,
    val unallocatedCents: Long,
    val overAllocated: Boolean,
    val nodes: List<BudgetTreeDisplayNode>,
    val categoryNames: Map<Long, String>
) {
    val hasNodes: Boolean get() = nodes.isNotEmpty()
}

/**
 * 预算树只是“分配视图”。总预算剩余仍按总预算 - 实际支出计算。
 * 节点已用 = 该节点关联类别在当前范围内的支出合计。
 */
object BudgetTreeMath {

    fun build(
        nodes: List<BudgetNodeEntity>,
        totalCents: Long,
        expenses: List<ExpenseEntity>,
        categoryNames: Map<Long, String>
    ): BudgetTreeModel {
        val topAllocated = nodes.filter { it.parentId == null }.sumOf { it.amountCents }
        val usedByCategory = expenses.groupBy { it.categoryId }
            .mapValues { (_, list) -> list.sumOf { it.amountCents } }
        val childrenByParent = nodes.filter { it.parentId != null }
            .groupBy { it.parentId }
        val hasBudget = totalCents > 0L

        fun buildNode(node: BudgetNodeEntity, depth: Int): BudgetTreeDisplayNode {
            val used = node.categoryId?.let { usedByCategory[it] } ?: 0L
            val children = childrenByParent[node.id].orEmpty()
                .sortedWith(compareBy({ it.sortOrder }, { it.id }))
                .map { buildNode(it, depth + 1) }
            val childAllocated = children.sumOf { it.node.amountCents }
            val over = !hasBudget || childAllocated > node.amountCents
            val allocationShare = if (totalCents > 0) {
                node.amountCents.toFloat() / totalCents
            } else {
                0f
            }
            val usedShare = if (node.amountCents > 0) {
                used.toFloat() / node.amountCents
            } else {
                0f
            }
            return BudgetTreeDisplayNode(
                node = node,
                depth = depth,
                usedCents = used,
                allocationShare = allocationShare,
                usedShare = usedShare,
                overAllocated = over,
                nearLimit = usedShare >= 0.9f || allocationShare >= 0.9f,
                children = children
            )
        }

        val tree = nodes.filter { it.parentId == null }
            .sortedWith(compareBy({ it.sortOrder }, { it.id }))
            .map { buildNode(it, 1) }

        return BudgetTreeModel(
            totalCents = totalCents,
            topAllocatedCents = topAllocated,
            unallocatedCents = totalCents - topAllocated,
            overAllocated = topAllocated > totalCents,
            nodes = tree,
            categoryNames = categoryNames
        )
    }

    fun availableForTopLevel(model: BudgetTreeModel): Long =
        max(0L, model.totalCents - model.topAllocatedCents)
}
