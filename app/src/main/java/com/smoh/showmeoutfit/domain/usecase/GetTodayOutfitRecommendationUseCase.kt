package com.smoh.showmeoutfit.domain.usecase

import com.smoh.showmeoutfit.domain.engine.IOutfitRecommendationEngine
import com.smoh.showmeoutfit.domain.model.LocationInfo
import com.smoh.showmeoutfit.domain.model.OutfitRecommendation
import com.smoh.showmeoutfit.domain.repository.LocationRepository
import com.smoh.showmeoutfit.domain.repository.UserRepository
import com.smoh.showmeoutfit.domain.repository.WeatherRepository
import kotlinx.coroutines.flow.first

class GetTodayOutfitRecommendationUseCase(
    private val weatherRepository: WeatherRepository,
    private val userRepository: UserRepository,
    private val locationRepository: LocationRepository,
    private val engine: IOutfitRecommendationEngine
) {

    suspend operator fun invoke(
        targetLocation: LocationInfo? = null,
        forceRefresh: Boolean = false
    ): Result<OutfitRecommendation> {
        return runCatching {
            val profile = userRepository.getUserProfile().first()

            val location = targetLocation
                ?: profile.selectedLocation
                ?: locationRepository.getLastKnownLocation().getOrThrow()

            val weatherSnapshot = weatherRepository.getTodayWeather(
                location = location,
                forceRefresh = forceRefresh
            ).getOrThrow()

            engine.recommend(
                weather = weatherSnapshot,
                profile = profile
            )
        }
    }
}
