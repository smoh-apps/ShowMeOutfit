package com.smoh.showmeoutfit.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.smoh.showmeoutfit.R
import com.smoh.showmeoutfit.ui.theme.DesignTokens
import com.smoh.showmeoutfit.ui.theme.LocalWeatherPalette
import com.smoh.showmeoutfit.ui.theme.WeatherConditionGroup

/**
 * One hourly sample for [DiurnalTimelineBar].
 *
 * UI-level value object: [hourLabel] is already localized/formatted by the caller, and the
 * numbers stay raw so the component owns the chart scaling.
 */
@Immutable
data class TimelinePoint(
    val hourLabel: String,
    val temperatureC: Double,
    val precipitationProbability: Int,
    val uvIndex: Double,
)

private val TimelineMinHeight = 112.dp
private val TimelineMaxFontScale = 1.5f

/**
 * Diurnal timeline: temperature curve plus rain-chance and UV bands.
 *
 * The chart grows with the user font scale (capped at 150%) so labels stay readable at 200%
 * text size. The whole component is exposed as one semantics node with a spoken summary of
 * hour range, temperature range, peak rain chance and peak UV index.
 */
@Composable
fun DiurnalTimelineBar(
    points: List<TimelinePoint>,
    modifier: Modifier = Modifier,
) {
    if (points.isEmpty()) {
        EmptyTimeline(modifier = modifier)
        return
    }

    val palette = LocalWeatherPalette.current
    val curveColor = MaterialTheme.colorScheme.primary
    val rainColor = palette[WeatherConditionGroup.RAIN].accent
    val uvColor = palette[WeatherConditionGroup.HEAT].accent
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val secondaryTextColor = MaterialTheme.colorScheme.onSurfaceVariant

    val minTemp = points.minOf { it.temperatureC }
    val maxTemp = points.maxOf { it.temperatureC }
    val peakRain = points.maxOf { it.precipitationProbability }
    val peakUv = points.maxOf { it.uvIndex }
    val description = stringResource(
        R.string.component_timeline_content_description,
        points.first().hourLabel,
        points.last().hourLabel,
        minTemp,
        maxTemp,
        peakRain,
        peakUv,
    )

    val fontScale = LocalDensity.current.fontScale.coerceIn(1f, TimelineMaxFontScale)
    val chartHeight = TimelineMinHeight * fontScale

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = description },
        verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.sm),
    ) {
        Text(
            text = stringResource(R.string.component_timeline_range, minTemp, maxTemp),
            style = MaterialTheme.typography.titleSmall,
            color = secondaryTextColor,
        )

        DiurnalTimelineChart(
            points = points,
            curveColor = curveColor,
            rainColor = rainColor,
            uvColor = uvColor,
            gridColor = gridColor,
            chartHeight = chartHeight,
        )

        val labelIndices = listOf(0, points.lastIndex / 2, points.lastIndex).distinct()
        Row(modifier = Modifier.fillMaxWidth()) {
            labelIndices.forEachIndexed { position, index ->
                Text(
                    text = points[index].hourLabel,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = secondaryTextColor,
                    textAlign = when (position) {
                        0 -> TextAlign.Start
                        labelIndices.lastIndex -> TextAlign.End
                        else -> TextAlign.Center
                    },
                    maxLines = 1,
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.xxs)) {
            LegendItem(color = curveColor, label = stringResource(R.string.component_timeline_legend_temperature))
            LegendItem(color = rainColor, label = stringResource(R.string.component_timeline_legend_rain))
            LegendItem(color = uvColor, label = stringResource(R.string.component_timeline_legend_uv))
        }
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(modifier = Modifier.size(DesignTokens.Spacing.sm)) {
            drawCircle(color = color, radius = size.minDimension / 2f)
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EmptyTimeline(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(DesignTokens.Radius.md),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        border = BorderStroke(DesignTokens.Hairline, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Text(
            text = stringResource(R.string.component_timeline_empty),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(DesignTokens.Spacing.lg),
        )
    }
}
