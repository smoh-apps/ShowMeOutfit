package com.smoh.showmeoutfit.data.repository

import com.smoh.showmeoutfit.core.datastore.UserPreferencesStore
import com.smoh.showmeoutfit.data.remote.datasource.RemoteWeatherDataSource
import com.smoh.showmeoutfit.domain.model.DomainError
import com.smoh.showmeoutfit.domain.model.LocationInfo
import com.smoh.showmeoutfit.domain.repository.LocationRepository
import kotlinx.coroutines.flow.first
import kotlin.coroutines.cancellation.CancellationException

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
        return try {
            val userProfile = preferencesStore.getUserProfile().first()
            Result.success(userProfile.selectedLocation ?: defaultLocation)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Throwable) {
            Result.failure(DomainError.Unknown(error))
        }
    }

    override suspend fun searchLocation(query: String): Result<List<LocationInfo>> {
        if (query.isBlank()) return Result.success(emptyList())
        return remoteDataSource.geocodeLocation(query)
    }
}
