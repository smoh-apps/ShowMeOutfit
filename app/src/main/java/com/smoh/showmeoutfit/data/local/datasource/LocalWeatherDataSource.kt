package com.smoh.showmeoutfit.data.local.datasource

import com.smoh.showmeoutfit.domain.model.WeatherSnapshot
import kotlinx.coroutines.flow.Flow

interface LocalWeatherDataSource {
    suspend fun save(snapshot: WeatherSnapshot)
    suspend fun getLatest(): WeatherSnapshot?
    fun observeLatest(): Flow<WeatherSnapshot?>
    suspend fun pruneOlderThan(thresholdMs: Long)
}
