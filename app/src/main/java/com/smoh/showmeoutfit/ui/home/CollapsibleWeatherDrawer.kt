package com.smoh.showmeoutfit.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.tooling.preview.Preview
import com.smoh.showmeoutfit.R
import com.smoh.showmeoutfit.domain.model.OutfitRecommendation
import com.smoh.showmeoutfit.domain.model.TodayOutfit
import com.smoh.showmeoutfit.ui.components.DiurnalTimelineBar
import com.smoh.showmeoutfit.ui.components.TimelinePoint
import com.smoh.showmeoutfit.ui.components.WeatherMetricChip
import com.smoh.showmeoutfit.ui.theme.DesignTokens
import com.smoh.showmeoutfit.ui.theme.ShowMeOutfitTheme
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Bottom-of-dashboard weather drawer: the day's peaks stay on screen, the hourly chart is one tap
 * away.
 *
 * Collapsed is the resting state because that is what keeps the outfit grid above the fold; the
 * flag is [rememberSaveable] so a rotation never opens the chart on its own.
 *
 * The whole header is a single button-role node whose label and state are spoken explicitly — the
 * chevron is decorative and carries no description of its own.
 */
@Composable
fun CollapsibleWeatherDrawer(
    recommendation: OutfitRecommendation,
    points: List<TimelinePoint>,
    modifier: Modifier = Modifier,
    initiallyExpanded: Boolean = false
) {
    var expanded by rememberSaveable { mutableStateOf(initiallyExpanded) }

    val actionLabel = stringResource(
        if (expanded) {
            R.string.home_weather_collapse_timeline
        } else {
            R.string.home_weather_expand_timeline
        }
    )
    val stateLabel = stringResource(
        if (expanded) {
            R.string.home_weather_state_expanded
        } else {
            R.string.home_weather_state_collapsed
        }
    )

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(DesignTokens.Radius.lg),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(DesignTokens.Hairline, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(horizontal = DesignTokens.Spacing.md)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = DesignTokens.MinTouchTarget)
                    .testTag(HomeTestTags.TIMELINE_TOGGLE)
                    .clickable { expanded = !expanded }
                    // Merged so TalkBack reads one node ("show hourly timeline, collapsed") instead
                    // of stopping on the title, the chips and the chevron separately.
                    .semantics(mergeDescendants = true) {
                        role = Role.Button
                        contentDescription = actionLabel
                        stateDescription = stateLabel
                    }
                    .padding(vertical = DesignTokens.Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.sm)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionHeading(title = stringResource(R.string.home_section_highlights))
                    Spacer(modifier = Modifier.weight(1f))
                    Icon(
                        imageVector = if (expanded) {
                            Icons.Filled.KeyboardArrowUp
                        } else {
                            Icons.Filled.KeyboardArrowDown
                        },
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(DesignTokens.IconSize.lg)
                    )
                }
                PeakMetricChips(recommendation = recommendation)
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(
                    modifier = Modifier.padding(
                        top = DesignTokens.Spacing.xs,
                        bottom = DesignTokens.Spacing.md
                    ),
                    verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.sm)
                ) {
                    SectionHeading(title = stringResource(R.string.home_section_timeline))
                    DiurnalTimelineBar(
                        points = points,
                        modifier = Modifier.testTag(HomeTestTags.TIMELINE)
                    )
                }
            }
        }
    }
}

/**
 * Peak rain, UV and wind, always visible.
 *
 * The row keeps its own merge boundary: it sits inside the header button, and merging the readouts
 * into that button would leave one unreadable run-on label instead of three metric chips.
 */
@Composable
private fun PeakMetricChips(recommendation: OutfitRecommendation) {
    val highlights = recommendation.weatherHighlights
    val locale = Locale.getDefault()
    // Three chips side by side stop being readable once the text doubles. FlowRow either keeps
    // them in one row (normal scale) or stacks them full width (200%), so nothing clips.
    val itemsPerRow = if (LocalDensity.current.fontScale >= LargeFontScale) 1 else 3

    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(HomeTestTags.METRICS)
            .semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.sm),
        maxItemsInEachRow = itemsPerRow
    ) {
        WeatherMetricChip(
            label = stringResource(R.string.home_metric_rain_label),
            value = TextArg.Count(highlights.maxPrecipitationProb).format(locale),
            unit = stringResource(R.string.home_metric_percent_unit),
            modifier = Modifier.weight(1f)
        )
        WeatherMetricChip(
            label = stringResource(R.string.home_metric_uv_label),
            value = TextArg.Number(highlights.maxUvIndex, decimals = 1).format(locale),
            modifier = Modifier.weight(1f)
        )
        WeatherMetricChip(
            label = stringResource(R.string.home_metric_wind_label),
            value = TextArg.Number(highlights.maxWindSpeedKmh, decimals = 0).format(locale),
            unit = stringResource(R.string.home_metric_wind_unit),
            modifier = Modifier.weight(1f)
        )
    }
}

private fun previewTimelinePoints(outfit: TodayOutfit): List<TimelinePoint> =
    HomeTimelineMapper.points(outfit.weather.snapshot.hourlyForecast) { time ->
        time.format(DateTimeFormatter.ofPattern("HH:mm"))
    }

@Preview(name = "Weather drawer – collapsed", showBackground = true, widthDp = 380)
@Composable
private fun CollapsibleWeatherDrawerCollapsedPreview() {
    val outfit = previewTodayOutfit()
    ShowMeOutfitTheme(darkTheme = false, dynamicColor = false) {
        CollapsibleWeatherDrawer(
            recommendation = outfit.recommendation,
            points = previewTimelinePoints(outfit),
            modifier = Modifier.padding(DesignTokens.Spacing.lg)
        )
    }
}

@Preview(name = "Weather drawer – expanded", showBackground = true, widthDp = 380)
@Composable
private fun CollapsibleWeatherDrawerExpandedPreview() {
    val outfit = previewTodayOutfit()
    ShowMeOutfitTheme(darkTheme = false, dynamicColor = false) {
        CollapsibleWeatherDrawer(
            recommendation = outfit.recommendation,
            points = previewTimelinePoints(outfit),
            modifier = Modifier.padding(DesignTokens.Spacing.lg),
            initiallyExpanded = true
        )
    }
}
