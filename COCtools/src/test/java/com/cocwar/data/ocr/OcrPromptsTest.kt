package com.cocwar.data.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** OcrPrompts：花名册注入 / 赛事模式 / 形近名提醒 测试。 */
class OcrPromptsTest {

    @Test
    fun `空名单返回默认提示词`() {
        assertEquals(OcrPrompts.DEFAULT, OcrPrompts.buildPrompt(emptyList()))
    }

    @Test
    fun `全空白名单视为空返回默认提示词`() {
        assertEquals(OcrPrompts.DEFAULT, OcrPrompts.buildPrompt(listOf("", "  ")))
    }

    @Test
    fun `注入名单包含全部成员与人数`() {
        val prompt = OcrPrompts.buildPrompt(listOf("张三", "李四", "王五"))
        assertTrue(prompt.contains("共 3 人"))
        assertTrue(prompt.contains("张三"))
        assertTrue(prompt.contains("李四"))
        assertTrue(prompt.contains("王五"))
    }

    @Test
    fun `重复与首尾空白名字去重清理`() {
        val prompt = OcrPrompts.buildPrompt(listOf(" 张三 ", "张三", ""))
        assertTrue(prompt.contains("共 1 人"))
        assertEquals(1, Regex("(?m)^张三$").findAll(prompt).count())
    }

    @Test
    fun `提示词包含表头与严格输出约束`() {
        val prompt = OcrPrompts.DEFAULT
        assertTrue(prompt.contains(OcrPrompts.HEADER))
        assertTrue(prompt.contains("禁止任何解释"))
        assertTrue(prompt.contains("Markdown 代码块围栏"))
        assertTrue(prompt.contains("成员名,排名,总星数,进攻1摧毁率,进攻2摧毁率"))
    }

    @Test
    fun `提示词包含独立观察值与范围核对约束`() {
        val prompt = OcrPrompts.DEFAULT
        // 独立观察值：总星数与摧毁率无换算关系，禁止互推
        assertTrue(prompt.contains("独立观察值"))
        assertTrue(prompt.contains("不要用摧毁率反推星数"))
        assertTrue(prompt.contains("不要用星数反推摧毁率"))
        // 范围核对（不做数值互推）
        assertTrue(prompt.contains("0-6"))
        assertTrue(prompt.contains("0-100"))
        // -1 是合法的「看不清」标记
        assertTrue(prompt.contains("-1"))
        assertTrue(prompt.contains("看不清"))
    }

    @Test
    fun `联赛模式要求进攻2留空`() {
        val prompt = OcrPrompts.buildPrompt(listOf("张三"), com.cocwar.data.model.EVENT_TYPE_LEAGUE)
        assertTrue(prompt.contains("联赛"))
        assertTrue(prompt.contains("进攻2摧毁率」列必须留空"))
    }

    @Test
    fun `部落战模式要求两列都填`() {
        val prompt = OcrPrompts.buildPrompt(listOf("张三"), com.cocwar.data.model.EVENT_TYPE_WAR)
        assertTrue(prompt.contains("两列都要填写"))
    }

    @Test
    fun `未知事件类型回落到自动判断`() {
        assertEquals(OcrPrompts.WarMode.AUTO, OcrPrompts.modeOf(null))
        assertEquals(OcrPrompts.WarMode.AUTO, OcrPrompts.modeOf("unknown"))
        assertEquals(OcrPrompts.WarMode.WAR, OcrPrompts.modeOf("war"))
        assertEquals(OcrPrompts.WarMode.LEAGUE, OcrPrompts.modeOf("league"))
    }

    @Test
    fun `形近名生成易混提醒`() {
        val pair = OcrPrompts.findConfusables(listOf("张三", "张五"))
        assertEquals(listOf("张三" to "张五"), pair)

        val prompt = OcrPrompts.buildPrompt(listOf("张三", "张五"))
        assertTrue(prompt.contains("易混名字提醒"))
        assertTrue(prompt.contains("张三 ↔ 张五"))
    }

    @Test
    fun `大小写差异的名字判定为易混`() {
        val pairs = OcrPrompts.findConfusables(listOf("GuoAn", "GuOAN", "李四"))
        assertTrue(pairs.any { it.first == "GuoAn" && it.second == "GuOAN" })
    }

    @Test
    fun `无易混名时不生成提醒段`() {
        val prompt = OcrPrompts.buildPrompt(listOf("张三", "李四", "王五"))
        assertFalse(prompt.contains("易混名字提醒"))
    }

    @Test
    fun `同名编号变体不算易混名`() {
        // 余味 / 余味1 是按顺序编号区分的两名不同成员，提示"逐字核对"会诱导模型合并成一人
        assertEquals(emptyList<Pair<String, String>>(), OcrPrompts.findConfusables(listOf("余味", "余味1")))
        assertEquals(emptyList<Pair<String, String>>(), OcrPrompts.findConfusables(listOf("余味1", "余味2")))
        val prompt = OcrPrompts.buildPrompt(listOf("余味", "余味1"))
        assertFalse(prompt.contains("易混名字提醒"))
    }

    @Test
    fun `提示词要求原样保留编号后缀`() {
        // 「成员名转录」段始终输出：编号必须原样保留
        val base = OcrPrompts.DEFAULT
        assertTrue(base.contains("区分编号"))
        assertTrue(base.contains("不要当成笔误删除"))
        // 「名单匹配规则」段仅在注入花名册时输出：编号不同即不同成员
        val withRoster = OcrPrompts.buildPrompt(listOf("余味", "余味1"))
        assertTrue(withRoster.contains("末尾数字编号不同 = 不同成员"))
        assertTrue(withRoster.contains("不要相互纠正、不要合并成一个人"))
    }

    @Test
    fun `名单超长时截断并提示`() {
        val names = (1..70).map { "成员$it" }
        val prompt = OcrPrompts.buildPrompt(names)
        assertTrue(prompt.contains("共 60 人"))
        assertTrue(prompt.contains("仅列出前 60 位"))
        assertFalse(prompt.contains("成员70"))
    }

    @Test
    fun `注入名单后仍保留全部格式与规则段`() {
        val prompt = OcrPrompts.buildPrompt(listOf("张三"))
        assertTrue(prompt.contains("# 角色"))
        assertTrue(prompt.contains("# 输出格式"))
        assertTrue(prompt.contains("# 字段口径"))
        assertTrue(prompt.contains("# 自洽性检查"))
        assertTrue(prompt.contains("# 成员名转录"))
        assertTrue(prompt.contains("# 名单匹配规则"))
        assertTrue(prompt.contains("禁止臆造名单中不存在"))
    }
}
