package com.smoh.showmeoutfit.data.remote.datasource

import com.smoh.showmeoutfit.data.remote.dto.OpenMeteoForecastDto
import com.smoh.showmeoutfit.data.remote.dto.OpenMeteoGeocodingResponseDto
import com.smoh.showmeoutfit.data.remote.mapper.WeatherNetworkMapper
import com.smoh.showmeoutfit.domain.model.LocationInfo
import com.smoh.showmeoutfit.domain.model.WeatherSnapshot
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kotlin.coroutines.cancellation.CancellationException

class OpenMeteoDataSource(
    private val httpClient: HttpClient
) : RemoteWeatherDataSource {

    companion object {
        private const val BASE_URL = "https://api.open-meteo.com/v1"
        private const val GEOCODING_URL = "https://geocoding-api.open-meteo.com/v1"
        private const val HOURLY_PARAMS =
            "temperature_2m,apparent_temperature,precipitation_probability,uv_index,wind_speed_10m,weather_code"
        private const val DAILY_PARAMS =
            "temperature_2m_max,temperature_2m_min,precipitation_probability_max,weather_code"
        private const val CURRENT_PARAMS =
            "temperature_2m,apparent_temperature,relative_humidity_2m,weather_code,wind_speed_10m"
    }

    override suspend fun fetchForecast(location: LocationInfo): Result<WeatherSnapshot> {
        return try {
            val response = httpClient.get("$BASE_URL/forecast") {
                parameter("latitude", location.latitude)
                parameter("longitude", location.longitude)
                parameter("hourly", HOURLY_PARAMS)
                parameter("daily", DAILY_PARAMS)
                parameter("current", CURRENT_PARAMS)
                parameter("wind_speed_unit", "kmh")
                parameter("timezone", location.timezone ?: "auto")
                parameter("forecast_days", 1)
            }
            val dto: OpenMeteoForecastDto = response.body()
            Result.success(WeatherNetworkMapper.mapToDomain(dto, location))
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Throwable) {
            Result.failure(error)
        }
    }

    override suspend fun geocodeLocation(query: String): Result<List<LocationInfo>> {
        return try {
            val response = httpClient.get("$GEOCODING_URL/search") {
                parameter("name", query)
                parameter("count", 10)
                parameter("language", "en")
                parameter("format", "json")
            }
            val dto: OpenMeteoGeocodingResponseDto = response.body()
            Result.success(dto.results?.map { WeatherNetworkMapper.mapGeocodingResultToDomain(it) } ?: emptyList())
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Throwable) {
            Result.failure(error)
        }
    }
}
