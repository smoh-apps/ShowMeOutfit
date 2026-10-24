package com.smoh.showmeoutfit.data.remote.datasource

import com.smoh.showmeoutfit.domain.model.LocationInfo
import com.smoh.showmeoutfit.domain.model.WeatherSnapshot

interface RemoteWeatherDataSource {
    suspend fun fetchForecast(location: LocationInfo): Result<WeatherSnapshot>
    suspend fun geocodeLocation(query: String): Result<List<LocationInfo>>
}
