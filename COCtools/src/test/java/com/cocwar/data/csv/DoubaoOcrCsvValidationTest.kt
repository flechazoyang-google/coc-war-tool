package com.cocwar.data.csv

import com.cocwar.data.parser.WarJsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 临时结构校验测试（豆包识图验证）：把模型输出的 CSV（此处为 mock 识别结果，
 * 含 3 处模拟识别误差：名字错字 1、摧毁率错 2）喂给 CsvImporter.parse，
 * 验证能够解析、列不错位、可入库——只校验结构与导入链路，不评估准确度。
 */
class DoubaoOcrCsvValidationTest {

    private val modelCsv = """
        成员名,排名,总星数,进攻1摧毁率,进攻2摧毁率
        陈平安,1,4,92,95
        混子祭天,2,6,100,100
        压爆了,3,6,100,100
        一剑封喉,4,6,100,100
        夜空之刃,5,6,100,100
        狂暴野猪,6,5,92,100
        雷霆战将,7,6,100,100
        清风明月,8,5,100,95
        铁壁铜墙,9,6,100,100
        暗影猎手,10,6,100,93
        九天揽月,11,5,92,100
        破阵之矛,12,6,100,100
        苍狼啸月,13,6,100,100
        烈日灼心,14,6,100,100
        寒冰法师,15,5,100,95
        无敌小钢炮,16,5,92,100
        随风飘零,17,6,100,100
        龙吟九霄,18,6,100,100
        疾风骤雨,19,0,0,0
        烈欲红唇,20,6,100,100
        沉默刺客,21,5,92,100
        王者归来,22,5,100,95
        逐风少年,23,6,100,100
        不灭星辰,24,6,98,100
        残月孤星,25,6,100,100
        霸王别姬,26,5,92,100
        迷途书生,27,6,100,100
        佛系养生,28,0,0,0
        摸鱼达人,29,0,0,0
        划水冠军,30,0,0,0
    """.trimIndent()

    @Test
    fun `model csv parses as success with 30 members`() {
        val result = CsvImporter.parse(modelCsv, slotCount = 2, eventType = "war")
        assertTrue(result is WarJsonParser.ParseResult.Success)
        val members = (result as WarJsonParser.ParseResult.Success).data.members
        assertEquals(30, members.size)
    }

    @Test
    fun `rank aligns with row order and all members have 2 attack slots`() {
        val result = CsvImporter.parse(modelCsv, slotCount = 2, eventType = "war")
        val members = (result as WarJsonParser.ParseResult.Success).data.members
        members.forEachIndexed { index, m ->
            assertEquals("列错位：第 ${index + 1} 行", index + 1, m.rank)
            assertEquals(2, m.attacks.size)
        }
    }

    @Test
    fun `spot check values incl non-attacker and typo name`() {
        val result = CsvImporter.parse(modelCsv, slotCount = 2, eventType = "war")
        val members = (result as WarJsonParser.ParseResult.Success).data.members

        val chen = members.first { it.playerName == "陈平安" }
        assertEquals(4, chen.totalStars)
        assertEquals(listOf(92, 95), chen.attacks.map { it.destructionPercentage })

        // 未进攻成员（疾风骤雨，rank 19）：0 星、双列 0 摧毁率
        val nonAttacker = members.first { it.playerName == "疾风骤雨" }
        assertEquals(0, nonAttacker.totalStars)
        assertTrue(nonAttacker.attacks.all { it.destructionPercentage == 0 })

        // 模型错字名字照常入库（准确度由比对工具评估，不阻塞导入）
        val typo = members.first { it.playerName == "烈欲红唇" }
        assertEquals(20, typo.rank)
        assertEquals(6, typo.totalStars)
        assertEquals(listOf(100, 100), typo.attacks.map { it.destructionPercentage })
    }
}
