package com.smoh.showmeoutfit.ui.home

import androidx.annotation.DrawableRes
import com.smoh.showmeoutfit.R
import com.smoh.showmeoutfit.domain.model.AccessoryType
import com.smoh.showmeoutfit.domain.model.GarmentFit
import com.smoh.showmeoutfit.domain.model.GarmentId

/**
 * Maps domain identifiers to the preset multi-colour vector artwork in `res/drawable`.
 *
 * Pure presentation: no `Context`, no `ContextCompat`, no image loading. Each function returns a
 * `@DrawableRes` id that the caller hands to `painterResource`. The flat-illustration pack is a
 * fashion look-book (every garment has its own silhouette and palette), so the outfit grid reads
 * as a flat-lay rather than a row of identical tinted glyphs.
 *
 * The fills inside the vector files are explicit hex colors rather than theme attributes on
 * purpose: the artwork is a photograph substitute, so a garment must look the same in light and
 * dark themes instead of being tinted by the surface color around it.
 *
 * The `when` blocks are exhaustive over the domain enums: adding a `GarmentId` or an
 * `AccessoryType` without artwork is a compile error, not a blank tile at runtime.
 */
object GarmentVisualMapper {

    /**
     * Illustration for a catalog garment.
     *
     * [fit] is accepted but deliberately ignored in v1.1.0: one illustration serves every cut, and
     * the parameter keeps the call shape that Milestone 2 (virtual closet) needs when a user photo
     * or fit-specific cut may override the preset art.
     */
    @Suppress("UNUSED_PARAMETER")
    @DrawableRes
    fun garmentDrawableRes(id: GarmentId, fit: GarmentFit = GarmentFit.UNISEX): Int = when (id) {
        // Base layers
        GarmentId.THERMAL_UNDERSHIRT -> R.drawable.ill_garment_thermal_undershirt
        GarmentId.LONG_SLEEVE_TEE -> R.drawable.ill_garment_long_sleeve_tee
        GarmentId.COTTON_TEE -> R.drawable.ill_garment_cotton_tee
        GarmentId.POLO_SHIRT -> R.drawable.ill_garment_polo_shirt
        GarmentId.LINEN_TEE -> R.drawable.ill_garment_linen_tee
        GarmentId.ULTRA_LIGHT_TANK -> R.drawable.ill_garment_ultra_light_tank

        // Mid layers
        GarmentId.WOOL_SWEATER -> R.drawable.ill_garment_wool_sweater
        GarmentId.KNITTED_SWEATER -> R.drawable.ill_garment_knitted_sweater
        GarmentId.CARDIGAN -> R.drawable.ill_garment_cardigan
        GarmentId.HOODIE -> R.drawable.ill_garment_hoodie
        GarmentId.LIGHT_OVERSHIRT -> R.drawable.ill_garment_light_overshirt

        // Outer layers
        GarmentId.DOWN_PARKA -> R.drawable.ill_garment_down_parka
        GarmentId.WOOL_COAT -> R.drawable.ill_garment_wool_coat
        GarmentId.PUFFER_JACKET -> R.drawable.ill_garment_puffer_jacket
        GarmentId.DENIM_JACKET -> R.drawable.ill_garment_denim_jacket
        GarmentId.LIGHT_JACKET -> R.drawable.ill_garment_light_jacket
        GarmentId.WINDBREAKER -> R.drawable.ill_garment_windbreaker
        GarmentId.RAINCOAT -> R.drawable.ill_garment_raincoat

        // Bottoms
        GarmentId.THERMAL_PANTS -> R.drawable.ill_garment_thermal_pants
        GarmentId.JEANS -> R.drawable.ill_garment_jeans
        GarmentId.CHINOS -> R.drawable.ill_garment_chinos
        GarmentId.TROUSERS -> R.drawable.ill_garment_trousers
        GarmentId.SHORTS -> R.drawable.ill_garment_shorts

        // Footwear
        GarmentId.WINTER_BOOTS -> R.drawable.ill_garment_winter_boots
        GarmentId.LEATHER_BOOTS -> R.drawable.ill_garment_leather_boots
        GarmentId.SNEAKERS -> R.drawable.ill_garment_sneakers
        GarmentId.WATERPROOF_SHOES -> R.drawable.ill_garment_waterproof_shoes
        GarmentId.LOAFERS -> R.drawable.ill_garment_loafers
        GarmentId.SANDALS -> R.drawable.ill_garment_sandals
    }

    /** Illustration for a protective accessory badge. */
    @DrawableRes
    fun accessoryDrawableRes(type: AccessoryType): Int = when (type) {
        AccessoryType.UMBRELLA -> R.drawable.ill_accessory_umbrella
        AccessoryType.SUNGLASSES -> R.drawable.ill_accessory_sunglasses
        AccessoryType.SUN_HAT -> R.drawable.ill_accessory_sun_hat
        AccessoryType.SCARF -> R.drawable.ill_accessory_scarf
        AccessoryType.GLOVES -> R.drawable.ill_accessory_gloves
        AccessoryType.EXTRA_EVENING_LAYER -> R.drawable.ill_accessory_extra_evening_layer
        AccessoryType.SUNSCREEN -> R.drawable.ill_accessory_sunscreen
        AccessoryType.WATER_BOTTLE -> R.drawable.ill_accessory_water_bottle
    }
}
