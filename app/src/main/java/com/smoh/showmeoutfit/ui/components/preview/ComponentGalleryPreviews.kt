package com.smoh.showmeoutfit.ui.components.preview

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.smoh.showmeoutfit.ui.components.AccessoryBadge
import com.smoh.showmeoutfit.ui.components.AccessoryPriority
import com.smoh.showmeoutfit.ui.components.DiurnalTimelineBar
import com.smoh.showmeoutfit.ui.components.OfflineBanner
import com.smoh.showmeoutfit.ui.components.OutfitLayerCard
import com.smoh.showmeoutfit.ui.components.WeatherMetricChip
import com.smoh.showmeoutfit.ui.theme.DesignTokens
import com.smoh.showmeoutfit.ui.theme.LocalWeatherPalette
import com.smoh.showmeoutfit.ui.theme.ShowMeOutfitTheme
import com.smoh.showmeoutfit.ui.theme.WeatherConditionGroup

/**
 * "Gallery" preview: every SMO-S2-01 component on one screen, in the order the dashboard will
 * use them. Preview-only copy is intentionally inline; production copy is always a resource.
 */
@Composable
private fun ComponentGallery() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(DesignTokens.Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.lg),
    ) {
        OutfitLayerCard(
            title = "Linen shirt",
            description = "Light base for a 22° afternoon.",
            layerLabel = "Base layer",
            accentColor = LocalWeatherPalette.current[WeatherConditionGroup.CLEAR].accent,
        )
        OutfitLayerCard(
            title = "Packable cardigan",
            description = "Put it back on when the evening drops to 14°.",
            layerLabel = "Mid layer",
            accentColor = LocalWeatherPalette.current[WeatherConditionGroup.COLD].accent,
        )
        Column(verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.sm)) {
            AccessoryBadge(label = "Umbrella", priority = AccessoryPriority.CRITICAL)
            AccessoryBadge(label = "Sunglasses", priority = AccessoryPriority.RECOMMENDED)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.sm)) {
            WeatherMetricChip(label = "Rain chance", value = "55", unit = "%")
            WeatherMetricChip(label = "Feels like", value = "21", unit = "°C")
        }
        OfflineBanner(
            text = "Showing the forecast cached at 06:40.",
            actionLabel = "Retry",
            onAction = {},
        )
        DiurnalTimelineBar(points = SampleTimelinePoints)
    }
}

@Preview(name = "Component gallery – light", showBackground = true, widthDp = 400, heightDp = 1400)
@Composable
private fun ComponentGalleryLightPreview() {
    ShowMeOutfitTheme(darkTheme = false, dynamicColor = false) {
        ComponentGallery()
    }
}

@Preview(
    name = "Component gallery – dark",
    showBackground = true,
    widthDp = 400,
    heightDp = 1400,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun ComponentGalleryDarkPreview() {
    ShowMeOutfitTheme(darkTheme = true, dynamicColor = false) {
        ComponentGallery()
    }
}
