package com.smoh.showmeoutfit.data.local

import com.smoh.showmeoutfit.core.database.dao.WeatherSnapshotDao
import com.smoh.showmeoutfit.core.database.entity.WeatherSnapshotEntity
import com.smoh.showmeoutfit.data.local.datasource.RoomLocalWeatherDataSource
import com.smoh.showmeoutfit.domain.model.CurrentCondition
import com.smoh.showmeoutfit.domain.model.DailySummary
import com.smoh.showmeoutfit.domain.model.HourlyCondition
import com.smoh.showmeoutfit.domain.model.LocationInfo
import com.smoh.showmeoutfit.domain.model.WeatherCode
import com.smoh.showmeoutfit.domain.model.WeatherSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

class RoomLocalWeatherDataSourceTest {

    private lateinit var fakeDao: FakeWeatherSnapshotDao
    private lateinit var dataSource: RoomLocalWeatherDataSource

    @Before
    fun setUp() {
        fakeDao = FakeWeatherSnapshotDao()
        dataSource = RoomLocalWeatherDataSource(fakeDao)
    }

    private val parisLocation = LocationInfo(48.8566, 2.3522, "Paris", "France", timezone = "Europe/Paris")

    private fun buildSnapshot(city: String = "Paris", fetchedAtMs: Long = 1000L): WeatherSnapshot {
        val baseTime = LocalDateTime.of(2026, 10, 24, 10, 0)
        return WeatherSnapshot(
            location = parisLocation.copy(cityName = city),
            fetchedAt = Instant.ofEpochMilli(fetchedAtMs),
            current = CurrentCondition(
                time = baseTime,
                temperatureCelsius = 17.0,
                apparentTemperatureCelsius = 15.5,
                humidityPercent = 65,
                weatherCode = WeatherCode.PARTLY_CLOUDY,
                windSpeedKmh = 12.0
            ),
            hourlyForecast = listOf(
                HourlyCondition(
                    time = baseTime,
                    temperatureCelsius = 17.0,
                    apparentTemperatureCelsius = 15.5,
                    precipitationProbability = 10,
                    uvIndex = 3.0,
                    windSpeedKmh = 12.0,
                    weatherCode = WeatherCode.PARTLY_CLOUDY
                )
            ),
            dailyForecast = DailySummary(
                date = LocalDate.of(2026, 10, 24),
                maxTempCelsius = 19.0,
                minTempCelsius = 10.0,
                maxPrecipitationProbability = 20,
                weatherCode = WeatherCode.PARTLY_CLOUDY
            )
        )
    }

    @Test
    fun `save and getLatest round-trips the snapshot with correct city and temperature`() = runTest {
        val original = buildSnapshot(city = "Paris", fetchedAtMs = 2000L)
        dataSource.save(original)

        val retrieved = dataSource.getLatest()
        assertNotNull(retrieved)
        assertEquals("Paris", retrieved!!.location.cityName)
        assertEquals(17.0, retrieved.current.temperatureCelsius, 0.01)
        assertEquals(WeatherCode.PARTLY_CLOUDY, retrieved.current.weatherCode)
    }

    @Test
    fun `getLatest returns null when datastore is empty`() = runTest {
        val result = dataSource.getLatest()
        assertNull(result)
    }

    @Test
    fun `save round-trips hourly forecast data correctly`() = runTest {
        val snapshot = buildSnapshot()
        dataSource.save(snapshot)

        val retrieved = dataSource.getLatest()
        assertNotNull(retrieved)
        assertEquals(1, retrieved!!.hourlyForecast.size)
        val hourly = retrieved.hourlyForecast.first()
        assertEquals(17.0, hourly.temperatureCelsius, 0.01)
        assertEquals(3.0, hourly.uvIndex, 0.01)
        assertEquals(WeatherCode.PARTLY_CLOUDY, hourly.weatherCode)
    }

    @Test
    fun `pruneOlderThan delegates cutoff to dao`() = runTest {
        dataSource.pruneOlderThan(5000L)
        assertEquals(5000L, fakeDao.lastPruneCutoff)
    }

    // --- Fake DAO ---
    private class FakeWeatherSnapshotDao : WeatherSnapshotDao {
        private val store = mutableListOf<WeatherSnapshotEntity>()
        var lastPruneCutoff: Long = -1L

        override suspend fun insert(entity: WeatherSnapshotEntity): Long {
            store.add(entity)
            return store.size.toLong()
        }

        override suspend fun getLatest(): WeatherSnapshotEntity? =
            store.maxByOrNull { it.fetchedAtEpochMs }

        override fun observeLatest(): Flow<WeatherSnapshotEntity?> =
            flowOf(store.maxByOrNull { it.fetchedAtEpochMs })

        override suspend fun deleteOlderThan(cutoffEpochMs: Long): Int {
            lastPruneCutoff = cutoffEpochMs
            val removed = store.count { it.fetchedAtEpochMs < cutoffEpochMs }
            store.removeAll { it.fetchedAtEpochMs < cutoffEpochMs }
            return removed
        }

        override suspend fun deleteAll(): Int {
            val count = store.size
            store.clear()
            return count
        }
    }
}
