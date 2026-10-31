package com.smoh.showmeoutfit.ui.components.preview

import android.content.res.Configuration
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.smoh.showmeoutfit.ui.components.DiurnalTimelineBar
import com.smoh.showmeoutfit.ui.theme.DesignTokens
import com.smoh.showmeoutfit.ui.theme.ShowMeOutfitTheme

@Preview(name = "DiurnalTimelineBar – light", showBackground = true, widthDp = 360)
@Composable
private fun DiurnalTimelineBarLightPreview() {
    ShowMeOutfitTheme(darkTheme = false, dynamicColor = false) {
        DiurnalTimelineBar(
            points = SampleTimelinePoints,
            modifier = Modifier.padding(DesignTokens.Spacing.lg),
        )
    }
}

@Preview(
    name = "DiurnalTimelineBar – dark",
    showBackground = true,
    widthDp = 360,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun DiurnalTimelineBarDarkPreview() {
    ShowMeOutfitTheme(darkTheme = true, dynamicColor = false) {
        DiurnalTimelineBar(
            points = SampleTimelinePoints,
            modifier = Modifier.padding(DesignTokens.Spacing.lg),
        )
    }
}

@Preview(
    name = "DiurnalTimelineBar – 200% font",
    showBackground = true,
    widthDp = 360,
    fontScale = 2f,
)
@Composable
private fun DiurnalTimelineBarLargeFontPreview() {
    ShowMeOutfitTheme(darkTheme = false, dynamicColor = false) {
        DiurnalTimelineBar(
            points = SampleTimelinePoints,
            modifier = Modifier.padding(DesignTokens.Spacing.lg),
        )
    }
}

@Preview(name = "DiurnalTimelineBar – empty", showBackground = true, widthDp = 360)
@Composable
private fun DiurnalTimelineBarEmptyPreview() {
    ShowMeOutfitTheme(darkTheme = false, dynamicColor = false) {
        DiurnalTimelineBar(
            points = emptyList(),
            modifier = Modifier.padding(DesignTokens.Spacing.lg),
        )
    }
}

@Preview(name = "DiurnalTimelineBar – single point", showBackground = true, widthDp = 360)
@Composable
private fun DiurnalTimelineBarSinglePointPreview() {
    ShowMeOutfitTheme(darkTheme = false, dynamicColor = false) {
        DiurnalTimelineBar(
            points = listOf(SampleTimelinePoints.first()),
            modifier = Modifier.padding(DesignTokens.Spacing.lg),
        )
    }
}
