package com.example.fitapp.core.designsystem.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.fitapp.core.designsystem.ManTheme

/*
 * Charts are drawn straight on a Compose Canvas: the shapes the app needs (a line, bars, a grid, a ring) are a few lines
 * each and this keeps the project free of a charting library's API. Every chart carries a plain sentence for TalkBack.
 */

/** A point on a line chart: [x] is a day number (for example LocalDate.toEpochDay) and [y] the value. */
data class ChartPoint(
    val x: Long,
    val y: Double,
)

/** A bar: [label] is only used for the axis ends and the spoken summary. */
data class ChartBar(
    val label: String,
    val value: Double,
)

/**
 * Dots for [points] and a line for [line] (the trend) on a shared axis. [minY] and [maxY] come from the caller so the
 * labels and the drawing agree. [description] is what TalkBack reads.
 */
@Composable
fun LineChart(
    points: List<ChartPoint>,
    line: List<ChartPoint>,
    minY: Double,
    maxY: Double,
    valueLabel: (Double) -> String,
    description: String,
    modifier: Modifier = Modifier,
    height: Dp = 160.dp,
    lineColor: Color = ManTheme.colors.protein,
    dotColor: Color = ManTheme.colors.muted,
) {
    val hairline = ManTheme.colors.hairline
    val all = points + line
    val minX = all.minOfOrNull { it.x } ?: 0L
    val maxX = all.maxOfOrNull { it.x } ?: 0L
    val spanX = (maxX - minX).coerceAtLeast(0L)
    val spanY = (maxY - minY).takeIf { it > 0 } ?: 1.0
    Row(modifier = modifier.fillMaxWidth().semantics { contentDescription = description }) {
        Column(
            modifier = Modifier.height(height).padding(end = 8.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(valueLabel(maxY), style = MaterialTheme.typography.labelSmall, color = ManTheme.colors.muted)
            Text(valueLabel(minY), style = MaterialTheme.typography.labelSmall, color = ManTheme.colors.muted)
        }
        Canvas(modifier = Modifier.weight(1f).height(height).clearAndSetSemantics { }) {
            val w = size.width
            val h = size.height
            val grid = 1.dp.toPx()
            for (i in 0..2) {
                val y = h * i / 2f
                drawLine(hairline, Offset(0f, y), Offset(w, y), strokeWidth = grid)
            }
            fun px(x: Long): Float = if (spanX == 0L) w / 2f else (x - minX).toFloat() / spanX * w

            fun py(y: Double): Float = (h - ((y - minY) / spanY).toFloat().coerceIn(0f, 1f) * h)
            if (line.size >= 2) {
                val path = Path()
                line.sortedBy { it.x }.forEachIndexed { i, p ->
                    if (i == 0) path.moveTo(px(p.x), py(p.y)) else path.lineTo(px(p.x), py(p.y))
                }
                drawPath(path, lineColor, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            points.forEach { drawCircle(dotColor, radius = 3.dp.toPx(), center = Offset(px(it.x), py(it.y))) }
            if (line.size == 1) drawCircle(lineColor, radius = 4.dp.toPx(), center = Offset(px(line[0].x), py(line[0].y)))
        }
    }
}

/**
 * Vertical bars from zero. [target], when given, is a dashed line across the chart. The first and last labels sit under
 * the ends.
 */
@Composable
fun BarChart(
    bars: List<ChartBar>,
    valueLabel: (Double) -> String,
    description: String,
    modifier: Modifier = Modifier,
    height: Dp = 140.dp,
    color: Color = ManTheme.colors.protein,
    target: Double? = null,
) {
    val hairline = ManTheme.colors.hairline
    val top = maxOf(bars.maxOfOrNull { it.value } ?: 0.0, target ?: 0.0).takeIf { it > 0 }?.times(1.1) ?: 1.0
    Column(modifier = modifier.fillMaxWidth().semantics { contentDescription = description }) {
        Row {
            Column(
                modifier = Modifier.height(height).padding(end = 8.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(valueLabel(top), style = MaterialTheme.typography.labelSmall, color = ManTheme.colors.muted)
                Text(valueLabel(0.0), style = MaterialTheme.typography.labelSmall, color = ManTheme.colors.muted)
            }
            Canvas(modifier = Modifier.weight(1f).height(height).clearAndSetSemantics { }) {
                val w = size.width
                val h = size.height
                drawLine(hairline, Offset(0f, h), Offset(w, h), strokeWidth = 1.dp.toPx())
                if (bars.isNotEmpty()) {
                    val slot = w / bars.size
                    val barWidth = slot * 0.6f
                    bars.forEachIndexed { i, bar ->
                        val barHeight = (bar.value / top).toFloat().coerceIn(0f, 1f) * h
                        if (barHeight > 0f) {
                            drawRoundRect(
                                color = color,
                                topLeft = Offset(i * slot + (slot - barWidth) / 2f, h - barHeight),
                                size = Size(barWidth, barHeight),
                                cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx()),
                            )
                        }
                    }
                }
                if (target != null) {
                    val y = h - (target / top).toFloat().coerceIn(0f, 1f) * h
                    drawLine(
                        color = hairline.copy(alpha = 1f),
                        start = Offset(0f, y),
                        end = Offset(w, y),
                        strokeWidth = 2.dp.toPx(),
                        pathEffect =
                            androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 6.dp.toPx())),
                    )
                }
            }
        }
        if (bars.size >= 2) {
            Row(modifier = Modifier.fillMaxWidth().padding(start = 40.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(bars.first().label, style = MaterialTheme.typography.labelSmall, color = ManTheme.colors.muted)
                Text(bars.last().label, style = MaterialTheme.typography.labelSmall, color = ManTheme.colors.muted)
            }
        }
    }
}

/**
 * A calendar grid: one column per week, seven rows Monday to Sunday. [levels] holds 0 to [maxLevel] per day, or null for
 * days that have not happened yet.
 */
@Composable
fun Heatmap(
    levels: List<List<Int?>>,
    description: String,
    modifier: Modifier = Modifier,
    maxLevel: Int = 3,
) {
    if (levels.isEmpty()) return
    val empty = ManTheme.colors.hairline
    val full = ManTheme.colors.success
    val columns = levels.size
    Canvas(
        modifier =
            modifier
                .fillMaxWidth()
                .aspectRatio(columns / 7f)
                .semantics { contentDescription = description },
    ) {
        val cell = size.width / columns
        val inset = 1.5.dp.toPx()
        levels.forEachIndexed { col, week ->
            week.forEachIndexed { row, level ->
                if (level != null) {
                    val color = if (level <= 0) empty else full.copy(alpha = 0.35f + 0.65f * (level.coerceAtMost(maxLevel).toFloat() / maxLevel))
                    drawRoundRect(
                        color = color,
                        topLeft = Offset(col * cell + inset, row * cell + inset),
                        size = Size(cell - 2 * inset, cell - 2 * inset),
                        cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx()),
                    )
                }
            }
        }
    }
}

/** A ring that fills clockwise from the top. [content] sits in the middle. */
@Composable
fun ProgressRing(
    fraction: Float,
    description: String,
    modifier: Modifier = Modifier,
    size: Dp = 128.dp,
    strokeWidth: Dp = 12.dp,
    color: Color = ManTheme.colors.protein,
    content: @Composable () -> Unit = {},
) {
    val track = ManTheme.colors.hairline
    Box(
        modifier = modifier.size(size).semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(size).clearAndSetSemantics { }) {
            val stroke = strokeWidth.toPx()
            val inset = stroke / 2f
            val arc = Size(this.size.width - stroke, this.size.height - stroke)
            drawArc(track, startAngle = -90f, sweepAngle = 360f, useCenter = false, topLeft = Offset(inset, inset), size = arc, style = Stroke(stroke))
            val sweep = 360f * fraction.coerceIn(0f, 1f)
            if (sweep > 0f) {
                drawArc(color, startAngle = -90f, sweepAngle = sweep, useCenter = false, topLeft = Offset(inset, inset), size = arc, style = Stroke(stroke, cap = StrokeCap.Round))
            }
        }
        content()
    }
}
