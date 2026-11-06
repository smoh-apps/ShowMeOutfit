package com.smoh.showmeoutfit.data.remote.mapper

import com.smoh.showmeoutfit.data.remote.dto.OpenMeteoCurrentDto
import com.smoh.showmeoutfit.data.remote.dto.OpenMeteoDailyDto
import com.smoh.showmeoutfit.data.remote.dto.OpenMeteoForecastDto
import com.smoh.showmeoutfit.data.remote.dto.OpenMeteoGeocodingResultDto
import com.smoh.showmeoutfit.data.remote.dto.OpenMeteoHourlyDto
import com.smoh.showmeoutfit.domain.model.CurrentCondition
import com.smoh.showmeoutfit.domain.model.DailySummary
import com.smoh.showmeoutfit.domain.model.DomainError
import com.smoh.showmeoutfit.domain.model.HourlyCondition
import com.smoh.showmeoutfit.domain.model.LocationInfo
import com.smoh.showmeoutfit.domain.model.WeatherCode
import com.smoh.showmeoutfit.domain.model.WeatherSnapshot
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * Maps Open-Meteo wire payloads onto the domain.
 *
 * Open-Meteo arrays are independently optional: a sparse, truncated or partially empty reply is a
 * normal occurrence, not a bug. The mapper therefore
 *  - intersects the hourly/daily array lengths and maps only the slots that are valid in every
 *    required array, never indexing past the shortest one;
 *  - rejects a payload with no usable daily entry with [DomainError.MalformedData] instead of
 *    indexing `[0]` blindly;
 *  - turns parse failures into [DomainError.MalformedData] so a bad timestamp can never surface as
 *    the generic `Unknown` retry copy;
 *  - drops geocoding results that cannot describe a place (blank name, non-finite or out-of-range
 *    coordinates).
 */
object WeatherNetworkMapper {

    private val ISO_DATETIME: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")
    private val ISO_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    private const val MIN_LATITUDE = -90.0
    private const val MAX_LATITUDE = 90.0
    private const val MIN_LONGITUDE = -180.0
    private const val MAX_LONGITUDE = 180.0

    fun mapToDomain(
        dto: OpenMeteoForecastDto,
        location: LocationInfo,
        fetchedAt: Instant = Instant.now()
    ): WeatherSnapshot = try {
        WeatherSnapshot(
            location = location,
            fetchedAt = fetchedAt,
            current = mapCurrent(dto.current),
            hourlyForecast = mapHourly(dto.hourly),
            dailyForecast = mapDaily(dto.daily)
        )
    } catch (alreadyMapped: DomainError) {
        throw alreadyMapped
    } catch (parse: DateTimeParseException) {
        throw DomainError.MalformedData(parse)
    } catch (parse: NumberFormatException) {
        throw DomainError.MalformedData(parse)
    }

    private fun mapCurrent(dto: OpenMeteoCurrentDto) = CurrentCondition(
        time = LocalDateTime.parse(dto.time, ISO_DATETIME),
        temperatureCelsius = dto.temperature2m,
        apparentTemperatureCelsius = dto.apparentTemperature,
        humidityPercent = dto.relativeHumidity2m,
        weatherCode = WeatherCode.fromCode(dto.weatherCode),
        windSpeedKmh = dto.windSpeed10m
    )

    /**
     * Maps every hour that has a value in *all* required arrays.
     *
     * `minOf` of the lengths is what keeps a shorter `uv_index` or `weather_code` array from
     * throwing `IndexOutOfBoundsException`; the trailing slots are skipped, and the surviving
     * slots keep their wire order.
     */
    private fun mapHourly(dto: OpenMeteoHourlyDto): List<HourlyCondition> {
        val size = usableSlots(
            dto.time.size,
            dto.temperature2m.size,
            dto.apparentTemperature.size,
            dto.precipitationProbability.size,
            dto.uvIndex.size,
            dto.windSpeed10m.size,
            dto.weatherCode.size
        )
        if (size == 0) return emptyList()

        return (0 until size).map { i ->
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

    /**
     * Today's daily aggregate, taken from the first slot that is complete in every array.
     *
     * A fully sparse daily block is unusable: [DomainError.MalformedData] tells the UI to ask for a
     * retry instead of inventing a "today" out of missing numbers.
     */
    private fun mapDaily(dto: OpenMeteoDailyDto): DailySummary {
        val size = usableSlots(
            dto.time.size,
            dto.temperature2mMax.size,
            dto.temperature2mMin.size,
            dto.precipitationProbabilityMax.size,
            dto.weatherCode.size
        )
        if (size == 0) {
            throw DomainError.MalformedData(
                IllegalStateException("Daily forecast has no complete entry")
            )
        }

        return DailySummary(
            date = LocalDate.parse(dto.time[0], ISO_DATE),
            maxTempCelsius = dto.temperature2mMax[0],
            minTempCelsius = dto.temperature2mMin[0],
            maxPrecipitationProbability = dto.precipitationProbabilityMax[0],
            weatherCode = WeatherCode.fromCode(dto.weatherCode[0])
        )
    }

    /** Number of leading slots valid in every array; never negative. */
    private fun usableSlots(vararg sizes: Int): Int = sizes.minOrNull()?.coerceAtLeast(0) ?: 0

    // --- Geocoding ------------------------------------------------------------------------

    /** Maps a whole search response, dropping the entries that cannot describe a usable place. */
    fun mapGeocodingResults(dtos: List<OpenMeteoGeocodingResultDto>): List<LocationInfo> =
        dtos.mapNotNull { mapGeocodingResultToDomain(it) }

    /**
     * Maps one geocoding hit, or `null` when it is unusable.
     *
     * A blank name, a NaN/Infinite coordinate or an out-of-range latitude/longitude would produce a
     * [LocationInfo] the forecast API cannot answer for, so such hits are filtered out rather than
     * shown as a selectable city. Optional metadata stays `null` when absent or blank.
     */
    fun mapGeocodingResultToDomain(dto: OpenMeteoGeocodingResultDto): LocationInfo? {
        val cityName = dto.name.trim()
        if (cityName.isEmpty()) return null
        if (!dto.latitude.isFinite() || !dto.longitude.isFinite()) return null
        if (dto.latitude !in MIN_LATITUDE..MAX_LATITUDE) return null
        if (dto.longitude !in MIN_LONGITUDE..MAX_LONGITUDE) return null

        return LocationInfo(
            latitude = dto.latitude,
            longitude = dto.longitude,
            cityName = cityName,
            country = dto.country.cleanOrNull(),
            adminRegion = dto.admin1.cleanOrNull(),
            timezone = dto.timezone.cleanOrNull()
        )
    }

    private fun String?.cleanOrNull(): String? = this?.trim()?.takeIf { it.isNotEmpty() }
}
