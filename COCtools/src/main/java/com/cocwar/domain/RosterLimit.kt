package com.cocwar.domain

/**
 * 花名册规模上限：COC 部落成员上限 50 人（含首领）。
 *
 * 用途：
 * - 成员更新（硬替换）确认阶段校验，超出则拒绝落库并提示用户核对识别结果；
 * - 花名册页面副标题展示「当前 / 上限」。
 *
 * 注意：这只是**业务校验**，不是数据库约束——历史数据或备份还原可能短暂超过该值，
 * 因此不做硬截断，交由用户在下一次成员更新时自行收敛。
 */
const val ROSTER_LIMIT = 50

/**
 * 校验更新后的花名册规模。返回 null 表示通过，否则返回给用户的提示文案。
 */
fun checkRosterLimit(finalSize: Int): String? =
    if (finalSize > ROSTER_LIMIT) {
        "更新后共 $finalSize 人，超过部落上限 $ROSTER_LIMIT 人，请核对识别结果（多半是识别串入了其他部落的成员或重复行）"
    } else {
        null
    }
