package com.cocwar.data.sync

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WebDavClient 协议级测试：通过注入 fake HttpURLConnection 验证
 * 请求方法 / Basic Auth 头 / URL 拼接 / 三态探测 / 错误处理。
 */
class WebDavClientTest {

    private val requests = mutableListOf<FakeHttp>()

    private fun fakeClient(
        baseUrl: String = "https://dav.example.com/",
        responseCode: (method: String, path: String) -> Int = { _, _ -> 200 },
        body: String = "",
        errorBody: String = "",
        factory: (String) -> HttpURLConnection = { url ->
            val u = URL(url)
            FakeHttp(u, responseCode(u.path, u.path), body, errorBody).also { requests.add(it) }
        },
    ): WebDavClient = WebDavClient(
        baseUrl = baseUrl,
        username = "user",
        password = "pass",
        connectionFactory = factory
    )

    private val basicAuth = "Basic " + Base64.getEncoder().encodeToString("user:pass".toByteArray(Charsets.UTF_8))

    // ─── upload ───

    @Test
    fun `upload 成功-PUT 主备份路径带 Basic Auth 与 JSON 内容`() {
        val client = fakeClient()
        val result = client.upload("{\"data\":1}")
        assertTrue("上传应成功", result.isSuccess)

        val put = requests.last()
        assertEquals("PUT", put.reqMethod)
        assertEquals(basicAuth, put.headers["Authorization"])
        assertEquals("application/json; charset=utf-8", put.headers["Content-Type"])
        assertEquals("https://dav.example.com/coc_backup/coc_war_backup.json", put.reqUrl.toString())
        assertEquals("{\"data\":1}", put.writtenBody())
        // 目录创建(MKCOL)先于上传
        assertTrue("应包含目录创建请求", requests.any { it.reqMethod == "MKCOL" })
    }

    @Test
    fun `upload 失败-非2xx 返回错误正文`() {
        val client = fakeClient(responseCode = { _, _ -> 500 }, errorBody = "Internal Server Error")
        val result = client.upload("{}")
        assertTrue("上传应失败", result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("500"))
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("Internal Server Error"))
    }

    @Test
    fun `upload 网络异常失败`() {
        val client = fakeClient(factory = { throw IOException("boom") })
        assertTrue(client.upload("{}").isFailure)
    }

    // ─── download ───

    @Test
    fun `download 成功返回正文`() {
        val client = fakeClient(body = "{\"remote\":true}")
        val result = client.download()
        assertTrue(result.isSuccess)
        assertEquals("{\"remote\":true}", result.getOrNull())
        assertEquals("GET", requests.last().reqMethod)
        assertEquals(basicAuth, requests.last().headers["Authorization"])
    }

    @Test
    fun `download 404 失败`() {
        val client = fakeClient(responseCode = { _, _ -> 404 })
        val result = client.download()
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("404"))
    }

    // ─── probe 三态 ───

    @Test
    fun `probe 2xx 返回 EXISTS`() {
        assertEquals(WebDavClient.RemoteState.EXISTS, fakeClient().probe())
        assertEquals("HEAD", requests.last().reqMethod)
    }

    @Test
    fun `probe 404 返回 MISSING`() {
        assertEquals(WebDavClient.RemoteState.MISSING, fakeClient(responseCode = { _, _ -> 404 }).probe())
    }

    @Test
    fun `probe 401 返回 UNKNOWN 保守中止`() {
        assertEquals(WebDavClient.RemoteState.UNKNOWN, fakeClient(responseCode = { _, _ -> 401 }).probe())
    }

    @Test
    fun `probe 非 2xx 非 404 返回 UNKNOWN`() {
        assertEquals(WebDavClient.RemoteState.UNKNOWN, fakeClient(responseCode = { _, _ -> 500 }).probe())
    }

    @Test
    fun `probe 网络异常返回 UNKNOWN`() {
        val client = fakeClient(factory = { throw IOException("timeout") })
        assertEquals(WebDavClient.RemoteState.UNKNOWN, client.probe())
    }

    @Test
    fun `probe HEAD 405 回退 GET Range 返回 EXISTS`() {
        var calls = 0
        val codes = intArrayOf(405, 200)  // 第 1 次 HEAD=405，第 2 次 GET=200
        val client = WebDavClient(
            baseUrl = "https://dav.example.com/",
            username = "user",
            password = "pass",
            connectionFactory = { url ->
                val code = codes[calls.coerceAtMost(codes.lastIndex)]
                FakeHttp(URL(url), code, "", "").also { requests.add(it); calls++ }
            }
        )
        assertEquals(WebDavClient.RemoteState.EXISTS, client.probe())
        assertEquals(2, requests.size)
        assertEquals("HEAD", requests[0].reqMethod)
        assertEquals("GET", requests[1].reqMethod)
        assertEquals("bytes=0-0", requests[1].headers["Range"])
    }

    @Test
    fun `probe HEAD 501 回退 GET Range 返回 MISSING`() {
        var calls = 0
        val codes = intArrayOf(501, 404)  // 第 1 次 HEAD=501，第 2 次 GET=404
        val client = WebDavClient(
            baseUrl = "https://dav.example.com/",
            username = "user",
            password = "pass",
            connectionFactory = { url ->
                val code = codes[calls.coerceAtMost(codes.lastIndex)]
                FakeHttp(URL(url), code, "", "").also { requests.add(it); calls++ }
            }
        )
        assertEquals(WebDavClient.RemoteState.MISSING, client.probe())
        assertEquals(2, requests.size)
        assertEquals("GET", requests[1].reqMethod)
    }

    // ─── delete ───

    @Test
    fun `delete 2xx 成功`() {
        assertTrue(fakeClient().delete("https://dav.example.com/coc_backup/archives/a.json").isSuccess)
        assertEquals("DELETE", requests.last().reqMethod)
    }

    @Test
    fun `delete 404 视为已删除成功`() {
        assertTrue(fakeClient(responseCode = { _, _ -> 404 })
            .delete("https://dav.example.com/coc_backup/archives/x.json").isSuccess)
    }

    @Test
    fun `delete 500 失败`() {
        assertTrue(fakeClient(responseCode = { _, _ -> 500 })
            .delete("https://dav.example.com/coc_backup/archives/x.json").isFailure)
    }

    // ─── testConnection ───

    @Test
    fun `testConnection 2xx 为 true`() {
        assertTrue(fakeClient().testConnection().getOrDefault(false))
    }

    @Test
    fun `testConnection 3xx 重定向为 false`() {
        assertFalse(fakeClient(responseCode = { _, _ -> 302 }).testConnection().getOrDefault(true))
    }

    // ─── URL 拼接 ───

    @Test
    fun `URL 拼接-去除尾部斜杠`() {
        // 带斜杠与不带斜杠的 baseUrl 得到同一 fileUrl
        val withSlash = fakeClient(baseUrl = "https://dav.example.com/")
        withSlash.probe()
        assertEquals("https://dav.example.com/coc_backup/coc_war_backup.json", requests.last().reqUrl.toString())
    }

    @Test
    fun `archiveUrl 拼接归档路径`() {
        val client = fakeClient()
        assertEquals(
            "https://dav.example.com/coc_backup/archives/sync_1.json",
            client.archiveUrl("sync_1.json")
        )
    }
}

/** 测试用 fake HttpURLConnection：内存实现，记录请求并返回预设响应。 */
private class FakeHttp(
    val reqUrl: URL,
    private val code: Int,
    private val body: String,
    private val errorBody: String,
) : HttpURLConnection(reqUrl) {

    val headers = mutableMapOf<String, String>()
    var reqMethod: String = "GET"
    private val out = ByteArrayOutputStream()

    override fun connect() = Unit
    override fun disconnect() = Unit
    override fun usingProxy() = false

    override fun setRequestProperty(key: String, value: String) {
        headers[key] = value
    }

    override fun getRequestProperty(key: String): String? = headers[key]

    override fun setRequestMethod(method: String) {
        this.reqMethod = method
    }

    override fun getInputStream(): InputStream =
        ByteArrayInputStream(body.toByteArray(Charsets.UTF_8))

    override fun getOutputStream(): OutputStream = out

    override fun getResponseCode(): Int = code

    override fun getErrorStream(): InputStream? =
        if (errorBody.isNotEmpty()) ByteArrayInputStream(errorBody.toByteArray(Charsets.UTF_8)) else null

    fun writtenBody(): String = out.toString(Charsets.UTF_8.name())
}
