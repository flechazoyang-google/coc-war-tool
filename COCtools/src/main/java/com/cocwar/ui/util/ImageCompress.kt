package com.cocwar.ui.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayOutputStream

/**
 * 截图压缩工具：识图上传前把大图缩放到最长边 [MAX_EDGE] 并转 JPEG，
 * 显著减小请求体（2388×1080 游戏截图原图 base64 过大，压缩后识别质量不受影响）。
 */
object ImageCompress {

    /** 上传前最长边像素上限。 */
    const val MAX_EDGE = 1600

    /** JPEG 压缩质量（0-100）。 */
    private const val JPEG_QUALITY = 80

    /**
     * 读取 Uri 图片 → 缩放（最长边 [MAX_EDGE]，等比）→ JPEG 压缩 → base64。
     * @return base64 字符串（不含 data: 前缀）；读取/解码失败返回 null。
     */
    fun readAndCompressToBase64(context: Context, uri: Uri): String? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input, null, bounds)
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            val maxEdge = maxOf(bounds.outWidth, bounds.outHeight)
            val sample = if (maxEdge > MAX_EDGE) {
                // 2 的幂采样，先粗降再精缩，避免大图直接解码爆内存
                var s = 1
                while (maxEdge / (s * 2) > MAX_EDGE) s *= 2
                s
            } else 1

            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val bitmap = context.contentResolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input, null, opts)
            } ?: return null

            val scaled = if (maxOf(bitmap.width, bitmap.height) > MAX_EDGE) {
                val scale = MAX_EDGE.toFloat() / maxOf(bitmap.width, bitmap.height)
                Bitmap.createScaledBitmap(
                    bitmap,
                    (bitmap.width * scale).toInt().coerceAtLeast(1),
                    (bitmap.height * scale).toInt().coerceAtLeast(1),
                    true
                ).also { if (it != bitmap) bitmap.recycle() }
            } else bitmap

            val out = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            if (scaled != bitmap) scaled.recycle()
            Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
        } catch (e: Exception) {
            null
        }
    }
}
