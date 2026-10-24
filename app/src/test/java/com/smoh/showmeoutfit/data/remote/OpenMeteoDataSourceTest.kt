package com.smoh.showmeoutfit.data.remote

import com.smoh.showmeoutfit.core.network.HttpClientFactory
import com.smoh.showmeoutfit.data.remote.datasource.OpenMeteoDataSource
import com.smoh.showmeoutfit.domain.model.LocationInfo
import com.smoh.showmeoutfit.domain.model.WeatherCode
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenMeteoDataSourceTest {

    private fun buildMockDataSource(mockResponse: String, status: HttpStatusCode = HttpStatusCode.OK): OpenMeteoDataSource {
        val mockEngine = MockEngine { _ ->
            respond(
                content = mockResponse,
                status = status,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        }
        val client = HttpClientFactory.create(mockEngine)
        return OpenMeteoDataSource(client)
    }

    private val berlinLocation = LocationInfo(
        latitude = 52.52,
        longitude = 13.405,
        cityName = "Berlin",
        country = "Germany",
        timezone = "Europe/Berlin"
    )

    @Test
    fun `fetchForecast Berlin successfully parses all domain fields`() = runTest {
        val dataSource = buildMockDataSource(BERLIN_FORECAST_JSON)
        val result = dataSource.fetchForecast(berlinLocation)

        assertTrue(result.isSuccess)
        val snapshot = result.getOrThrow()
        assertEquals("Berlin", snapshot.location.cityName)
        assertEquals(52.52, snapshot.location.latitude, 0.01)
        assertEquals(14.2, snapshot.current.temperatureCelsius, 0.01)
        assertEquals(WeatherCode.PARTLY_CLOUDY, snapshot.current.weatherCode)
        assertTrue(snapshot.hourlyForecast.isNotEmpty())
        assertTrue(snapshot.hourlyForecast.all { it.temperatureCelsius > -50.0 })
    }

    @Test
    fun `fetchForecast Tokyo correctly maps UV and precipitation data`() = runTest {
        val tokyoLocation = LocationInfo(35.689, 139.691, "Tokyo", "Japan", timezone = "Asia/Tokyo")
        val dataSource = buildMockDataSource(TOKYO_FORECAST_JSON)
        val result = dataSource.fetchForecast(tokyoLocation)

        assertTrue(result.isSuccess)
        val snapshot = result.getOrThrow()
        val maxUv = snapshot.hourlyForecast.maxOfOrNull { it.uvIndex } ?: 0.0
        assertTrue("UV index should be positive", maxUv > 0.0)
        val maxPrecip = snapshot.hourlyForecast.maxOfOrNull { it.precipitationProbability } ?: 0
        assertTrue("Precipitation probability should be 0-100", maxPrecip in 0..100)
    }

    @Test
    fun `fetchForecast correctly maps London snow weather codes`() = runTest {
        val londonLocation = LocationInfo(51.507, -0.127, "London", "United Kingdom", timezone = "Europe/London")
        val dataSource = buildMockDataSource(LONDON_SNOW_FORECAST_JSON)
        val result = dataSource.fetchForecast(londonLocation)

        assertTrue(result.isSuccess)
        val snapshot = result.getOrThrow()
        val hasSnowCode = snapshot.hourlyForecast.any { it.weatherCode.isSnow }
        assertTrue("Should detect snow weather codes", hasSnowCode)
        assertEquals(WeatherCode.SNOW_FALL_MODERATE, snapshot.dailyForecast.weatherCode)
    }

    @Test
    fun `fetchForecast returns failure on HTTP 500 error`() = runTest {
        val dataSource = buildMockDataSource("{}", HttpStatusCode.InternalServerError)
        val result = dataSource.fetchForecast(berlinLocation)
        assertTrue("Should return failure on server error", result.isFailure)
    }

    @Test
    fun `fetchForecast returns failure on malformed JSON`() = runTest {
        val dataSource = buildMockDataSource("NOT_VALID_JSON_AT_ALL")
        val result = dataSource.fetchForecast(berlinLocation)
        assertTrue("Should return failure on malformed response", result.isFailure)
    }

    @Test
    fun `geocodeLocation parses geocoding results correctly`() = runTest {
        val dataSource = buildMockDataSource(GEOCODING_RESPONSE_JSON)
        val result = dataSource.geocodeLocation("Paris")

        assertTrue(result.isSuccess)
        val locations = result.getOrThrow()
        assertTrue(locations.isNotEmpty())
        val paris = locations.first()
        assertEquals("Paris", paris.cityName)
        assertEquals(48.8534, paris.latitude, 0.01)
        assertEquals(2.3488, paris.longitude, 0.01)
        assertEquals("France", paris.country)
    }

    @Test
    fun `geocodeLocation returns empty list when no results`() = runTest {
        val dataSource = buildMockDataSource("""{"results": null}""")
        val result = dataSource.geocodeLocation("XyzZyxNonExistentCity99")
        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().isEmpty())
    }

    companion object {
        // Realistic Open-Meteo payloads (condensed for testing)
        private val BERLIN_FORECAST_JSON = """
        {
          "latitude": 52.52,
          "longitude": 13.405,
          "timezone": "Europe/Berlin",
          "current": {
            "time": "2026-10-24T10:00",
            "temperature_2m": 14.2,
            "apparent_temperature": 12.1,
            "relative_humidity_2m": 70,
            "weather_code": 2,
            "wind_speed_10m": 18.0
          },
          "hourly": {
            "time": ["2026-10-24T07:00","2026-10-24T08:00","2026-10-24T09:00","2026-10-24T10:00","2026-10-24T11:00","2026-10-24T12:00","2026-10-24T13:00","2026-10-24T14:00","2026-10-24T15:00","2026-10-24T16:00"],
            "temperature_2m": [10.5,11.2,12.1,13.0,14.2,15.5,16.1,15.8,14.9,13.2],
            "apparent_temperature": [8.5,9.2,10.1,11.0,12.2,13.5,14.1,13.8,12.9,11.2],
            "precipitation_probability": [5,5,10,10,15,20,20,15,10,5],
            "uv_index": [0.5,1.2,2.1,3.0,3.5,3.8,3.2,2.5,1.5,0.8],
            "wind_speed_10m": [15.0,16.0,17.0,18.0,19.0,18.5,17.5,16.5,15.5,14.5],
            "weather_code": [2,2,2,2,2,2,2,2,2,2]
          },
          "daily": {
            "time": ["2026-10-24"],
            "temperature_2m_max": [16.1],
            "temperature_2m_min": [9.5],
            "precipitation_probability_max": [20],
            "weather_code": [2]
          }
        }
        """.trimIndent()

        private val TOKYO_FORECAST_JSON = """
        {
          "latitude": 35.689,
          "longitude": 139.691,
          "timezone": "Asia/Tokyo",
          "current": {
            "time": "2026-10-24T10:00",
            "temperature_2m": 22.0,
            "apparent_temperature": 21.0,
            "relative_humidity_2m": 55,
            "weather_code": 1,
            "wind_speed_10m": 10.0
          },
          "hourly": {
            "time": ["2026-10-24T07:00","2026-10-24T12:00","2026-10-24T15:00"],
            "temperature_2m": [20.0,25.0,24.0],
            "apparent_temperature": [19.0,24.0,23.0],
            "precipitation_probability": [10,30,20],
            "uv_index": [2.0,8.5,6.0],
            "wind_speed_10m": [10.0,12.0,11.0],
            "weather_code": [1,1,1]
          },
          "daily": {
            "time": ["2026-10-24"],
            "temperature_2m_max": [25.0],
            "temperature_2m_min": [18.0],
            "precipitation_probability_max": [30],
            "weather_code": [1]
          }
        }
        """.trimIndent()

        private val LONDON_SNOW_FORECAST_JSON = """
        {
          "latitude": 51.507,
          "longitude": -0.127,
          "timezone": "Europe/London",
          "current": {
            "time": "2026-10-24T08:00",
            "temperature_2m": -2.0,
            "apparent_temperature": -5.0,
            "relative_humidity_2m": 85,
            "weather_code": 73,
            "wind_speed_10m": 22.0
          },
          "hourly": {
            "time": ["2026-10-24T07:00","2026-10-24T10:00","2026-10-24T14:00"],
            "temperature_2m": [-3.0,-2.0,-1.0],
            "apparent_temperature": [-6.0,-5.0,-4.0],
            "precipitation_probability": [90,85,80],
            "uv_index": [0.2,0.5,0.3],
            "wind_speed_10m": [22.0,20.0,18.0],
            "weather_code": [73,73,71]
          },
          "daily": {
            "time": ["2026-10-24"],
            "temperature_2m_max": [-1.0],
            "temperature_2m_min": [-5.0],
            "precipitation_probability_max": [90],
            "weather_code": [73]
          }
        }
        """.trimIndent()

        private val GEOCODING_RESPONSE_JSON = """
        {
          "results": [
            {
              "id": 2988507,
              "name": "Paris",
              "latitude": 48.8534,
              "longitude": 2.3488,
              "country": "France",
              "admin1": "Île-de-France",
              "timezone": "Europe/Paris"
            }
          ]
        }
        """.trimIndent()
    }
}
