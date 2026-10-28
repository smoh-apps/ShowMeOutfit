package com.smoh.showmeoutfit.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import com.smoh.showmeoutfit.R
import com.smoh.showmeoutfit.ui.theme.DesignTokens
import com.smoh.showmeoutfit.ui.theme.ShowMeOutfitTheme

/**
 * Compact metric readout, e.g. humidity, wind or UV.
 *
 * [icon] is an optional slot so the component does not depend on an icon artifact; the caller
 * supplies a sized icon composable (typically 20–24dp). [value] and [unit] are already-formatted
 * strings, keeping locale-aware number formatting in the caller/ViewModel layer.
 *
 * The chip is exposed as one semantics node reading "<label>: <value> <unit>".
 */
@Composable
fun WeatherMetricChip(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    unit: String? = null,
    icon: (@Composable () -> Unit)? = null,
) {
    val hasUnit = !unit.isNullOrBlank()
    val spokenValue = if (hasUnit) "$value $unit" else value
    val description = stringResource(
        R.string.component_metric_chip_content_description,
        label,
        spokenValue,
    )

    Surface(
        modifier = modifier.clearAndSetSemantics { contentDescription = description },
        shape = RoundedCornerShape(DesignTokens.Radius.md),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(DesignTokens.Hairline, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = DesignTokens.MinTouchTarget)
                .padding(horizontal = DesignTokens.Spacing.md, vertical = DesignTokens.Spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Box(
                    modifier = Modifier.size(DesignTokens.IconSize.lg),
                    contentAlignment = Alignment.Center,
                ) {
                    icon()
                }
            }
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row {
                    Text(
                        text = value,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.alignByBaseline(),
                    )
                    if (hasUnit) {
                        Text(
                            text = unit.orEmpty(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .padding(start = DesignTokens.Spacing.xxs)
                                .alignByBaseline(),
                        )
                    }
                }
            }
        }
    }
}

/** Preview-only glyph: the real app passes a Material/vector icon through the slot. */
@Composable
private fun PreviewMetricGlyph(color: Color) {
    Canvas(modifier = Modifier.size(DesignTokens.IconSize.md)) {
        drawCircle(color = color, radius = size.minDimension / 2f)
    }
}

@Preview(name = "WeatherMetricChip – light", showBackground = true, widthDp = 360)
@Composable
private fun WeatherMetricChipLightPreview() {
    ShowMeOutfitTheme(darkTheme = false, dynamicColor = false) {
        Row(
            modifier = Modifier.padding(DesignTokens.Spacing.lg),
            horizontalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.sm),
        ) {
            WeatherMetricChip(
                label = "Humidity",
                value = "62",
                unit = "%",
                icon = { PreviewMetricGlyph(MaterialTheme.colorScheme.primary) },
            )
            WeatherMetricChip(label = "UV index", value = "6.4")
        }
    }
}

@Preview(
    name = "WeatherMetricChip – dark",
    showBackground = true,
    widthDp = 360,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun WeatherMetricChipDarkPreview() {
    ShowMeOutfitTheme(darkTheme = true, dynamicColor = false) {
        Row(
            modifier = Modifier.padding(DesignTokens.Spacing.lg),
            horizontalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.sm),
        ) {
            WeatherMetricChip(
                label = "Wind",
                value = "18",
                unit = "km/h",
                icon = { PreviewMetricGlyph(MaterialTheme.colorScheme.primary) },
            )
            WeatherMetricChip(label = "Feels like", value = "21", unit = "°C")
        }
    }
}

@Preview(
    name = "WeatherMetricChip – 200% font",
    showBackground = true,
    widthDp = 360,
    fontScale = 2f,
)
@Composable
private fun WeatherMetricChipLargeFontPreview() {
    ShowMeOutfitTheme(darkTheme = false, dynamicColor = false) {
        Column(
            modifier = Modifier.padding(DesignTokens.Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.sm),
        ) {
            WeatherMetricChip(label = "Rain chance", value = "55", unit = "%")
        }
    }
}
