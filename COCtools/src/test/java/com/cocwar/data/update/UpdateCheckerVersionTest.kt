package com.cocwar.data.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** UpdateChecker.compareVersion 版本比较测试（正式版 > 预览版，逐段数字比较）。 */
class UpdateCheckerVersionTest {

    @Test
    fun `相同版本为 0`() {
        assertEquals(0, UpdateChecker.compareVersion("1.2.3", "1.2.3"))
        assertEquals(0, UpdateChecker.compareVersion("v1.2.3", "1.2.3"))
    }

    @Test
    fun `数字段大小比较`() {
        assertTrue(UpdateChecker.compareVersion("1.3.0", "1.2.9") > 0)
        assertTrue(UpdateChecker.compareVersion("1.2.9", "1.3.0") < 0)
    }

    @Test
    fun `段数不同-缺段按 0 比较`() {
        assertTrue(UpdateChecker.compareVersion("2.0", "1.9.9") > 0)
        assertTrue(UpdateChecker.compareVersion("1.2", "1.2.1") < 0)
        assertTrue(UpdateChecker.compareVersion("1.2.0", "1.2") == 0)
    }

    @Test
    fun `数值相等时正式版大于预览版`() {
        assertTrue(UpdateChecker.compareVersion("1.2", "1.2-beta") > 0)
        assertTrue(UpdateChecker.compareVersion("1.2-alpha", "1.2") < 0)
    }

    @Test
    fun `多段与预览后缀混合`() {
        assertTrue(UpdateChecker.compareVersion("v4.5-preview", "v4.4") > 0)
        assertTrue(UpdateChecker.compareVersion("v4.5-preview", "v4.5") < 0)
        assertTrue(UpdateChecker.compareVersion("v4.5-preview", "v4.5-preview") == 0)
    }
}
