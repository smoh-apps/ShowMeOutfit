package com.smoh.showmeoutfit.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.smoh.showmeoutfit.core.database.entity.WeatherSnapshotEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WeatherSnapshotDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: WeatherSnapshotEntity): Long

    @Query("SELECT * FROM weather_snapshots ORDER BY fetchedAtEpochMs DESC LIMIT 1")
    suspend fun getLatest(): WeatherSnapshotEntity?

    @Query("SELECT * FROM weather_snapshots ORDER BY fetchedAtEpochMs DESC LIMIT 1")
    fun observeLatest(): Flow<WeatherSnapshotEntity?>

    @Query("DELETE FROM weather_snapshots WHERE fetchedAtEpochMs < :cutoffEpochMs")
    suspend fun deleteOlderThan(cutoffEpochMs: Long): Int

    @Query("DELETE FROM weather_snapshots")
    suspend fun deleteAll(): Int
}
