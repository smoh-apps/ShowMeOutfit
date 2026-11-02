package com.smoh.showmeoutfit.ui.notifications

import com.smoh.showmeoutfit.R
import com.smoh.showmeoutfit.domain.model.AccessoryReason
import com.smoh.showmeoutfit.domain.model.AccessoryType
import com.smoh.showmeoutfit.domain.model.GarmentId
import com.smoh.showmeoutfit.domain.model.OutfitRecommendation
import com.smoh.showmeoutfit.ui.home.MappedText
import com.smoh.showmeoutfit.ui.home.TextArg
import kotlin.math.roundToInt

/**
 * The "why this outfit" lines of the morning alert.
 *
 * Deliberately pure: it reads the structured reasons the engine already attached to the
 * recommendation instead of re-deriving thresholds, so the notification can never disagree with
 * the dashboard. Every line is a [MappedText] so the caller formats times and numbers for the
 * device locale (and the mapper stays testable on a plain JVM).
 *
 * Order is the order a person checks them: rain first (it changes what you carry), then UV, then
 * wind, then the evening temperature drop.
 */
object NotificationHighlights {

    /** Wind speed from which the engine swaps in a windbreaker. */
    private const val WINDBREAKER_WIND_KMH = 35.0

    fun of(recommendation: OutfitRecommendation): List<MappedText> = buildList {
        rainLine(recommendation)?.let(::add)
        uvLine(recommendation)?.let(::add)
        windLine(recommendation)?.let(::add)
        swingLine(recommendation)?.let(::add)
    }

    /**
     * Rain clause, preferring the structured summary reason.
     *
     * The umbrella accessory carries the same reason; the summary is the one the dashboard's
     * headline sentence uses, so it is the canonical source when present.
     */
    private fun rainLine(recommendation: OutfitRecommendation): MappedText? {
        val reason = recommendation.summary.rainReason
            ?: recommendation.accessories
                .firstOrNull { it.type == AccessoryType.UMBRELLA }
                ?.reason

        return when (reason) {
            is AccessoryReason.RainWindow -> MappedText(
                resId = R.string.notification_highlight_rain_window,
                args = listOf(TextArg.Clock(reason.start), TextArg.Clock(reason.end))
            )

            is AccessoryReason.RainProbability -> MappedText(
                resId = R.string.notification_highlight_rain_chance,
                args = listOf(TextArg.Count(reason.peakPercent))
            )

            else -> null
        }
    }

    /**
     * One UV line for the whole set of sun accessories: sunglasses are the marker that UV crossed
     * the advisory threshold, and a sun hat (UV >= 6) upgrades the wording.
     */
    private fun uvLine(recommendation: OutfitRecommendation): MappedText? {
        val uvReason = recommendation.accessories
            .firstOrNull { it.type == AccessoryType.SUNGLASSES }
            ?.reason as? AccessoryReason.UvIndex
            ?: return null

        val needsHat = recommendation.accessories.any { it.type == AccessoryType.SUN_HAT }
        return MappedText(
            resId = if (needsHat) {
                R.string.notification_highlight_uv_hat
            } else {
                R.string.notification_highlight_uv
            },
            args = listOf(TextArg.Number(uvReason.peakUv, decimals = 1))
        )
    }

    /**
     * Wind line only when the engine actually chose a windbreaker: a windy but mild day gets no
     * advisory, and announcing one would contradict the wardrobe below it.
     */
    private fun windLine(recommendation: OutfitRecommendation): MappedText? {
        val peakWind = recommendation.weatherHighlights.maxWindSpeedKmh
        val wearsWindbreaker = recommendation.outerLayer?.id == GarmentId.WINDBREAKER
        if (!wearsWindbreaker || peakWind < WINDBREAKER_WIND_KMH) return null

        return MappedText(
            resId = R.string.notification_highlight_wind,
            args = listOf(TextArg.Count(peakWind.roundToInt()))
        )
    }

    /** Evening drop, taken from the structured diurnal reason so the delta matches the advice. */
    private fun swingLine(recommendation: OutfitRecommendation): MappedText? {
        val delta = (recommendation.accessories
            .firstOrNull { it.type == AccessoryType.EXTRA_EVENING_LAYER }
            ?.reason as? AccessoryReason.DiurnalSwing)
            ?.deltaCelsius
            ?: return null

        return MappedText(
            resId = R.string.notification_highlight_swing,
            args = listOf(TextArg.Number(delta, decimals = 0))
        )
    }
}
