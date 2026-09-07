package com.cocwar.data.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 花名册识别提示词构建测试：名单注入、易混名提醒、多图合并约定。 */
class RosterOcrPromptsTest {

    @Test
    fun `输出表头固定为 名字 职位`() {
        assertEquals("名字,职位", RosterOcrPrompts.HEADER)
        assertTrue(RosterOcrPrompts.build(listOf("张三")).contains(RosterOcrPrompts.HEADER))
    }

    @Test
    fun `注入当前花名册名单`() {
        val names = listOf("陈平安", "宁姚", "裴钱", "曹慈")
        val prompt = RosterOcrPrompts.build(names)
        names.forEach { assertTrue("应注入 $it", prompt.contains(it)) }
        assertTrue(prompt.contains("共 ${names.size} 人"))
    }

    @Test
    fun `空名单时给出通用提示词且不出现名单段落`() {
        val prompt = RosterOcrPrompts.build(emptyList())
        assertTrue(prompt.contains(RosterOcrPrompts.HEADER))
        assertFalse(prompt.contains("当前在册名单"))
    }

    @Test
    fun `易混名字对给出逐字核对提醒`() {
        // 余味 / 余昧：等长且编辑距离为 1 → 判定为易混
        val prompt = RosterOcrPrompts.build(listOf("余味", "余昧"))
        assertTrue(prompt.contains("易混名字提醒"))
        assertTrue(prompt.contains("余味 ↔ 余昧"))
    }

    @Test
    fun `编号变体不作为易混名提醒`() {
        // 余味 / 余味1 是两名不同成员，提示逐字核对会诱导模型合并，必须排除
        val prompt = RosterOcrPrompts.build(listOf("余味", "余味1"))
        assertFalse(prompt.contains("易混名字提醒"))
    }

    @Test
    fun `名单去重且忽略空白`() {
        val prompt = RosterOcrPrompts.build(listOf(" 张三 ", "张三", "", "李四"))
        assertTrue(prompt.contains("共 2 人"))
    }

    @Test
    fun `多张截图要求合并去重`() {
        val prompt = RosterOcrPrompts.build(listOf("张三"))
        assertTrue(prompt.contains("多张截图"))
        assertTrue(prompt.contains("只输出一次"))
    }

    @Test
    fun `职位限定为四种且看不清时填成员`() {
        val prompt = RosterOcrPrompts.build(listOf("张三"))
        assertTrue(prompt.contains("首领"))
        assertTrue(prompt.contains("副首领"))
        assertTrue(prompt.contains("长老"))
        assertTrue(prompt.contains("成员"))
    }
}
