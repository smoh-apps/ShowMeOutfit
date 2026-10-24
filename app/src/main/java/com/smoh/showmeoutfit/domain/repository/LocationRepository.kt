package com.smoh.showmeoutfit.domain.repository

import com.smoh.showmeoutfit.domain.model.LocationInfo

interface LocationRepository {
    suspend fun getLastKnownLocation(): Result<LocationInfo>
    suspend fun searchLocation(query: String): Result<List<LocationInfo>>
}
