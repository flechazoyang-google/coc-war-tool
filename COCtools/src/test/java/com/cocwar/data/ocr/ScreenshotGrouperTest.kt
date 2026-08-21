package com.cocwar.data.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenshotGrouperTest {

    private fun info(timestamp: Long) = ScreenshotGrouper.ScreenshotInfo(
        id = timestamp, path = "/x/COC_20260821_101500_000.png", timestampMillis = timestamp
    )

    @Test
    fun `filenameMillis 解析毫秒时间戳`() {
        val t = ScreenshotGrouper.filenameMillis("/storage/Pictures/CocWarTool/COC_20260821_101530_123.png")
        assertNotNull(t)
    }

    @Test
    fun `filenameMillis 非法文件名返回 null`() {
        assertEquals(null, ScreenshotGrouper.filenameMillis("/x/screenshot_1.png"))
    }

    @Test
    fun `filenameMillis 同秒不同毫秒差正确`() {
        val a = ScreenshotGrouper.filenameMillis("/x/COC_20260821_101530_005.png")
        val b = ScreenshotGrouper.filenameMillis("/x/COC_20260821_101530_500.png")
        assertNotNull(a)
        assertNotNull(b)
        assertEquals(495L, b!! - a!!)
    }

    @Test
    fun `group 按间隔分组`() {
        val items = listOf(info(1000L), info(1500L), info(2000L), info(70000L), info(71500L))
        val groups = ScreenshotGrouper.group(items, gapMillis = 60_000L)
        assertEquals(2, groups.size)
        assertEquals(3, groups[0].items.size)
        assertEquals(2, groups[1].items.size)
    }

    @Test
    fun `group 空列表返回空`() {
        assertTrue(ScreenshotGrouper.group(emptyList()).isEmpty())
    }

    @Test
    fun `group 无序输入按时序排序后再分组`() {
        val items = listOf(info(71500L), info(1000L), info(2000L), info(1500L))
        val groups = ScreenshotGrouper.group(items, gapMillis = 60_000L)
        assertEquals(2, groups.size)
        assertEquals(listOf(1000L, 1500L, 2000L), groups[0].items.map { it.timestampMillis })
    }
}
