package com.cocwar.data.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * UpdateChecker.parseGitHubRelease 单元测试（GitHub Releases API `releases/latest` 响应）。
 */
class UpdateCheckerTest {

    private fun releaseJson(
        tag: String? = "v4.11.0",
        body: String? = "正式版更新日志",
        assets: String? = """{"name":"COCtools-v4.11.0.apk","browser_download_url":"https://github.com/flechazoyang-google/coc-war-tool/releases/download/v4.11.0/COCtools-v4.11.0.apk"}""",
        draft: Boolean = false,
        prerelease: Boolean = false
    ): String {
        val parts = mutableListOf<String>()
        if (tag != null) parts.add("\"tag_name\":\"$tag\"")
        if (body != null) parts.add("\"body\":\"$body\"")
        if (assets != null) parts.add("\"assets\":[$assets]")
        parts.add("\"draft\":$draft")
        parts.add("\"prerelease\":$prerelease")
        return "{${parts.joinToString(",")}}"
    }

    @Test
    fun `parses version body and apk url`() {
        val info = UpdateChecker.parseGitHubRelease(releaseJson())
        assertNotNull(info)
        assertEquals("4.11.0", info!!.version)
        assertEquals("正式版更新日志", info.body)
        assertTrue(info.apkUrl.endsWith("COCtools-v4.11.0.apk"))
        assertFalse(info.isPrerelease)
    }

    @Test
    fun `tag without v prefix kept as is`() {
        val info = UpdateChecker.parseGitHubRelease(releaseJson(tag = "4.11.0"))
        assertEquals("4.11.0", info!!.version)
    }

    @Test
    fun `uppercase V prefix stripped`() {
        val info = UpdateChecker.parseGitHubRelease(releaseJson(tag = "V4.11.0"))
        assertEquals("4.11.0", info!!.version)
    }

    @Test
    fun `blank tag returns null`() {
        assertNull(UpdateChecker.parseGitHubRelease(releaseJson(tag = "   ")))
    }

    @Test
    fun `missing tag returns null`() {
        assertNull(UpdateChecker.parseGitHubRelease(releaseJson(tag = null)))
    }

    @Test
    fun `missing assets returns null`() {
        assertNull(UpdateChecker.parseGitHubRelease(releaseJson(assets = null)))
    }

    @Test
    fun `assets without apk returns null`() {
        val assets = """{"name":"source.zip","browser_download_url":"https://github.com/x/source.zip"}"""
        assertNull(UpdateChecker.parseGitHubRelease(releaseJson(assets = assets)))
    }

    @Test
    fun `picks first apk asset among mixed assets`() {
        val assets = listOf(
            """{"name":"notes.txt","browser_download_url":"https://github.com/x/notes.txt"}""",
            """{"name":"COCtools-v4.11.0.apk","browser_download_url":"https://github.com/x/first.apk"}""",
            """{"name":"COCtools-other.apk","browser_download_url":"https://github.com/x/second.apk"}"""
        ).joinToString(",")
        val info = UpdateChecker.parseGitHubRelease(releaseJson(assets = assets))
        assertEquals("https://github.com/x/first.apk", info!!.apkUrl)
    }

    @Test
    fun `apk asset with blank url skipped`() {
        val assets = listOf(
            """{"name":"COCtools-v4.11.0.apk","browser_download_url":""}""",
            """{"name":"COCtools-v4.11.0-alt.apk","browser_download_url":"https://github.com/x/alt.apk"}"""
        ).joinToString(",")
        val info = UpdateChecker.parseGitHubRelease(releaseJson(assets = assets))
        assertEquals("https://github.com/x/alt.apk", info!!.apkUrl)
    }

    @Test
    fun `body absent yields empty string`() {
        val info = UpdateChecker.parseGitHubRelease(releaseJson(body = null))
        assertEquals("", info!!.body)
    }

    @Test
    fun `body truncated to 500 chars`() {
        val info = UpdateChecker.parseGitHubRelease(releaseJson(body = "x".repeat(1000)))
        assertEquals(500, info!!.body.length)
    }

    @Test
    fun `prerelease flag returns null`() {
        assertNull(UpdateChecker.parseGitHubRelease(releaseJson(prerelease = true)))
    }

    @Test
    fun `draft flag returns null`() {
        assertNull(UpdateChecker.parseGitHubRelease(releaseJson(draft = true)))
    }

    @Test
    fun `prerelease version detected from tag`() {
        val info = UpdateChecker.parseGitHubRelease(releaseJson(tag = "v4.11.0-beta.1"))
        assertTrue(info!!.isPrerelease)
        assertEquals("4.11.0-beta.1", info.version)
    }

    @Test
    fun `malformed JSON returns null`() {
        assertNull(UpdateChecker.parseGitHubRelease("not json"))
    }

    @Test
    fun `empty object returns null`() {
        assertNull(UpdateChecker.parseGitHubRelease("{}"))
    }

    @Test
    fun `JSON array returns null`() {
        assertNull(UpdateChecker.parseGitHubRelease("[1,2,3]"))
    }
}
