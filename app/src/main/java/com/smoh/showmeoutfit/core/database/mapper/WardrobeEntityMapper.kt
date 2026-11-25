package com.smoh.showmeoutfit.core.database.mapper

import com.smoh.showmeoutfit.core.database.entity.WardrobeEntity
import com.smoh.showmeoutfit.domain.model.GarmentFit
import com.smoh.showmeoutfit.domain.model.GarmentId
import com.smoh.showmeoutfit.domain.model.LayerCategory
import com.smoh.showmeoutfit.domain.model.WardrobeItem

/**
 * Entity <-> domain mapping for the wardrobe.
 *
 * Enum values are written as `name` and read with a null-safe fallback, so a row persisted by a
 * newer build (unknown enum constant) degrades to the catalog default instead of crashing the
 * closet screen.
 */
object WardrobeEntityMapper {

    fun toEntity(item: WardrobeItem): WardrobeEntity = WardrobeEntity(
        id = item.id,
        name = item.name,
        category = item.category.name,
        garmentId = item.garmentId.name,
        fit = item.fit.name,
        isWaterResistant = item.isWaterResistant,
        photoRelativePath = item.photoRelativePath,
        createdAtEpochMillis = item.createdAtEpochMillis
    )

    fun toDomain(entity: WardrobeEntity): WardrobeItem = WardrobeItem(
        id = entity.id,
        name = entity.name,
        category = enumValueOrNull<LayerCategory>(entity.category) ?: LayerCategory.BASE,
        garmentId = enumValueOrNull<GarmentId>(entity.garmentId) ?: GarmentId.COTTON_TEE,
        fit = enumValueOrNull<GarmentFit>(entity.fit) ?: GarmentFit.UNISEX,
        isWaterResistant = entity.isWaterResistant,
        photoRelativePath = entity.photoRelativePath,
        createdAtEpochMillis = entity.createdAtEpochMillis
    )

    private inline fun <reified T : Enum<T>> enumValueOrNull(name: String): T? =
        enumValues<T>().firstOrNull { it.name == name }
}
