package com.smoh.showmeoutfit.domain.repository

import com.smoh.showmeoutfit.domain.model.LocationInfo
import com.smoh.showmeoutfit.domain.model.WeatherSnapshot

interface WeatherRepository {
    suspend fun getTodayWeather(
        location: LocationInfo,
        forceRefresh: Boolean = false
    ): Result<WeatherSnapshot>

    suspend fun getCachedWeather(): WeatherSnapshot?
}
