package com.dsha.sagittarius.agent.tools

import com.dsha.sagittarius.agent.tools.ports.ExpensePort
import com.dsha.sagittarius.data.BudgetRangeType
import com.dsha.sagittarius.data.CategoryEntity
import com.dsha.sagittarius.util.Money
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.JsonPrimitive
import java.time.LocalDate
import java.time.format.DateTimeParseException

class UpdateExpenseTool(private val port: ExpensePort) : AgentTool {
    override val name = "update_expense"
    override val description = "修改一条历史账单；缺少的字段保留原值"
    override val parametersSchema: JsonObject = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {
            put("expense_id", buildJsonObject { put("type", "integer") })
            put("amount", buildJsonObject { put("type", "number") })
            put("category", buildJsonObject {
                put("type", "string")
                put("description", "分类名，须为现有分类之一；不确定时先调用 list_categories")
            })
            put("category_id", buildJsonObject {
                put("type", "integer")
                put("description", "可选；存在同名分类时指定分类 ID")
            })
            put("note", buildJsonObject { put("type", "string") })
        })
        put("required", buildJsonArray { add(JsonPrimitive("expense_id")) })
    }

    override suspend fun execute(args: JsonObject): ToolResult = withContext(Dispatchers.IO) {
        val id = args["expense_id"]?.jsonPrimitive?.content?.toLongOrNull()
            ?: return@withContext fail("缺少有效 expense_id")
        val old = port.getExpense(id) ?: return@withContext fail("找不到账单 #$id")
        val amount = args["amount"]?.jsonPrimitive?.content?.toDoubleOrNull()
            ?.let { Money.parseYuanToCents(it.toString()) }
        val categoryText = args["category"]?.jsonPrimitive?.content?.trim()
        val categoryId = args["category_id"]?.jsonPrimitive?.content?.toLongOrNull()
        val categories = port.categories()
        val category = when {
            categoryId != null -> categories.firstOrNull { it.id == categoryId }
            !categoryText.isNullOrBlank() -> {
                val matches = categories.filter { it.name == categoryText }
                if (matches.size > 1) return@withContext fail("存在同名分类，请传 category_id")
                matches.firstOrNull()
            }
            else -> null
        }
        if ((!categoryText.isNullOrBlank() || categoryId != null) && category == null) {
            return@withContext fail("分类不存在，请先在设置中创建")
        }
        val note = args["note"]?.jsonPrimitive?.content
        val updated = old.copy(
            amountCents = amount?.takeIf { it > 0 } ?: old.amountCents,
            categoryId = category?.id ?: old.categoryId,
            note = if (note == null) old.note else note.trim().ifBlank { null }
        )
        runCatching { port.updateExpense(updated) }.getOrElse { return@withContext fail("修改失败") }
        val snapshot = port.activeSnapshot()
        ToolResult(
            true,
            buildJsonObject {
                put("ok", true)
                put("id", id)
                put("amount", Money.formatCents(updated.amountCents))
                put("category", category?.name ?: old.categoryId.toString())
                put("remaining", snapshot.remainingCents?.let(Money::formatCents).orEmpty())
            }.toString(),
            "已修改账单 #$id"
        )
    }

    private fun fail(message: String) = ToolResult(false, buildJsonObject { put("ok", false); put("status", "error"); put("message", message) }.toString())


}

class DeleteExpenseTool(private val port: ExpensePort) : AgentTool {
    override val name = "delete_expense"
    override val description = "删除一条历史账单"
    override val parametersSchema: JsonObject = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject { put("expense_id", buildJsonObject { put("type", "integer") }) })
        put("required", buildJsonArray { add(JsonPrimitive("expense_id")) })
    }

    override suspend fun execute(args: JsonObject): ToolResult = withContext(Dispatchers.IO) {
        val id = args["expense_id"]?.jsonPrimitive?.content?.toLongOrNull()
            ?: return@withContext fail("缺少有效 expense_id")
        val old = port.getExpense(id) ?: return@withContext fail("找不到账单 #$id")
        runCatching { port.deleteExpense(old) }.getOrElse { return@withContext fail("删除失败") }
        val snapshot = port.activeSnapshot()
        ToolResult(
            true,
            buildJsonObject {
                put("ok", true)
                put("id", id)
                put("remaining", snapshot.remainingCents?.let(Money::formatCents).orEmpty())
            }.toString(),
            "已删除账单 #$id"
        )
    }

    private fun fail(error: String) = ToolResult(false, buildJsonObject { put("ok", false); put("status", "error"); put("message", error) }.toString())
}

class SetBudgetTool(private val port: ExpensePort) : AgentTool {
    override val name = "set_budget"
    override val description = "设置预算总额，可指定本月/本周/自定义范围"
    override val parametersSchema: JsonObject = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {
            put("amount", buildJsonObject { put("type", "number") })
            put("range_type", buildJsonObject { put("type", "string"); put("enum", buildJsonArray { add(JsonPrimitive("month")); add(JsonPrimitive("week")); add(JsonPrimitive("custom")) }) })
            put("start_date", buildJsonObject { put("type", "string") })
            put("end_date", buildJsonObject { put("type", "string") })
        })
        put("required", buildJsonArray { add(JsonPrimitive("amount")); add(JsonPrimitive("range_type")) })
    }

    override suspend fun execute(args: JsonObject): ToolResult = withContext(Dispatchers.IO) {
        val cents = args["amount"]?.jsonPrimitive?.content?.toDoubleOrNull()
            ?.let { Money.parseYuanToCents(it.toString()) } ?: return@withContext fail("金额格式不正确")
        if (cents <= 0) return@withContext fail("金额必须大于 0")
        val type = when (args["range_type"]?.jsonPrimitive?.content) {
            "month" -> BudgetRangeType.THIS_MONTH
            "week" -> BudgetRangeType.THIS_WEEK
            "custom" -> BudgetRangeType.CUSTOM
            else -> return@withContext fail("range_type 只能是 month/week/custom")
        }
        val start = parseDate(args["start_date"]?.jsonPrimitive?.content)
        val end = parseDate(args["end_date"]?.jsonPrimitive?.content)
        if (type == BudgetRangeType.CUSTOM && (start == null || end == null)) return@withContext fail("自定义范围需要 start_date/end_date")
        runCatching { port.setActiveRange(type, start, end) }
        val ok = runCatching { port.setBudget(cents) }.getOrDefault(false)
        if (!ok) return@withContext fail("预算设置失败：可能低于预算树已分配金额")
        ToolResult(true, buildJsonObject { put("ok", true); put("amount", Money.formatCents(cents)) }.toString(), "预算已设置为 ${Money.formatCents(cents)}")
    }

    private fun parseDate(value: String?): LocalDate? = runCatching { value?.let(LocalDate::parse) }.getOrNull()
    private fun fail(error: String) = ToolResult(false, buildJsonObject { put("ok", false); put("status", "error"); put("message", error) }.toString())
}

class AllocateBudgetTreeTool(private val port: ExpensePort) : AgentTool {
    override val name = "allocate_budget_tree"
    override val description = "把预算金额分配到支出/预算分类；可按分类名或 category_id 定位，可挂到父分类节点下"
    override val parametersSchema: JsonObject = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {
            put("category", buildJsonObject {
                put("type", "string")
                put("description", "分类名，须为现有分类之一；同名时请改传 category_id")
            })
            put("category_id", buildJsonObject {
                put("type", "integer")
                put("description", "可选；存在同名分类时指定分类 ID")
            })
            put("parent_category", buildJsonObject {
                put("type", "string")
                put("description", "可选；父分类名，父节点必须已在预算树中")
            })
            put("parent_category_id", buildJsonObject {
                put("type", "integer")
                put("description", "可选；父分类 ID，用于同名歧义")
            })
            put("amount", buildJsonObject { put("type", "number") })
        })
        put("required", buildJsonArray { add(JsonPrimitive("amount")) })
    }

    override suspend fun execute(args: JsonObject): ToolResult = withContext(Dispatchers.IO) {
        val cents = args["amount"]?.jsonPrimitive?.content?.toDoubleOrNull()
            ?.let { Money.parseYuanToCents(it.toString()) } ?: return@withContext fail("金额格式不正确")
        if (cents <= 0) return@withContext fail("金额必须大于 0")
        val categories = port.categories()
        val targetId = args["category_id"]?.jsonPrimitive?.content?.toLongOrNull()
        val targetName = args["category"]?.jsonPrimitive?.content
        val target = resolve(categories, targetId, targetName)
            ?: return@withContext fail(
                if (targetId == null && isAmbiguous(categories, targetName)) {
                    "存在同名分类，请传 category_id"
                } else "分类不存在，请先在设置中创建"
            )
        val parent = if (args["parent_category_id"] != null || args["parent_category"] != null) {
            val parentId = args["parent_category_id"]?.jsonPrimitive?.content?.toLongOrNull()
            val parentName = args["parent_category"]?.jsonPrimitive?.content
            resolve(categories, parentId, parentName)
                ?: return@withContext fail(
                    if (parentId == null && isAmbiguous(categories, parentName)) {
                        "存在同名父分类，请传 parent_category_id"
                    } else "父分类不存在"
                )
        } else null
        if (parent?.id == target.id) return@withContext fail("父分类不能是自身")
        val nodes = port.budgetNodes()
        val parentNode = parent?.let { parentCategory ->
            nodes.firstOrNull { it.categoryId == parentCategory.id && it.parentId == null }
                ?: return@withContext fail("父分类尚未分配预算树节点")
        }
        val existing = nodes.firstOrNull {
            it.categoryId == target.id && it.parentId == parentNode?.id
        }
        val result = if (existing == null) {
            port.addBudgetNode(parentNode?.id, target.name, cents, target.id)
        } else {
            port.updateBudgetNode(existing.id, target.name, cents, target.id)
        }
        if (result !is com.dsha.sagittarius.data.BudgetNodeResult.Success) {
            return@withContext fail("预算树分配失败：$result")
        }
        ToolResult(
            true,
            buildJsonObject {
                put("ok", true)
                put("node_id", result.id)
                put("category_id", target.id)
                put("category", target.name)
                put("amount", Money.formatCents(cents))
                put("parent_category_id", parent?.id)
            }.toString(),
            "已分配 ${target.name} ${Money.formatCents(cents)}"
        )
    }

    private fun isAmbiguous(categories: List<CategoryEntity>, name: String?): Boolean {
        val text = name?.trim().orEmpty()
        if (text.isEmpty()) return false
        return categories.count { it.name == text } > 1
    }

    private fun resolve(categories: List<CategoryEntity>, id: Long?, name: String?): CategoryEntity? {
        if (id != null) return categories.firstOrNull { it.id == id }
        val text = name?.trim().orEmpty()
        if (text.isEmpty()) return null
        val matches = categories.filter {
            it.name == text || it.systemKey.equals(text, ignoreCase = true)
        }
        return if (matches.size == 1) matches.first() else null
    }

    private fun fail(message: String) = ToolResult(
        false,
        buildJsonObject { put("ok", false); put("status", "error"); put("message", message) }.toString()
    )
}

