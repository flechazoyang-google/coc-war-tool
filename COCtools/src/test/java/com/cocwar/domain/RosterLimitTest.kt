package com.cocwar.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test

/** 花名册 50 人上限校验测试。 */
class RosterLimitTest {

    @Test
    fun `恰好等于上限时通过`() {
        assertNull(checkRosterLimit(ROSTER_LIMIT))
    }

    @Test
    fun `低于上限时通过`() {
        assertNull(checkRosterLimit(0))
        assertNull(checkRosterLimit(1))
        assertNull(checkRosterLimit(49))
    }

    @Test
    fun `超过上限时给出提示`() {
        val message = checkRosterLimit(ROSTER_LIMIT + 1)
        assertNotNull(message)
        // 提示里带上实际人数与上限，便于用户核对识别结果
        assertEquals(true, message!!.contains("51"))
        assertEquals(true, message.contains("50"))
    }

    @Test
    fun `上限为 COC 部落人数上限 50`() {
        assertEquals(50, ROSTER_LIMIT)
    }
}
