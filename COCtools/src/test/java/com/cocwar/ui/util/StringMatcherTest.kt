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

    @Test
    fun `isLikelySameName 相似度达标判定`() {
        // 张叁 vs 张三：编辑距离 1 / 长度 2 = 0.5 恰好达标
        assertTrue(StringMatcher.isLikelySameName("张叁", "张三"))
        // 王小明 vs 赵小刚：2/3 编辑距离 → 0.33 不达标
        assertEquals(false, StringMatcher.isLikelySameName("王小明", "赵小刚"))
    }

    @Test
    fun `isLikelySameName 单字名互不匹配`() {
        // 单字名不适用一字之差兜底：任意两个不同单字名距离都是 1，兜底会全部误判
        assertEquals(false, StringMatcher.isLikelySameName("明", "朋"))
        assertEquals(false, StringMatcher.isLikelySameName("明", "昭"))
    }

    @Test
    fun `isLikelySameName 相同与空串`() {
        assertTrue(StringMatcher.isLikelySameName("张三", "张三"))
        assertEquals(false, StringMatcher.isLikelySameName("", "张三"))
        // 与 similarity 口径一致：两空串相似度 1
        assertTrue(StringMatcher.isLikelySameName("", ""))
    }

    @Test
    fun `bestLikelyMatch 取相似度最高的合格候选`() {
        // 张叁 vs 张三 = 0.5；张叁 vs 张叁叁 = 编辑距离 1 / 长度 3 → 0.67，取后者
        val match = StringMatcher.bestLikelyMatch("张叁", listOf("张三", "张叁叁"))
        assertEquals("张叁叁", match?.first)
        assertEquals(1f - 1f / 3f, match?.second!!, 0.0001f)
    }

    @Test
    fun `bestLikelyMatch 无合格候选或空候选返回 null`() {
        assertNull(StringMatcher.bestLikelyMatch("王小明", listOf("赵小刚")))
        assertNull(StringMatcher.bestLikelyMatch("张三", emptyList()))
    }
}
