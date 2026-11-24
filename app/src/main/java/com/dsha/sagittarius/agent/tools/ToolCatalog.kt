package com.dsha.sagittarius.agent.tools

/** 终端页展示用的工具元数据；schema/description 仍以 ToolRegistry 为准。 */
object ToolCatalog {

    data class Meta(
        val displayName: String,
        val category: String,
        val readOnly: Boolean,
        val note: String = ""
    )

    private val meta = mapOf(
        "add_expense" to Meta("记一笔支出", "记账", false),
        "update_expense" to Meta("修改支出", "记账", false),
        "delete_expense" to Meta("删除支出", "记账", false),
        "set_budget" to Meta("设置预算", "记账", false),
        "allocate_budget_tree" to Meta("分配预算树", "记账", false),
        "list_categories" to Meta("列出分类", "记账", true),
        "manage_category" to Meta("管理分类", "记账", false),
        "add_diet" to Meta("记录饮食", "饮食", false),
        "add_food_to_library" to Meta("添加食材", "饮食", false),
        "set_food_unit" to Meta("设置自定义单位", "饮食", false),
        "set_food_image" to Meta("给食材配图", "饮食", false),
        "manage_food_category" to Meta("管理食材分类", "饮食", false),
        "search_food_cn_local" to Meta("搜索中国食物库", "饮食", true),
        "get_cn_food_nutrition" to Meta("查询食物营养", "饮食", true),
        "manage_food_library" to Meta("管理食材库", "饮食", false),
        "web_search" to Meta("联网搜索", "查询", true),
        "query_app_data" to Meta("查询应用数据", "查询", true),
        "query_data" to Meta("查询数据", "查询", true),
        "update_app_settings" to Meta("修改应用设置", "系统", false),
        "toggle_network_search" to Meta("切换联网搜索", "系统", false),
        "update_theme" to Meta("切换主题", "系统", false),
        "update_date_card_color" to Meta("修改日期卡颜色", "系统", false),
        "toggle_floating_window" to Meta("切换悬浮窗", "系统", false),
        "navigate_to_tab" to Meta("跳转页面", "系统", true),
        "control_weather_debug" to Meta("天气调试", "系统", false),
        "remember_preference" to Meta("记住长期偏好", "记忆", false)
    )

    fun metaFor(tool: AgentTool): Meta = meta[tool.name] ?: Meta(tool.name, "其他", false)
}
