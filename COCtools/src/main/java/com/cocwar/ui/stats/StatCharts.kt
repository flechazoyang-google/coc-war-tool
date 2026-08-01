package com.cocwar.ui.stats

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cocwar.ui.theme.cocColors
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * 每场星数趋势折线图：横向网格 + 折线 + 数据点 + 横轴场次标签。
 * 由 Compose Canvas 自绘，不引入第三方图表库。
 */
@Composable
fun StarTrendLineChart(
    values: List<Float>,
    xLabels: List<String>,
    modifier: Modifier = Modifier,
    lineColor: Color = MaterialTheme.cocColors.accent,
    gridColor: Color = MaterialTheme.cocColors.hairline,
    textColor: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    if (values.isEmpty()) return

    val labelTextSize = 10.sp
    val padLeft = 34.dp
    val padBottom = 24.dp
    val padTop = 10.dp
    val padRight = 10.dp

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(190.dp)
    ) {
        val plotWidth = size.width - padLeft.toPx() - padRight.toPx()
        val plotHeight = size.height - padTop.toPx() - padBottom.toPx()

        val rawMax = values.maxOrNull() ?: 0f
        val maxY = if (rawMax > 0f) (ceil(rawMax / 10f) * 10f).toFloat() else 10f

        // 横向网格 + Y 轴标签（0 / 一半 / 最大值）
        val fractions = listOf(0f, 0.5f, 1f)
        fractions.forEach { f ->
            val y = padTop.toPx() + plotHeight * (1f - f)
            drawLine(
                color = gridColor,
                start = Offset(padLeft.toPx(), y),
                end = Offset(padLeft.toPx() + plotWidth, y),
                strokeWidth = 1f
            )
            val textPaint = Paint().apply {
                color = textColor.toArgb()
                textSize = labelTextSize.toPx()
                isAntiAlias = true
                textAlign = Paint.Align.LEFT
            }
            drawContext.canvas.nativeCanvas.drawText(
                (maxY * f).roundToInt().toString(),
                3f,
                y + labelTextSize.toPx() / 3f,
                textPaint
            )
        }

        // 折线 + 数据点
        val n = values.size
        val pointXs = values.mapIndexed { i, _ ->
            if (n == 1) padLeft.toPx() + plotWidth / 2f
            else padLeft.toPx() + i * plotWidth / (n - 1)
        }
        val pointYs = values.map { v ->
            padTop.toPx() + plotHeight * (1f - (v.coerceIn(0f, maxY) / maxY))
        }

        val path = Path()
        pointXs.zip(pointYs).forEachIndexed { i, (x, y) ->
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        if (n > 1) {
            drawPath(
                path,
                color = lineColor,
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
            )
        }
        pointXs.zip(pointYs).forEach { (x, y) ->
            drawCircle(color = lineColor, radius = 3.5.dp.toPx(), center = Offset(x, y))
        }

        // X 轴标签：最多 6 个，均匀分布
        val labelCount = minOf(n, 6)
        val labelIndices = if (n == 1) listOf(0)
        else (0 until labelCount).map { k -> k * (n - 1) / (labelCount - 1) }
        val labelPaint = Paint().apply {
            color = textColor.toArgb()
            textSize = labelTextSize.toPx()
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        labelIndices.forEach { i ->
            val x = pointXs[i]
            drawContext.canvas.nativeCanvas.drawText(
                xLabels.getOrElse(i) { "" },
                x,
                padTop.toPx() + plotHeight + labelTextSize.toPx() * 1.7f,
                labelPaint
            )
        }
    }
}

/** 雷达图轴：label + 归一化值（0~1） */
data class RadarAxis(
    val label: String,
    val value: Float
)

/**
 * 整体五维雷达图：同心多边形网格 + 轴线 + 数据多边形（填充+描边）+ 轴标签。
 */
@Composable
fun OverviewRadarChart(
    axes: List<RadarAxis>,
    modifier: Modifier = Modifier,
    accentColor: Color = MaterialTheme.cocColors.accent,
    gridColor: Color = MaterialTheme.cocColors.hairline,
    textColor: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    if (axes.isEmpty()) return

    val labelTextSize = 10.sp

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(230.dp)
    ) {
        val center = Offset(size.width / 2f, size.height / 2f + 4.dp.toPx())
        val radius = minOf(size.width, size.height) / 2f - 42.dp.toPx()
        if (radius <= 0f) return@Canvas

        val count = axes.size
        val angleStep = (2f * PI / count).toFloat()
        val startAngle = -PI.toFloat() / 2f   // 从正上方开始

        fun pointAt(radiusAt: Float, index: Int): Offset {
            val angle = startAngle + angleStep * index
            return Offset(
                center.x + cos(angle.toDouble()).toFloat() * radiusAt,
                center.y + sin(angle.toDouble()).toFloat() * radiusAt
            )
        }

        // 同心多边形网格（25% / 50% / 75% / 100%）
        val gridPath = Path()
        for (ring in 1..4) {
            val frac = ring / 4f
            gridPath.reset()
            for (i in 0 until count) {
                val p = pointAt(radius * frac, i)
                if (i == 0) gridPath.moveTo(p.x, p.y) else gridPath.lineTo(p.x, p.y)
            }
            gridPath.close()
            drawPath(gridPath, color = gridColor, style = Stroke(width = 1f))
        }

        // 轴线
        for (i in 0 until count) {
            drawLine(
                color = gridColor,
                start = center,
                end = pointAt(radius, i),
                strokeWidth = 1f
            )
        }

        // 数据多边形（填充 + 描边）
        val dataPath = Path()
        axes.forEachIndexed { i, axis ->
            val p = pointAt(radius * axis.value.coerceIn(0f, 1f), i)
            if (i == 0) dataPath.moveTo(p.x, p.y) else dataPath.lineTo(p.x, p.y)
        }
        dataPath.close()
        drawPath(dataPath, color = accentColor.copy(alpha = 0.16f))
        drawPath(
            dataPath,
            color = accentColor,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        )

        // 轴标签（顶点外侧）
        val labelPaint = Paint().apply {
            color = textColor.toArgb()
            textSize = labelTextSize.toPx()
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        axes.forEachIndexed { i, axis ->
            val labelPoint = pointAt(radius + 18.dp.toPx(), i)
            drawContext.canvas.nativeCanvas.drawText(
                axis.label,
                labelPoint.x,
                labelPoint.y + labelTextSize.toPx() / 3f,
                labelPaint
            )
        }
    }
}
