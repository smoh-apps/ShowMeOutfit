package com.smoh.showmeoutfit.domain.usecase

import com.smoh.showmeoutfit.domain.engine.IOutfitRecommendationEngine
import com.smoh.showmeoutfit.domain.model.DomainError
import com.smoh.showmeoutfit.domain.model.LocationInfo
import com.smoh.showmeoutfit.domain.model.TodayOutfit
import com.smoh.showmeoutfit.domain.repository.LocationRepository
import com.smoh.showmeoutfit.domain.repository.UserRepository
import com.smoh.showmeoutfit.domain.repository.WeatherRepository
import kotlinx.coroutines.flow.first
import kotlin.coroutines.cancellation.CancellationException

/**
 * Resolves the location, loads weather (cache-aware), and runs the recommendation engine.
 *
 * Expected failures surface as [DomainError]; cancellation is always rethrown so a cancelled
 * scope is never reported to the UI as a weather problem.
 */
class GetTodayOutfitRecommendationUseCase(
    private val weatherRepository: WeatherRepository,
    private val userRepository: UserRepository,
    private val locationRepository: LocationRepository,
    private val engine: IOutfitRecommendationEngine
) {

    suspend operator fun invoke(
        targetLocation: LocationInfo? = null,
        forceRefresh: Boolean = false
    ): Result<TodayOutfit> {
        return try {
            val profile = userRepository.getUserProfile().first()

            val location = targetLocation
                ?: profile.selectedLocation
                ?: locationRepository.getLastKnownLocation().getOrElse { cause ->
                    throw if (cause is DomainError) cause else DomainError.NoLocation(cause)
                }

            val weather = weatherRepository.getTodayWeather(
                location = location,
                forceRefresh = forceRefresh
            ).getOrThrow()

            Result.success(
                TodayOutfit(
                    recommendation = engine.recommend(
                        weather = weather.snapshot,
                        profile = profile
                    ),
                    weather = weather,
                    profile = profile
                )
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Throwable) {
            Result.failure(error)
        }
    }
}
