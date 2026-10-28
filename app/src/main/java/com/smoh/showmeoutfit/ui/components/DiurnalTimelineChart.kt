package com.smoh.showmeoutfit.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Canvas rendering for [DiurnalTimelineBar].
 *
 * Vertical layout (fractions of the chart height), so the three series never overlap:
 * - 0%–22%: UV index band
 * - 34%–74%: temperature curve
 * - 80%–100%: rain-chance bars
 */
@Composable
internal fun DiurnalTimelineChart(
    points: List<TimelinePoint>,
    curveColor: Color,
    rainColor: Color,
    uvColor: Color,
    gridColor: Color,
    chartHeight: Dp,
    modifier: Modifier = Modifier,
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(chartHeight),
    ) {
        val chartTop = 4.dp.toPx()
        val chartBottom = size.height - 4.dp.toPx()
        val plotHeight = chartBottom - chartTop
        val uvTop = chartTop
        val uvBottom = chartTop + plotHeight * 0.22f
        val tempTop = chartTop + plotHeight * 0.34f
        val tempBottom = chartTop + plotHeight * 0.74f
        val rainMaxHeight = plotHeight * 0.20f
        val inset = 6.dp.toPx()
        val plotWidth = size.width - inset * 2f
        val lastIndex = points.lastIndex

        fun xAt(index: Int): Float = if (points.size == 1) {
            size.width / 2f
        } else {
            inset + plotWidth * index / lastIndex.toFloat()
        }

        val low = points.minOf { it.temperatureC }
        val high = points.maxOf { it.temperatureC }
        val range = high - low
        val isFlat = range < 0.5

        fun yAtTemperature(temperature: Double): Float = if (isFlat) {
            (tempTop + tempBottom) / 2f
        } else {
            tempBottom - ((temperature - low) / range).toFloat() * (tempBottom - tempTop)
        }

        fun yAtUv(uv: Double): Float {
            val fraction = (uv / 11.0).coerceIn(0.0, 1.0).toFloat()
            return uvBottom - fraction * (uvBottom - uvTop)
        }

        // Axis baseline.
        drawLine(
            color = gridColor,
            start = Offset(0f, chartBottom),
            end = Offset(size.width, chartBottom),
            strokeWidth = 1.dp.toPx(),
        )

        // Rain chance: bars growing up from the baseline.
        val barSlot = plotWidth / points.size
        val barWidth = (barSlot * 0.45f).coerceAtMost(12.dp.toPx())
        points.forEachIndexed { index, point ->
            val probability = point.precipitationProbability.coerceIn(0, 100) / 100f
            val barHeight = rainMaxHeight * probability
            if (barHeight > 1f) {
                drawRoundRect(
                    color = rainColor.copy(alpha = 0.35f),
                    topLeft = Offset(xAt(index) - barWidth / 2f, chartBottom - barHeight),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(barWidth / 2f),
                )
            }
        }

        // UV index band near the top.
        if (points.size > 1) {
            val uvArea = Path().apply {
                moveTo(xAt(0), uvBottom)
                points.forEachIndexed { index, point -> lineTo(xAt(index), yAtUv(point.uvIndex)) }
                lineTo(xAt(lastIndex), uvBottom)
                close()
            }
            drawPath(path = uvArea, color = uvColor.copy(alpha = 0.20f))

            val uvLine = Path().apply {
                moveTo(xAt(0), yAtUv(points.first().uvIndex))
                points.forEachIndexed { index, point -> lineTo(xAt(index), yAtUv(point.uvIndex)) }
            }
            drawPath(
                path = uvLine,
                color = uvColor,
                style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        } else {
            drawCircle(
                color = uvColor,
                radius = 2.dp.toPx(),
                center = Offset(xAt(0), yAtUv(points.first().uvIndex)),
            )
        }

        // Temperature curve.
        if (points.size > 1) {
            val temperatureLine = Path().apply {
                moveTo(xAt(0), yAtTemperature(points.first().temperatureC))
                points.forEachIndexed { index, point ->
                    lineTo(xAt(index), yAtTemperature(point.temperatureC))
                }
            }
            drawPath(
                path = temperatureLine,
                color = curveColor,
                style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
            points.forEachIndexed { index, point ->
                drawCircle(
                    color = curveColor,
                    radius = 2.5.dp.toPx(),
                    center = Offset(xAt(index), yAtTemperature(point.temperatureC)),
                )
            }
        } else {
            drawCircle(
                color = curveColor,
                radius = 4.dp.toPx(),
                center = Offset(xAt(0), yAtTemperature(points.first().temperatureC)),
            )
        }
    }
}
