package com.cocwar.data.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** UpdateChecker.compareVersion 版本比较测试（SemVer：alpha < beta < rc < 正式版）。 */
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
    fun `正式版大于 prerelease`() {
        assertTrue(UpdateChecker.compareVersion("1.2.0", "1.2.0-alpha.1") > 0)
        assertTrue(UpdateChecker.compareVersion("1.2.0-beta.1", "1.2.0") < 0)
    }

    @Test
    fun `alpha 小于 beta 小于 rc`() {
        assertTrue(UpdateChecker.compareVersion("4.9.0-alpha.1", "4.9.0-beta.1") < 0)
        assertTrue(UpdateChecker.compareVersion("4.9.0-beta.1", "4.9.0-rc.1") < 0)
        assertTrue(UpdateChecker.compareVersion("4.9.0-alpha.1", "4.9.0-rc.1") < 0)
    }

    @Test
    fun `同阶段按序号比较`() {
        assertTrue(UpdateChecker.compareVersion("4.9.0-alpha.2", "4.9.0-alpha.1") > 0)
        assertTrue(UpdateChecker.compareVersion("4.9.0-beta.1", "4.9.0-beta.3") < 0)
        assertEquals(0, UpdateChecker.compareVersion("4.9.0-rc.1", "4.9.0-rc.1"))
    }

    @Test
    fun `不同数字段时忽略 prerelease 阶段`() {
        assertTrue(UpdateChecker.compareVersion("4.9.0-alpha.1", "4.8.0") > 0)
        assertTrue(UpdateChecker.compareVersion("4.8.0", "4.9.0-rc.1") < 0)
    }

    @Test
    fun `isPrereleaseVersion 检测`() {
        assertTrue(UpdateChecker.isPrereleaseVersion("4.9.0-alpha.1"))
        assertTrue(UpdateChecker.isPrereleaseVersion("v4.9.0-beta.2"))
        assertTrue(UpdateChecker.isPrereleaseVersion("4.9.0-rc.1"))
        assertFalse(UpdateChecker.isPrereleaseVersion("4.8.0"))
        assertFalse(UpdateChecker.isPrereleaseVersion("v4.8.0"))
    }

    @Test
    fun `prereleaseLabel 返回正确中文标签`() {
        assertEquals("（内部测试版）", UpdateChecker.prereleaseLabel("4.9.0-alpha.1"))
        assertEquals("（公开测试版）", UpdateChecker.prereleaseLabel("4.9.0-beta.1"))
        assertEquals("（候选版）", UpdateChecker.prereleaseLabel("4.9.0-rc.1"))
        assertEquals("", UpdateChecker.prereleaseLabel("4.8.0"))
    }
}
