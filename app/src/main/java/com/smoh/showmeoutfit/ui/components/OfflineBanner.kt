package com.smoh.showmeoutfit.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import com.smoh.showmeoutfit.ui.theme.DesignTokens
import com.smoh.showmeoutfit.ui.theme.ShowMeOutfitTheme

/**
 * Inline status banner for cached/offline or error states.
 *
 * [text] is always supplied by the caller (localized). When both [actionLabel] and [onAction]
 * are provided the banner shows a trailing text action with a 48dp minimum touch target.
 * The visual is text-first, so status never relies on color alone.
 */
@Composable
fun OfflineBanner(
    text: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val action = onAction
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(DesignTokens.Radius.md),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        border = BorderStroke(DesignTokens.Hairline, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = DesignTokens.MinTouchTarget)
                .padding(
                    start = DesignTokens.Spacing.lg,
                    end = DesignTokens.Spacing.sm,
                    top = DesignTokens.Spacing.sm,
                    bottom = DesignTokens.Spacing.sm,
                ),
            horizontalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            if (!actionLabel.isNullOrBlank() && action != null) {
                TextButton(
                    onClick = action,
                    modifier = Modifier.heightIn(min = DesignTokens.MinTouchTarget),
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    ),
                    contentPadding = PaddingValues(horizontal = DesignTokens.Spacing.md),
                ) {
                    Text(
                        text = actionLabel,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Preview(name = "OfflineBanner – cached, light", showBackground = true, widthDp = 360)
@Composable
private fun OfflineBannerCachedPreview() {
    ShowMeOutfitTheme(darkTheme = false, dynamicColor = false) {
        OfflineBanner(
            text = "You're offline. Showing the forecast cached at 06:40.",
            actionLabel = "Retry",
            onAction = {},
            modifier = Modifier.padding(DesignTokens.Spacing.lg),
        )
    }
}

@Preview(name = "OfflineBanner – error, light", showBackground = true, widthDp = 360)
@Composable
private fun OfflineBannerErrorPreview() {
    ShowMeOutfitTheme(darkTheme = false, dynamicColor = false) {
        OfflineBanner(
            text = "Couldn't refresh today's forecast. Check your connection and try again.",
            actionLabel = "Try again",
            onAction = {},
            modifier = Modifier.padding(DesignTokens.Spacing.lg),
        )
    }
}

@Preview(
    name = "OfflineBanner – dark",
    showBackground = true,
    widthDp = 360,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun OfflineBannerDarkPreview() {
    ShowMeOutfitTheme(darkTheme = true, dynamicColor = false) {
        OfflineBanner(
            text = "You're offline. Showing the forecast cached at 06:40.",
            actionLabel = "Retry",
            onAction = {},
            modifier = Modifier.padding(DesignTokens.Spacing.lg),
        )
    }
}

@Preview(
    name = "OfflineBanner – no action, 200% font",
    showBackground = true,
    widthDp = 360,
    fontScale = 2f,
)
@Composable
private fun OfflineBannerNoActionPreview() {
    ShowMeOutfitTheme(darkTheme = false, dynamicColor = false) {
        OfflineBanner(
            text = "Forecast data is more than 24 hours old.",
            modifier = Modifier.padding(DesignTokens.Spacing.lg),
        )
    }
}
