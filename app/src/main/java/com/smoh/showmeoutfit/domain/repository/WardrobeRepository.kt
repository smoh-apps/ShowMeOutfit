package com.smoh.showmeoutfit.domain.repository

import com.smoh.showmeoutfit.domain.model.LayerCategory
import com.smoh.showmeoutfit.domain.model.WardrobeItem
import kotlinx.coroutines.flow.Flow

/**
 * User-owned closet.
 *
 * Everything is exposed as a [Flow] so the UI stays subscribed to Room and re-renders after an
 * insert, update, or delete without an explicit refresh call.
 */
interface WardrobeRepository {

    /** Every wardrobe item, newest first. */
    fun observeAll(): Flow<List<WardrobeItem>>

    /** Only the items in [category], newest first. */
    fun observeByCategory(category: LayerCategory): Flow<List<WardrobeItem>>

    /** Inserts a new item or replaces the item with the same [WardrobeItem.id]. */
    suspend fun upsert(item: WardrobeItem)

    /** Removes the item with [id]; a missing id is a no-op. */
    suspend fun delete(id: String)
}
