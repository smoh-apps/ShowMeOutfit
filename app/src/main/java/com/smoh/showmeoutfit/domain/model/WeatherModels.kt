package com.smoh.showmeoutfit.domain.model

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

enum class WeatherCode(val code: Int) {
    CLEAR_SKY(0),
    MAINLY_CLEAR(1),
    PARTLY_CLOUDY(2),
    OVERCAST(3),
    FOG(45),
    DEPOSITING_RIME_FOG(48),
    DRIZZLE_LIGHT(51),
    DRIZZLE_MODERATE(53),
    DRIZZLE_DENSE(55),
    FREEZING_DRIZZLE_LIGHT(56),
    FREEZING_DRIZZLE_DENSE(57),
    RAIN_SLIGHT(61),
    RAIN_MODERATE(63),
    RAIN_HEAVY(65),
    FREEZING_RAIN_LIGHT(66),
    FREEZING_RAIN_HEAVY(67),
    SNOW_FALL_SLIGHT(71),
    SNOW_FALL_MODERATE(73),
    SNOW_FALL_HEAVY(75),
    SNOW_GRAINS(77),
    RAIN_SHOWERS_SLIGHT(80),
    RAIN_SHOWERS_MODERATE(81),
    RAIN_SHOWERS_VIOLENT(82),
    SNOW_SHOWERS_SLIGHT(85),
    SNOW_SHOWERS_HEAVY(86),
    THUNDERSTORM(95),
    THUNDERSTORM_SLIGHT_HAIL(96),
    THUNDERSTORM_HEAVY_HAIL(99),
    UNKNOWN(-1);

    val isPrecipitation: Boolean
        get() = this in listOf(
            DRIZZLE_LIGHT, DRIZZLE_MODERATE, DRIZZLE_DENSE,
            FREEZING_DRIZZLE_LIGHT, FREEZING_DRIZZLE_DENSE,
            RAIN_SLIGHT, RAIN_MODERATE, RAIN_HEAVY,
            FREEZING_RAIN_LIGHT, FREEZING_RAIN_HEAVY,
            SNOW_FALL_SLIGHT, SNOW_FALL_MODERATE, SNOW_FALL_HEAVY, SNOW_GRAINS,
            RAIN_SHOWERS_SLIGHT, RAIN_SHOWERS_MODERATE, RAIN_SHOWERS_VIOLENT,
            SNOW_SHOWERS_SLIGHT, SNOW_SHOWERS_HEAVY,
            THUNDERSTORM, THUNDERSTORM_SLIGHT_HAIL, THUNDERSTORM_HEAVY_HAIL
        )

    val isSnow: Boolean
        get() = this in listOf(
            SNOW_FALL_SLIGHT, SNOW_FALL_MODERATE, SNOW_FALL_HEAVY, SNOW_GRAINS,
            SNOW_SHOWERS_SLIGHT, SNOW_SHOWERS_HEAVY
        )

    val isRain: Boolean
        get() = isPrecipitation && !isSnow

    companion object {
        fun fromCode(code: Int): WeatherCode =
            entries.find { it.code == code } ?: UNKNOWN
    }
}

data class CurrentCondition(
    val time: LocalDateTime,
    val temperatureCelsius: Double,
    val apparentTemperatureCelsius: Double,
    val humidityPercent: Int,
    val weatherCode: WeatherCode,
    val windSpeedKmh: Double
)

data class HourlyCondition(
    val time: LocalDateTime,
    val temperatureCelsius: Double,
    val apparentTemperatureCelsius: Double,
    val precipitationProbability: Int, // 0 - 100%
    val uvIndex: Double,
    val windSpeedKmh: Double,
    val weatherCode: WeatherCode
)

data class DailySummary(
    val date: LocalDate,
    val maxTempCelsius: Double,
    val minTempCelsius: Double,
    val maxPrecipitationProbability: Int,
    val weatherCode: WeatherCode,
    val sunrise: LocalDateTime? = null,
    val sunset: LocalDateTime? = null
)

data class WeatherHighlights(
    val tempMinCelsius: Double,
    val tempMaxCelsius: Double,
    val deltaTCelsius: Double,
    val maxPrecipitationProb: Int,
    val maxUvIndex: Double,
    val maxWindSpeedKmh: Double,
    val hasRainSpike: Boolean,
    val hasLargeTempSwing: Boolean
)

data class WeatherSnapshot(
    val location: LocationInfo,
    val fetchedAt: Instant,
    val current: CurrentCondition,
    val hourlyForecast: List<HourlyCondition>,
    val dailyForecast: DailySummary
)
