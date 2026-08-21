package com.cocwar.data.ocr

import org.junit.Assert.assertEquals
import org.junit.Test

class OcrProvidersTest {

    @Test
    fun `百炼预设端点与模型`() {
        assertEquals("https://dashscope.aliyuncs.com/compatible-mode/v1", OcrProviders.BAILIAN.baseUrl)
        assertEquals("qwen-vl-max", OcrProviders.BAILIAN.model)
    }

    @Test
    fun `match 命中 agnes 默认预设`() {
        assertEquals(OcrProviders.AGNES, OcrProviders.match(OcrConfig.DEFAULT_BASE_URL, OcrConfig.DEFAULT_MODEL))
    }

    @Test
    fun `match 命中百炼预设`() {
        assertEquals(OcrProviders.BAILIAN, OcrProviders.match(OcrConfig.BAILIAN_BASE_URL, OcrConfig.BAILIAN_MODEL))
    }

    @Test
    fun `match 未命中返回自定义`() {
        assertEquals(OcrProviders.CUSTOM, OcrProviders.match("https://example.com/v1", "my-model"))
    }

    @Test
    fun `indexOf 返回正确索引`() {
        assertEquals(0, OcrProviders.indexOf(OcrProviders.AGNES))
        assertEquals(1, OcrProviders.indexOf(OcrProviders.BAILIAN))
        assertEquals(2, OcrProviders.indexOf(OcrProviders.CUSTOM))
    }
}
