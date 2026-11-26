package com.smoh.showmeoutfit.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.smoh.showmeoutfit.domain.model.AccessoryAlert
import com.smoh.showmeoutfit.domain.model.AccessoryType
import com.smoh.showmeoutfit.domain.model.OutfitRecommendation
import com.smoh.showmeoutfit.ui.theme.DesignTokens
import com.smoh.showmeoutfit.ui.theme.ShowMeOutfitTheme
import java.util.Locale

/** Tags for visual-only hotspots; their semantics are cleared for TalkBack. */
internal object LookbookTestTags {
    const val HOTSPOT_PREFIX = "lookbook_hotspot_"
    fun garment(layerKey: String): String = HOTSPOT_PREFIX + layerKey.lowercase(Locale.ROOT)
    fun accessory(type: AccessoryType): String = HOTSPOT_PREFIX + type.name.lowercase(Locale.ROOT)
}

private val LookbookHeight = 400.dp
private val AccessoryHotspotSize = 64.dp
private val AccessoryAreaWidth = 132.dp

/**
 * Text-free editorial flat-lay of the active recommendation on one studio mat.
 *
 * Garments occupy the left two-thirds and accessories form a two-column rail on the right. The
 * asymmetric placement reads as one outfit rather than a second card grid. Every piece remains
 * tappable for sighted users, while clear semantics make the chips below the only TalkBack stops.
 * Only this spatial subtree is forced LTR; headings and chip rows keep the ambient locale direction.
 */
@Composable
fun OutfitLookbookCanvas(
    recommendation: OutfitRecommendation,
    modifier: Modifier = Modifier,
    onGarmentClick: (OutfitTileUi) -> Unit = {},
    onAccessoryClick: (AccessoryAlert) -> Unit = {}
) {
    val tiles = recommendationTiles(recommendation)
    val tilesByLayer = tiles.associateBy { it.layerKey }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        StudioMatSurface(
            modifier = modifier
                .fillMaxWidth()
                .height(LookbookHeight)
                .testTag(HomeTestTags.OUTFIT_LOOKBOOK)
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val garmentAreaWidth = (maxWidth - AccessoryAreaWidth - 8.dp)
                    .coerceAtLeast(140.dp)
                val baseSize = (garmentAreaWidth * 0.52f).coerceIn(72.dp, 112.dp)
                val layerSize = (garmentAreaWidth * 0.50f).coerceIn(72.dp, 108.dp)
                val bottomSize = (garmentAreaWidth * 0.54f).coerceIn(80.dp, 112.dp)
                val footwearSize = (garmentAreaWidth * 0.46f).coerceIn(72.dp, 100.dp)

                tilesByLayer["base"]?.let { tile ->
                    LookbookHotspot(
                        tile = tile,
                        x = 4.dp,
                        y = 16.dp,
                        size = baseSize,
                        onClick = { onGarmentClick(tile) }
                    )
                }
                tilesByLayer["mid"]?.let { tile ->
                    LookbookHotspot(
                        tile = tile,
                        x = garmentAreaWidth * 0.40f,
                        y = 40.dp,
                        size = layerSize,
                        onClick = { onGarmentClick(tile) }
                    )
                }
                tilesByLayer["outer"]?.let { tile ->
                    LookbookHotspot(
                        tile = tile,
                        x = garmentAreaWidth * 0.46f,
                        y = 148.dp,
                        size = layerSize,
                        onClick = { onGarmentClick(tile) }
                    )
                }
                tilesByLayer["bottom"]?.let { tile ->
                    LookbookHotspot(
                        tile = tile,
                        x = 4.dp,
                        y = 188.dp,
                        size = bottomSize,
                        onClick = { onGarmentClick(tile) }
                    )
                }
                tilesByLayer["footwear"]?.let { tile ->
                    LookbookHotspot(
                        tile = tile,
                        x = garmentAreaWidth * 0.38f,
                        y = 284.dp,
                        size = footwearSize,
                        onClick = { onGarmentClick(tile) }
                    )
                }

                val accessoryStartX = maxWidth - AccessoryAreaWidth
                recommendation.accessories.forEachIndexed { index, alert ->
                    val column = index % 2
                    val row = index / 2
                    LookbookHotspot(
                        source = GarmentVisualMapper.accessoryVisualSource(alert.type),
                        x = accessoryStartX + 68.dp * column,
                        y = 16.dp + 88.dp * row,
                        size = AccessoryHotspotSize,
                        onClick = { onAccessoryClick(alert) },
                        tag = LookbookTestTags.accessory(alert.type)
                    )
                }
            }
        }
    }
}

/** A visual hotspot with a real touch callback but no duplicate accessibility content or action. */
@Composable
private fun LookbookHotspot(
    source: GarmentVisualSource,
    x: Dp,
    y: Dp,
    size: Dp,
    onClick: () -> Unit,
    tag: String
) {
    Box(
        modifier = Modifier
            .offset(x = x, y = y)
            .size(size)
            .clickable(role = Role.Button, onClick = onClick)
            .clearAndSetSemantics {}
            .testTag(tag)
    ) {
        GarmentArtwork(
            source = source,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentPadding = 0.dp,
            showStudioMat = false
        )
    }
}

@Composable
private fun LookbookHotspot(
    tile: OutfitTileUi,
    x: Dp,
    y: Dp,
    size: Dp,
    onClick: () -> Unit
) {
    LookbookHotspot(
        source = GarmentVisualSource.Preset(tile.illustrationRes),
        x = x,
        y = y,
        size = size,
        onClick = onClick,
        tag = LookbookTestTags.garment(tile.layerKey)
    )
}

/** Accessible piece and trigger chips directly below the collage, in anatomical order. */
@Composable
fun OutfitLookbookChips(
    recommendation: OutfitRecommendation,
    modifier: Modifier = Modifier,
    onGarmentClick: (OutfitTileUi) -> Unit = {},
    onAccessoryClick: (AccessoryAlert) -> Unit = {}
) {
    val tiles = recommendationTiles(recommendation)
    val largeFont = LocalDensity.current.fontScale >= LargeFontScale

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.md)
    ) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.sm),
            maxItemsInEachRow = if (largeFont) 1 else 2
        ) {
            tiles.forEach { tile ->
                OutfitGarmentChip(
                    tile = tile,
                    onClick = { onGarmentClick(tile) },
                    modifier = if (largeFont) Modifier.weight(1f) else Modifier
                )
            }
        }
        AccessoryTriggerChips(
            alerts = recommendation.accessories,
            onAccessoryClick = onAccessoryClick
        )
    }
}

/** One merged button-role stop for a garment; its prose remains in the detail sheet. */
@Composable
private fun OutfitGarmentChip(
    tile: OutfitTileUi,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .heightIn(min = DesignTokens.MinTouchTarget)
            .clickable(onClickLabel = tile.title, role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) {},
        shape = RoundedCornerShape(DesignTokens.Radius.full),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(DesignTokens.Hairline, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Text(
            text = tile.title,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(
                horizontal = DesignTokens.Spacing.md,
                vertical = DesignTokens.Spacing.sm
            )
        )
    }
}

@Preview(name = "Outfit lookbook – light", showBackground = true, widthDp = 380)
@Composable
private fun OutfitLookbookPreview() {
    ShowMeOutfitTheme(darkTheme = false, dynamicColor = false) {
        val recommendation = previewTodayOutfit().recommendation
        Column(
            modifier = Modifier.padding(DesignTokens.Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.md)
        ) {
            OutfitLookbookCanvas(recommendation = recommendation)
            OutfitLookbookChips(recommendation = recommendation)
        }
    }
}
