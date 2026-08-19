package com.cocwar.data.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * OcrValidation 合法性校验纯函数测试。
 */
class OcrValidationTest {

    @Test
    fun `valid rows produce no issues`() {
        val csv = """
            成员名,排名,总星数,进攻1摧毁率,进攻2摧毁率
            陈平安,1,4,92,95
            混子祭天,2,6,100,100
            希希,3,0,0,0
        """.trimIndent()
        assertTrue(OcrValidation.validate(csv).isEmpty())
    }

    @Test
    fun `star out of range flagged`() {
        val csv = "成员名,排名,总星数,进攻1摧毁率,进攻2摧毁率\n不用醉微醺就好啦,15,12,100,100"
        val issues = OcrValidation.validate(csv)
        assertEquals(1, issues.size)
        assertEquals("总星数", issues[0].field)
        assertEquals("12", issues[0].value)
        assertEquals(15, issues[0].rank)
        assertTrue(issues[0].message.contains("0-6"))
    }

    @Test
    fun `non numeric star flagged`() {
        val csv = "成员名,排名,总星数,进攻1摧毁率,进攻2摧毁率\n张三,1,abc,100,100"
        val issues = OcrValidation.validate(csv)
        assertEquals(1, issues.size)
        assertEquals("总星数", issues[0].field)
        assertTrue(issues[0].message.contains("不是数字"))
    }

    @Test
    fun `destruction out of range or non numeric flagged`() {
        val csv = "成员名,排名,总星数,进攻1摧毁率,进攻2摧毁率\n" +
            "甲,1,6,-1,101\n乙,2,6,abc,99"
        val issues = OcrValidation.validate(csv)
        // 甲: -1 与 101 各一条；乙: abc 一条
        assertEquals(3, issues.size)
        assertTrue(issues.all { it.field.contains("摧毁率") })
    }

    @Test
    fun `destruction with percent accepted`() {
        val csv = "成员名,排名,总星数,进攻1摧毁率,进攻2摧毁率\n张三,1,6,100%,100%"
        assertTrue(OcrValidation.validate(csv).isEmpty())
    }

    @Test
    fun `header skipped and blank input empty`() {
        assertTrue(OcrValidation.validate("").isEmpty())
        assertTrue(OcrValidation.validate("成员名,排名,总星数,进攻1摧毁率,进攻2摧毁率").isEmpty())
    }
}
