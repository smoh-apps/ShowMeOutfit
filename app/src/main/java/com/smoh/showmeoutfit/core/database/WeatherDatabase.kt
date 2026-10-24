package com.smoh.showmeoutfit.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.smoh.showmeoutfit.core.database.dao.WeatherSnapshotDao
import com.smoh.showmeoutfit.core.database.entity.WeatherSnapshotEntity

@Database(
    entities = [WeatherSnapshotEntity::class],
    version = 1,
    exportSchema = false
)
abstract class WeatherDatabase : RoomDatabase() {
    abstract fun weatherSnapshotDao(): WeatherSnapshotDao

    companion object {
        const val DATABASE_NAME = "weather_db"
    }
}
