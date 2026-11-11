package com.smoh.showmeoutfit.ui.home

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.smoh.showmeoutfit.R
import com.smoh.showmeoutfit.domain.model.CurrentCondition
import com.smoh.showmeoutfit.domain.model.DomainError
import com.smoh.showmeoutfit.domain.model.OutfitRecommendation
import com.smoh.showmeoutfit.domain.model.TodayOutfit
import com.smoh.showmeoutfit.domain.model.WeatherData
import com.smoh.showmeoutfit.domain.model.WeatherOrigin
import com.smoh.showmeoutfit.ui.components.AccessoryBadge
import com.smoh.showmeoutfit.ui.components.DiurnalTimelineBar
import com.smoh.showmeoutfit.ui.components.OfflineBanner
import com.smoh.showmeoutfit.ui.components.TimelinePoint
import com.smoh.showmeoutfit.ui.components.WeatherMetricChip
import com.smoh.showmeoutfit.ui.theme.DesignTokens
import com.smoh.showmeoutfit.ui.theme.LocalWeatherPalette
import com.smoh.showmeoutfit.ui.theme.ShowMeOutfitTheme
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import java.time.Duration
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Test tags so instrumented tests never depend on English copy. */
object HomeTestTags {
    const val CONTENT = "home_content"
    const val LOADING = "home_loading"
    const val EMPTY = "home_empty"
    const val ERROR = "home_error"
    const val CACHE_BANNER = "home_cache_banner"
    const val REFRESH_FAILED_BANNER = "home_refresh_failed_banner"
    const val TIMELINE = "home_timeline"
    const val METRICS = "home_metrics"
    const val OPEN_SETTINGS = "home_open_settings"
    const val OUTFIT_GRID = "home_outfit_grid"
    const val GARMENT_DETAIL_SHEET = "home_garment_detail_sheet"

    /** Prefix for the per-tile tags: `outfit_tile_base`, `outfit_tile_mid`, … */
    const val OUTFIT_TILE_PREFIX = "outfit_tile_"

    /** Tag for the tile of one active layer ([layerKey] is `base`, `mid`, `outer`, `bottom`, `footwear`). */
    fun outfitTile(layerKey: String): String = OUTFIT_TILE_PREFIX + layerKey.lowercase(Locale.ROOT)
}

private val HourLabelFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/** Font scale at which multi-column rows stop being readable and switch to one item per row. */
internal const val LargeFontScale = 1.5f

/**
 * Stateful entry point: owns the [HomeViewModel], the snackbar host and the effect handling.
 *
 * Wire this from the navigation graph (SMO-S2-05) as
 * `HomeRoute(onOpenLocationSearch = navigateToLocationSearch)`.
 *
 * Effects are consumed here and turned into UI consequences: [HomeEffect.ShowMessage] becomes a
 * snackbar with localized copy, [HomeEffect.OpenLocationSearch] is forwarded to the host so this
 * layer never owns navigation.
 */
@Composable
fun HomeRoute(
    onOpenLocationSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    // LocalResources (not LocalContext) so snackbar copy is re-resolved when the configuration
    // changes and so the compose lint rule against cached resource lookups stays satisfied.
    val resources = LocalResources.current
    val currentOnOpenLocationSearch by rememberUpdatedState(onOpenLocationSearch)

    val onEffect: (HomeEffect) -> Unit = remember(resources, snackbarHostState, coroutineScope) {
        { effect ->
            when (effect) {
                is HomeEffect.ShowMessage -> coroutineScope.launch {
                    snackbarHostState.showSnackbar(
                        resources.getString(HomeMessageMapper.errorMessageRes(effect.error))
                    )
                }

                HomeEffect.OpenLocationSearch -> currentOnOpenLocationSearch()
            }
        }
    }
    val currentOnEffect by rememberUpdatedState(onEffect)

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect -> currentOnEffect(effect) }
    }

    HomeScreen(
        state = state,
        onIntent = viewModel::onIntent,
        onEffect = onEffect,
        onOpenSettings = onOpenSettings,
        snackbarHostState = snackbarHostState,
        modifier = modifier
    )
}

/**
 * Stateless dashboard: every state is renderable from the arguments alone, so Compose tests and
 * previews can drive it without Koin, coroutines or a repository.
 *
 * Every action leaves through [onIntent]. A navigation request becomes
 * [HomeIntent.OpenLocationSearch], the ViewModel answers with [HomeEffect.OpenLocationSearch] and
 * the host opens the sheet, so the screen keeps no navigation dependency and there is exactly one
 * way to ask for the picker.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: HomeUiState,
    onIntent: (HomeIntent) -> Unit,
    onEffect: (HomeEffect) -> Unit,
    modifier: Modifier = Modifier,
    onOpenSettings: () -> Unit = {},
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() }
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.app_name)) },
                actions = {
                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier
                            .heightIn(min = DesignTokens.MinTouchTarget)
                            .testTag(HomeTestTags.OPEN_SETTINGS)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = stringResource(R.string.settings_open_action)
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        val stateModifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)

        when (state) {
            HomeUiState.Loading -> HomeLoadingState(modifier = stateModifier)

            is HomeUiState.Content -> HomeContentState(
                state = state,
                onIntent = onIntent,
                modifier = stateModifier
            )

            HomeUiState.Empty -> HomeEmptyState(
                onOpenLocationSearch = { onIntent(HomeIntent.OpenLocationSearch) },
                modifier = stateModifier
            )

            is HomeUiState.Error -> HomeErrorState(
                error = state.error,
                // The retry must hit the network: repeating a cache read would hand back the same
                // expired snapshot, and the copy promises today's data.
                onRetry = { onIntent(HomeIntent.Refresh) },
                onOpenLocationSearch = { onIntent(HomeIntent.OpenLocationSearch) },
                modifier = stateModifier
            )
        }
    }
}

// --- Content -----------------------------------------------------------------------------

@Composable
private fun HomeContentState(
    state: HomeUiState.Content,
    onIntent: (HomeIntent) -> Unit,
    modifier: Modifier = Modifier
) {
    val outfit: TodayOutfit = state.outfit
    val recommendation: OutfitRecommendation = outfit.recommendation
    val weather: WeatherData = outfit.weather

    val points: List<TimelinePoint> = remember(weather.snapshot.hourlyForecast) {
        HomeTimelineMapper.points(weather.snapshot.hourlyForecast) { time ->
            HourLabelFormatter.format(time)
        }
    }
    val accent = LocalWeatherPalette.current[
        GarmentTextMapper.conditionGroup(weather.snapshot.current.weatherCode)
    ].accent
    // The detail sheet is transient presentation state: it owns no domain data and closes without
    // touching the recommendation, so it stays local to the content composable.
    var inspectedTile by remember { mutableStateOf<OutfitTileUi?>(null) }

    PullToRefreshBox(
        isRefreshing = state.isRefreshing,
        onRefresh = { onIntent(HomeIntent.Refresh) },
        modifier = modifier.testTag(HomeTestTags.CONTENT)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = DesignTokens.Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.lg)
        ) {
            Spacer(modifier = Modifier.height(DesignTokens.Spacing.sm))

            HomeHeader(
                current = weather.snapshot.current,
                cityName = weather.snapshot.location.cityName,
                isRefreshing = state.isRefreshing,
                onRefresh = { onIntent(HomeIntent.Refresh) }
            )

            // A refresh that failed leaves the *previous* snapshot on screen. Without this banner
            // that data (possibly hours old) would look live as soon as the snackbar disappears.
            // Cached snapshots already carry their own provenance banner, so this one is only for
            // the case provenance cannot express: a network snapshot that could not be refreshed.
            if (state.refreshFailed && weather.origin == WeatherOrigin.NETWORK) {
                OfflineBanner(
                    text = stringResource(R.string.home_banner_refresh_failed),
                    actionLabel = stringResource(R.string.home_error_retry),
                    onAction = { onIntent(HomeIntent.Refresh) },
                    modifier = Modifier
                        .testTag(HomeTestTags.REFRESH_FAILED_BANNER)
                        .semantics(mergeDescendants = true) {
                            liveRegion = LiveRegionMode.Polite
                        }
                )
            }

            if (weather.origin != WeatherOrigin.NETWORK) {
                CacheProvenanceBanner(origin = weather.origin, cacheAge = weather.cacheAge)
            }

            Column(verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.xs)) {
                Text(
                    text = localizedText(GarmentTextMapper.headline(recommendation.headline)),
                    style = MaterialTheme.typography.headlineSmall
                )
                Text(
                    text = localizedText(GarmentTextMapper.summary(recommendation.summary)),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            SectionHeading(title = stringResource(R.string.home_section_timeline))
            DiurnalTimelineBar(
                points = points,
                modifier = Modifier.testTag(HomeTestTags.TIMELINE)
            )

            SectionHeading(title = stringResource(R.string.home_section_outfit))
            // Outfit-first: only the layers that were actually recommended get a tile, so the grid
            // never pads itself with "not needed" placeholders.
            OutfitBentoGrid(
                recommendation = recommendation,
                accentColor = accent,
                modifier = Modifier.testTag(HomeTestTags.OUTFIT_GRID),
                onGarmentClick = { tile -> inspectedTile = tile }
            )

            SectionHeading(title = stringResource(R.string.home_section_accessories))
            AccessorySection(recommendation = recommendation)

            SectionHeading(title = stringResource(R.string.home_section_highlights))
            MetricSection(recommendation = recommendation)

            Spacer(modifier = Modifier.height(DesignTokens.Spacing.lg))
        }
    }

    // Rendered as a dialog window, so it sits outside the scrolling content and above the scrim.
    inspectedTile?.let { tile ->
        GarmentDetailSheet(
            tile = tile,
            onDismissRequest = { inspectedTile = null }
        )
    }
}

@Composable
private fun HomeHeader(
    current: CurrentCondition,
    cityName: String,
    isRefreshing: Boolean,
    onRefresh: () -> Unit
) {
    val locale = Locale.getDefault()
    // A city name is content, not decoration: at 200% text size it may wrap instead of being
    // truncated to "Lisb…". The normal font scale keeps the single-line header.
    val maxCityLines = if (LocalDensity.current.fontScale >= LargeFontScale) 2 else 1
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = cityName,
                style = MaterialTheme.typography.titleLarge,
                maxLines = maxCityLines,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = stringResource(
                    R.string.home_header_conditions,
                    TextArg.Number(current.temperatureCelsius, decimals = 0).format(locale),
                    stringResource(
                        GarmentTextMapper.conditionDescriptionRes(current.weatherCode)
                    ),
                    TextArg.Number(current.apparentTemperatureCelsius, decimals = 0).format(locale)
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        TextButton(
            onClick = onRefresh,
            enabled = !isRefreshing,
            modifier = Modifier.heightIn(min = DesignTokens.MinTouchTarget)
        ) {
            Text(text = stringResource(R.string.home_header_refresh))
        }
    }
}

@Composable
private fun CacheProvenanceBanner(origin: WeatherOrigin, cacheAge: Duration?) {
    val withAgeRes = HomeMessageMapper.cacheBannerWithAgeRes(origin)
    val message = when {
        cacheAge != null && withAgeRes != null ->
            stringResource(withAgeRes, cacheAgeText(CacheAge.of(cacheAge)))

        else -> HomeMessageMapper.cacheBannerWithoutAgeRes(origin)
            ?.let { stringResource(it) }
            .orEmpty()
    }

    if (message.isNotEmpty()) {
        OfflineBanner(
            text = message,
            modifier = Modifier
                .testTag(HomeTestTags.CACHE_BANNER)
                // A status change (fresh -> stale, or the banner appearing at all) should be
                // announced without stealing focus from whatever the user is reading. Merging the
                // message into the node gives the live region text to announce.
                .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }
        )
    }
}

/** "3 hours 12 minutes" / "1 hour" / "25 minutes", with grammatical plurals. */
@Composable
private fun cacheAgeText(age: CacheAge): String {
    val hours = pluralStringResource(R.plurals.home_cache_age_hours, age.hours, age.hours)
    val minutes = pluralStringResource(R.plurals.home_cache_age_minutes, age.minutes, age.minutes)
    return when {
        age.hours > 0 && age.minutes > 0 ->
            stringResource(R.string.home_cache_age_hours_minutes, hours, minutes)

        age.hours > 0 -> hours

        else -> minutes
    }
}

@Composable
private fun SectionHeading(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface
    )
}

@Composable
private fun AccessorySection(recommendation: OutfitRecommendation) {
    if (recommendation.accessories.isEmpty()) {
        Text(
            text = stringResource(R.string.home_accessories_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.md)) {
        recommendation.accessories.forEach { alert ->
            Column(verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.xxs)) {
                AccessoryBadge(
                    label = stringResource(GarmentTextMapper.accessoryNameRes(alert.type)),
                    priority = GarmentTextMapper.accessoryPriority(alert.priority),
                    iconRes = GarmentVisualMapper.accessoryDrawableRes(alert.type)
                )
                Text(
                    text = localizedText(GarmentTextMapper.accessoryReason(alert.reason)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = DesignTokens.Spacing.md)
                )
            }
        }
    }
}

@Composable
private fun MetricSection(recommendation: OutfitRecommendation) {
    val highlights = recommendation.weatherHighlights
    val locale = Locale.getDefault()
    // Three chips side by side stop being readable once the text doubles. FlowRow either keeps
    // them in one row (normal scale) or stacks them full width (200%), so nothing clips.
    val itemsPerRow = if (LocalDensity.current.fontScale >= LargeFontScale) 1 else 3

    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(HomeTestTags.METRICS),
        horizontalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.sm),
        maxItemsInEachRow = itemsPerRow
    ) {
        WeatherMetricChip(
            label = stringResource(R.string.home_metric_rain_label),
            value = TextArg.Count(highlights.maxPrecipitationProb).format(locale),
            unit = stringResource(R.string.home_metric_percent_unit),
            modifier = Modifier.weight(1f)
        )
        WeatherMetricChip(
            label = stringResource(R.string.home_metric_uv_label),
            value = TextArg.Number(highlights.maxUvIndex, decimals = 1).format(locale),
            modifier = Modifier.weight(1f)
        )
        WeatherMetricChip(
            label = stringResource(R.string.home_metric_wind_label),
            value = TextArg.Number(highlights.maxWindSpeedKmh, decimals = 0).format(locale),
            unit = stringResource(R.string.home_metric_wind_unit),
            modifier = Modifier.weight(1f)
        )
    }
}

// --- Loading -----------------------------------------------------------------------------

@Composable
private fun HomeLoadingState(modifier: Modifier = Modifier) {
    val description = stringResource(R.string.home_loading_content_description)

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = DesignTokens.Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.lg)
    ) {
        Spacer(modifier = Modifier.height(DesignTokens.Spacing.sm))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .testTag(HomeTestTags.LOADING)
                .clearAndSetSemantics { contentDescription = description },
            verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.lg)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.xs)) {
                Text(
                    text = stringResource(R.string.home_loading_title),
                    style = MaterialTheme.typography.headlineSmall
                )
                Text(
                    text = stringResource(R.string.home_loading_body),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            SkeletonBlock(height = 96.dp)
            SkeletonBlock(height = 152.dp)
            SkeletonBlock(height = 96.dp)
            SkeletonBlock(height = 96.dp)
            SkeletonBlock(height = 72.dp)
        }
        Spacer(modifier = Modifier.height(DesignTokens.Spacing.lg))
    }
}

/** Static placeholder: no shimmer, no animation, nothing that TalkBack could mistake for content. */
@Composable
private fun SkeletonBlock(height: Dp) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(height),
        shape = RoundedCornerShape(DesignTokens.Radius.lg),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(DesignTokens.Hairline, MaterialTheme.colorScheme.outlineVariant)
    ) {}
}

// --- Empty / Error -----------------------------------------------------------------------

@Composable
private fun HomeEmptyState(
    onOpenLocationSearch: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(DesignTokens.Spacing.xl)
            .testTag(HomeTestTags.EMPTY),
        verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.md),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.home_empty_title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )
        Text(
            text = stringResource(R.string.home_empty_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(DesignTokens.Spacing.sm))
        Button(
            onClick = onOpenLocationSearch,
            modifier = Modifier.heightIn(min = DesignTokens.MinTouchTarget)
        ) {
            Text(text = stringResource(R.string.home_empty_action))
        }
    }
}

@Composable
private fun HomeErrorState(
    error: DomainError,
    onRetry: () -> Unit,
    onOpenLocationSearch: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(DesignTokens.Spacing.xl)
            .testTag(HomeTestTags.ERROR),
        verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.md),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.home_error_title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )
        Text(
            text = stringResource(HomeMessageMapper.errorMessageRes(error)),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(DesignTokens.Spacing.sm))
        Button(
            onClick = onRetry,
            modifier = Modifier.heightIn(min = DesignTokens.MinTouchTarget)
        ) {
            Text(text = stringResource(R.string.home_error_retry))
        }
        // Offline, a failing API or a revoked permission all make retry fail the same way. The
        // city picker is the one action that can still get the user to a dashboard.
        TextButton(
            onClick = onOpenLocationSearch,
            modifier = Modifier.heightIn(min = DesignTokens.MinTouchTarget)
        ) {
            Text(text = stringResource(R.string.home_error_change_city))
        }
    }
}

// --- Helpers -----------------------------------------------------------------------------

/** Resolves a mapper result in the current locale. */
@Composable
private fun localizedText(text: MappedText): String =
    stringResource(text.resId, *text.resolve(Locale.getDefault()))

// --- Previews ----------------------------------------------------------------------------

@Preview(name = "Home – content, light", showBackground = true, widthDp = 380, heightDp = 900)
@Composable
private fun HomeContentPreview() {
    ShowMeOutfitTheme(darkTheme = false, dynamicColor = false) {
        HomeScreen(
            state = HomeUiState.Content(outfit = previewTodayOutfit(WeatherOrigin.NETWORK)),
            onIntent = {},
            onEffect = {}
        )
    }
}

@Preview(
    name = "Home – content, dark",
    showBackground = true,
    widthDp = 380,
    heightDp = 900,
    uiMode = Configuration.UI_MODE_NIGHT_YES
)
@Composable
private fun HomeContentDarkPreview() {
    ShowMeOutfitTheme(darkTheme = true, dynamicColor = false) {
        HomeScreen(
            state = HomeUiState.Content(
                outfit = previewTodayOutfit(
                    origin = WeatherOrigin.CACHE_STALE,
                    cacheAge = Duration.ofHours(5).plusMinutes(20)
                )
            ),
            onIntent = {},
            onEffect = {}
        )
    }
}

@Preview(name = "Home – loading", showBackground = true, widthDp = 380, heightDp = 900)
@Composable
private fun HomeLoadingPreview() {
    ShowMeOutfitTheme(darkTheme = false, dynamicColor = false) {
        HomeScreen(state = HomeUiState.Loading, onIntent = {}, onEffect = {})
    }
}

@Preview(name = "Home – empty", showBackground = true, widthDp = 380, heightDp = 900)
@Composable
private fun HomeEmptyPreview() {
    ShowMeOutfitTheme(darkTheme = false, dynamicColor = false) {
        HomeScreen(state = HomeUiState.Empty, onIntent = {}, onEffect = {})
    }
}

@Preview(name = "Home – error", showBackground = true, widthDp = 380, heightDp = 900)
@Composable
private fun HomeErrorPreview() {
    ShowMeOutfitTheme(darkTheme = false, dynamicColor = false) {
        HomeScreen(
            state = HomeUiState.Error(DomainError.Offline()),
            onIntent = {},
            onEffect = {}
        )
    }
}
