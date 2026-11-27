package com.smoh.showmeoutfit.data.repository

import com.smoh.showmeoutfit.core.database.dao.WardrobeDao
import com.smoh.showmeoutfit.core.database.mapper.WardrobeEntityMapper
import com.smoh.showmeoutfit.domain.model.LayerCategory
import com.smoh.showmeoutfit.domain.model.WardrobeItem
import com.smoh.showmeoutfit.domain.repository.WardrobeRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Room-backed wardrobe repository.
 *
 * Room already orders by `createdAtEpochMillis DESC` and re-emits on every write, so the
 * implementation only translates entities to domain models and never caches a list of its own.
 */
class WardrobeRepositoryImpl(
    private val dao: WardrobeDao
) : WardrobeRepository {

    override fun observeAll(): Flow<List<WardrobeItem>> =
        dao.observeAll().map { entities -> entities.map(WardrobeEntityMapper::toDomain) }

    override fun observeByCategory(category: LayerCategory): Flow<List<WardrobeItem>> =
        dao.observeByCategory(category.name)
            .map { entities -> entities.map(WardrobeEntityMapper::toDomain) }

    override suspend fun upsert(item: WardrobeItem) {
        dao.upsert(WardrobeEntityMapper.toEntity(item))
    }

    override suspend fun delete(id: String) {
        dao.deleteById(id)
    }
}
