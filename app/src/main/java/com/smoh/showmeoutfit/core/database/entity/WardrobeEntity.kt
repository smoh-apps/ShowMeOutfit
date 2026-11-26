package com.smoh.showmeoutfit.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room mirror of a user wardrobe item.
 *
 * Enums are persisted as their `name` strings so the storage stays readable and a future enum
 * reorder cannot silently remap rows. Mapping lives in `WardrobeEntityMapper`.
 */
@Entity(
    tableName = "wardrobe_items",
    indices = [Index("category")]
)
data class WardrobeEntity(
    @PrimaryKey val id: String,
    val name: String,
    val category: String,
    val garmentId: String,
    val fit: String,
    val isWaterResistant: Boolean,
    val photoRelativePath: String?,
    val createdAtEpochMillis: Long
)
