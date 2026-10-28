package com.smoh.showmeoutfit.domain.model

/**
 * Everything the Home screen needs for one refresh.
 *
 * Bundling the recommendation with [weather] keeps provenance (network vs. cache) and the
 * profile that produced the recommendation available to the presentation layer, so it can
 * show an honest freshness banner and recompute without re-reading the repository.
 */
data class TodayOutfit(
    val recommendation: OutfitRecommendation,
    val weather: WeatherData,
    val profile: UserProfile
)
