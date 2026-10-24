package com.smoh.showmeoutfit.domain.engine

import com.smoh.showmeoutfit.domain.model.AccessoryAlert
import com.smoh.showmeoutfit.domain.model.AccessoryType
import com.smoh.showmeoutfit.domain.model.Garment
import com.smoh.showmeoutfit.domain.model.HourlyCondition
import com.smoh.showmeoutfit.domain.model.OutfitRecommendation
import com.smoh.showmeoutfit.domain.model.PriorityLevel
import com.smoh.showmeoutfit.domain.model.UserProfile
import com.smoh.showmeoutfit.domain.model.WeatherHighlights
import com.smoh.showmeoutfit.domain.model.WeatherSnapshot
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

class RuleBasedPresetEngine : IOutfitRecommendationEngine {

    override fun recommend(
        weather: WeatherSnapshot,
        profile: UserProfile
    ): OutfitRecommendation {
        val daytimeHours = filterDaytimeHours(weather.hourlyForecast)
        val sensitivityOffset = profile.thermalSensitivity.tempOffsetCelsius

        // Effective temperatures applied per hour
        val effectiveHourly = daytimeHours.map { hourly ->
            val effTemp = hourly.temperatureCelsius - sensitivityOffset
            val effApparent = hourly.apparentTemperatureCelsius - sensitivityOffset
            hourly to effApparent
        }

        val effTemperatures = effectiveHourly.map { it.second }
        val minEffTemp = effTemperatures.minOrNull() ?: (weather.current.apparentTemperatureCelsius - sensitivityOffset)
        val maxEffTemp = effTemperatures.maxOrNull() ?: (weather.current.apparentTemperatureCelsius - sensitivityOffset)
        val deltaT = maxEffTemp - minEffTemp
        val hasLargeTempSwing = deltaT >= 7.0 || (minEffTemp < 14.0 && maxEffTemp >= 16.0)

        val maxPrecipProb = daytimeHours.maxOfOrNull { it.precipitationProbability } ?: weather.dailyForecast.maxPrecipitationProbability
        val hasRainCode = daytimeHours.any { it.weatherCode.isRain } || weather.dailyForecast.weatherCode.isRain
        val hasSnowCode = daytimeHours.any { it.weatherCode.isSnow } || weather.dailyForecast.weatherCode.isSnow
        val hasRainSpike = maxPrecipProb >= 35 || hasRainCode
        val maxUv = daytimeHours.maxOfOrNull { it.uvIndex } ?: 0.0
        val maxWind = daytimeHours.maxOfOrNull { it.windSpeedKmh } ?: weather.current.windSpeedKmh

        val highlights = WeatherHighlights(
            tempMinCelsius = minEffTemp,
            tempMaxCelsius = maxEffTemp,
            deltaTCelsius = deltaT,
            maxPrecipitationProb = maxPrecipProb,
            maxUvIndex = maxUv,
            maxWindSpeedKmh = maxWind,
            hasRainSpike = hasRainSpike,
            hasLargeTempSwing = hasLargeTempSwing
        )

        // Determine Garments
        val (baseLayer, midLayer, outerLayer, bottomLayer, footwear) = selectLayers(
            minEffTemp = minEffTemp,
            maxEffTemp = maxEffTemp,
            hasLargeTempSwing = hasLargeTempSwing,
            maxWind = maxWind,
            hasRain = hasRainSpike,
            hasSnow = hasSnowCode
        )

        // Determine Accessories
        val accessories = selectAccessories(
            daytimeHours = daytimeHours,
            minEffTemp = minEffTemp,
            maxEffTemp = maxEffTemp,
            deltaT = deltaT,
            hasLargeTempSwing = hasLargeTempSwing,
            maxPrecipProb = maxPrecipProb,
            hasRainCode = hasRainCode,
            hasSnow = hasSnowCode,
            maxUv = maxUv,
            maxWind = maxWind
        )

        // Headlines and description
        val (headline, summary) = buildSummary(
            highlights = highlights,
            accessories = accessories,
            hasSnow = hasSnowCode
        )

        return OutfitRecommendation(
            headline = headline,
            summaryDescription = summary,
            baseLayer = baseLayer,
            midLayer = midLayer,
            outerLayer = outerLayer,
            bottomLayer = bottomLayer,
            footwear = footwear,
            accessories = accessories,
            weatherHighlights = highlights
        )
    }

    private fun filterDaytimeHours(hourlyForecast: List<HourlyCondition>): List<HourlyCondition> {
        val daytime = hourlyForecast.filter { it.time.hour in 7..22 }
        return if (daytime.isNotEmpty()) daytime else hourlyForecast
    }

    private fun selectLayers(
        minEffTemp: Double,
        maxEffTemp: Double,
        hasLargeTempSwing: Boolean,
        maxWind: Double,
        hasRain: Boolean,
        hasSnow: Boolean
    ): LayerSelection {
        return when {
            maxEffTemp < 0.0 || hasSnow -> {
                LayerSelection(
                    base = GarmentCatalog.THERMAL_UNDERSHIRT,
                    mid = GarmentCatalog.WOOL_SWEATER,
                    outer = GarmentCatalog.DOWN_PARKA,
                    bottom = GarmentCatalog.THERMAL_PANTS,
                    footwear = GarmentCatalog.WINTER_BOOTS
                )
            }
            maxEffTemp < 10.0 -> {
                LayerSelection(
                    base = GarmentCatalog.LONG_SLEEVE_TEE,
                    mid = GarmentCatalog.KNITTED_SWEATER,
                    outer = if (maxWind >= 35.0) GarmentCatalog.WINDBREAKER else GarmentCatalog.WOOL_COAT,
                    bottom = GarmentCatalog.JEANS,
                    footwear = if (hasRain) GarmentCatalog.WATERPROOF_SHOES else GarmentCatalog.LEATHER_BOOTS
                )
            }
            maxEffTemp < 16.0 -> {
                LayerSelection(
                    base = GarmentCatalog.COTTON_TEE,
                    mid = if (hasLargeTempSwing) GarmentCatalog.CARDIGAN else GarmentCatalog.HOODIE,
                    outer = if (maxWind >= 35.0) GarmentCatalog.WINDBREAKER else GarmentCatalog.LIGHT_JACKET,
                    bottom = GarmentCatalog.TROUSERS,
                    footwear = if (hasRain) GarmentCatalog.WATERPROOF_SHOES else GarmentCatalog.SNEAKERS
                )
            }
            maxEffTemp < 22.0 -> {
                LayerSelection(
                    base = GarmentCatalog.COTTON_TEE,
                    mid = if (hasLargeTempSwing) GarmentCatalog.CARDIGAN else null,
                    outer = if (maxWind >= 35.0) GarmentCatalog.WINDBREAKER else if (minEffTemp < 12.0) GarmentCatalog.LIGHT_JACKET else null,
                    bottom = GarmentCatalog.CHINOS,
                    footwear = if (hasRain) GarmentCatalog.WATERPROOF_SHOES else GarmentCatalog.SNEAKERS
                )
            }
            maxEffTemp < 28.0 -> {
                LayerSelection(
                    base = GarmentCatalog.LINEN_TEE,
                    mid = if (hasLargeTempSwing) GarmentCatalog.CARDIGAN else null,
                    outer = if (maxWind >= 35.0) GarmentCatalog.WINDBREAKER else null,
                    bottom = GarmentCatalog.SHORTS,
                    footwear = if (hasRain) GarmentCatalog.WATERPROOF_SHOES else GarmentCatalog.LOAFERS
                )
            }
            else -> {
                // Hot > 28°C
                LayerSelection(
                    base = GarmentCatalog.ULTRA_LIGHT_TANK,
                    mid = null,
                    outer = null,
                    bottom = GarmentCatalog.SHORTS,
                    footwear = GarmentCatalog.SANDALS
                )
            }
        }
    }

    private fun selectAccessories(
        daytimeHours: List<HourlyCondition>,
        minEffTemp: Double,
        maxEffTemp: Double,
        deltaT: Double,
        hasLargeTempSwing: Boolean,
        maxPrecipProb: Int,
        hasRainCode: Boolean,
        hasSnow: Boolean,
        maxUv: Double,
        maxWind: Double
    ): List<AccessoryAlert> {
        val alerts = mutableListOf<AccessoryAlert>()

        // 1. Rain / Precipitation Alert
        if (maxPrecipProb >= 35 || hasRainCode) {
            val rainWindow = findRainWindow(daytimeHours)
            val reason = if (rainWindow != null) {
                "Rain expected between ${rainWindow.first} – ${rainWindow.second}"
            } else {
                "Precipitation probability reaches $maxPrecipProb%"
            }
            val priority = if (maxPrecipProb >= 60 || hasRainCode) PriorityLevel.CRITICAL else PriorityLevel.RECOMMENDED
            alerts.add(
                AccessoryAlert(
                    type = AccessoryType.UMBRELLA,
                    name = "Compact Umbrella",
                    reason = reason,
                    priority = priority
                )
            )
        }

        // 2. Snow / Extreme Cold
        if (hasSnow || minEffTemp <= 0.0) {
            alerts.add(
                AccessoryAlert(
                    type = AccessoryType.GLOVES,
                    name = "Thermal Gloves",
                    reason = "Sub-zero or freezing conditions expected",
                    priority = PriorityLevel.CRITICAL
                )
            )
            alerts.add(
                AccessoryAlert(
                    type = AccessoryType.SCARF,
                    name = "Heavy Winter Scarf",
                    reason = "Protects against biting cold air",
                    priority = PriorityLevel.CRITICAL
                )
            )
        } else if (maxWind >= 35.0 && maxEffTemp < 15.0) {
            alerts.add(
                AccessoryAlert(
                    type = AccessoryType.SCARF,
                    name = "Wind Scarf",
                    reason = "Gusty winds reaching ${maxWind.toInt()} km/h",
                    priority = PriorityLevel.RECOMMENDED
                )
            )
        }

        // 3. UV Sun Protection
        if (maxUv >= 3.0) {
            alerts.add(
                AccessoryAlert(
                    type = AccessoryType.SUNGLASSES,
                    name = "UV Sunglasses",
                    reason = "Peak UV index reaches ${String.format(Locale.US, "%.1f", maxUv)}",
                    priority = if (maxUv >= 6.0) PriorityLevel.CRITICAL else PriorityLevel.RECOMMENDED
                )
            )
            alerts.add(
                AccessoryAlert(
                    type = AccessoryType.SUNSCREEN,
                    name = "Sunscreen SPF 30+",
                    reason = "UV protection recommended during peak afternoon hours",
                    priority = PriorityLevel.RECOMMENDED
                )
            )
        }
        if (maxUv >= 6.0) {
            alerts.add(
                AccessoryAlert(
                    type = AccessoryType.SUN_HAT,
                    name = "Wide-Brim Sun Hat",
                    reason = "Very high UV radiation (${String.format(Locale.US, "%.1f", maxUv)}) requires head shade",
                    priority = PriorityLevel.CRITICAL
                )
            )
        }

        // 4. Diurnal Temperature Swing
        if (hasLargeTempSwing) {
            alerts.add(
                AccessoryAlert(
                    type = AccessoryType.EXTRA_EVENING_LAYER,
                    name = "Removable Layer",
                    reason = "Large diurnal swing of ${String.format(Locale.US, "%.1f", deltaT)}°C between day and night",
                    priority = PriorityLevel.RECOMMENDED
                )
            )
        }

        return alerts
    }

    private fun findRainWindow(hours: List<HourlyCondition>): Pair<String, String>? {
        val rainHours = hours.filter { it.precipitationProbability >= 35 || it.weatherCode.isRain }
        if (rainHours.isEmpty()) return null

        val start = rainHours.first().time.format(DateTimeFormatter.ofPattern("HH:mm"))
        val end = rainHours.last().time.format(DateTimeFormatter.ofPattern("HH:mm"))
        return if (start == end) {
            start to "${rainHours.first().time.plusHours(1).format(DateTimeFormatter.ofPattern("HH:mm"))}"
        } else {
            start to end
        }
    }

    private fun buildSummary(
        highlights: WeatherHighlights,
        accessories: List<AccessoryAlert>,
        hasSnow: Boolean
    ): Pair<String, String> {
        val headline = when {
            hasSnow -> "Sub-Zero Snow & Frost Alert"
            highlights.hasLargeTempSwing -> "Large Diurnal Temperature Swing (${String.format(Locale.US, "%.1f", highlights.deltaTCelsius)}°C)"
            highlights.hasRainSpike -> "Rain & Showers Expected Today"
            highlights.maxUvIndex >= 6.0 -> "Hot & Intense UV Day"
            highlights.tempMaxCelsius >= 24.0 -> "Warm and Pleasant Weather"
            highlights.tempMaxCelsius < 10.0 -> "Chilly Day - Layer Up"
            else -> "Mild Conditions Ahead"
        }

        val summary = buildString {
            append("Temperatures span from ${String.format(Locale.US, "%.1f", highlights.tempMinCelsius)}°C to ${String.format(Locale.US, "%.1f", highlights.tempMaxCelsius)}°C. ")
            if (highlights.hasLargeTempSwing) {
                append("Significant temperature shift anticipated; keep a removable layer handy. ")
            }
            if (highlights.hasRainSpike) {
                val rain = accessories.find { it.type == AccessoryType.UMBRELLA }
                if (rain != null) append("${rain.reason}. ")
            }
            if (highlights.maxUvIndex >= 3.0) {
                append("UV index peaks at ${String.format(Locale.US, "%.1f", highlights.maxUvIndex)}. ")
            }
        }

        return headline to summary.trim()
    }

    private data class LayerSelection(
        val base: Garment,
        val mid: Garment?,
        val outer: Garment?,
        val bottom: Garment,
        val footwear: Garment
    )
}
