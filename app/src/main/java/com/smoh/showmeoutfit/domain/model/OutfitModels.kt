package com.smoh.showmeoutfit.domain.model

enum class LayerCategory {
    BASE,
    MID,
    OUTER,
    BOTTOM,
    FOOTWEAR
}

data class Garment(
    val id: String,
    val name: String,
    val category: LayerCategory,
    val description: String,
    val iconRes: String? = null
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

data class AccessoryAlert(
    val type: AccessoryType,
    val name: String,
    val reason: String,
    val priority: PriorityLevel
)

data class OutfitRecommendation(
    val headline: String,
    val summaryDescription: String,
    val baseLayer: Garment,
    val midLayer: Garment?,
    val outerLayer: Garment?,
    val bottomLayer: Garment,
    val footwear: Garment,
    val accessories: List<AccessoryAlert>,
    val weatherHighlights: WeatherHighlights
)
