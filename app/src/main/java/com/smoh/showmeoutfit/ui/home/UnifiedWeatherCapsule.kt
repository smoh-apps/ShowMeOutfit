package com.smoh.showmeoutfit.ui.home

import android.content.res.Configuration
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.smoh.showmeoutfit.R
import com.smoh.showmeoutfit.domain.model.CurrentCondition
import com.smoh.showmeoutfit.domain.model.TodayOutfit
import com.smoh.showmeoutfit.domain.model.WeatherHighlights
import com.smoh.showmeoutfit.ui.components.DiurnalTimelineBar
import com.smoh.showmeoutfit.ui.components.TimelinePoint
import com.smoh.showmeoutfit.ui.theme.DesignTokens
import com.smoh.showmeoutfit.ui.theme.LocalWeatherPalette
import com.smoh.showmeoutfit.ui.theme.ShowMeOutfitTheme
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Icon-first top capsule: location, sky, temperature and the day's peaks in one rounded card.
 *
 * Replaces the v1.1.0 prose header *and* the separate bottom weather drawer: the hourly chart is
 * folded into the same card behind [onToggleTimeline], so the outfit grid stays above the fold
 * without deleting the weather detail.
 *
 * Layout rules:
 * - The header line owns exactly one tap target per action (48.dp each): the city row opens the
 *   location picker, then refresh and the timeline toggle.
 * - The metric line is a [FlowRow], so at 200% text size the sky/temperature group wraps instead of
 *   clipping and the peak chips drop onto extra lines on their own.
 * - Peak chips are decorative-plus-number for sighted users (the icon carries the meaning) and one
 *   merged semantics node with a full spoken sentence for TalkBack.
 * - No prose weather summary is rendered here on purpose (ADR-DESIGN-V2 D3).
 *
 * Stateless: [expanded], [isRefreshing] and every action come from the caller, so previews, screenshot
 * tests and the future Home integration all drive the same function.
 */
@Composable
fun UnifiedWeatherCapsule(
    cityName: String,
    current: CurrentCondition,
    highlights: WeatherHighlights,
    points: List<TimelinePoint>,
    accentColor: Color,
    isRefreshing: Boolean,
    expanded: Boolean,
    onToggleTimeline: () -> Unit,
    onLocationClick: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    val locale = Locale.getDefault()
    val currentTemp = TextArg.Number(current.temperatureCelsius, decimals = 0).format(locale)
    val minTemp = TextArg.Number(highlights.tempMinCelsius, decimals = 0).format(locale)
    val maxTemp = TextArg.Number(highlights.tempMaxCelsius, decimals = 0).format(locale)
    val rainValue = TextArg.Count(highlights.maxPrecipitationProb).format(locale)
    val uvValue = TextArg.Number(highlights.maxUvIndex, decimals = 0).format(locale)
    val windValue = TextArg.Number(highlights.maxWindSpeedKmh, decimals = 0).format(locale)

    val changeLocationLabel = stringResource(R.string.home_capsule_change_location, cityName)
    val refreshLabel = stringResource(R.string.home_capsule_refresh)
    val toggleLabel = stringResource(
        if (expanded) R.string.home_capsule_timeline_hide else R.string.home_capsule_timeline_show
    )
    // A city name is content: at 200% it may use two lines instead of being reduced to "Lisb…".
    val cityMaxLines = if (LocalDensity.current.fontScale >= LargeFontScale) 2 else 1

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(DesignTokens.Radius.lg),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(DesignTokens.Hairline, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = DesignTokens.Spacing.md,
                vertical = DesignTokens.Spacing.xs
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = DesignTokens.MinTouchTarget),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Merged so TalkBack offers one button ("Change location: Lisbon") for the whole
                // city block; the place pin and the chevron are decorative and stay silent.
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = DesignTokens.MinTouchTarget)
                        .clip(RoundedCornerShape(DesignTokens.Radius.md))
                        .clickable(onClick = onLocationClick)
                        .semantics(mergeDescendants = true) {
                            role = Role.Button
                            contentDescription = changeLocationLabel
                        }
                        .padding(end = DesignTokens.Spacing.sm),
                    horizontalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.xxs),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Place,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(DesignTokens.IconSize.md)
                    )
                    Text(
                        text = cityName,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = cityMaxLines,
                        overflow = TextOverflow.Ellipsis
                    )
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(DesignTokens.IconSize.md)
                    )
                }

                IconButton(
                    onClick = onRefresh,
                    enabled = !isRefreshing,
                    modifier = Modifier.heightIn(min = DesignTokens.MinTouchTarget)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = refreshLabel,
                        tint = accentColor
                    )
                }

                IconButton(
                    onClick = onToggleTimeline,
                    modifier = Modifier
                        .heightIn(min = DesignTokens.MinTouchTarget)
                        .testTag(HomeTestTags.TIMELINE_TOGGLE)
                ) {
                    Icon(
                        imageVector = if (expanded) {
                            Icons.Filled.KeyboardArrowUp
                        } else {
                            Icons.Filled.KeyboardArrowDown
                        },
                        contentDescription = toggleLabel,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = DesignTokens.Spacing.xxs),
                horizontalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.xs)
            ) {
                Image(
                    painter = painterResource(WeatherVisualMapper.skyIconRes(current.weatherCode)),
                    contentDescription = null,
                    modifier = Modifier.size(28.dp)
                )
                Text(
                    text = stringResource(R.string.home_capsule_temp_current, currentTemp),
                    style = MaterialTheme.typography.titleLarge,
                    color = accentColor,
                    maxLines = 1
                )
                Text(
                    text = stringResource(R.string.home_capsule_temp_range, minTemp, maxTemp),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
                PeakChip(
                    iconRes = R.drawable.ic_metric_rain_drop,
                    value = stringResource(R.string.home_capsule_rain_value, rainValue),
                    description = stringResource(R.string.home_capsule_rain_peak, rainValue),
                    accentColor = accentColor
                )
                PeakChip(
                    iconRes = R.drawable.ic_metric_uv_sun,
                    value = uvValue,
                    description = stringResource(R.string.home_capsule_uv_peak, uvValue),
                    accentColor = accentColor
                )
                PeakChip(
                    iconRes = R.drawable.ic_metric_wind,
                    value = windValue,
                    description = stringResource(R.string.home_capsule_wind_peak, windValue),
                    accentColor = accentColor
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.padding(top = DesignTokens.Spacing.xs)
                    )
                    DiurnalTimelineBar(
                        points = points,
                        modifier = Modifier
                            .testTag(HomeTestTags.TIMELINE)
                            .padding(top = DesignTokens.Spacing.sm),
                        accentColor = accentColor
                    )
                }
            }
        }
    }
}

/**
 * One read-only peak: a tinted metric glyph plus its number inside a pill.
 *
 * The chip is not clickable, so it is safe to collapse into a single semantics node: TalkBack reads
 * "Rain peak 40 percent" instead of the icon and the bare number as two separate stops.
 */
@Composable
private fun PeakChip(
    @DrawableRes iconRes: Int,
    value: String,
    description: String,
    accentColor: Color
) {
    Surface(
        modifier = Modifier.clearAndSetSemantics { contentDescription = description },
        shape = RoundedCornerShape(DesignTokens.Radius.full),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(DesignTokens.Hairline, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = DesignTokens.Spacing.sm,
                vertical = DesignTokens.Spacing.xxs
            ),
            horizontalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(DesignTokens.IconSize.sm)
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1
            )
        }
    }
}

// --- Previews ----------------------------------------------------------------------------

private fun previewPoints(outfit: TodayOutfit): List<TimelinePoint> =
    HomeTimelineMapper.points(outfit.weather.snapshot.hourlyForecast) { time ->
        DateTimeFormatter.ofPattern("HH:mm").format(time)
    }

@Composable
private fun CapsulePreview(darkTheme: Boolean, expanded: Boolean) {
    val outfit = previewTodayOutfit()
    ShowMeOutfitTheme(darkTheme = darkTheme, dynamicColor = false) {
        val weather = outfit.weather.snapshot
        UnifiedWeatherCapsule(
            cityName = weather.location.cityName,
            current = weather.current,
            highlights = outfit.recommendation.weatherHighlights,
            points = previewPoints(outfit),
            accentColor = LocalWeatherPalette.current[
                GarmentTextMapper.conditionGroup(weather.current.weatherCode)
            ].accent,
            isRefreshing = false,
            expanded = expanded,
            onToggleTimeline = {},
            onLocationClick = {},
            onRefresh = {},
            modifier = Modifier.padding(DesignTokens.Spacing.lg)
        )
    }
}

@Preview(name = "Capsule – collapsed, light", showBackground = true, widthDp = 380)
@Composable
private fun CapsuleCollapsedLightPreview() {
    CapsulePreview(darkTheme = false, expanded = false)
}

@Preview(
    name = "Capsule – collapsed, dark",
    showBackground = true,
    widthDp = 380,
    uiMode = Configuration.UI_MODE_NIGHT_YES
)
@Composable
private fun CapsuleCollapsedDarkPreview() {
    CapsulePreview(darkTheme = true, expanded = false)
}

@Preview(name = "Capsule – expanded, light", showBackground = true, widthDp = 380)
@Composable
private fun CapsuleExpandedLightPreview() {
    CapsulePreview(darkTheme = false, expanded = true)
}

@Preview(
    name = "Capsule – expanded, dark, 200% text",
    showBackground = true,
    widthDp = 380,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
    fontScale = 2f
)
@Composable
private fun CapsuleExpandedDarkLargeFontPreview() {
    CapsulePreview(darkTheme = true, expanded = true)
}

@Preview(
    name = "Capsule – collapsed, light, 200% text",
    showBackground = true,
    widthDp = 380,
    fontScale = 2f
)
@Composable
private fun CapsuleCollapsedLightLargeFontPreview() {
    CapsulePreview(darkTheme = false, expanded = false)
}
