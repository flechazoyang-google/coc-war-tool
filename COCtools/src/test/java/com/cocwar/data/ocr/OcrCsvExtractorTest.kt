package com.cocwar.data.ocr

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * OcrCsvExtractor 纯函数测试：围栏剥离 / 表头定位 / 空白行。
 */
class OcrCsvExtractorTest {

    @Test
    fun `strips csv fence and keeps all lines`() {
        val content = """
            好的，以下是识别结果：
            ```csv
            成员名,排名,总星数,进攻1摧毁率,进攻2摧毁率
            陈平安,1,4,92,95
            混子祭天,2,6,100,100
            ```
            识别完毕。
        """.trimIndent()
        val expected = """
            成员名,排名,总星数,进攻1摧毁率,进攻2摧毁率
            陈平安,1,4,92,95
            混子祭天,2,6,100,100
        """.trimIndent()
        assertEquals(expected, OcrCsvExtractor.extract(content))
    }

    @Test
    fun `strips plain fence without csv marker`() {
        val content = "```\n成员名,排名,总星数,进攻1摧毁率,进攻2摧毁率\n张三,1,6,100,100\n```"
        val expected = "成员名,排名,总星数,进攻1摧毁率,进攻2摧毁率\n张三,1,6,100,100"
        assertEquals(expected, OcrCsvExtractor.extract(content))
    }

    @Test
    fun `drops explanation lines before header`() {
        val content = "这是您需要的表格：\n\n成员名,排名,总星数,进攻1摧毁率,进攻2摧毁率\n张三,1,6,100,100"
        val expected = "成员名,排名,总星数,进攻1摧毁率,进攻2摧毁率\n张三,1,6,100,100"
        assertEquals(expected, OcrCsvExtractor.extract(content))
    }

    @Test
    fun `keeps all lines when no header found`() {
        val content = "张三,1,6,100,100\n李四,2,3,50,20"
        assertEquals(content, OcrCsvExtractor.extract(content))
    }

    @Test
    fun `handles CRLF and blank lines`() {
        val content = "```csv\r\n成员名,排名,总星数,进攻1摧毁率,进攻2摧毁率\r\n张三,1,6,100,100\r\n\r\n李四,2,0,0,0\r\n```"
        val expected = "成员名,排名,总星数,进攻1摧毁率,进攻2摧毁率\n张三,1,6,100,100\n李四,2,0,0,0"
        assertEquals(expected, OcrCsvExtractor.extract(content))
    }

    @Test
    fun `empty input returns empty`() {
        assertEquals("", OcrCsvExtractor.extract(""))
        assertEquals("", OcrCsvExtractor.extract("   \n  "))
    }
}
