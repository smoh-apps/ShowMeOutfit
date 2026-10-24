package com.smoh.showmeoutfit.domain.usecase

import com.smoh.showmeoutfit.domain.model.LocationInfo
import com.smoh.showmeoutfit.domain.repository.LocationRepository

class SearchLocationUseCase(
    private val locationRepository: LocationRepository
) {
    suspend operator fun invoke(query: String): Result<List<LocationInfo>> {
        return locationRepository.searchLocation(query)
    }
}
