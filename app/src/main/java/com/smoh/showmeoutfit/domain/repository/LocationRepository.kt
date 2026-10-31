package com.smoh.showmeoutfit.domain.repository

import com.smoh.showmeoutfit.domain.model.LocationInfo

interface LocationRepository {
    /**
     * Resolves the device's current position.
     *
     * Implementations prefer a recent last-known fix and fall back to a single bounded
     * current-location request. They never prompt for permissions: a missing permission is
     * reported as [com.smoh.showmeoutfit.domain.model.DomainError.LocationPermissionDenied] so the
     * presentation layer can show its own rationale and manual-search fallback.
     */
    suspend fun getCurrentLocation(): Result<LocationInfo>

    suspend fun searchLocation(query: String): Result<List<LocationInfo>>
}
