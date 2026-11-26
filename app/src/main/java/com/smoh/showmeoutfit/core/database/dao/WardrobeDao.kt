package com.smoh.showmeoutfit.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.smoh.showmeoutfit.core.database.entity.WardrobeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WardrobeDao {

    @Query("SELECT * FROM wardrobe_items ORDER BY createdAtEpochMillis DESC")
    fun observeAll(): Flow<List<WardrobeEntity>>

    @Query(
        "SELECT * FROM wardrobe_items WHERE category = :category " +
            "ORDER BY createdAtEpochMillis DESC"
    )
    fun observeByCategory(category: String): Flow<List<WardrobeEntity>>

    @Upsert
    suspend fun upsert(entity: WardrobeEntity)

    @Query("DELETE FROM wardrobe_items WHERE id = :id")
    suspend fun deleteById(id: String)
}
