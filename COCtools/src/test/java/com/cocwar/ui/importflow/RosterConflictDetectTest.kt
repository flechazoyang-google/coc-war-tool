package com.cocwar.ui.importflow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 入册确认闸 detectRosterConflicts 纯函数测试。 */
class RosterConflictDetectTest {

    @Test
    fun `两字名一字之差相似度达标触发`() {
        // 张叁 vs 张三：编辑距离 1 / 长度 2 = 0.5
        val result = detectRosterConflicts(listOf("张叁"), listOf("张三"))
        assertEquals(1, result.size)
        assertEquals("张三", result[0].suggestion)
        assertEquals(0.5f, result[0].score, 0.0001f)
    }

    @Test
    fun `单字名互不匹配不触发`() {
        // 单字名一字之差兜底已移除：明/朋 距离 1 但会误判所有单字名，不触发
        val result = detectRosterConflicts(listOf("明"), listOf("朋"))
        assertTrue(result.isEmpty())
    }

    @Test
    fun `三字名一字之差触发`() {
        // 王小朋 vs 王小明：相似度 2/3 ≈ 0.67
        val result = detectRosterConflicts(listOf("王小朋"), listOf("王小明"))
        assertEquals(1, result.size)
        assertEquals("王小明", result[0].suggestion)
    }

    @Test
    fun `完全不同的名字不触发`() {
        val result = detectRosterConflicts(listOf("张三"), listOf("王五", "赵小刚"))
        assertTrue(result.isEmpty())
    }

    @Test
    fun `两字差三字名不触发`() {
        // 王小明 vs 赵小刚：编辑距离 2 / 长度 3 ≈ 0.33 < 0.5，等长但差两字
        val result = detectRosterConflicts(listOf("王小明"), listOf("赵小刚"))
        assertTrue(result.isEmpty())
    }

    @Test
    fun `已在花名册的名字跳过`() {
        val result = detectRosterConflicts(listOf("张三"), listOf("张三", "李四"))
        assertTrue(result.isEmpty())
    }

    @Test
    fun `多候选取相似度最高者`() {
        // 张叁 vs 张三 = 0.5；张叁 vs 张叁叁 = 1/3 编辑距离 → 2/3 ≈ 0.67，取后者
        val result = detectRosterConflicts(listOf("张叁"), listOf("张三", "张叁叁"))
        assertEquals(1, result.size)
        assertEquals("张叁叁", result[0].suggestion)
    }

    @Test
    fun `重复出现的新名字只报一次`() {
        val result = detectRosterConflicts(listOf("张叁", "张叁"), listOf("张三"))
        assertEquals(1, result.size)
    }

    @Test
    fun `空花名册返回空`() {
        assertTrue(detectRosterConflicts(listOf("张三"), emptyList()).isEmpty())
    }

    @Test
    fun `多个新名字逐个检测且保持顺序`() {
        val result = detectRosterConflicts(listOf("李四四", "张叁"), listOf("张三", "李四", "王五"))
        assertEquals(2, result.size)
        assertEquals("李四", result[0].suggestion)
        assertEquals("张三", result[1].suggestion)
    }
}
