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
        stableVersion: String? = "v4.9.0",
        stableUrl: String? = "https://cdn.flechazo.icu/COCtools-stable.apk",
        stableBody: String? = "正式版更新日志",
        alphaVersion: String? = null,
        alphaUrl: String? = null,
        alphaBody: String? = null,
        betaVersion: String? = null,
        betaUrl: String? = null,
        betaBody: String? = null,
        rcVersion: String? = "v4.9.0-rc.1",
        rcUrl: String? = "https://cdn.flechazo.icu/COCtools-rc.apk",
        rcBody: String? = "候选版更新日志",
        previewVersion: String? = null,
        previewUrl: String? = null,
        previewBody: String? = null
    ): String {
        val sb = StringBuilder("{")
        val channels = mutableListOf<String>()
        if (stableVersion != null) {
            channels.add("\"stable\":{\"version\":\"$stableVersion\",\"url\":\"$stableUrl\",\"body\":\"$stableBody\"}")
        }
        if (alphaVersion != null) {
            channels.add("\"alpha\":{\"version\":\"$alphaVersion\",\"url\":\"$alphaUrl\",\"body\":\"$alphaBody\"}")
        }
        if (betaVersion != null) {
            channels.add("\"beta\":{\"version\":\"$betaVersion\",\"url\":\"$betaUrl\",\"body\":\"$betaBody\"}")
        }
        if (rcVersion != null) {
            channels.add("\"rc\":{\"version\":\"$rcVersion\",\"url\":\"$rcUrl\",\"body\":\"$rcBody\"}")
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
        assertEquals("4.9.0", info!!.version)
        assertFalse(info.isPrerelease)
    }

    @Test
    fun `prerelease channels ignored when prerelease disabled`() {
        val json = releaseJson(alphaVersion = "v4.10.0-alpha.1", alphaUrl = "https://cdn/a.apk")
        val info = UpdateChecker.parseReleaseJson(json, includePrerelease = false)
        assertEquals("4.9.0", info!!.version)
    }

    @Test
    fun `null when stable missing and prerelease disabled`() {
        val json = releaseJson(stableVersion = null, rcVersion = "v4.9.0-rc.1")
        assertNull(UpdateChecker.parseReleaseJson(json, includePrerelease = false))
    }

    // === includePrerelease = true → 选最高版本 ===

    @Test
    fun `picks highest version across all channels`() {
        val json = releaseJson(
            alphaVersion = "v4.10.0-alpha.1", alphaUrl = "https://cdn/a.apk",
            rcVersion = "v4.9.0-rc.1"
        )
        val info = UpdateChecker.parseReleaseJson(json, includePrerelease = true)
        assertEquals("4.10.0-alpha.1", info!!.version)
        assertTrue(info.isPrerelease)
    }

    @Test
    fun `picks stable when it is highest`() {
        val json = releaseJson(
            stableVersion = "v4.9.0",
            alphaVersion = "v4.9.0-alpha.1", alphaUrl = "https://cdn/a.apk"
        )
        val info = UpdateChecker.parseReleaseJson(json, includePrerelease = true)
        assertEquals("4.9.0", info!!.version)
        assertFalse(info.isPrerelease)
    }

    @Test
    fun `rc beats beta beats alpha`() {
        val json = releaseJson(
            stableVersion = null,
            alphaVersion = "v4.9.0-alpha.1", alphaUrl = "https://cdn/a.apk",
            betaVersion = "v4.9.0-beta.1", betaUrl = "https://cdn/b.apk",
            rcVersion = "v4.9.0-rc.1"
        )
        val info = UpdateChecker.parseReleaseJson(json, includePrerelease = true)
        assertEquals("4.9.0-rc.1", info!!.version)
    }

    @Test
    fun `falls back to stable when no prerelease channels`() {
        val json = releaseJson(rcVersion = null)
        val info = UpdateChecker.parseReleaseJson(json, includePrerelease = true)
        assertEquals("4.9.0", info!!.version)
    }

    @Test
    fun `null when all channels missing`() {
        val json = releaseJson(stableVersion = null, rcVersion = null)
        assertNull(UpdateChecker.parseReleaseJson(json, includePrerelease = true))
    }

    // === 旧格式兼容 ===

    @Test
    fun `old format preview channel works`() {
        val json = releaseJson(stableVersion = null, rcVersion = null,
            previewVersion = "v4.8.0-preview", previewUrl = "https://cdn/p.apk")
        val info = UpdateChecker.parseReleaseJson(json, includePrerelease = true)
        assertEquals("4.8.0-preview", info!!.version)
        assertTrue(info.isPrerelease)
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
        val json = """{"stable":{"version":"v4.9.0","body":"no url"}}"""
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
        val json = """{"stable":{"version":"v4.9.0","url":"https://cdn.flechazo.icu/app.apk","body":""}}"""
        val info = UpdateChecker.parseReleaseJson(json, includePrerelease = false)
        assertEquals("4.9.0", info!!.version)
    }

    @Test
    fun `isPrerelease auto-detected from version string`() {
        val jsonAlpha = """{"stable":{"version":"v4.9.0-alpha.1","url":"https://cdn.flechazo.icu/app.apk","body":""}}"""
        assertTrue(UpdateChecker.parseReleaseJson(jsonAlpha, includePrerelease = false)!!.isPrerelease)

        val jsonStable = """{"stable":{"version":"v4.9.0","url":"https://cdn.flechazo.icu/app.apk","body":""}}"""
        assertFalse(UpdateChecker.parseReleaseJson(jsonStable, includePrerelease = false)!!.isPrerelease)
    }
}
