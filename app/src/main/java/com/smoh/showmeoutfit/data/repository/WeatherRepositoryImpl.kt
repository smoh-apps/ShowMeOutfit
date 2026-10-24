package com.smoh.showmeoutfit.data.repository

import com.smoh.showmeoutfit.data.local.datasource.LocalWeatherDataSource
import com.smoh.showmeoutfit.data.remote.datasource.RemoteWeatherDataSource
import com.smoh.showmeoutfit.domain.model.LocationInfo
import com.smoh.showmeoutfit.domain.model.WeatherSnapshot
import com.smoh.showmeoutfit.domain.repository.WeatherRepository
import java.time.Duration
import java.time.Instant

class WeatherRepositoryImpl(
    private val remoteDataSource: RemoteWeatherDataSource,
    private val localDataSource: LocalWeatherDataSource,
    private val currentTimeProvider: () -> Instant = { Instant.now() }
) : WeatherRepository {

    companion object {
        // Data is considered fresh for 3 hours
        val CACHE_FRESHNESS_DURATION: Duration = Duration.ofHours(3)
        // Data up to 12 hours old can be used as fallback on network failure
        val CACHE_MAX_FALLBACK_DURATION: Duration = Duration.ofHours(12)
    }

    override suspend fun getTodayWeather(
        location: LocationInfo,
        forceRefresh: Boolean
    ): Result<WeatherSnapshot> {
        val now = currentTimeProvider()

        if (!forceRefresh) {
            val cached = localDataSource.getLatest()
            if (cached != null) {
                val age = Duration.between(cached.fetchedAt, now)
                val isSameLocation = isSameLocation(cached.location, location)
                if (isSameLocation && age < CACHE_FRESHNESS_DURATION && !age.isNegative) {
                    return Result.success(cached)
                }
            }
        }

        // Fetch from network
        val networkResult = remoteDataSource.fetchForecast(location)
        return networkResult.fold(
            onSuccess = { snapshot ->
                localDataSource.save(snapshot)
                Result.success(snapshot)
            },
            onFailure = { error ->
                // Fallback to cache if < 12 hours old
                val cached = localDataSource.getLatest()
                if (cached != null) {
                    val age = Duration.between(cached.fetchedAt, now)
                    if (age < CACHE_MAX_FALLBACK_DURATION && !age.isNegative) {
                        return Result.success(cached)
                    }
                }
                Result.failure(error)
            }
        )
    }

    override suspend fun getCachedWeather(): WeatherSnapshot? {
        return localDataSource.getLatest()
    }

    private fun isSameLocation(loc1: LocationInfo, loc2: LocationInfo): Boolean {
        val latDiff = Math.abs(loc1.latitude - loc2.latitude)
        val lonDiff = Math.abs(loc1.longitude - loc2.longitude)
        return latDiff < 0.05 && lonDiff < 0.05
    }
}
