package com.smoh.showmeoutfit.domain.engine

import com.smoh.showmeoutfit.domain.model.Garment
import com.smoh.showmeoutfit.domain.model.GarmentFit
import com.smoh.showmeoutfit.domain.model.GarmentId
import com.smoh.showmeoutfit.domain.model.LayerCategory

/**
 * Static catalog of garments the engine can recommend.
 *
 * Entries carry ids only. Names, descriptions, and artwork live in the presentation layer,
 * keyed by [GarmentId] and optionally [GarmentFit].
 */
object GarmentCatalog {
    // Base layers
    val THERMAL_UNDERSHIRT = garment(GarmentId.THERMAL_UNDERSHIRT, LayerCategory.BASE)
    val LONG_SLEEVE_TEE = garment(GarmentId.LONG_SLEEVE_TEE, LayerCategory.BASE)
    val COTTON_TEE = garment(GarmentId.COTTON_TEE, LayerCategory.BASE)
    val POLO_SHIRT = garment(GarmentId.POLO_SHIRT, LayerCategory.BASE)
    val LINEN_TEE = garment(GarmentId.LINEN_TEE, LayerCategory.BASE)
    val ULTRA_LIGHT_TANK = garment(GarmentId.ULTRA_LIGHT_TANK, LayerCategory.BASE)

    // Mid layers
    val WOOL_SWEATER = garment(GarmentId.WOOL_SWEATER, LayerCategory.MID)
    val KNITTED_SWEATER = garment(GarmentId.KNITTED_SWEATER, LayerCategory.MID)
    val CARDIGAN = garment(GarmentId.CARDIGAN, LayerCategory.MID)
    val HOODIE = garment(GarmentId.HOODIE, LayerCategory.MID)
    val LIGHT_OVERSHIRT = garment(GarmentId.LIGHT_OVERSHIRT, LayerCategory.MID)

    // Outer layers
    val DOWN_PARKA = garment(GarmentId.DOWN_PARKA, LayerCategory.OUTER)
    val WOOL_COAT = garment(GarmentId.WOOL_COAT, LayerCategory.OUTER)
    val PUFFER_JACKET = garment(GarmentId.PUFFER_JACKET, LayerCategory.OUTER)
    val DENIM_JACKET = garment(GarmentId.DENIM_JACKET, LayerCategory.OUTER)
    val LIGHT_JACKET = garment(GarmentId.LIGHT_JACKET, LayerCategory.OUTER)
    val WINDBREAKER = garment(GarmentId.WINDBREAKER, LayerCategory.OUTER)
    val RAINCOAT = garment(GarmentId.RAINCOAT, LayerCategory.OUTER)

    // Bottoms
    val THERMAL_PANTS = garment(GarmentId.THERMAL_PANTS, LayerCategory.BOTTOM)
    val JEANS = garment(GarmentId.JEANS, LayerCategory.BOTTOM)
    val CHINOS = garment(GarmentId.CHINOS, LayerCategory.BOTTOM)
    val TROUSERS = garment(GarmentId.TROUSERS, LayerCategory.BOTTOM)
    val SHORTS = garment(GarmentId.SHORTS, LayerCategory.BOTTOM)

    // Footwear
    val WINTER_BOOTS = garment(GarmentId.WINTER_BOOTS, LayerCategory.FOOTWEAR)
    val LEATHER_BOOTS = garment(GarmentId.LEATHER_BOOTS, LayerCategory.FOOTWEAR)
    val SNEAKERS = garment(GarmentId.SNEAKERS, LayerCategory.FOOTWEAR)
    val WATERPROOF_SHOES = garment(GarmentId.WATERPROOF_SHOES, LayerCategory.FOOTWEAR)
    val LOAFERS = garment(GarmentId.LOAFERS, LayerCategory.FOOTWEAR)
    val SANDALS = garment(GarmentId.SANDALS, LayerCategory.FOOTWEAR)

    private fun garment(id: GarmentId, category: LayerCategory) =
        Garment(id = id, category = category)
}
