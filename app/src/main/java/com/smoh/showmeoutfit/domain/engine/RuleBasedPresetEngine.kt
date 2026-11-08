package com.smoh.showmeoutfit.domain.engine

import com.smoh.showmeoutfit.domain.model.AccessoryAlert
import com.smoh.showmeoutfit.domain.model.AccessoryReason
import com.smoh.showmeoutfit.domain.model.AccessoryType
import com.smoh.showmeoutfit.domain.model.AgeBand
import com.smoh.showmeoutfit.domain.model.Garment
import com.smoh.showmeoutfit.domain.model.GarmentFit
import com.smoh.showmeoutfit.domain.model.HeadlineReason
import com.smoh.showmeoutfit.domain.model.HourlyCondition
import com.smoh.showmeoutfit.domain.model.OutfitRecommendation
import com.smoh.showmeoutfit.domain.model.PriorityLevel
import com.smoh.showmeoutfit.domain.model.SummaryData
import com.smoh.showmeoutfit.domain.model.UserProfile
import com.smoh.showmeoutfit.domain.model.WeatherHighlights
import com.smoh.showmeoutfit.domain.model.WeatherSnapshot
import com.smoh.showmeoutfit.domain.personalization.PersonalizationPolicy

/**
 * Pure, deterministic diurnal recommendation engine.
 *
 * The engine produces structured results only: no display copy, no `String.format`, no locale
 * or date formatting. The presentation layer resolves names, descriptions, and sentences.
 */
class RuleBasedPresetEngine : IOutfitRecommendationEngine {

    companion object {
        /**
         * Effective daytime minimum (°C) at or below which the headline warns about frost.
         *
         * Inclusive so that exactly -10 °C reads as frost: it is the first temperature at which
         * exposed skin is at risk in a light outfit.
         */
        const val FROST_HEADLINE_THRESHOLD_CELSIUS: Double = -10.0

        /**
         * Effective daytime maximum (°C) at or above which the recommendation escalates to
         * heatwave guidance (lightest layers plus a hydration alert).
         *
         * Inclusive: 40 °C is already a health risk, not merely a hot day.
         */
        const val HEATWAVE_THRESHOLD_CELSIUS: Double = 40.0
    }

    override fun recommend(
        weather: WeatherSnapshot,
        profile: UserProfile
    ): OutfitRecommendation {
        val daytimeHours = filterDaytimeHours(weather.hourlyForecast)
        val personalOffset = PersonalizationPolicy.thermalOffsetCelsius(profile)
        val fit = PersonalizationPolicy.fitFor(profile.genderPreference)

        // Positive offset => feels cold => evaluate as if it were colder.
        val effectiveTemperatures = daytimeHours.map {
            it.apparentTemperatureCelsius - personalOffset
        }
        val effectiveCurrent = weather.current.apparentTemperatureCelsius - personalOffset

        val minEffTemp = effectiveTemperatures.minOrNull() ?: effectiveCurrent
        val maxEffTemp = effectiveTemperatures.maxOrNull() ?: effectiveCurrent
        val deltaT = maxEffTemp - minEffTemp
        val hasLargeTempSwing = deltaT >= 7.0 || (minEffTemp < 14.0 && maxEffTemp >= 16.0)

        val maxPrecipProb = daytimeHours.maxOfOrNull { it.precipitationProbability }
            ?: weather.dailyForecast.maxPrecipitationProbability
        val hasRainCode = daytimeHours.any { it.weatherCode.isRain } ||
            weather.dailyForecast.weatherCode.isRain
        val hasSnowCode = daytimeHours.any { it.weatherCode.isSnow } ||
            weather.dailyForecast.weatherCode.isSnow
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

        val layers = selectLayers(
            minEffTemp = minEffTemp,
            maxEffTemp = maxEffTemp,
            hasLargeTempSwing = hasLargeTempSwing,
            maxWind = maxWind,
            hasRain = hasRainSpike,
            hasSnow = hasSnowCode
        ).withFit(fit)

        val accessories = selectAccessories(
            daytimeHours = daytimeHours,
            ageBand = profile.ageBand,
            minEffTemp = minEffTemp,
            maxEffTemp = maxEffTemp,
            maxPrecipProb = maxPrecipProb,
            hasRainCode = hasRainCode,
            hasSnow = hasSnowCode,
            hasLargeTempSwing = hasLargeTempSwing,
            deltaT = deltaT,
            maxUv = maxUv,
            maxWind = maxWind
        )

        val headline = headlineFor(
            hasSnow = hasSnowCode,
            minEffTemp = minEffTemp,
            maxEffTemp = maxEffTemp,
            highlights = highlights
        )

        val summary = SummaryData(
            tempMinCelsius = minEffTemp,
            tempMaxCelsius = maxEffTemp,
            hasLargeTempSwing = hasLargeTempSwing,
            rainReason = accessories
                .firstOrNull { it.type == AccessoryType.UMBRELLA }
                ?.reason,
            peakUvIndex = maxUv
        )

        return OutfitRecommendation(
            headline = headline,
            summary = summary,
            baseLayer = layers.base,
            midLayer = layers.mid,
            outerLayer = layers.outer,
            bottomLayer = layers.bottom,
            footwear = layers.footwear,
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
            maxEffTemp < 0.0 || hasSnow -> LayerSelection(
                base = GarmentCatalog.THERMAL_UNDERSHIRT,
                mid = GarmentCatalog.WOOL_SWEATER,
                outer = GarmentCatalog.DOWN_PARKA,
                bottom = GarmentCatalog.THERMAL_PANTS,
                footwear = GarmentCatalog.WINTER_BOOTS
            )

            maxEffTemp < 10.0 -> LayerSelection(
                base = GarmentCatalog.LONG_SLEEVE_TEE,
                mid = GarmentCatalog.KNITTED_SWEATER,
                outer = if (maxWind >= 35.0) GarmentCatalog.WINDBREAKER else GarmentCatalog.WOOL_COAT,
                bottom = GarmentCatalog.JEANS,
                footwear = if (hasRain) GarmentCatalog.WATERPROOF_SHOES else GarmentCatalog.LEATHER_BOOTS
            )

            maxEffTemp < 16.0 -> LayerSelection(
                base = GarmentCatalog.COTTON_TEE,
                mid = if (hasLargeTempSwing) GarmentCatalog.CARDIGAN else GarmentCatalog.HOODIE,
                outer = if (maxWind >= 35.0) GarmentCatalog.WINDBREAKER else GarmentCatalog.LIGHT_JACKET,
                bottom = GarmentCatalog.TROUSERS,
                footwear = if (hasRain) GarmentCatalog.WATERPROOF_SHOES else GarmentCatalog.SNEAKERS
            )

            maxEffTemp < 22.0 -> LayerSelection(
                base = GarmentCatalog.COTTON_TEE,
                mid = if (hasLargeTempSwing) GarmentCatalog.CARDIGAN else null,
                outer = when {
                    maxWind >= 35.0 -> GarmentCatalog.WINDBREAKER
                    minEffTemp < 12.0 -> GarmentCatalog.LIGHT_JACKET
                    else -> null
                },
                bottom = GarmentCatalog.CHINOS,
                footwear = if (hasRain) GarmentCatalog.WATERPROOF_SHOES else GarmentCatalog.SNEAKERS
            )

            maxEffTemp < 28.0 -> LayerSelection(
                base = GarmentCatalog.LINEN_TEE,
                mid = if (hasLargeTempSwing) GarmentCatalog.CARDIGAN else null,
                outer = if (maxWind >= 35.0) GarmentCatalog.WINDBREAKER else null,
                bottom = GarmentCatalog.SHORTS,
                footwear = if (hasRain) GarmentCatalog.WATERPROOF_SHOES else GarmentCatalog.LOAFERS
            )

            // 28 °C and above: the lightest set. This branch also serves the heatwave band
            // (>= [HEATWAVE_THRESHOLD_CELSIUS]), so extreme heat never carries a mid or outer layer.
            else -> LayerSelection(
                base = GarmentCatalog.ULTRA_LIGHT_TANK,
                mid = null,
                outer = null,
                bottom = GarmentCatalog.SHORTS,
                footwear = GarmentCatalog.SANDALS
            )
        }
    }

    private fun selectAccessories(
        daytimeHours: List<HourlyCondition>,
        ageBand: AgeBand,
        minEffTemp: Double,
        maxEffTemp: Double,
        maxPrecipProb: Int,
        hasRainCode: Boolean,
        hasSnow: Boolean,
        hasLargeTempSwing: Boolean,
        deltaT: Double,
        maxUv: Double,
        maxWind: Double
    ): List<AccessoryAlert> {
        val alerts = mutableListOf<AccessoryAlert>()

        // 1. Rain / precipitation
        if (maxPrecipProb >= 35 || hasRainCode) {
            val rainReason = findRainWindow(daytimeHours)
                ?.let { AccessoryReason.RainWindow(start = it.first, end = it.second) }
                ?: AccessoryReason.RainProbability(peakPercent = maxPrecipProb)
            alerts += AccessoryAlert(
                type = AccessoryType.UMBRELLA,
                reason = rainReason,
                priority = if (maxPrecipProb >= 60 || hasRainCode) {
                    PriorityLevel.CRITICAL
                } else {
                    PriorityLevel.RECOMMENDED
                }
            )
        }

        // 2. Snow / extreme cold
        if (hasSnow || minEffTemp <= 0.0) {
            alerts += AccessoryAlert(
                type = AccessoryType.GLOVES,
                reason = AccessoryReason.FreezingConditions,
                priority = PriorityLevel.CRITICAL
            )
            alerts += AccessoryAlert(
                type = AccessoryType.SCARF,
                reason = AccessoryReason.FreezingConditions,
                priority = PriorityLevel.CRITICAL
            )
        } else if (maxWind >= 35.0 && minEffTemp < 15.0) {
            alerts += AccessoryAlert(
                type = AccessoryType.SCARF,
                reason = AccessoryReason.Wind(speedKmh = maxWind.toInt()),
                priority = PriorityLevel.RECOMMENDED
            )
        }

        // 3. Extreme heat: hydration outranks every cosmetic accessory below.
        if (maxEffTemp >= HEATWAVE_THRESHOLD_CELSIUS) {
            alerts += AccessoryAlert(
                type = AccessoryType.WATER_BOTTLE,
                reason = AccessoryReason.ExtremeHeat(tempCelsius = maxEffTemp),
                priority = PriorityLevel.CRITICAL
            )
        }

        // 4. UV protection
        if (maxUv >= 3.0) {
            alerts += AccessoryAlert(
                type = AccessoryType.SUNGLASSES,
                reason = AccessoryReason.UvIndex(peakUv = maxUv),
                priority = if (maxUv >= 6.0) PriorityLevel.CRITICAL else PriorityLevel.RECOMMENDED
            )
            alerts += AccessoryAlert(
                type = AccessoryType.SUNSCREEN,
                reason = AccessoryReason.UvIndex(peakUv = maxUv),
                priority = safetyPriority(
                    base = PriorityLevel.RECOMMENDED,
                    ageBand = ageBand,
                    escalate = maxUv >= 6.0
                )
            )
        }
        if (maxUv >= 6.0) {
            alerts += AccessoryAlert(
                type = AccessoryType.SUN_HAT,
                reason = AccessoryReason.UvIndex(peakUv = maxUv),
                priority = PriorityLevel.CRITICAL
            )
        }

        // 5. Diurnal temperature swing
        if (hasLargeTempSwing) {
            alerts += AccessoryAlert(
                type = AccessoryType.EXTRA_EVENING_LAYER,
                reason = AccessoryReason.DiurnalSwing(deltaCelsius = deltaT),
                priority = safetyPriority(
                    base = PriorityLevel.RECOMMENDED,
                    ageBand = ageBand,
                    escalate = true
                )
            )
        }

        return alerts
    }

    private fun safetyPriority(
        base: PriorityLevel,
        ageBand: AgeBand,
        escalate: Boolean
    ): PriorityLevel = if (escalate) {
        PersonalizationPolicy.escalateForSafety(base, ageBand)
    } else {
        base
    }

    private fun findRainWindow(
        hours: List<HourlyCondition>
    ): Pair<java.time.LocalTime, java.time.LocalTime>? {
        val rainHours = hours.filter {
            it.precipitationProbability >= 35 || it.weatherCode.isRain
        }
        if (rainHours.isEmpty()) return null

        val start = rainHours.first().time.toLocalTime()
        val end = rainHours.last().time.toLocalTime()
        return if (start == end) start to start.plusHours(1) else start to end
    }

    /**
     * Headline selection order is deliberate and explicit:
     * 1. [HeadlineReason.FrostWarning] beats everything, including snow: extreme cold is the
     *    condition the user has to dress for first.
     * 2. [HeadlineReason.Heatwave] next. Frost and heatwave cannot co-occur, but the order is
     *    spelled out so a future threshold edit cannot silently reorder the two extremes.
     * 3. Then the existing snow, swing, rain, UV and temperature bands, unchanged.
     */
    private fun headlineFor(
        hasSnow: Boolean,
        minEffTemp: Double,
        maxEffTemp: Double,
        highlights: WeatherHighlights
    ): HeadlineReason = when {
        minEffTemp <= FROST_HEADLINE_THRESHOLD_CELSIUS -> HeadlineReason.FrostWarning
        maxEffTemp >= HEATWAVE_THRESHOLD_CELSIUS -> HeadlineReason.Heatwave
        hasSnow -> HeadlineReason.SnowAndFrost
        highlights.hasLargeTempSwing ->
            HeadlineReason.LargeTempSwing(deltaCelsius = highlights.deltaTCelsius)
        highlights.hasRainSpike -> HeadlineReason.RainExpected
        highlights.maxUvIndex >= 6.0 -> HeadlineReason.HotAndIntenseUv
        highlights.tempMaxCelsius >= 24.0 -> HeadlineReason.WarmAndPleasant
        highlights.tempMaxCelsius < 10.0 -> HeadlineReason.Chilly
        else -> HeadlineReason.Mild
    }

    private data class LayerSelection(
        val base: Garment,
        val mid: Garment?,
        val outer: Garment?,
        val bottom: Garment,
        val footwear: Garment
    ) {
        fun withFit(fit: GarmentFit): LayerSelection = copy(
            base = base.copy(fit = fit),
            mid = mid?.copy(fit = fit),
            outer = outer?.copy(fit = fit),
            bottom = bottom.copy(fit = fit),
            footwear = footwear.copy(fit = fit)
        )
    }
}
