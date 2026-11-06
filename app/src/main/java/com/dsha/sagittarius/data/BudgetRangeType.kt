package com.dsha.sagittarius.data

/** 预算范围类型，与分类体系无关。 */
enum class BudgetRangeType(val label: String) {
    LAST_7_DAYS("近7天"),
    THIS_WEEK("本周"),
    THIS_MONTH("本月"),
    NEXT_MONTH("下个月"),
    /** 旧版本持久化的上月范围，仅用于兼容读取，不再出现在按钮列表。 */
    LAST_MONTH("上月"),
    CUSTOM("自定义");

    companion object {
        val presets: List<BudgetRangeType> = listOf(
            THIS_MONTH, NEXT_MONTH, THIS_WEEK, LAST_7_DAYS, CUSTOM
        )

        fun fromStorage(value: String?): BudgetRangeType =
            entries.firstOrNull { it.name == value } ?: THIS_MONTH
    }
}
