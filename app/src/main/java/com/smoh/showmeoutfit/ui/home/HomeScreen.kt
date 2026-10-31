package com.smoh.showmeoutfit.ui.home

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.smoh.showmeoutfit.R
import com.smoh.showmeoutfit.domain.model.CurrentCondition
import com.smoh.showmeoutfit.domain.model.DomainError
import com.smoh.showmeoutfit.domain.model.Garment
import com.smoh.showmeoutfit.domain.model.OutfitRecommendation
import com.smoh.showmeoutfit.domain.model.TodayOutfit
import com.smoh.showmeoutfit.domain.model.WeatherData
import com.smoh.showmeoutfit.domain.model.WeatherOrigin
import com.smoh.showmeoutfit.ui.components.AccessoryBadge
import com.smoh.showmeoutfit.ui.components.DiurnalTimelineBar
import com.smoh.showmeoutfit.ui.components.OfflineBanner
import com.smoh.showmeoutfit.ui.components.OutfitLayerCard
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
    const val TIMELINE = "home_timeline"
}

private val HourLabelFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

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
        snackbarHostState = snackbarHostState,
        modifier = modifier
    )
}

/**
 * Stateless dashboard: every state is renderable from the arguments alone, so Compose tests and
 * previews can drive it without Koin, coroutines or a repository.
 *
 * Navigation requests leave through [onEffect] (currently [HomeEffect.OpenLocationSearch]), which
 * keeps the screen free of a navigation dependency. [HomeIntent.OpenLocationSearch] remains the
 * ViewModel-level equivalent for hosts that prefer to route the request through state.
 */
@Composable
fun HomeScreen(
    state: HomeUiState,
    onIntent: (HomeIntent) -> Unit,
    onEffect: (HomeEffect) -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() }
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
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
                onOpenLocationSearch = { onEffect(HomeEffect.OpenLocationSearch) },
                modifier = stateModifier
            )

            is HomeUiState.Error -> HomeErrorState(
                error = state.error,
                onRetry = { onIntent(HomeIntent.DismissError) },
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
            LayerCard(
                layerLabel = stringResource(R.string.home_layer_base),
                garment = recommendation.baseLayer,
                accentColor = accent
            )
            val midLayer = recommendation.midLayer
            if (midLayer != null) {
                LayerCard(
                    layerLabel = stringResource(R.string.home_layer_mid),
                    garment = midLayer,
                    accentColor = accent
                )
            } else {
                NotNeededLayerCard(
                    layerLabel = stringResource(R.string.home_layer_mid),
                    description = stringResource(R.string.home_layer_mid_not_needed_description)
                )
            }
            val outerLayer = recommendation.outerLayer
            if (outerLayer != null) {
                LayerCard(
                    layerLabel = stringResource(R.string.home_layer_outer),
                    garment = outerLayer,
                    accentColor = accent
                )
            } else {
                NotNeededLayerCard(
                    layerLabel = stringResource(R.string.home_layer_outer),
                    description = stringResource(R.string.home_layer_outer_not_needed_description)
                )
            }
            LayerCard(
                layerLabel = stringResource(R.string.home_layer_bottom),
                garment = recommendation.bottomLayer,
                accentColor = accent
            )
            LayerCard(
                layerLabel = stringResource(R.string.home_layer_footwear),
                garment = recommendation.footwear,
                accentColor = accent
            )

            SectionHeading(title = stringResource(R.string.home_section_accessories))
            AccessorySection(recommendation = recommendation)

            SectionHeading(title = stringResource(R.string.home_section_highlights))
            MetricSection(recommendation = recommendation)

            Spacer(modifier = Modifier.height(DesignTokens.Spacing.lg))
        }
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
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = cityName,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
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
            modifier = Modifier.testTag(HomeTestTags.CACHE_BANNER)
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
private fun LayerCard(
    layerLabel: String,
    garment: Garment,
    accentColor: Color
) {
    OutfitLayerCard(
        layerLabel = layerLabel,
        title = stringResource(
            GarmentTextMapper.garmentNameRes(garment.id, garment.fit)
        ),
        description = stringResource(
            GarmentTextMapper.garmentDescriptionRes(garment.id, garment.fit)
        ),
        accentColor = accentColor
    )
}

/**
 * "Not needed today" replacement for an absent mid/outer layer.
 *
 * Rendered as a real card instead of an empty gap so the five-layer model stays legible, and the
 * muted accent never carries the meaning on its own: the title says it in words.
 */
@Composable
private fun NotNeededLayerCard(layerLabel: String, description: String) {
    OutfitLayerCard(
        layerLabel = layerLabel,
        title = stringResource(R.string.home_layer_not_needed),
        description = description,
        accentColor = MaterialTheme.colorScheme.outline
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
                    priority = GarmentTextMapper.accessoryPriority(alert.priority)
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

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.sm)
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
