package com.smoh.showmeoutfit.ui.home

import android.content.res.Configuration
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.smoh.showmeoutfit.R
import com.smoh.showmeoutfit.domain.model.Garment
import com.smoh.showmeoutfit.domain.model.OutfitRecommendation
import com.smoh.showmeoutfit.ui.theme.DesignTokens
import com.smoh.showmeoutfit.ui.theme.LocalWeatherPalette
import com.smoh.showmeoutfit.ui.theme.ShowMeOutfitTheme
import com.smoh.showmeoutfit.ui.theme.WeatherConditionGroup

/** Illustration edge used inside a grid tile. */
private val TileIllustrationSize = 64.dp

/** Illustration edge used inside the detail sheet. */
private val DetailIllustrationSize = 88.dp

/**
 * One active outfit layer, resolved to display copy and artwork.
 *
 * The grid builds these from an [OutfitRecommendation]; the detail sheet renders the same instance
 * so the tapped tile and the sheet can never disagree. Copy is resolved by the caller so this type
 * stays locale agnostic and previewable.
 */
@Immutable
data class OutfitTileUi(
    /** Stable key used in the test tag: `base`, `mid`, `outer`, `bottom`, `footwear`. */
    val layerKey: String,
    val layerLabel: String,
    val title: String,
    val description: String,
    @get:DrawableRes val illustrationRes: Int,
    val garment: Garment,
) {
    val testTag: String get() = HomeTestTags.outfitTile(layerKey)
}

/**
 * Outfit-first bento grid: only the layers the engine actually recommended, one illustration tile
 * each.
 *
 * Two tiles per row at the default font scale, one per row once the system font reaches
 * [LargeFontScale], so a doubled title never clips (same breakpoint as the rest of the dashboard).
 * Tiles stay single, merged semantics nodes; the illustration is decorative and carries no
 * description of its own.
 */
@Composable
fun OutfitBentoGrid(
    recommendation: OutfitRecommendation,
    accentColor: Color,
    modifier: Modifier = Modifier,
    onGarmentClick: (OutfitTileUi) -> Unit = {}
) {
    val baseTile = garmentTile(
        layerKey = "base",
        layerLabelRes = R.string.home_layer_base,
        garment = recommendation.baseLayer
    )
    val midTile = recommendation.midLayer?.let { garment ->
        garmentTile(layerKey = "mid", layerLabelRes = R.string.home_layer_mid, garment = garment)
    }
    val outerTile = recommendation.outerLayer?.let { garment ->
        garmentTile(layerKey = "outer", layerLabelRes = R.string.home_layer_outer, garment = garment)
    }
    val bottomTile = garmentTile(
        layerKey = "bottom",
        layerLabelRes = R.string.home_layer_bottom,
        garment = recommendation.bottomLayer
    )
    val footwearTile = garmentTile(
        layerKey = "footwear",
        layerLabelRes = R.string.home_layer_footwear,
        garment = recommendation.footwear
    )
    val tiles = listOfNotNull(baseTile, midTile, outerTile, bottomTile, footwearTile)

    val itemsPerRow = if (LocalDensity.current.fontScale >= LargeFontScale) 1 else 2

    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.md),
        verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.md),
        maxItemsInEachRow = itemsPerRow
    ) {
        tiles.forEach { tile ->
            OutfitVisualTile(
                tile = tile,
                accentColor = accentColor,
                onClick = { onGarmentClick(tile) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * One tappable garment tile: illustration, layer pill and garment name. Deliberately no prose —
 * the description lives in [GarmentDetailSheet] so the grid stays scannable.
 *
 * The whole tile is a single merged, button-role semantics node, so TalkBack reads
 * "<layer label>, <garment name>, double tap to activate" instead of three fragments.
 */
@Composable
fun OutfitVisualTile(
    tile: OutfitTileUi,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // The clickable Surface overload keeps the indication clipped to `shape`, merges the content
    // into one button-role node and supplies the click semantics itself.
    Surface(
        onClick = onClick,
        modifier = modifier
            .heightIn(min = 168.dp)
            .testTag(tile.testTag),
        shape = RoundedCornerShape(DesignTokens.Radius.lg),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = DesignTokens.Elevation.raised,
        border = BorderStroke(DesignTokens.Hairline, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column {
            // Thin weather-accent bar: keeps the diurnal palette tying the whole outfit together
            // without the color carrying any meaning on its own (the pill and title say it).
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(DesignTokens.Spacing.xs)
                    .background(accentColor)
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(DesignTokens.Spacing.lg),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.sm),
            ) {
                Image(
                    painter = painterResource(tile.illustrationRes),
                    contentDescription = null,
                    modifier = Modifier.size(TileIllustrationSize)
                )
                LayerPill(label = tile.layerLabel, accentColor = accentColor)
                Text(
                    text = tile.title,
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/** Layer-category label tinted with the weather accent so tiles read as one outfit. */
@Composable
private fun LayerPill(label: String, accentColor: Color) {
    Surface(
        shape = RoundedCornerShape(DesignTokens.Radius.full),
        color = accentColor.copy(alpha = 0.14f),
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        border = BorderStroke(DesignTokens.Hairline, accentColor.copy(alpha = 0.45f)),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(
                horizontal = DesignTokens.Spacing.sm,
                vertical = DesignTokens.Spacing.xxs
            )
        )
    }
}

/**
 * Expanded view of one garment, opened by tapping its tile.
 *
 * Dismissible through the scrim, the drag handle and the system back gesture; no close button is
 * needed (and no extra copy is introduced for one).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GarmentDetailSheet(
    tile: OutfitTileUi,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier.testTag(HomeTestTags.GARMENT_DETAIL_SHEET)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(
                    start = DesignTokens.Spacing.xl,
                    end = DesignTokens.Spacing.xl,
                    bottom = DesignTokens.Spacing.xxl
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.md),
        ) {
            Image(
                painter = painterResource(tile.illustrationRes),
                contentDescription = null,
                modifier = Modifier.size(DetailIllustrationSize)
            )
            Text(
                text = tile.layerLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = tile.title,
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center
            )
            Text(
                text = tile.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Resolves one recommended garment into a display-ready tile. */
@Composable
private fun garmentTile(
    layerKey: String,
    @StringRes layerLabelRes: Int,
    garment: Garment
): OutfitTileUi = OutfitTileUi(
    layerKey = layerKey,
    layerLabel = stringResource(layerLabelRes),
    title = stringResource(GarmentTextMapper.garmentNameRes(garment.id, garment.fit)),
    description = stringResource(GarmentTextMapper.garmentDescriptionRes(garment.id, garment.fit)),
    illustrationRes = GarmentVisualMapper.garmentDrawableRes(garment.id, garment.fit),
    garment = garment
)

@Preview(name = "Outfit bento grid – light", showBackground = true, widthDp = 380)
@Composable
private fun OutfitBentoGridLightPreview() {
    ShowMeOutfitTheme(darkTheme = false, dynamicColor = false) {
        OutfitBentoGrid(
            recommendation = previewTodayOutfit().recommendation,
            accentColor = LocalWeatherPalette.current[WeatherConditionGroup.CLEAR].accent,
            modifier = Modifier.padding(DesignTokens.Spacing.lg)
        )
    }
}

@Preview(
    name = "Outfit bento grid – dark",
    showBackground = true,
    widthDp = 380,
    uiMode = Configuration.UI_MODE_NIGHT_YES
)
@Composable
private fun OutfitBentoGridDarkPreview() {
    ShowMeOutfitTheme(darkTheme = true, dynamicColor = false) {
        OutfitBentoGrid(
            recommendation = previewTodayOutfit().recommendation,
            accentColor = LocalWeatherPalette.current[WeatherConditionGroup.RAIN].accent,
            modifier = Modifier.padding(DesignTokens.Spacing.lg)
        )
    }
}

@Preview(name = "Outfit bento grid – 200% font", showBackground = true, widthDp = 380, fontScale = 2f)
@Composable
private fun OutfitBentoGridLargeFontPreview() {
    ShowMeOutfitTheme(darkTheme = false, dynamicColor = false) {
        OutfitBentoGrid(
            recommendation = previewTodayOutfit().recommendation,
            accentColor = LocalWeatherPalette.current[WeatherConditionGroup.CLEAR].accent,
            modifier = Modifier.padding(DesignTokens.Spacing.lg)
        )
    }
}

@Preview(name = "Garment detail sheet – light", showBackground = true, widthDp = 380)
@Composable
private fun GarmentDetailSheetPreview() {
    ShowMeOutfitTheme(darkTheme = false, dynamicColor = false) {
        val garment = previewTodayOutfit().recommendation.baseLayer
        GarmentDetailSheet(
            tile = OutfitTileUi(
                layerKey = "base",
                layerLabel = stringResource(R.string.home_layer_base),
                title = stringResource(
                    GarmentTextMapper.garmentNameRes(garment.id, garment.fit)
                ),
                description = stringResource(
                    GarmentTextMapper.garmentDescriptionRes(garment.id, garment.fit)
                ),
                illustrationRes = GarmentVisualMapper.garmentDrawableRes(garment.id, garment.fit),
                garment = garment
            ),
            onDismissRequest = {}
        )
    }
}
