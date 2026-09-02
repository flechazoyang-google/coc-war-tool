package com.cocwar.data.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** OcrPrompts.buildPrompt 花名册注入测试。 */
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
    fun `注入后保留默认格式段与匹配规则`() {
        val prompt = OcrPrompts.buildPrompt(listOf("张三"))
        assertTrue(prompt.startsWith(OcrPrompts.DEFAULT))
        assertTrue(prompt.contains("名单匹配规则"))
        assertTrue(prompt.contains("禁止臆造名单中不存在的名字"))
    }

    @Test
    fun `重复与首尾空白名字去重清理`() {
        val prompt = OcrPrompts.buildPrompt(listOf(" 张三 ", "张三", ""))
        assertTrue(prompt.contains("共 1 人"))
        assertEquals(1, Regex("(?m)^张三$").findAll(prompt).count())
    }
}
