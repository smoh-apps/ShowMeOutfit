package com.smoh.showmeoutfit.data.remote.mapper

import com.smoh.showmeoutfit.data.remote.dto.OpenMeteoCurrentDto
import com.smoh.showmeoutfit.data.remote.dto.OpenMeteoForecastDto
import com.smoh.showmeoutfit.data.remote.dto.OpenMeteoGeocodingResultDto
import com.smoh.showmeoutfit.domain.model.CurrentCondition
import com.smoh.showmeoutfit.domain.model.DailySummary
import com.smoh.showmeoutfit.domain.model.HourlyCondition
import com.smoh.showmeoutfit.domain.model.LocationInfo
import com.smoh.showmeoutfit.domain.model.WeatherCode
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object WeatherNetworkMapper {

    private val ISO_DATETIME: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")
    private val ISO_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    fun mapToDomain(
        dto: OpenMeteoForecastDto,
        location: LocationInfo,
        fetchedAt: Instant = Instant.now()
    ) = com.smoh.showmeoutfit.domain.model.WeatherSnapshot(
        location = location,
        fetchedAt = fetchedAt,
        current = mapCurrent(dto.current),
        hourlyForecast = mapHourly(dto.hourly),
        dailyForecast = mapDaily(dto.daily)
    )

    private fun mapCurrent(dto: OpenMeteoCurrentDto) = CurrentCondition(
        time = LocalDateTime.parse(dto.time, ISO_DATETIME),
        temperatureCelsius = dto.temperature2m,
        apparentTemperatureCelsius = dto.apparentTemperature,
        humidityPercent = dto.relativeHumidity2m,
        weatherCode = WeatherCode.fromCode(dto.weatherCode),
        windSpeedKmh = dto.windSpeed10m
    )

    private fun mapHourly(
        dto: com.smoh.showmeoutfit.data.remote.dto.OpenMeteoHourlyDto
    ): List<HourlyCondition> {
        return dto.time.indices.map { i ->
            HourlyCondition(
                time = LocalDateTime.parse(dto.time[i], ISO_DATETIME),
                temperatureCelsius = dto.temperature2m[i],
                apparentTemperatureCelsius = dto.apparentTemperature[i],
                precipitationProbability = dto.precipitationProbability[i],
                uvIndex = dto.uvIndex[i],
                windSpeedKmh = dto.windSpeed10m[i],
                weatherCode = WeatherCode.fromCode(dto.weatherCode[i])
            )
        }
    }

    private fun mapDaily(
        dto: com.smoh.showmeoutfit.data.remote.dto.OpenMeteoDailyDto
    ): DailySummary {
        // Use the first (today's) daily entry
        return DailySummary(
            date = LocalDate.parse(dto.time[0], ISO_DATE),
            maxTempCelsius = dto.temperature2mMax[0],
            minTempCelsius = dto.temperature2mMin[0],
            maxPrecipitationProbability = dto.precipitationProbabilityMax[0],
            weatherCode = WeatherCode.fromCode(dto.weatherCode[0])
        )
    }

    fun mapGeocodingResultToDomain(dto: OpenMeteoGeocodingResultDto) = LocationInfo(
        latitude = dto.latitude,
        longitude = dto.longitude,
        cityName = dto.name,
        country = dto.country,
        adminRegion = dto.admin1,
        timezone = dto.timezone
    )
}
