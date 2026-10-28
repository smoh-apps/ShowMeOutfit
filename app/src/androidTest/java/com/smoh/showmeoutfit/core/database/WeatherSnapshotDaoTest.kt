package com.smoh.showmeoutfit.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.smoh.showmeoutfit.core.database.dao.WeatherSnapshotDao
import com.smoh.showmeoutfit.core.database.entity.WeatherSnapshotEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented Room in-memory database test.
 * Tests DAO operations: insert, getLatest, observeLatest, deleteOlderThan.
 * Requires an Android emulator or device.
 */
@RunWith(AndroidJUnit4::class)
class WeatherSnapshotDaoTest {

    private lateinit var database: WeatherDatabase
    private lateinit var dao: WeatherSnapshotDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            WeatherDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = database.weatherSnapshotDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun buildEntity(fetchedAtMs: Long, city: String = "Berlin") = WeatherSnapshotEntity(
        fetchedAtEpochMs = fetchedAtMs,
        latitude = 52.52,
        longitude = 13.405,
        cityName = city,
        country = "Germany",
        timezone = "Europe/Berlin",
        currentTempCelsius = 14.0,
        currentApparentTempCelsius = 12.0,
        currentHumidityPercent = 70,
        currentWeatherCode = 2,
        currentWindSpeedKmh = 15.0,
        currentTimeIso = "2026-10-24T10:00",
        dailyMaxTempCelsius = 17.0,
        dailyMinTempCelsius = 9.0,
        dailyMaxPrecipProb = 20,
        dailyWeatherCode = 2,
        dailyDateIso = "2026-10-24",
        hourlyJson = "[]"
    )

    @Test
    fun insertAndGetLatest_returnsInsertedEntity() = runTest {
        val entity = buildEntity(fetchedAtMs = 1000L)
        dao.insert(entity)

        val latest = dao.getLatest()
        assertNotNull(latest)
        assertEquals("Berlin", latest!!.cityName)
        assertEquals(1000L, latest.fetchedAtEpochMs)
    }

    @Test
    fun getLatest_whenEmpty_returnsNull() = runTest {
        val latest = dao.getLatest()
        assertNull(latest)
    }

    @Test
    fun insertMultiple_getLatest_returnsNewest() = runTest {
        dao.insert(buildEntity(fetchedAtMs = 1000L, city = "Berlin"))
        dao.insert(buildEntity(fetchedAtMs = 5000L, city = "Tokyo"))

        val latest = dao.getLatest()
        assertNotNull(latest)
        assertEquals("Tokyo", latest!!.cityName)
        assertEquals(5000L, latest.fetchedAtEpochMs)
    }

    @Test
    fun observeLatest_emitsLatestAfterInsert() = runTest {
        assertNull(dao.observeLatest().first())

        dao.insert(buildEntity(fetchedAtMs = 2000L, city = "Paris"))
        val observed = dao.observeLatest().first()
        assertNotNull(observed)
        assertEquals("Paris", observed!!.cityName)
    }

    @Test
    fun deleteOlderThan_removesStaleEntries() = runTest {
        dao.insert(buildEntity(fetchedAtMs = 1000L, city = "OldCity"))
        dao.insert(buildEntity(fetchedAtMs = 9000L, city = "NewCity"))

        val deleted = dao.deleteOlderThan(cutoffEpochMs = 5000L)
        assertEquals(1, deleted)

        val latest = dao.getLatest()
        assertNotNull(latest)
        assertEquals("NewCity", latest!!.cityName)
    }

    @Test
    fun deleteAll_clearsTable() = runTest {
        dao.insert(buildEntity(fetchedAtMs = 1000L))
        dao.deleteAll()

        val latest = dao.getLatest()
        assertNull(latest)
    }
}
