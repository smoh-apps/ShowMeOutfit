package com.smoh.showmeoutfit.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OpenMeteoForecastDto(
    @SerialName("latitude") val latitude: Double,
    @SerialName("longitude") val longitude: Double,
    @SerialName("timezone") val timezone: String,
    @SerialName("current") val current: OpenMeteoCurrentDto,
    @SerialName("hourly") val hourly: OpenMeteoHourlyDto,
    @SerialName("daily") val daily: OpenMeteoDailyDto
)

@Serializable
data class OpenMeteoCurrentDto(
    @SerialName("time") val time: String,
    @SerialName("temperature_2m") val temperature2m: Double,
    @SerialName("apparent_temperature") val apparentTemperature: Double,
    @SerialName("relative_humidity_2m") val relativeHumidity2m: Int,
    @SerialName("weather_code") val weatherCode: Int,
    @SerialName("wind_speed_10m") val windSpeed10m: Double
)

@Serializable
data class OpenMeteoHourlyDto(
    // Arrays are independently optional in an Open-Meteo reply: a sparse or truncated payload can
    // omit one of them, so every list defaults to empty and the mapper intersects the lengths.
    @SerialName("time") val time: List<String> = emptyList(),
    @SerialName("temperature_2m") val temperature2m: List<Double> = emptyList(),
    @SerialName("apparent_temperature") val apparentTemperature: List<Double> = emptyList(),
    @SerialName("precipitation_probability") val precipitationProbability: List<Int> = emptyList(),
    @SerialName("uv_index") val uvIndex: List<Double> = emptyList(),
    @SerialName("wind_speed_10m") val windSpeed10m: List<Double> = emptyList(),
    @SerialName("weather_code") val weatherCode: List<Int> = emptyList()
)

@Serializable
data class OpenMeteoDailyDto(
    @SerialName("time") val time: List<String> = emptyList(),
    @SerialName("temperature_2m_max") val temperature2mMax: List<Double> = emptyList(),
    @SerialName("temperature_2m_min") val temperature2mMin: List<Double> = emptyList(),
    @SerialName("precipitation_probability_max") val precipitationProbabilityMax: List<Int> = emptyList(),
    @SerialName("weather_code") val weatherCode: List<Int> = emptyList()
)

@Serializable
data class OpenMeteoGeocodingResponseDto(
    @SerialName("results") val results: List<OpenMeteoGeocodingResultDto>? = null
)

@Serializable
data class OpenMeteoGeocodingResultDto(
    @SerialName("id") val id: Long,
    @SerialName("name") val name: String,
    @SerialName("latitude") val latitude: Double,
    @SerialName("longitude") val longitude: Double,
    @SerialName("country") val country: String? = null,
    @SerialName("admin1") val admin1: String? = null,
    @SerialName("timezone") val timezone: String? = null
)
