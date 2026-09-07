package com.cocwar.data.ocr

import com.cocwar.data.csv.CsvImporter
import com.cocwar.data.model.EVENT_TYPE_LEAGUE
import com.cocwar.data.model.EVENT_TYPE_WAR
import com.cocwar.data.model.ParseResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 端到端往返测试：模拟 v2 提示词的典型输出（含花名册纠正、易混名、联赛留空列、Markdown 围栏）
 * → [OcrCsvExtractor] 剥围栏 → [CsvImporter.parse] 解析 → 断言字段无损。
 *
 * 这是 v2 提示词相对 v1 新增的格式不变量测试，约束新引入的列行为不会让 CsvImporter 静默回归。
 */
class OcrPromptRoundTripTest {

    @Test
    fun `联赛模式 进攻2 留空列仍可解析`() {
        // v2 联赛 prompt 显式要求 "进攻2摧毁率" 列留空，模型会输出末尾逗号（5 列：最后一列为空）
        val raw = """
            成员名,排名,总星数,进攻1摧毁率,进攻2摧毁率
            张三,1,3,100,
            李四,2,2,87,
            王五,3,0,0,
        """.trimIndent()

        val csv = OcrCsvExtractor.extract(raw)
        val ok = CsvImporter.parse(
            text = csv,
            slotCount = 1,
            eventType = EVENT_TYPE_LEAGUE
        ) as ParseResult.Success
        val members = ok.data.members
        assertEquals(3, members.size)
        assertEquals(listOf("张三", "李四", "王五"), members.map { it.playerName })
        assertEquals(listOf(3, 2, 0), members.map { it.totalStars })
        // slotCount=1：只有进攻1 有效，进攻2 留空
        assertEquals(100, members[0].attacks[0].destructionPercentage)
        assertEquals(87, members[1].attacks[0].destructionPercentage)
    }

    @Test
    fun `联赛模式 省略末尾逗号仍可解析`() {
        // 部分模型可能直接省略末尾逗号，只输出 4 列；同样要能解析
        val raw = """
            成员名,排名,总星数,进攻1摧毁率,进攻2摧毁率
            张三,1,3,100
            李四,2,2,87
        """.trimIndent()

        val csv = OcrCsvExtractor.extract(raw)
        val ok = CsvImporter.parse(
            text = csv,
            slotCount = 1,
            eventType = EVENT_TYPE_LEAGUE
        ) as ParseResult.Success
        assertEquals(listOf("张三", "李四"), ok.data.members.map { it.playerName })
    }

    @Test
    fun `部落战模式 两列摧毁率都填可解析`() {
        val raw = """
            成员名,排名,总星数,进攻1摧毁率,进攻2摧毁率
            桑平安,1,6,100,100
            混子祭天,2,4,98,72
            妄司逸,3,0,0,0
        """.trimIndent()

        val csv = OcrCsvExtractor.extract(raw)
        val ok = CsvImporter.parse(
            text = csv,
            slotCount = 2,
            eventType = EVENT_TYPE_WAR
        ) as ParseResult.Success
        val members = ok.data.members
        assertEquals(3, members.size)
        // 花名册纠正后的名字原样保留
        assertEquals("桑平安", members[0].playerName)
        assertEquals(6, members[0].totalStars)
        assertEquals(100, members[0].attacks[0].destructionPercentage)
        assertEquals(100, members[0].attacks[1].destructionPercentage)
    }

    @Test
    fun `Markdown 围栏与前置解释不影响解析`() {
        // v2 提示词要求"禁止围栏"，但模型偶发仍会输出围栏或多余解释——extractor 必须兜住
        val raw = """
            下面是识别结果：

            ```csv
            成员名,排名,总星数,进攻1摧毁率,进攻2摧毁率
            张三,1,3,100,
            李四,2,2,87,
            ```
        """.trimIndent()

        val csv = OcrCsvExtractor.extract(raw)
        assertTrue(csv.startsWith("成员名,排名"))
        val ok = CsvImporter.parse(
            text = csv,
            slotCount = 1,
            eventType = EVENT_TYPE_LEAGUE
        ) as ParseResult.Success
        assertEquals(2, ok.data.members.size)
    }

    @Test
    fun `联赛输出经 OcrValidation 不会因进攻2空列误报`() {
        // 锁定 v2 联赛留空列在 OcrValidation 不产生误报（摧毁率空应跳过）
        val raw = """
            成员名,排名,总星数,进攻1摧毁率,进攻2摧毁率
            张三,1,3,100,
            李四,2,2,87,
        """.trimIndent()
        val csv = OcrCsvExtractor.extract(raw)
        assertEquals(emptyList<OcrValidation.RowIssue>(), OcrValidation.validate(csv))
    }

    @Test
    fun `同名编号成员作为两个独立成员入库`() {
        // 余味 / 余味1 是编号区分的两名不同成员：都有各自的一行，不能合并
        val raw = """
            成员名,排名,总星数,进攻1摧毁率,进攻2摧毁率
            余味,1,5,100,80
            余味1,2,3,90,60
        """.trimIndent()
        val csv = OcrCsvExtractor.extract(raw)
        val ok = CsvImporter.parse(
            text = csv,
            slotCount = 2,
            eventType = EVENT_TYPE_WAR
        ) as ParseResult.Success
        assertEquals(listOf("余味", "余味1"), ok.data.members.map { it.playerName })
        assertEquals(listOf(1, 2), ok.data.members.map { it.rank })
    }

    @Test
    fun `易混名在提示词下被模型纠正为花名册写法后能正常入库`() {
        // 验证「图中写「请詶」/花名册写「请訓」」场景：模型按 v2 易混提醒纠正后写入，请訓 入库
        val raw = """
            成员名,排名,总星数,进攻1摧毁率,进攻2摧毁率
            请訓,1,3,100,80
            余味,2,0,0,0
        """.trimIndent()
        val csv = OcrCsvExtractor.extract(raw)
        val ok = CsvImporter.parse(
            text = csv,
            slotCount = 2,
            eventType = EVENT_TYPE_WAR,
            rosterRoles = mapOf("请訓" to "长老", "余味" to "副首领")
        ) as ParseResult.Success
        val byName = ok.data.members.associateBy { it.playerName }
        assertNotNull(byName["请訓"])
        assertNotNull(byName["余味"])
        // 角色以花名册为准
        assertEquals("长老", byName["请訓"]!!.role)
        assertEquals("副首领", byName["余味"]!!.role)
    }
}