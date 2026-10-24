package com.smoh.showmeoutfit.data.repository

import com.smoh.showmeoutfit.core.datastore.UserPreferencesStore
import com.smoh.showmeoutfit.data.remote.datasource.RemoteWeatherDataSource
import com.smoh.showmeoutfit.domain.model.LocationInfo
import com.smoh.showmeoutfit.domain.repository.LocationRepository
import kotlinx.coroutines.flow.first

class LocationRepositoryImpl(
    private val remoteDataSource: RemoteWeatherDataSource,
    private val preferencesStore: UserPreferencesStore,
    private val defaultLocation: LocationInfo = LocationInfo(
        latitude = 48.8566,
        longitude = 2.3522,
        cityName = "Paris",
        country = "France",
        timezone = "Europe/Paris"
    )
) : LocationRepository {

    override suspend fun getLastKnownLocation(): Result<LocationInfo> {
        return runCatching {
            val userProfile = preferencesStore.getUserProfile().first()
            userProfile.selectedLocation ?: defaultLocation
        }
    }

    override suspend fun searchLocation(query: String): Result<List<LocationInfo>> {
        if (query.isBlank()) return Result.success(emptyList())
        return remoteDataSource.geocodeLocation(query)
    }
}
