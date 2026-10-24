package com.smoh.showmeoutfit.core.database.mapper

import com.smoh.showmeoutfit.core.database.entity.WeatherSnapshotEntity
import com.smoh.showmeoutfit.domain.model.CurrentCondition
import com.smoh.showmeoutfit.domain.model.DailySummary
import com.smoh.showmeoutfit.domain.model.HourlyCondition
import com.smoh.showmeoutfit.domain.model.LocationInfo
import com.smoh.showmeoutfit.domain.model.WeatherCode
import com.smoh.showmeoutfit.domain.model.WeatherSnapshot
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object WeatherEntityMapper {

    private val ISO_DATETIME: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")
    private val ISO_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    fun toEntity(snapshot: WeatherSnapshot, hourlyJson: String): WeatherSnapshotEntity {
        return WeatherSnapshotEntity(
            fetchedAtEpochMs = snapshot.fetchedAt.toEpochMilli(),
            latitude = snapshot.location.latitude,
            longitude = snapshot.location.longitude,
            cityName = snapshot.location.cityName,
            country = snapshot.location.country,
            timezone = snapshot.location.timezone,
            currentTempCelsius = snapshot.current.temperatureCelsius,
            currentApparentTempCelsius = snapshot.current.apparentTemperatureCelsius,
            currentHumidityPercent = snapshot.current.humidityPercent,
            currentWeatherCode = snapshot.current.weatherCode.code,
            currentWindSpeedKmh = snapshot.current.windSpeedKmh,
            currentTimeIso = snapshot.current.time.format(ISO_DATETIME),
            dailyMaxTempCelsius = snapshot.dailyForecast.maxTempCelsius,
            dailyMinTempCelsius = snapshot.dailyForecast.minTempCelsius,
            dailyMaxPrecipProb = snapshot.dailyForecast.maxPrecipitationProbability,
            dailyWeatherCode = snapshot.dailyForecast.weatherCode.code,
            dailyDateIso = snapshot.dailyForecast.date.format(ISO_DATE),
            hourlyJson = hourlyJson
        )
    }

    fun toDomain(entity: WeatherSnapshotEntity, hourlyForecast: List<HourlyCondition>): WeatherSnapshot {
        return WeatherSnapshot(
            location = LocationInfo(
                latitude = entity.latitude,
                longitude = entity.longitude,
                cityName = entity.cityName,
                country = entity.country,
                timezone = entity.timezone
            ),
            fetchedAt = Instant.ofEpochMilli(entity.fetchedAtEpochMs),
            current = CurrentCondition(
                time = LocalDateTime.parse(entity.currentTimeIso, ISO_DATETIME),
                temperatureCelsius = entity.currentTempCelsius,
                apparentTemperatureCelsius = entity.currentApparentTempCelsius,
                humidityPercent = entity.currentHumidityPercent,
                weatherCode = WeatherCode.fromCode(entity.currentWeatherCode),
                windSpeedKmh = entity.currentWindSpeedKmh
            ),
            hourlyForecast = hourlyForecast,
            dailyForecast = DailySummary(
                date = LocalDate.parse(entity.dailyDateIso, ISO_DATE),
                maxTempCelsius = entity.dailyMaxTempCelsius,
                minTempCelsius = entity.dailyMinTempCelsius,
                maxPrecipitationProbability = entity.dailyMaxPrecipProb,
                weatherCode = WeatherCode.fromCode(entity.dailyWeatherCode)
            )
        )
    }
}
