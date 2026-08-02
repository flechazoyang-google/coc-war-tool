package com.cocwar.data.samples

import com.cocwar.data.model.AttackDto
import com.cocwar.data.model.MemberDto
import com.cocwar.data.model.WarDto
import com.cocwar.data.parser.WarJsonParser

/**
 * Generates the two built-in example datasets:
 *  - a 30-player clan war
 *  - a 15-player league (CWL) round
 * Both are insertable like any imported event and can be deleted by the user.
 *
 * 采用与用户导入一致的精简结构：成员只有 player_name / total_stars / attacks
 * （attack 只有 attack_order / destruction_percentage，status 由摧毁率推导），
 * 职位通过 rosterRoles 注入（与花名册职位映射同源），未进攻成员不写 attacks 占位。
 */
object SampleDataProvider {

    private val WAR_NAMES = listOf(
        "陈平安", "混子祭天", "压爆了", "一剑封喉", "夜空之刃",
        "狂暴野猪", "雷霆战将", "清风明月", "铁壁铜墙", "暗影猎手",
        "九天揽月", "破阵之矛", "苍狼啸月", "烈日灼心", "寒冰法师",
        "无敌小钢炮", "随风飘零", "龙吟九霄", "疾风骤雨", "烈焰红唇",
        "沉默刺客", "王者归来", "逐风少年", "不灭星辰", "残月孤星",
        "霸王别姬", "迷途书生", "佛系养生", "摸鱼达人", "划水冠军"
    )

    private val LEAGUE_NAMES = listOf(
        "陈平安", "混子祭天", "压爆了", "一剑封喉", "夜空之刃",
        "狂暴野猪", "雷霆战将", "清风明月", "铁壁铜墙", "暗影猎手",
        "九天揽月", "破阵之矛", "苍狼啸月", "烈日灼心", "寒冰法师"
    )

    fun warSample(createdAt: Long): WarJsonParser.ParsedEvent {
        val roles = mapOf(
            "陈平安" to "leader",
            "混子祭天" to "coLeader",
            "压爆了" to "elder",
            "一剑封喉" to "elder",
            "夜空之刃" to "elder",
            "狂暴野猪" to "elder",
            "雷霆战将" to "elder"
        )
        val members = WAR_NAMES.mapIndexed { i, name ->
            val isNonAttacker = i in listOf(18, 27, 28, 29)
            val attacks = if (isNonAttacker) {
                emptyList()  // 未进攻成员无 attacks，解析器自动补 destruction=0 占位
            } else {
                val star1 = if (i % 5 == 0) 2 else 3
                val star2 = if (i % 7 == 0) 2 else 3
                listOf(
                    AttackDto(1, if (star1 == 3) 100 else 92),
                    AttackDto(2, if (star2 == 3) 100 else 95)
                )
            }
            val totalStars = if (isNonAttacker) 0 else (if (i % 5 == 0) 2 else 3) + (if (i % 7 == 0) 2 else 3)
            MemberDto(playerName = name, totalStars = totalStars, attacks = attacks)
        }

        val dto = WarDto(members = members)
        return WarJsonParser.fromDto(dto, isSample = true, createdAt, rosterRoles = roles).let { parsed ->
            parsed.copy(event = parsed.event.copy(eventName = "示例·30人部落战"))
        }
    }

    fun leagueSample(createdAt: Long): WarJsonParser.ParsedEvent {
        val roles = mapOf(
            "陈平安" to "leader",
            "混子祭天" to "coLeader",
            "压爆了" to "elder",
            "一剑封喉" to "elder",
            "夜空之刃" to "elder"
        )
        val members = LEAGUE_NAMES.mapIndexed { i, name ->
            val isNonAttacker = i in listOf(13, 14)
            val attacks = if (isNonAttacker) {
                emptyList()
            } else {
                val stars = if (i % 4 == 0) 2 else 3
                listOf(AttackDto(1, if (stars == 3) 100 else 94))
            }
            val totalStars = if (isNonAttacker) 0 else (if (i % 4 == 0) 2 else 3)
            MemberDto(playerName = name, totalStars = totalStars, attacks = attacks)
        }

        val dto = WarDto(members = members)
        return WarJsonParser.fromDto(dto, isSample = true, createdAt, rosterRoles = roles).let { parsed ->
            parsed.copy(event = parsed.event.copy(eventName = "示例·15人联赛（第3轮）", eventType = "league", eventRound = 3))
        }
    }
}
