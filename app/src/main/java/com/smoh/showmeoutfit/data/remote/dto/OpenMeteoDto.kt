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
    @SerialName("time") val time: List<String>,
    @SerialName("temperature_2m") val temperature2m: List<Double>,
    @SerialName("apparent_temperature") val apparentTemperature: List<Double>,
    @SerialName("precipitation_probability") val precipitationProbability: List<Int>,
    @SerialName("uv_index") val uvIndex: List<Double>,
    @SerialName("wind_speed_10m") val windSpeed10m: List<Double>,
    @SerialName("weather_code") val weatherCode: List<Int>
)

@Serializable
data class OpenMeteoDailyDto(
    @SerialName("time") val time: List<String>,
    @SerialName("temperature_2m_max") val temperature2mMax: List<Double>,
    @SerialName("temperature_2m_min") val temperature2mMin: List<Double>,
    @SerialName("precipitation_probability_max") val precipitationProbabilityMax: List<Int>,
    @SerialName("weather_code") val weatherCode: List<Int>
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
