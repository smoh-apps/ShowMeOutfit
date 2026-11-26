package com.smoh.showmeoutfit.ui.home

import androidx.annotation.DrawableRes
import com.smoh.showmeoutfit.R
import com.smoh.showmeoutfit.domain.model.AccessoryType
import com.smoh.showmeoutfit.domain.model.GarmentFit
import com.smoh.showmeoutfit.domain.model.GarmentId

/** Maps catalog identifiers to the v2 studio cutout pack. */
object GarmentVisualMapper {

    fun garmentVisualSource(
        id: GarmentId,
        fit: GarmentFit = GarmentFit.UNISEX
    ): GarmentVisualSource = GarmentVisualSource.Preset(garmentDrawableRes(id, fit))

    @DrawableRes
    fun garmentDrawableRes(
        id: GarmentId,
        fit: GarmentFit = GarmentFit.UNISEX
    ): Int {
        if (fit == GarmentFit.WOMEN) {
            womenDrawableRes(id)?.let { return it }
        }
        return when (id) {
            GarmentId.THERMAL_UNDERSHIRT -> R.drawable.cutout_thermal_undershirt
            GarmentId.LONG_SLEEVE_TEE -> R.drawable.cutout_long_sleeve_tee
            GarmentId.COTTON_TEE -> R.drawable.cutout_cotton_tee
            GarmentId.POLO_SHIRT -> R.drawable.cutout_polo_shirt
            GarmentId.LINEN_TEE -> R.drawable.cutout_linen_tee
            GarmentId.ULTRA_LIGHT_TANK -> R.drawable.cutout_ultra_light_tank
            GarmentId.WOOL_SWEATER -> R.drawable.cutout_wool_sweater
            GarmentId.KNITTED_SWEATER -> R.drawable.cutout_knitted_sweater
            GarmentId.CARDIGAN -> R.drawable.cutout_cardigan
            GarmentId.HOODIE -> R.drawable.cutout_hoodie
            GarmentId.LIGHT_OVERSHIRT -> R.drawable.cutout_light_overshirt
            GarmentId.DOWN_PARKA -> R.drawable.cutout_down_parka
            GarmentId.WOOL_COAT -> R.drawable.cutout_wool_coat
            GarmentId.PUFFER_JACKET -> R.drawable.cutout_puffer_jacket
            GarmentId.DENIM_JACKET -> R.drawable.cutout_denim_jacket
            GarmentId.LIGHT_JACKET -> R.drawable.cutout_light_jacket
            GarmentId.WINDBREAKER -> R.drawable.cutout_windbreaker
            GarmentId.RAINCOAT -> R.drawable.cutout_raincoat
            GarmentId.THERMAL_PANTS -> R.drawable.cutout_thermal_pants
            GarmentId.JEANS -> R.drawable.cutout_jeans
            GarmentId.CHINOS -> R.drawable.cutout_chinos
            GarmentId.TROUSERS -> R.drawable.cutout_trousers
            GarmentId.SHORTS -> R.drawable.cutout_shorts
            GarmentId.WINTER_BOOTS -> R.drawable.cutout_winter_boots
            GarmentId.LEATHER_BOOTS -> R.drawable.cutout_leather_boots
            GarmentId.SNEAKERS -> R.drawable.cutout_sneakers
            GarmentId.WATERPROOF_SHOES -> R.drawable.cutout_waterproof_shoes
            GarmentId.LOAFERS -> R.drawable.cutout_loafers
            GarmentId.SANDALS -> R.drawable.cutout_sandals
        }
    }

    fun accessoryVisualSource(type: AccessoryType): GarmentVisualSource =
        GarmentVisualSource.Preset(accessoryDrawableRes(type))

    @DrawableRes
    fun accessoryDrawableRes(type: AccessoryType): Int = when (type) {
        AccessoryType.UMBRELLA -> R.drawable.cutout_accessory_umbrella
        AccessoryType.SUNGLASSES -> R.drawable.cutout_accessory_sunglasses
        AccessoryType.SUN_HAT -> R.drawable.cutout_accessory_sun_hat
        AccessoryType.SCARF -> R.drawable.cutout_accessory_scarf
        AccessoryType.GLOVES -> R.drawable.cutout_accessory_gloves
        AccessoryType.EXTRA_EVENING_LAYER -> R.drawable.cutout_accessory_extra_evening_layer
        AccessoryType.SUNSCREEN -> R.drawable.cutout_accessory_sunscreen
        AccessoryType.WATER_BOTTLE -> R.drawable.cutout_accessory_water_bottle
    }

    @DrawableRes
    private fun womenDrawableRes(id: GarmentId): Int? = when (id) {
        GarmentId.COTTON_TEE -> R.drawable.cutout_cotton_tee_women
        GarmentId.POLO_SHIRT -> R.drawable.cutout_polo_shirt_women
        GarmentId.CARDIGAN -> R.drawable.cutout_cardigan_women
        GarmentId.HOODIE -> R.drawable.cutout_hoodie_women
        GarmentId.JEANS -> R.drawable.cutout_jeans_women
        GarmentId.CHINOS -> R.drawable.cutout_chinos_women
        GarmentId.TROUSERS -> R.drawable.cutout_trousers_women
        GarmentId.SHORTS -> R.drawable.cutout_shorts_women
        else -> null
    }
}
