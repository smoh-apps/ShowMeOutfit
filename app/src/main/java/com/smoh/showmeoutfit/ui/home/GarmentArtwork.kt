package com.smoh.showmeoutfit.ui.home

import android.content.res.Configuration
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.smoh.showmeoutfit.R
import com.smoh.showmeoutfit.domain.model.GarmentFit
import com.smoh.showmeoutfit.domain.model.GarmentId
import com.smoh.showmeoutfit.ui.theme.DesignTokens
import com.smoh.showmeoutfit.ui.theme.ShowMeOutfitTheme
import java.io.File

@Immutable
sealed interface GarmentVisualSource {
    data class Preset(@get:DrawableRes val drawableRes: Int) : GarmentVisualSource

    data class UserClosetPhoto(
        val filePath: String,
        @get:DrawableRes val fallbackRes: Int
    ) : GarmentVisualSource
}

/** Warm light mat or elevated dark mat with a restrained white studio backlight. */
@Composable
fun StudioMatSurface(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val dark = isSystemInDarkTheme()
    val outlineVariant = MaterialTheme.colorScheme.outlineVariant
    val shape = RoundedCornerShape(DesignTokens.Radius.lg)
    Box(
        modifier = modifier
            .clip(shape)
            .drawWithCache {
                val brush = if (dark) {
                    Brush.radialGradient(
                        0f to Color.White.copy(alpha = 0.07f),
                        1f to Color(0xFF2B2930),
                        center = Offset(size.width * 0.32f, size.height * 0.22f),
                        radius = size.maxDimension * 0.9f
                    )
                } else {
                    Brush.radialGradient(
                        0f to Color.White,
                        1f to Color(0xFFFAF8F5),
                        center = Offset(size.width * 0.32f, size.height * 0.22f),
                        radius = size.maxDimension * 0.9f
                    )
                }
                onDrawBehind { drawRect(brush) }
            }
            .border(
                width = DesignTokens.Hairline,
                color = outlineVariant,
                shape = shape
            ),
        contentAlignment = Alignment.Center,
        content = content
    )
}

@Composable
fun GarmentArtwork(
    source: GarmentVisualSource,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentPadding: Dp = DesignTokens.Spacing.xs
) {
    StudioMatSurface(modifier = modifier) {
        when (source) {
            is GarmentVisualSource.Preset -> Image(
                painter = painterResource(source.drawableRes),
                contentDescription = contentDescription,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
                contentScale = ContentScale.Fit
            )

            is GarmentVisualSource.UserClosetPhoto -> {
                val fallback = painterResource(source.fallbackRes)
                AsyncImage(
                    model = File(source.filePath),
                    contentDescription = contentDescription,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(contentPadding),
                    contentScale = ContentScale.Fit,
                    fallback = fallback,
                    error = fallback
                )
            }
        }
    }
}

@Preview(name = "Studio artwork – light", showBackground = true, widthDp = 180)
@Composable
private fun GarmentArtworkLightPreview() {
    ShowMeOutfitTheme(darkTheme = false, dynamicColor = false) {
        GarmentArtwork(
            source = GarmentVisualMapper.garmentVisualSource(
                GarmentId.CARDIGAN,
                GarmentFit.WOMEN
            ),
            contentDescription = null,
            modifier = Modifier.size(160.dp)
        )
    }
}

@Preview(
    name = "Studio artwork – dark",
    showBackground = true,
    widthDp = 180,
    uiMode = Configuration.UI_MODE_NIGHT_YES
)
@Composable
private fun GarmentArtworkDarkPreview() {
    ShowMeOutfitTheme(darkTheme = true, dynamicColor = false) {
        GarmentArtwork(
            source = GarmentVisualSource.Preset(R.drawable.cutout_down_parka),
            contentDescription = null,
            modifier = Modifier.size(160.dp)
        )
    }
}
