package com.smoh.showmeoutfit.domain.model

import java.time.LocalTime

enum class LayerCategory {
    BASE,
    MID,
    OUTER,
    BOTTOM,
    FOOTWEAR
}

/**
 * Stable identifier for a catalog garment.
 *
 * The domain never carries display copy: the presentation layer maps each id (and optional
 * [GarmentFit]) to a localized name, description, and artwork.
 */
enum class GarmentId {
    // Base layers
    THERMAL_UNDERSHIRT,
    LONG_SLEEVE_TEE,
    COTTON_TEE,
    POLO_SHIRT,
    LINEN_TEE,
    ULTRA_LIGHT_TANK,

    // Mid layers
    WOOL_SWEATER,
    KNITTED_SWEATER,
    CARDIGAN,
    HOODIE,
    LIGHT_OVERSHIRT,

    // Outer layers
    DOWN_PARKA,
    WOOL_COAT,
    PUFFER_JACKET,
    DENIM_JACKET,
    LIGHT_JACKET,
    WINDBREAKER,
    RAINCOAT,

    // Bottoms
    THERMAL_PANTS,
    JEANS,
    CHINOS,
    TROUSERS,
    SHORTS,

    // Footwear
    WINTER_BOOTS,
    LEATHER_BOOTS,
    SNEAKERS,
    WATERPROOF_SHOES,
    LOAFERS,
    SANDALS
}

/**
 * Fit/variant of a garment.
 *
 * Fit is a catalog concept only. It changes which label and cut the UI presents; it never
 * changes thermal thresholds, layering rules, or accessory priorities.
 */
enum class GarmentFit {
    UNISEX,
    MEN,
    WOMEN
}

data class Garment(
    val id: GarmentId,
    val category: LayerCategory,
    val fit: GarmentFit = GarmentFit.UNISEX
)

enum class AccessoryType {
    UMBRELLA,
    SUNGLASSES,
    SUN_HAT,
    SCARF,
    GLOVES,
    EXTRA_EVENING_LAYER,
    SUNSCREEN
}

enum class PriorityLevel {
    CRITICAL,
    RECOMMENDED,
    OPTIONAL
}

/**
 * Why an accessory was recommended, expressed as data.
 *
 * The UI turns each variant into a localized sentence (and formats times/numbers itself), so
 * the domain stays free of `String.format`, `Locale`, and date formatting.
 */
sealed interface AccessoryReason {
    data class RainWindow(val start: LocalTime, val end: LocalTime) : AccessoryReason
    data class RainProbability(val peakPercent: Int) : AccessoryReason
    data object FreezingConditions : AccessoryReason
    data class Wind(val speedKmh: Int) : AccessoryReason
    data class UvIndex(val peakUv: Double) : AccessoryReason
    data class DiurnalSwing(val deltaCelsius: Double) : AccessoryReason
}

data class AccessoryAlert(
    val type: AccessoryType,
    val reason: AccessoryReason,
    val priority: PriorityLevel
)

/**
 * Headline category for the recommendation. The UI maps each variant to localized copy.
 */
sealed interface HeadlineReason {
    data object SnowAndFrost : HeadlineReason
    data class LargeTempSwing(val deltaCelsius: Double) : HeadlineReason
    data object RainExpected : HeadlineReason
    data object HotAndIntenseUv : HeadlineReason
    data object WarmAndPleasant : HeadlineReason
    data object Chilly : HeadlineReason
    data object Mild : HeadlineReason
}

/**
 * Numeric inputs the UI needs to compose the daily summary sentence.
 */
data class SummaryData(
    val tempMinCelsius: Double,
    val tempMaxCelsius: Double,
    val hasLargeTempSwing: Boolean,
    val rainReason: AccessoryReason?,
    val peakUvIndex: Double
)

data class OutfitRecommendation(
    val headline: HeadlineReason,
    val summary: SummaryData,
    val baseLayer: Garment,
    val midLayer: Garment?,
    val outerLayer: Garment?,
    val bottomLayer: Garment,
    val footwear: Garment,
    val accessories: List<AccessoryAlert>,
    val weatherHighlights: WeatherHighlights
)
