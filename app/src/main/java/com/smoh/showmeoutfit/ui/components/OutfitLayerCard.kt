package com.smoh.showmeoutfit.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.smoh.showmeoutfit.ui.theme.DesignTokens
import com.smoh.showmeoutfit.ui.theme.ShowMeOutfitTheme

/**
 * Card for one outfit layer (base, mid, outer, bottom, footwear).
 *
 * The caller resolves all copy (localized strings) and may pass an optional [accentColor]
 * taken from [com.smoh.showmeoutfit.ui.theme.LocalWeatherPalette]. The accent is decorative:
 * [layerLabel] always carries the meaning as text, never color alone.
 *
 * The whole card is one merged semantics node, so TalkBack reads label, title and description
 * as a single coherent statement.
 */
@Composable
fun OutfitLayerCard(
    title: String,
    description: String,
    layerLabel: String,
    accentColor: Color? = null,
    minHeight: Dp = 96.dp,
    modifier: Modifier = Modifier,
) {
    val accent = accentColor ?: MaterialTheme.colorScheme.primary
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {},
        shape = RoundedCornerShape(DesignTokens.Radius.lg),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = DesignTokens.Elevation.raised,
        border = BorderStroke(DesignTokens.Hairline, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            // First child of the Row => rendered at the start edge in both LTR and RTL.
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(DesignTokens.Spacing.xs + DesignTokens.Spacing.xxs)
                    .background(accent)
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = minHeight)
                    .padding(DesignTokens.Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.xs),
            ) {
                Text(
                    text = layerLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                )
                if (description.isNotBlank()) {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Preview(name = "OutfitLayerCard – light", showBackground = true, widthDp = 360)
@Composable
private fun OutfitLayerCardLightPreview() {
    ShowMeOutfitTheme(darkTheme = false, dynamicColor = false) {
        OutfitLayerCard(
            title = "Fine merino tee",
            description = "Breathable base that stays comfortable on the warm afternoon walk.",
            layerLabel = "Base layer",
            accentColor = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(DesignTokens.Spacing.lg),
        )
    }
}

@Preview(name = "OutfitLayerCard – dark", showBackground = true, widthDp = 360, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun OutfitLayerCardDarkPreview() {
    ShowMeOutfitTheme(darkTheme = true, dynamicColor = false) {
        OutfitLayerCard(
            title = "Packable light jacket",
            description = "Take it off after 14:00 when the temperature peaks at 22°.",
            layerLabel = "Outer layer",
            accentColor = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(DesignTokens.Spacing.lg),
        )
    }
}

@Preview(name = "OutfitLayerCard – no description", showBackground = true, widthDp = 360)
@Composable
private fun OutfitLayerCardEmptyDescriptionPreview() {
    ShowMeOutfitTheme(darkTheme = false, dynamicColor = false) {
        OutfitLayerCard(
            title = "Waterproof sneakers",
            description = "",
            layerLabel = "Footwear",
            modifier = Modifier.padding(DesignTokens.Spacing.lg),
        )
    }
}
