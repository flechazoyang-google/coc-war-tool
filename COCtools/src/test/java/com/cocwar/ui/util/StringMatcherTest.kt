package com.cocwar.ui.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** StringMatcher（Levenshtein 编辑距离）测试。 */
class StringMatcherTest {

    @Test
    fun `levenshtein 相同字符串为 0`() {
        assertEquals(0, StringMatcher.levenshtein("陈平安", "陈平安"))
        assertEquals(0, StringMatcher.levenshtein("", ""))
    }

    @Test
    fun `levenshtein 空串与非空串为另一串长度`() {
        assertEquals(3, StringMatcher.levenshtein("", "abc"))
        assertEquals(3, StringMatcher.levenshtein("abc", ""))
    }

    @Test
    fun `levenshtein 单字符替换为 1`() {
        assertEquals(1, StringMatcher.levenshtein("陈平安", "陈平按"))
        assertEquals(1, StringMatcher.levenshtein("kitten", "sitten"))
    }

    @Test
    fun `levenshtein 插入删除计数`() {
        assertEquals(1, StringMatcher.levenshtein("book", "books"))
        assertEquals(2, StringMatcher.levenshtein("book", "bookx1"))
    }

    @Test
    fun `similarity 完全相同为 1`() {
        assertEquals(1f, StringMatcher.similarity("abc", "abc"))
    }

    @Test
    fun `similarity 完全不相关接近 0`() {
        assertTrue(StringMatcher.similarity("abc", "xyz") < 0.2f)
    }

    @Test
    fun `similarity 小编辑距离高分`() {
        // 编辑距离 1 / 长度 3 → 1 - 1/3 ≈ 0.67
        assertEquals(1f - 1f / 3f, StringMatcher.similarity("abc", "abd"))
    }

    @Test
    fun `bestMatch 找到高于阈值的候选`() {
        val match = StringMatcher.bestMatch("陈平安", listOf("陈平安", "张三", "李四"), threshold = 0.9f)
        assertEquals("陈平安", match?.first)
        assertEquals(1f, match?.second)
    }

    @Test
    fun `bestMatch 无高于阈值候选返回 null`() {
        val match = StringMatcher.bestMatch("陈平安", listOf("张三", "李四"), threshold = 0.9f)
        assertNull(match)
    }

    @Test
    fun `bestMatch 空候选返回 null`() {
        assertNull(StringMatcher.bestMatch("x", emptyList()))
    }
}
