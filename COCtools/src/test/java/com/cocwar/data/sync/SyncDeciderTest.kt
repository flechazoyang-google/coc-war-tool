package com.cocwar.data.sync

import com.cocwar.data.sync.SyncDecider.SyncAction
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * SyncDecider 决策表单元测试（与 docs/RULES.md §6 对齐）。
 */
class SyncDeciderTest {

    private val a = "fp-a"
    private val b = "fp-b"

    // === 无数据分支 ===

    @Test
    fun `both empty is up to date`() {
        assertEquals(SyncAction.UP_TO_DATE, SyncDecider.decide(null, null, null, null))
    }

    @Test
    fun `local empty pulls remote`() {
        assertEquals(SyncAction.PULL_REMOTE, SyncDecider.decide(null, a, null, null))
    }

    @Test
    fun `remote missing pushes local`() {
        assertEquals(SyncAction.PUSH_LOCAL, SyncDecider.decide(a, null, null, null))
    }

    // === 指纹相同 ===

    @Test
    fun `same fingerprint is up to date`() {
        assertEquals(SyncAction.UP_TO_DATE, SyncDecider.decide(a, a, null, null))
        assertEquals(SyncAction.UP_TO_DATE, SyncDecider.decide(a, a, a, a))
        assertEquals(SyncAction.UP_TO_DATE, SyncDecider.decide(a, a, b, b))
    }

    // === 仅一端变化 ===

    @Test
    fun `only local changed pushes`() {
        assertEquals(SyncAction.PUSH_LOCAL, SyncDecider.decide(b, a, a, a))
    }

    @Test
    fun `only remote changed pulls`() {
        assertEquals(SyncAction.PULL_REMOTE, SyncDecider.decide(a, b, a, a))
    }

    // === 首次同步（无上次指纹）且两端不同 → 冲突 ===

    @Test
    fun `first sync with both sides different conflicts`() {
        assertEquals(SyncAction.CONFLICT, SyncDecider.decide(a, b, null, null))
    }

    @Test
    fun `first sync with only local record conflicts`() {
        assertEquals(SyncAction.CONFLICT, SyncDecider.decide(b, a, a, null))
    }

    // === 两端都变化 → 冲突 ===

    @Test
    fun `both changed conflicts`() {
        assertEquals(SyncAction.CONFLICT, SyncDecider.decide(b, c(), a, a))
    }

    @Test
    fun `local reverted to previous remote still counts as local change`() {
        // 本地从 a 改到 b 又改回 a？指纹相等已在上层短路；此处验证端点状态而非历史
        assertEquals(SyncAction.PUSH_LOCAL, SyncDecider.decide(a, b, b, b))
    }

    private fun c(): String = "fp-c"
}
