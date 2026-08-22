package com.cocwar.data.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrProvidersTest {

    @Test
    fun `百炼预设端点与模型`() {
        assertEquals("https://dashscope.aliyuncs.com/compatible-mode/v1", OcrProviders.BAILIAN.baseUrl)
        assertEquals("qwen-vl-max", OcrProviders.BAILIAN.model)
    }

    @Test
    fun `豆包预设端点与模型`() {
        assertEquals("https://ark.cn-beijing.volces.com/api/v3", OcrProviders.DOUBAO.baseUrl)
        assertEquals("doubao-seed-2-1-pro-260628", OcrProviders.DOUBAO.model)
    }

    @Test
    fun `各预设模型列表非空且默认模型在列表中`() {
        for (preset in OcrProviders.ALL.filter { it.id != "custom" }) {
            assertTrue("${preset.name} models should not be empty", preset.models.isNotEmpty())
            assertTrue("${preset.name} default model should be in models list", preset.model in preset.models)
        }
    }

    @Test
    fun `豆包模型列表包含预期模型`() {
        assertTrue("doubao-seed-2-1-pro-260628" in OcrProviders.DOUBAO.models)
        assertTrue("doubao-seed-2-1-turbo-260628" in OcrProviders.DOUBAO.models)
        assertTrue("doubao-seed-evolving" in OcrProviders.DOUBAO.models)
    }

    @Test
    fun `自定义预设模型列表为空`() {
        assertTrue(OcrProviders.CUSTOM.models.isEmpty())
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
    fun `match 命中豆包预设`() {
        assertEquals(OcrProviders.DOUBAO, OcrProviders.match(OcrConfig.DOUBAO_BASE_URL, OcrConfig.DOUBAO_MODEL))
    }

    @Test
    fun `match 未命中返回自定义`() {
        assertEquals(OcrProviders.CUSTOM, OcrProviders.match("https://example.com/v1", "my-model"))
    }

    @Test
    fun `indexOf 返回正确索引`() {
        assertEquals(0, OcrProviders.indexOf(OcrProviders.AGNES))
        assertEquals(1, OcrProviders.indexOf(OcrProviders.BAILIAN))
        assertEquals(2, OcrProviders.indexOf(OcrProviders.DOUBAO))
        assertEquals(3, OcrProviders.indexOf(OcrProviders.CUSTOM))
    }
}
