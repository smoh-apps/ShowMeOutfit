package com.smoh.showmeoutfit.data.repository

import com.smoh.showmeoutfit.data.location.DeviceLocationSource
import com.smoh.showmeoutfit.data.remote.datasource.RemoteWeatherDataSource
import com.smoh.showmeoutfit.domain.model.DomainError
import com.smoh.showmeoutfit.domain.model.LocationInfo
import com.smoh.showmeoutfit.domain.repository.LocationRepository
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.cancellation.CancellationException

/**
 * Resolves the device location and performs city lookups.
 *
 * There is intentionally no built-in default city: a denied permission or a failed fix must
 * surface as a typed error so the UI can offer manual search instead of silently recommending
 * clothes for a place the user is not in.
 */
class LocationRepositoryImpl(
    private val deviceLocationSource: DeviceLocationSource,
    private val remoteDataSource: RemoteWeatherDataSource,
    private val currentLocationTimeoutMillis: Long = CURRENT_LOCATION_TIMEOUT_MILLIS
) : LocationRepository {

    companion object {
        /** Bounded so the dashboard never waits forever on a cold GPS fix. */
        const val CURRENT_LOCATION_TIMEOUT_MILLIS: Long = 10_000
    }

    override suspend fun getCurrentLocation(): Result<LocationInfo> {
        return try {
            // One deadline for the whole resolution: a slow cached fix plus a slow fresh request
            // must not add up to a wait the user notices.
            val result = withTimeoutOrNull(currentLocationTimeoutMillis) {
                // A cached fix is instantaneous and good enough for a weather forecast; the
                // source itself rejects fixes that are too old to describe where the user is.
                val lastKnown = deviceLocationSource.getLastKnown()
                if (lastKnown != null) {
                    Result.success(lastKnown)
                } else {
                    deviceLocationSource.getCurrent(currentLocationTimeoutMillis).toLocationResult()
                }
            }
            result ?: Result.failure(DomainError.LocationUnavailable())
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Throwable) {
            Result.failure(DomainError.LocationUnavailable(error))
        }
    }

    /**
     * Keeps the precise typed errors a location source reports and folds anything else into
     * [DomainError.LocationUnavailable], so the UI never has to interpret a raw platform failure.
     */
    private fun Result<LocationInfo>.toLocationResult(): Result<LocationInfo> = fold(
        onSuccess = { Result.success(it) },
        onFailure = { failure ->
            if (failure is CancellationException) throw failure
            if (failure is DomainError) Result.failure(failure)
            else Result.failure(DomainError.LocationUnavailable(failure))
        }
    )

    override suspend fun searchLocation(query: String): Result<List<LocationInfo>> {
        if (query.isBlank()) return Result.success(emptyList())

        return try {
            remoteDataSource.geocodeLocation(query.trim()).fold(
                onSuccess = { locations -> Result.success(locations) },
                onFailure = { failure ->
                    if (failure is CancellationException) throw failure
                    Result.failure(failure.toDomainError())
                }
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Throwable) {
            Result.failure(error.toDomainError())
        }
    }
}
