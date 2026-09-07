package com.cocwar.data.model

/**
 * 领域模型（App 内部使用，非空、已清洗）。
 * 数据源统一为 CSV：导入链路不再使用 JSON/Gson DTO。
 */

/** 单次进攻：attack_order 从 1 开始；destructionPercentage 为 0..100，-1 表示「看不清」待确认。 */
data class Attack(
    val attackOrder: Int = 0,
    val destructionPercentage: Int = 0
)

/** 是否已发起进攻：摧毁率 > 0 视为已使用（-1「看不清」不入统计，按未进攻处理）。 */
fun Attack.isUsed(): Boolean = destructionPercentage > 0

/** event_type values */
const val EVENT_TYPE_WAR = "war"
const val EVENT_TYPE_LEAGUE = "league"

/** 「看不清」哨兵值：识别/手填时用于标记无法确定的数值，导入预览时提示用户确认后归 0。 */
const val UNKNOWN_VALUE = -1
