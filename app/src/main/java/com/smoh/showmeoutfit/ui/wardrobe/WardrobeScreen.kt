package com.smoh.showmeoutfit.ui.wardrobe

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.smoh.showmeoutfit.R
import com.smoh.showmeoutfit.domain.model.GarmentFit
import com.smoh.showmeoutfit.domain.model.LayerCategory
import com.smoh.showmeoutfit.domain.model.WardrobeItem
import com.smoh.showmeoutfit.ui.home.GarmentArtwork
import com.smoh.showmeoutfit.ui.home.GarmentTextMapper
import com.smoh.showmeoutfit.ui.home.GarmentVisualMapper
import com.smoh.showmeoutfit.ui.theme.DesignTokens
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

/** Stable tags keep instrumented flows independent of the active language. */
object WardrobeTestTags {
    const val SCREEN = "wardrobe_screen"
    const val BACK = "wardrobe_back"
    const val ADD_ITEM = "wardrobe_add_item"
    const val FILTERS = "wardrobe_filters"
    const val FILTER_PREFIX = "wardrobe_filter_"
    const val GRID = "wardrobe_grid"
    const val ITEM_PREFIX = "wardrobe_item_"
    const val WATER_BADGE_PREFIX = "wardrobe_water_"
    const val EMPTY = "wardrobe_empty"
    const val EMPTY_ADD = "wardrobe_empty_add"
    const val LOADING = "wardrobe_loading"
    const val LOAD_ERROR = "wardrobe_load_error"
    const val RETRY = "wardrobe_retry"

    const val EDITOR = "wardrobe_editor"
    const val CATEGORY_SELECTOR = "wardrobe_editor_category"
    const val CATEGORY_OPTION_PREFIX = "wardrobe_category_option_"
    const val GARMENT_SELECTOR = "wardrobe_editor_garment"
    const val GARMENT_OPTION_PREFIX = "wardrobe_garment_option_"
    const val FIT_PREFIX = "wardrobe_fit_"
    const val NAME_INPUT = "wardrobe_editor_name"
    const val WATER_SWITCH = "wardrobe_editor_water"
    const val SAVE = "wardrobe_editor_save"
    const val DELETE = "wardrobe_editor_delete"
    const val PREVIEW = "wardrobe_editor_preview"

    fun filter(filter: WardrobeFilter): String = FILTER_PREFIX + filter.name.lowercase()
    fun item(id: String): String = ITEM_PREFIX + id
    fun waterBadge(id: String): String = WATER_BADGE_PREFIX + id
    fun categoryOption(category: LayerCategory): String =
        CATEGORY_OPTION_PREFIX + category.name.lowercase()
    fun garmentOption(id: com.smoh.showmeoutfit.domain.model.GarmentId): String =
        GARMENT_OPTION_PREFIX + id.name.lowercase()
    fun fit(fit: GarmentFit): String = FIT_PREFIX + fit.name.lowercase()
}

/** Font scale at which two-column garment cards become single-column cards. */
internal const val WardrobeLargeFontScale = 1.5f

private val ArtworkSize = 104.dp

@Composable
fun WardrobeRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WardrobeViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current

    LaunchedEffect(viewModel, resources, snackbarHostState) {
        viewModel.effects.collect { effect ->
            val messageRes = when (effect) {
                WardrobeEffect.ItemSaved -> R.string.wardrobe_effect_saved
                WardrobeEffect.ItemDeleted -> R.string.wardrobe_effect_deleted
                is WardrobeEffect.ShowError -> when (effect.error) {
                    WardrobeError.LOAD_FAILED -> R.string.wardrobe_effect_load_failed
                    WardrobeError.SAVE_FAILED -> R.string.wardrobe_effect_save_failed
                    WardrobeError.DELETE_FAILED -> R.string.wardrobe_effect_delete_failed
                }
            }
            snackbarHostState.showSnackbar(resources.getString(messageRes))
        }
    }

    WardrobeScreen(
        state = state,
        onIntent = viewModel::onIntent,
        onBack = onBack,
        snackbarHostState = snackbarHostState,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WardrobeScreen(
    state: WardrobeUiState,
    onIntent: (WardrobeIntent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() }
) {
    val resources = LocalResources.current

    val openAdd: (LayerCategory) -> Unit = { category ->
        val garmentId = WardrobeCatalog.garmentsFor(category).first()
        val defaultName = resources.getString(
            GarmentTextMapper.garmentNameRes(garmentId, GarmentFit.UNISEX)
        )
        onIntent(WardrobeIntent.OpenAdd(category, defaultName))
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.wardrobe_title)) },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag(WardrobeTestTags.BACK)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.wardrobe_back_action)
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { openAdd(state.selectedFilter.category ?: LayerCategory.BASE) },
                        modifier = Modifier.testTag(WardrobeTestTags.ADD_ITEM)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = stringResource(R.string.wardrobe_add_item)
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .testTag(WardrobeTestTags.SCREEN)
        ) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(WardrobeTestTags.FILTERS),
                contentPadding = PaddingValues(horizontal = DesignTokens.Spacing.lg),
                horizontalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.sm)
            ) {
                items(WardrobeFilter.entries, key = { it.name }) { filter ->
                    FilterChip(
                        selected = state.selectedFilter == filter,
                        onClick = { onIntent(WardrobeIntent.SelectFilter(filter)) },
                        label = { Text(stringResource(filterLabelRes(filter))) },
                        modifier = Modifier
                            .heightIn(min = DesignTokens.MinTouchTarget)
                            .testTag(WardrobeTestTags.filter(filter))
                    )
                }
            }

            when {
                state.isLoading -> LoadingState()
                state.loadFailed -> LoadErrorState(onRetry = { onIntent(WardrobeIntent.RetryLoad) })
                state.items.isEmpty() -> EmptyClosetState(
                    filter = state.selectedFilter,
                    onAdd = { openAdd(state.selectedFilter.category ?: LayerCategory.BASE) }
                )
                else -> WardrobeGrid(
                    items = state.items,
                    onItemClick = { item ->
                        val defaultName = resources.getString(
                            GarmentTextMapper.garmentNameRes(item.garmentId, item.fit)
                        )
                        onIntent(WardrobeIntent.OpenEdit(item, defaultName))
                    }
                )
            }
        }
    }

    state.editor?.let { editor ->
        WardrobeItemEditorSheet(
            editor = editor,
            onIntent = onIntent,
            onDismissRequest = { onIntent(WardrobeIntent.DismissEditor) }
        )
    }
}

@Composable
private fun WardrobeGrid(
    items: List<WardrobeItem>,
    onItemClick: (WardrobeItem) -> Unit
) {
    val columns = if (LocalDensity.current.fontScale >= WardrobeLargeFontScale) 1 else 2
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = Modifier
            .fillMaxSize()
            .testTag(WardrobeTestTags.GRID),
        contentPadding = PaddingValues(
            start = DesignTokens.Spacing.lg,
            end = DesignTokens.Spacing.lg,
            top = DesignTokens.Spacing.md,
            bottom = DesignTokens.Spacing.xxl
        ),
        horizontalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.md),
        verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.md)
    ) {
        items(items, key = { it.id }) { item ->
            WardrobeItemCard(item = item, onClick = { onItemClick(item) })
        }
    }
}

@Composable
private fun WardrobeItemCard(
    item: WardrobeItem,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.testTag(WardrobeTestTags.item(item.id)),
        shape = RoundedCornerShape(DesignTokens.Radius.lg),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = DesignTokens.Elevation.raised,
        border = BorderStroke(DesignTokens.Hairline, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(DesignTokens.Spacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.sm)
        ) {
            GarmentArtwork(
                source = GarmentVisualMapper.garmentVisualSource(item.garmentId, item.fit),
                contentDescription = null,
                modifier = Modifier.size(ArtworkSize)
            )
            Text(
                text = item.name,
                style = MaterialTheme.typography.titleSmall,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = stringResource(categoryLabelRes(item.category)),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            if (item.isWaterResistant) {
                Surface(
                    modifier = Modifier.testTag(WardrobeTestTags.waterBadge(item.id)),
                    shape = RoundedCornerShape(DesignTokens.Radius.full),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                ) {
                    Text(
                        text = stringResource(R.string.wardrobe_water_resistant),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(
                            horizontal = DesignTokens.Spacing.sm,
                            vertical = DesignTokens.Spacing.xs
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun LoadingState() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag(WardrobeTestTags.LOADING),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.md)
        ) {
            CircularProgressIndicator()
            Text(stringResource(R.string.wardrobe_loading))
        }
    }
}

@Composable
private fun LoadErrorState(onRetry: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(DesignTokens.Spacing.xl),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.testTag(WardrobeTestTags.LOAD_ERROR),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(DesignTokens.Spacing.xl),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.md)
            ) {
                Text(
                    text = stringResource(R.string.wardrobe_load_error),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center
                )
                Button(
                    onClick = onRetry,
                    modifier = Modifier
                        .heightIn(min = DesignTokens.MinTouchTarget)
                        .testTag(WardrobeTestTags.RETRY)
                ) {
                    Text(stringResource(R.string.wardrobe_retry))
                }
            }
        }
    }
}

@Composable
private fun EmptyClosetState(
    filter: WardrobeFilter,
    onAdd: () -> Unit
) {
    val category = filter.category
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(DesignTokens.Spacing.xl)
            .testTag(WardrobeTestTags.EMPTY),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.md)
        ) {
            Text(
                text = stringResource(
                    if (category == null) R.string.wardrobe_empty_title
                    else R.string.wardrobe_filter_empty_title
                ),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center
            )
            Text(
                text = if (category == null) {
                    stringResource(R.string.wardrobe_empty_body)
                } else {
                    stringResource(
                        R.string.wardrobe_filter_empty_body,
                        stringResource(categoryLabelRes(category))
                    )
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(DesignTokens.Spacing.sm))
            Button(
                onClick = onAdd,
                modifier = Modifier
                    .heightIn(min = DesignTokens.MinTouchTarget)
                    .testTag(WardrobeTestTags.EMPTY_ADD)
            ) {
                Text(stringResource(R.string.wardrobe_empty_action))
            }
        }
    }
}

@StringRes
private fun filterLabelRes(filter: WardrobeFilter): Int = when (filter) {
    WardrobeFilter.ALL -> R.string.wardrobe_filter_all
    WardrobeFilter.BASE -> R.string.wardrobe_category_base
    WardrobeFilter.MID -> R.string.wardrobe_category_mid
    WardrobeFilter.OUTER -> R.string.wardrobe_category_outer
    WardrobeFilter.BOTTOM -> R.string.wardrobe_category_bottom
    WardrobeFilter.FOOTWEAR -> R.string.wardrobe_category_footwear
}

@StringRes
internal fun categoryLabelRes(category: LayerCategory): Int = when (category) {
    LayerCategory.BASE -> R.string.wardrobe_category_base
    LayerCategory.MID -> R.string.wardrobe_category_mid
    LayerCategory.OUTER -> R.string.wardrobe_category_outer
    LayerCategory.BOTTOM -> R.string.wardrobe_category_bottom
    LayerCategory.FOOTWEAR -> R.string.wardrobe_category_footwear
}
