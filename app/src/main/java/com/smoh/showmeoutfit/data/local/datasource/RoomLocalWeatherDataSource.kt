package com.smoh.showmeoutfit.data.local.datasource

import com.smoh.showmeoutfit.core.database.dao.WeatherSnapshotDao
import com.smoh.showmeoutfit.core.database.mapper.WeatherEntityMapper
import com.smoh.showmeoutfit.domain.model.HourlyCondition
import com.smoh.showmeoutfit.domain.model.WeatherCode
import com.smoh.showmeoutfit.domain.model.WeatherSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class RoomLocalWeatherDataSource(
    private val dao: WeatherSnapshotDao
) : LocalWeatherDataSource {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun save(snapshot: WeatherSnapshot) {
        val hourlyJson = json.encodeToString(snapshot.hourlyForecast.map { it.toSerializable() })
        val entity = WeatherEntityMapper.toEntity(snapshot, hourlyJson)
        dao.insert(entity)
    }

    override suspend fun getLatest(): WeatherSnapshot? {
        val entity = dao.getLatest() ?: return null
        val hourly = decodeHourly(entity.hourlyJson)
        return WeatherEntityMapper.toDomain(entity, hourly)
    }

    override fun observeLatest(): Flow<WeatherSnapshot?> {
        return dao.observeLatest().map { entity ->
            entity?.let {
                val hourly = decodeHourly(it.hourlyJson)
                WeatherEntityMapper.toDomain(it, hourly)
            }
        }
    }

    override suspend fun pruneOlderThan(thresholdMs: Long) {
        dao.deleteOlderThan(thresholdMs)
    }

    private fun decodeHourly(hourlyJson: String): List<HourlyCondition> {
        return runCatching {
            json.decodeFromString<List<SerializableHourly>>(hourlyJson)
                .map { it.toDomain() }
        }.getOrElse { emptyList() }
    }

    // Internal serializable mirror of HourlyCondition (no java.time types in JSON)
    @Serializable
    private data class SerializableHourly(
        val timeIso: String,
        val temperatureCelsius: Double,
        val apparentTemperatureCelsius: Double,
        val precipitationProbability: Int,
        val uvIndex: Double,
        val windSpeedKmh: Double,
        val weatherCode: Int
    ) {
        fun toDomain(): HourlyCondition {
            return HourlyCondition(
                time = java.time.LocalDateTime.parse(
                    timeIso,
                    java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")
                ),
                temperatureCelsius = temperatureCelsius,
                apparentTemperatureCelsius = apparentTemperatureCelsius,
                precipitationProbability = precipitationProbability,
                uvIndex = uvIndex,
                windSpeedKmh = windSpeedKmh,
                weatherCode = WeatherCode.fromCode(weatherCode)
            )
        }
    }

    private fun HourlyCondition.toSerializable() = SerializableHourly(
        timeIso = time.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")),
        temperatureCelsius = temperatureCelsius,
        apparentTemperatureCelsius = apparentTemperatureCelsius,
        precipitationProbability = precipitationProbability,
        uvIndex = uvIndex,
        windSpeedKmh = windSpeedKmh,
        weatherCode = weatherCode.code
    )
}
