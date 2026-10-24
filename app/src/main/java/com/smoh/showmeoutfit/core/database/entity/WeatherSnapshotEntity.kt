package com.smoh.showmeoutfit.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "weather_snapshots")
data class WeatherSnapshotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fetchedAtEpochMs: Long,
    val latitude: Double,
    val longitude: Double,
    val cityName: String,
    val country: String?,
    val timezone: String?,
    val currentTempCelsius: Double,
    val currentApparentTempCelsius: Double,
    val currentHumidityPercent: Int,
    val currentWeatherCode: Int,
    val currentWindSpeedKmh: Double,
    val currentTimeIso: String,
    val dailyMaxTempCelsius: Double,
    val dailyMinTempCelsius: Double,
    val dailyMaxPrecipProb: Int,
    val dailyWeatherCode: Int,
    val dailyDateIso: String,
    // Hourly data stored as serialized JSON string
    val hourlyJson: String
)
