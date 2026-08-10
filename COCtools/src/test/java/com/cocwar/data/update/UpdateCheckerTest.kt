package com.cocwar.data.update

import com.google.gson.JsonArray
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * UpdateChecker 预览版筛选与 release 解析单元测试（与「加入测试计划」口径对齐）。
 */
class UpdateCheckerTest {

    private fun arr(json: String): JsonArray =
        JsonParser.parseString(json).asJsonArray

    private fun release(tag: String, prerelease: Boolean, hasApk: Boolean = true): String {
        val assets = if (hasApk) {
            """[{"name":"COCtools-$tag.apk","browser_download_url":"https://gitee.com/x/y/releases/download/$tag/COCtools-$tag.apk"}]"""
        } else "[]"
        return """{"tag_name":"$tag","prerelease":$prerelease,"body":"说明","assets":$assets}"""
    }

    // === selectTargetRelease ===

    /** 加入测试计划：预览版版本号更高时选中预览版。 */
    @Test
    fun `include prerelease picks newest prerelease`() {
        val list = arr("[${release("v4.3", false)},${release("v4.4-preview", true)}]")
        val target = UpdateChecker.selectTargetRelease(list, includePrerelease = true)
        assertEquals("v4.4-preview", target?.get("tag_name")?.asString)
    }

    /** 未加入测试计划：跳过预览版，选中最新正式版。 */
    @Test
    fun `exclude prerelease picks newest stable`() {
        val list = arr("[${release("v4.3", false)},${release("v4.4-preview", true)}]")
        val target = UpdateChecker.selectTargetRelease(list, includePrerelease = false)
        assertEquals("v4.3", target?.get("tag_name")?.asString)
    }

    /** 全部是预览版且未加入计划 → null（无可选正式版）。 */
    @Test
    fun `all prerelease without opt-in returns null`() {
        val list = arr("[${release("v4.4-preview", true)},${release("v4.5-preview", true)}]")
        assertNull(UpdateChecker.selectTargetRelease(list, includePrerelease = false))
    }

    /** 空列表 → null。 */
    @Test
    fun `empty list returns null`() {
        assertNull(UpdateChecker.selectTargetRelease(arr("[]"), includePrerelease = true))
    }

    /** 非对象元素（异常数据）被跳过不崩溃。 */
    @Test
    fun `malformed entries are skipped`() {
        val list = arr("[42,\"x\",${release("v4.3", false)}]")
        val target = UpdateChecker.selectTargetRelease(list, includePrerelease = true)
        assertEquals("v4.3", target?.get("tag_name")?.asString)
    }

    /** 正式版优先于同版本号的预览版（compareVersion 语义：正式版 > 预览版）。 */
    @Test
    fun `stable wins over same-version prerelease`() {
        val list = arr("[${release("v4.4-preview", true)},${release("v4.4", false)}]")
        val target = UpdateChecker.selectTargetRelease(list, includePrerelease = true)
        assertEquals("v4.4", target?.get("tag_name")?.asString)
    }

    // === releaseToUpdateInfo ===

    @Test
    fun `release parses version and prerelease flag`() {
        val info = UpdateChecker.releaseToUpdateInfo(
            JsonParser.parseString(release("v4.4-preview", true)).asJsonObject
        )
        assertEquals("4.4-preview", info?.version)
        assertTrue(info?.isPrerelease == true)
        assertEquals(
            "https://gitee.com/x/y/releases/download/v4.4-preview/COCtools-v4.4-preview.apk",
            info?.apkUrl
        )
    }

    @Test
    fun `release without apk returns null`() {
        assertNull(
            UpdateChecker.releaseToUpdateInfo(
                JsonParser.parseString(release("v4.3", false, hasApk = false)).asJsonObject
            )
        )
    }
}
