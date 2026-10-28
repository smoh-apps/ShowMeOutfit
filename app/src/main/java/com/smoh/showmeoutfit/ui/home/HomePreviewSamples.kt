package com.smoh.showmeoutfit.ui.home

import com.smoh.showmeoutfit.domain.engine.GarmentCatalog
import com.smoh.showmeoutfit.domain.model.AccessoryAlert
import com.smoh.showmeoutfit.domain.model.AccessoryReason
import com.smoh.showmeoutfit.domain.model.AccessoryType
import com.smoh.showmeoutfit.domain.model.CurrentCondition
import com.smoh.showmeoutfit.domain.model.DailySummary
import com.smoh.showmeoutfit.domain.model.GarmentFit
import com.smoh.showmeoutfit.domain.model.HeadlineReason
import com.smoh.showmeoutfit.domain.model.HourlyCondition
import com.smoh.showmeoutfit.domain.model.LocationInfo
import com.smoh.showmeoutfit.domain.model.OutfitRecommendation
import com.smoh.showmeoutfit.domain.model.PriorityLevel
import com.smoh.showmeoutfit.domain.model.SummaryData
import com.smoh.showmeoutfit.domain.model.TodayOutfit
import com.smoh.showmeoutfit.domain.model.UserProfile
import com.smoh.showmeoutfit.domain.model.WeatherCode
import com.smoh.showmeoutfit.domain.model.WeatherData
import com.smoh.showmeoutfit.domain.model.WeatherHighlights
import com.smoh.showmeoutfit.domain.model.WeatherOrigin
import com.smoh.showmeoutfit.domain.model.WeatherSnapshot
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Preview-only fixture for the Home dashboard.
 *
 * Mirrors a realistic autumn day so previews show a warm swing, a rain window and UV protection
 * instead of placeholder lorem ipsum. Deterministic: no clock reads, no locale dependence.
 */
internal fun previewTodayOutfit(
    origin: WeatherOrigin = WeatherOrigin.NETWORK,
    cacheAge: Duration? = null
): TodayOutfit {
    val city = LocationInfo(
        latitude = 38.7223,
        longitude = -9.1393,
        cityName = "Lisbon",
        country = "Portugal",
        timezone = "Europe/Lisbon"
    )
    val date = LocalDate.of(2026, 10, 28)
    val hourly = (0..23).map { hour ->
        HourlyCondition(
            time = LocalDateTime.of(date, LocalTime.of(hour, 0)),
            temperatureCelsius = 13.0 + (hour - 7).coerceAtLeast(0) * 0.8 - (hour - 17).coerceAtLeast(0) * 0.9,
            apparentTemperatureCelsius = 12.5 + (hour - 7).coerceAtLeast(0) * 0.8,
            precipitationProbability = when (hour) {
                in 15..17 -> 55
                in 18..20 -> 40
                in 7..14 -> 10
                else -> 25
            },
            uvIndex = when (hour) {
                in 7..9 -> 1.2
                in 10..12 -> 4.6
                in 13..15 -> 6.2
                in 16..17 -> 2.4
                else -> 0.0
            },
            windSpeedKmh = 18.0,
            weatherCode = WeatherCode.PARTLY_CLOUDY
        )
    }

    val recommendation = OutfitRecommendation(
        headline = HeadlineReason.LargeTempSwing(deltaCelsius = 9.4),
        summary = SummaryData(
            tempMinCelsius = 12.6,
            tempMaxCelsius = 22.0,
            hasLargeTempSwing = true,
            rainReason = AccessoryReason.RainWindow(
                start = LocalTime.of(15, 0),
                end = LocalTime.of(17, 0)
            ),
            peakUvIndex = 6.2
        ),
        baseLayer = GarmentCatalog.COTTON_TEE.copy(fit = GarmentFit.UNISEX),
        midLayer = GarmentCatalog.CARDIGAN.copy(fit = GarmentFit.UNISEX),
        outerLayer = null,
        bottomLayer = GarmentCatalog.CHINOS.copy(fit = GarmentFit.UNISEX),
        footwear = GarmentCatalog.SNEAKERS.copy(fit = GarmentFit.UNISEX),
        accessories = listOf(
            AccessoryAlert(
                type = AccessoryType.SUNGLASSES,
                reason = AccessoryReason.UvIndex(peakUv = 6.2),
                priority = PriorityLevel.CRITICAL
            ),
            AccessoryAlert(
                type = AccessoryType.UMBRELLA,
                reason = AccessoryReason.RainWindow(
                    start = LocalTime.of(15, 0),
                    end = LocalTime.of(17, 0)
                ),
                priority = PriorityLevel.RECOMMENDED
            ),
            AccessoryAlert(
                type = AccessoryType.EXTRA_EVENING_LAYER,
                reason = AccessoryReason.DiurnalSwing(deltaCelsius = 9.4),
                priority = PriorityLevel.RECOMMENDED
            )
        ),
        weatherHighlights = WeatherHighlights(
            tempMinCelsius = 12.6,
            tempMaxCelsius = 22.0,
            deltaTCelsius = 9.4,
            maxPrecipitationProb = 55,
            maxUvIndex = 6.2,
            maxWindSpeedKmh = 18.4,
            hasRainSpike = true,
            hasLargeTempSwing = true
        )
    )

    return TodayOutfit(
        recommendation = recommendation,
        weather = WeatherData(
            snapshot = WeatherSnapshot(
                location = city,
                fetchedAt = Instant.parse("2026-10-28T06:40:00Z"),
                current = CurrentCondition(
                    time = LocalDateTime.of(date, LocalTime.of(8, 0)),
                    temperatureCelsius = 14.0,
                    apparentTemperatureCelsius = 13.0,
                    humidityPercent = 68,
                    weatherCode = WeatherCode.PARTLY_CLOUDY,
                    windSpeedKmh = 12.0
                ),
                hourlyForecast = hourly,
                dailyForecast = DailySummary(
                    date = date,
                    maxTempCelsius = 22.0,
                    minTempCelsius = 12.6,
                    maxPrecipitationProbability = 55,
                    weatherCode = WeatherCode.PARTLY_CLOUDY
                )
            ),
            origin = origin,
            cacheAge = cacheAge
        ),
        profile = UserProfile(selectedLocation = city)
    )
}
