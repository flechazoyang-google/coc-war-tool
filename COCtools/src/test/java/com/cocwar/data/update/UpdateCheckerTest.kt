package com.cocwar.data.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * UpdateChecker.parseReleaseJson 单元测试。
 */
class UpdateCheckerTest {

    private fun releaseJson(
        stableVersion: String? = "v4.8.0",
        stableUrl: String? = "https://cdn.flechazo.icu/COCtools-v4.8.0.apk",
        stableBody: String? = "正式版更新日志",
        previewVersion: String? = "v4.9.0-alpha.1",
        previewUrl: String? = "https://cdn.flechazo.icu/COCtools-v4.9.0-alpha.1.apk",
        previewBody: String? = "测试版更新日志"
    ): String {
        val sb = StringBuilder("{")
        val channels = mutableListOf<String>()
        if (stableVersion != null) {
            channels.add("\"stable\":{\"version\":\"$stableVersion\",\"url\":\"$stableUrl\",\"body\":\"$stableBody\"}")
        }
        if (previewVersion != null) {
            channels.add("\"preview\":{\"version\":\"$previewVersion\",\"url\":\"$previewUrl\",\"body\":\"$previewBody\"}")
        }
        sb.append(channels.joinToString(","))
        sb.append("}")
        return sb.toString()
    }

    // === includePrerelease = false → 只看 stable ===

    @Test
    fun `stable channel returned when prerelease disabled`() {
        val info = UpdateChecker.parseReleaseJson(releaseJson(), includePrerelease = false)
        assertNotNull(info)
        assertEquals("4.8.0", info!!.version)
        assertEquals("https://cdn.flechazo.icu/COCtools-v4.8.0.apk", info.apkUrl)
        assertEquals("正式版更新日志", info.body)
        assertFalse(info.isPrerelease)
    }

    @Test
    fun `preview ignored when prerelease disabled`() {
        val json = releaseJson(stableVersion = "v4.8.0", previewVersion = "v4.9.0-beta.1")
        val info = UpdateChecker.parseReleaseJson(json, includePrerelease = false)
        assertEquals("4.8.0", info!!.version)
        assertFalse(info.isPrerelease)
    }

    @Test
    fun `null when stable missing and prerelease disabled`() {
        val json = releaseJson(stableVersion = null, previewVersion = "v4.9.0-alpha.1")
        assertNull(UpdateChecker.parseReleaseJson(json, includePrerelease = false))
    }

    // === includePrerelease = true → 优先 preview，回退 stable ===

    @Test
    fun `preview channel returned when prerelease enabled`() {
        val info = UpdateChecker.parseReleaseJson(releaseJson(), includePrerelease = true)
        assertNotNull(info)
        assertEquals("4.9.0-alpha.1", info!!.version)
        assertTrue(info.isPrerelease)
    }

    @Test
    fun `falls back to stable when preview missing`() {
        val json = releaseJson(previewVersion = null)
        val info = UpdateChecker.parseReleaseJson(json, includePrerelease = true)
        assertEquals("4.8.0", info!!.version)
        assertFalse(info.isPrerelease)
    }

    @Test
    fun `null when both channels missing`() {
        val json = releaseJson(stableVersion = null, previewVersion = null)
        assertNull(UpdateChecker.parseReleaseJson(json, includePrerelease = true))
    }

    // === 边界情况 ===

    @Test
    fun `malformed JSON returns null`() {
        assertNull(UpdateChecker.parseReleaseJson("not json", includePrerelease = false))
    }

    @Test
    fun `empty object returns null`() {
        assertNull(UpdateChecker.parseReleaseJson("{}", includePrerelease = false))
    }

    @Test
    fun `channel missing url returns null for that channel`() {
        val json = """{"stable":{"version":"v4.8.0","body":"no url"}}"""
        assertNull(UpdateChecker.parseReleaseJson(json, includePrerelease = false))
    }

    @Test
    fun `channel missing version returns null for that channel`() {
        val json = """{"stable":{"url":"https://cdn.flechazo.icu/app.apk","body":"no version"}}"""
        assertNull(UpdateChecker.parseReleaseJson(json, includePrerelease = false))
    }

    @Test
    fun `body truncated to 500 chars`() {
        val longBody = "x".repeat(1000)
        val json = releaseJson(stableBody = longBody)
        val info = UpdateChecker.parseReleaseJson(json, includePrerelease = false)
        assertEquals(500, info!!.body.length)
    }

    @Test
    fun `version prefix v stripped`() {
        val json = """{"stable":{"version":"v4.8.0","url":"https://cdn.flechazo.icu/app.apk","body":""}}"""
        val info = UpdateChecker.parseReleaseJson(json, includePrerelease = false)
        assertEquals("4.8.0", info!!.version)
    }

    @Test
    fun `isPrerelease auto-detected from version string`() {
        val jsonAlpha = """{"stable":{"version":"v4.9.0-alpha.1","url":"https://cdn.flechazo.icu/app.apk","body":""}}"""
        assertTrue(UpdateChecker.parseReleaseJson(jsonAlpha, includePrerelease = false)!!.isPrerelease)

        val jsonBeta = """{"stable":{"version":"v4.9.0-beta.2","url":"https://cdn.flechazo.icu/app.apk","body":""}}"""
        assertTrue(UpdateChecker.parseReleaseJson(jsonBeta, includePrerelease = false)!!.isPrerelease)

        val jsonRc = """{"stable":{"version":"v4.9.0-rc.1","url":"https://cdn.flechazo.icu/app.apk","body":""}}"""
        assertTrue(UpdateChecker.parseReleaseJson(jsonRc, includePrerelease = false)!!.isPrerelease)

        val jsonStable = """{"stable":{"version":"v4.8.0","url":"https://cdn.flechazo.icu/app.apk","body":""}}"""
        assertFalse(UpdateChecker.parseReleaseJson(jsonStable, includePrerelease = false)!!.isPrerelease)
    }
}
