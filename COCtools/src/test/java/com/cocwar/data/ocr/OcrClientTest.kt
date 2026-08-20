package com.cocwar.data.ocr

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * OcrClient 协议级测试：注入 fake HttpURLConnection 验证请求构造与错误映射。
 */
class OcrClientTest {

    private val requests = mutableListOf<FakeHttp>()

    private fun fakeClient(
        apiKey: String = "sk-test",
        responseCode: Int = 200,
        body: String = """{"choices":[{"message":{"role":"assistant","content":"成员名,排名,总星数,进攻1摧毁率,进攻2摧毁率\n张三,1,6,100,100"}}]}""",
        errorBody: String = "",
        factory: (String) -> HttpURLConnection = { url ->
            val u = URL(url)
            FakeHttp(u, responseCode, body, errorBody).also { requests.add(it) }
        }
    ): OcrClient = OcrClient(
        apiKey = apiKey,
        baseUrl = "https://dashscope.example.com/compatible-mode/v1",
        model = "qwen-test",
        connectionFactory = factory
    )

    private fun run(client: OcrClient, img: String = "QUJD") = runBlocking {
        client.recognize(img)
    }

    // ─── 成功路径 ───

    @Test
    fun `success posts to chat completions with bearer auth and base64 image`() {
        val client = fakeClient()
        val content = run(client, "QUJD")
        assertTrue(content.contains("张三"))

        val req = requests.last()
        assertEquals("POST", req.reqMethod)
        assertEquals("Bearer sk-test", req.headers["Authorization"])
        assertEquals("application/json; charset=utf-8", req.headers["Content-Type"])
        assertEquals(
            "https://dashscope.example.com/compatible-mode/v1/chat/completions",
            req.reqUrl.toString()
        )
        val body = req.writtenBody()
        assertTrue("请求体应含 base64 图片 data URL", body.contains("data:image/jpeg;base64,QUJD"))
        assertTrue("图片块应含 type:image_url 字段", body.contains("\"type\":\"image_url\""))
        assertTrue("请求体应含模型名", body.contains("\"model\":\"qwen-test\""))
        assertTrue("请求体应含提示词", body.contains("成员名,排名,总星数"))
    }

    @Test
    fun `blank content retries then succeeds`() {
        var calls = 0
        val client = OcrClient(
            apiKey = "sk-test",
            baseUrl = "https://dashscope.example.com/compatible-mode/v1",
            model = "qwen-test",
            connectionFactory = { url ->
                calls++
                val body = if (calls == 1) {
                    """{"choices":[{"message":{"content":""}}]}"""
                } else {
                    """{"choices":[{"message":{"content":"成员名,排名,总星数,进攻1摧毁率,进攻2摧毁率\n张三,1,6,100,100"}}]}"""
                }
                FakeHttp(URL(url), 200, body, "").also { requests.add(it) }
            }
        )
        val content = run(client, "QUJD")
        assertTrue(content.contains("张三"))
        assertEquals("空返回后应重试一次", 2, requests.size)
    }

    @Test
    fun `blank api key throws NotConfigured without request`() {
        val client = fakeClient(apiKey = "  ")
        try {
            run(client)
            fail("应抛出 NotConfigured")
        } catch (e: OcrClient.OcrException.NotConfigured) {
            assertTrue(e.message.orEmpty().contains("API Key"))
        }
        assertTrue("未配置 Key 不应发起请求", requests.isEmpty())
    }

    // ─── 错误映射 ───

    @Test
    fun `http 500 maps to ApiError with error message`() {
        val client = fakeClient(
            responseCode = 500,
            errorBody = """{"error":{"message":"model overloaded"}}"""
        )
        try {
            run(client)
            fail("应抛出 ApiError")
        } catch (e: OcrClient.OcrException.ApiError) {
            assertEquals(500, e.code)
            assertTrue(e.detail.contains("model overloaded"))
        }
    }

    @Test
    fun `http 401 maps to ApiError`() {
        val client = fakeClient(responseCode = 401, errorBody = "Unauthorized")
        try {
            run(client)
            fail("应抛出 ApiError")
        } catch (e: OcrClient.OcrException.ApiError) {
            assertEquals(401, e.code)
        }
    }

    @Test
    fun `io exception maps to Network`() {
        val client = fakeClient(factory = { throw IOException("boom") })
        try {
            run(client)
            fail("应抛出 Network")
        } catch (e: OcrClient.OcrException.Network) {
            assertTrue(e.detail.contains("boom"))
        }
    }

    @Test
    fun `socket timeout maps to Timeout`() {
        val client = fakeClient(factory = { throw SocketTimeoutException("read timed out") })
        try {
            run(client)
            fail("应抛出 Timeout")
        } catch (e: OcrClient.OcrException.Timeout) {
            // 期望类型
        }
    }

    @Test
    fun `malformed json maps to BadResponse`() {
        val client = fakeClient(body = "not-json")
        try {
            run(client)
            fail("应抛出 BadResponse")
        } catch (e: OcrClient.OcrException.BadResponse) {
            assertTrue(e.detail.startsWith("not-json"))
        }
    }

    @Test
    fun `empty choices maps to BadResponse`() {
        val client = fakeClient(body = """{"choices":[]}""")
        try {
            run(client)
            fail("应抛出 BadResponse")
        } catch (e: OcrClient.OcrException.BadResponse) {
            // 期望类型
        }
    }

    @Test
    fun `normalized base url keeps trailing slash safe`() {
        val client = OcrClient(
            apiKey = "k",
            baseUrl = "https://dashscope.example.com/compatible-mode/v1/",
            model = "m",
            connectionFactory = { url ->
                val u = URL(url)
                FakeHttp(u, 200, """{"choices":[{"message":{"content":"csv"}}]}""", "").also { requests.add(it) }
            }
        )
        run(client)
        assertEquals(
            "https://dashscope.example.com/compatible-mode/v1/chat/completions",
            requests.last().reqUrl.toString()
        )
    }
}

/** 与 WebDavClientTest 同款 fake：记录请求并回放预设响应。 */
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
