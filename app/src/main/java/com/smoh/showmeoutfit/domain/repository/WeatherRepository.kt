package com.smoh.showmeoutfit.domain.repository

import com.smoh.showmeoutfit.domain.model.LocationInfo
import com.smoh.showmeoutfit.domain.model.WeatherData

interface WeatherRepository {
    /**
     * Returns weather for [location], preferring a validated cache.
     *
     * A cached snapshot is only reused when it belongs to the same location, to the same
     * forecast day in the location's timezone, and is inside the freshness/fallback window.
     * Failures are reported as [com.smoh.showmeoutfit.domain.model.DomainError].
     */
    suspend fun getTodayWeather(
        location: LocationInfo,
        forceRefresh: Boolean = false
    ): Result<WeatherData>

    /** Latest cached snapshot with provenance, or `null` when the cache is empty. */
    suspend fun getCachedWeather(): WeatherData?
}
