package com.smoh.showmeoutfit.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import com.smoh.showmeoutfit.R
import com.smoh.showmeoutfit.ui.theme.DesignTokens
import com.smoh.showmeoutfit.ui.theme.LocalWeatherPalette
import com.smoh.showmeoutfit.ui.theme.ShowMeOutfitTheme
import com.smoh.showmeoutfit.ui.theme.WeatherConditionGroup

/**
 * UI-level urgency for an accessory hint.
 *
 * Mirrors the shape of the domain priority but stays independent of it, so components can be
 * previewed and reused without touching `domain.model.PriorityLevel`.
 */
enum class AccessoryPriority {
    CRITICAL,
    RECOMMENDED,
    OPTIONAL,
}

private val AccessoryPriority.labelRes: Int
    get() = when (this) {
        AccessoryPriority.CRITICAL -> R.string.component_accessory_priority_critical
        AccessoryPriority.RECOMMENDED -> R.string.component_accessory_priority_recommended
        AccessoryPriority.OPTIONAL -> R.string.component_accessory_priority_optional
    }

private val AccessoryPriority.weatherGroup: WeatherConditionGroup
    get() = when (this) {
        AccessoryPriority.CRITICAL -> WeatherConditionGroup.STORM
        AccessoryPriority.RECOMMENDED -> WeatherConditionGroup.CLEAR
        AccessoryPriority.OPTIONAL -> WeatherConditionGroup.CLOUDY
    }

/**
 * Accessory hint pill, e.g. "Umbrella · Essential".
 *
 * The priority is conveyed by its localized text label (not only by color), and the merged
 * semantics node announces "<priority> accessory: <label>".
 */
@Composable
fun AccessoryBadge(
    label: String,
    priority: AccessoryPriority,
    modifier: Modifier = Modifier,
) {
    val colors = LocalWeatherPalette.current[priority.weatherGroup]
    val priorityText = stringResource(priority.labelRes)
    val description = stringResource(
        R.string.component_accessory_badge_content_description,
        priorityText,
        label,
    )

    Surface(
        modifier = modifier.clearAndSetSemantics { contentDescription = description },
        shape = RoundedCornerShape(DesignTokens.Radius.full),
        color = colors.container,
        contentColor = colors.onContainer,
        border = BorderStroke(DesignTokens.Hairline, colors.accent.copy(alpha = 0.45f)),
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = DesignTokens.Spacing.md,
                vertical = DesignTokens.Spacing.sm,
            ),
            horizontalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(DesignTokens.Spacing.sm)
                    .background(colors.accent, CircleShape)
            )
            Column(verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.xxs)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = priorityText,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.accent,
                )
            }
        }
    }
}

@Preview(name = "AccessoryBadge – light", showBackground = true, widthDp = 360)
@Composable
private fun AccessoryBadgeLightPreview() {
    ShowMeOutfitTheme(darkTheme = false, dynamicColor = false) {
        Column(
            modifier = Modifier.padding(DesignTokens.Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.sm),
        ) {
            AccessoryBadge(label = "Umbrella", priority = AccessoryPriority.CRITICAL)
            AccessoryBadge(label = "Sunglasses", priority = AccessoryPriority.RECOMMENDED)
            AccessoryBadge(label = "Sun hat", priority = AccessoryPriority.OPTIONAL)
        }
    }
}

@Preview(
    name = "AccessoryBadge – dark",
    showBackground = true,
    widthDp = 360,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun AccessoryBadgeDarkPreview() {
    ShowMeOutfitTheme(darkTheme = true, dynamicColor = false) {
        Column(
            modifier = Modifier.padding(DesignTokens.Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.sm),
        ) {
            AccessoryBadge(label = "Umbrella", priority = AccessoryPriority.CRITICAL)
            AccessoryBadge(label = "Sunscreen", priority = AccessoryPriority.RECOMMENDED)
        }
    }
}

@Preview(
    name = "AccessoryBadge – 200% font",
    showBackground = true,
    widthDp = 360,
    fontScale = 2f,
)
@Composable
private fun AccessoryBadgeLargeFontPreview() {
    ShowMeOutfitTheme(darkTheme = false, dynamicColor = false) {
        Column(
            modifier = Modifier.padding(DesignTokens.Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.sm),
        ) {
            AccessoryBadge(
                label = "Extra evening layer",
                priority = AccessoryPriority.RECOMMENDED,
            )
        }
    }
}
