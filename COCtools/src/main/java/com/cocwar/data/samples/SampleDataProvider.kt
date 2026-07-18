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
        val members = WAR_NAMES.mapIndexed { i, name ->
            val role = when {
                i == 0 -> "leader"
                i == 1 -> "coLeader"
                i in 2..6 -> "elder"
                else -> "member"
            }
            val isNonAttacker = i in listOf(18, 27, 28, 29)
            val attacks = if (isNonAttacker) {
                listOf(AttackDto(1, "unused", 0), AttackDto(2, "unused", 0))
            } else {
                val star1 = if (i % 5 == 0) 2 else 3
                val star2 = if (i % 7 == 0) 2 else 3
                listOf(
                    AttackDto(1, "used", if (star1 == 3) 100 else 92),
                    AttackDto(2, "used", if (star2 == 3) 100 else 95)
                )
            }
            val totalStars = if (isNonAttacker) 0 else (if (i % 5 == 0) 2 else 3) + (if (i % 7 == 0) 2 else 3)
            MemberDto(i + 1, name, role, totalStars, attacks)
        }

        val dto = WarDto(members = members)
        return WarJsonParser.fromDto(dto, isSample = true, createdAt).let { parsed ->
            parsed.copy(event = parsed.event.copy(eventName = "示例·30人部落战"))
        }
    }

    fun leagueSample(createdAt: Long): WarJsonParser.ParsedEvent {
        val members = LEAGUE_NAMES.mapIndexed { i, name ->
            val role = when {
                i == 0 -> "leader"
                i == 1 -> "coLeader"
                i in 2..4 -> "elder"
                else -> "member"
            }
            val isNonAttacker = i in listOf(13, 14)
            val attacks = if (isNonAttacker) {
                listOf(AttackDto(1, "unused", 0))
            } else {
                val stars = if (i % 4 == 0) 2 else 3
                listOf(AttackDto(1, "used", if (stars == 3) 100 else 94))
            }
            val totalStars = if (isNonAttacker) 0 else (if (i % 4 == 0) 2 else 3)
            MemberDto(i + 1, name, role, totalStars, attacks)
        }

        val dto = WarDto(members = members)
        return WarJsonParser.fromDto(dto, isSample = true, createdAt).let { parsed ->
            parsed.copy(event = parsed.event.copy(eventName = "示例·15人联赛（第3轮）", eventType = "league", eventRound = 3))
        }
    }
}
