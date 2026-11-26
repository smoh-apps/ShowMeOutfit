package com.smoh.showmeoutfit.domain.model

/**
 * A single garment the user owns.
 *
 * Wardrobe items are user data, not catalog data: [garmentId] links an item back to the catalog
 * preset (which drives artwork and localized copy), while [name] is the user's own label and
 * [photoRelativePath] points at an on-device capture when one exists.
 *
 * Pure Kotlin on purpose: the engine can read the wardrobe on a plain JVM without any Android
 * dependency.
 */
data class WardrobeItem(
    val id: String,
    val name: String,
    val category: LayerCategory,
    val garmentId: GarmentId,
    val fit: GarmentFit = GarmentFit.UNISEX,
    val isWaterResistant: Boolean = false,
    val photoRelativePath: String? = null,
    val createdAtEpochMillis: Long
)
