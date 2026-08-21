package com.cocwar.data.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrCsvAggregatorTest {

    private val header = "成员名,排名,总星数,进攻1摧毁率,进攻2摧毁率"

    @Test
    fun `单屏直接还原`() {
        val out = OcrCsvAggregator.aggregate(listOf(header + "\n张三,1,6,100,100\n李四,2,5,87,100"))
        assertTrue(out.contains("张三,1,6,100,100"))
        assertTrue(out.contains("李四,2,5,87,100"))
    }

    @Test
    fun `多屏同排名聚合：非零优先、冲突取末屏`() {
        val csv1 = header + "\n张三,1,0,0,0"
        val csv2 = header + "\n张三,1,6,100,100"
        val out = OcrCsvAggregator.aggregate(listOf(csv1, csv2))
        assertTrue("应取末屏非零值", out.contains("张三,1,6,100,100"))
    }

    @Test
    fun `名字众数并列取首屏出现序`() {
        val screens = listOf(
            header + "\nGuoAn,1,6,100,100",
            header + "\nGuoAN,1,6,100,100",
            header + "\nGuoAn,1,6,100,100"
        )
        val out = OcrCsvAggregator.aggregate(screens)
        assertTrue(out.contains("GuoAn,1,6,100,100"))
    }

    @Test
    fun `空屏跳过`() {
        val out = OcrCsvAggregator.aggregate(listOf("", header, header + "\n张三,1,6,100,100"))
        assertTrue(out.contains("张三,1,6,100,100"))
    }

    @Test
    fun `全空返回空串`() {
        assertEquals("", OcrCsvAggregator.aggregate(listOf("", header)))
    }

    @Test
    fun `排名缺失留空档`() {
        val screens = listOf(
            header + "\n张三,1,6,100,100",
            header + "\n王五,3,6,100,100"
        )
        val out = OcrCsvAggregator.aggregate(screens)
        assertTrue(out.contains("张三,1,6,100,100"))
        assertTrue(out.contains("王五,3,6,100,100"))
    }

    @Test
    fun `输出以表头开头`() {
        val out = OcrCsvAggregator.aggregate(listOf(header + "\n张三,1,6,100,100"))
        assertTrue(out.startsWith(header))
    }
}
