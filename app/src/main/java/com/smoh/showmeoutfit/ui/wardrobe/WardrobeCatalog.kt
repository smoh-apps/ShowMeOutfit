package com.smoh.showmeoutfit.ui.wardrobe

import com.smoh.showmeoutfit.domain.model.GarmentId
import com.smoh.showmeoutfit.domain.model.LayerCategory

/** Presentation catalog: garment choices always follow the selected wardrobe category. */
object WardrobeCatalog {

    fun garmentsFor(category: LayerCategory): List<GarmentId> =
        garmentsByCategory.getValue(category)

    fun categoryFor(garmentId: GarmentId): LayerCategory? = categoryByGarment[garmentId]

    private val categoryByGarment: Map<GarmentId, LayerCategory> = buildMap {
        GarmentId.entries.forEach { id ->
            put(
                id,
                when (id) {
                    GarmentId.THERMAL_UNDERSHIRT,
                    GarmentId.LONG_SLEEVE_TEE,
                    GarmentId.COTTON_TEE,
                    GarmentId.POLO_SHIRT,
                    GarmentId.LINEN_TEE,
                    GarmentId.ULTRA_LIGHT_TANK -> LayerCategory.BASE

                    GarmentId.WOOL_SWEATER,
                    GarmentId.KNITTED_SWEATER,
                    GarmentId.CARDIGAN,
                    GarmentId.HOODIE,
                    GarmentId.LIGHT_OVERSHIRT -> LayerCategory.MID

                    GarmentId.DOWN_PARKA,
                    GarmentId.WOOL_COAT,
                    GarmentId.PUFFER_JACKET,
                    GarmentId.DENIM_JACKET,
                    GarmentId.LIGHT_JACKET,
                    GarmentId.WINDBREAKER,
                    GarmentId.RAINCOAT -> LayerCategory.OUTER

                    GarmentId.THERMAL_PANTS,
                    GarmentId.JEANS,
                    GarmentId.CHINOS,
                    GarmentId.TROUSERS,
                    GarmentId.SHORTS -> LayerCategory.BOTTOM

                    GarmentId.WINTER_BOOTS,
                    GarmentId.LEATHER_BOOTS,
                    GarmentId.SNEAKERS,
                    GarmentId.WATERPROOF_SHOES,
                    GarmentId.LOAFERS,
                    GarmentId.SANDALS -> LayerCategory.FOOTWEAR
                }
            )
        }
    }

    private val garmentsByCategory: Map<LayerCategory, List<GarmentId>> =
        LayerCategory.entries.associateWith { category ->
            GarmentId.entries.filter { categoryByGarment[it] == category }
        }
}
