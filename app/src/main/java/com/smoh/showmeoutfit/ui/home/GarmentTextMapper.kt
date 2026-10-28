package com.smoh.showmeoutfit.ui.home

import androidx.annotation.StringRes
import com.smoh.showmeoutfit.R
import com.smoh.showmeoutfit.domain.model.AccessoryReason
import com.smoh.showmeoutfit.domain.model.AccessoryType
import com.smoh.showmeoutfit.domain.model.GarmentFit
import com.smoh.showmeoutfit.domain.model.GarmentId
import com.smoh.showmeoutfit.domain.model.HeadlineReason
import com.smoh.showmeoutfit.domain.model.PriorityLevel
import com.smoh.showmeoutfit.domain.model.SummaryData
import com.smoh.showmeoutfit.domain.model.WeatherCode
import com.smoh.showmeoutfit.ui.components.AccessoryPriority
import com.smoh.showmeoutfit.ui.theme.WeatherConditionGroup
import java.text.NumberFormat
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * One localized template plus the values that still have to be formatted by the caller.
 *
 * The mapper never formats anything itself: it hands back a resource id and typed arguments so
 * the same mapping stays testable on a plain JVM. `stringResource(mapped.resId, *mapped.resolve(locale))`
 * turns it into display copy inside a composable (or `Context.getString` outside one).
 */
data class MappedText(
    @get:StringRes val resId: Int,
    val args: List<TextArg> = emptyList()
) {
    /** Formats every argument for [locale], ready to spread into `stringResource`/`getString`. */
    fun resolve(locale: Locale): Array<Any> = args.map { it.format(locale) }.toTypedArray()
}

/**
 * A number or a time that the UI must format.
 *
 * Formatting is deliberately separate from the resource lookup so nothing inside the mapper needs
 * a `Context`, and so a unit test can assert the formatted value without an Android runtime.
 */
sealed interface TextArg {

    fun format(locale: Locale): String

    /** Decimal number with a fixed number of fraction digits, e.g. a temperature or a UV index. */
    data class Number(val value: Double, val decimals: Int) : TextArg {
        override fun format(locale: Locale): String = NumberFormat.getNumberInstance(locale).apply {
            minimumFractionDigits = decimals
            maximumFractionDigits = decimals
        }.format(value)
    }

    /** Whole number, e.g. a percentage, an age in minutes or a wind speed in km/h. */
    data class Count(val value: Int) : TextArg {
        override fun format(locale: Locale): String =
            NumberFormat.getIntegerInstance(locale).format(value)
    }

    /**
     * Wall-clock time on a 24-hour axis.
     *
     * The timeline axis and every "rain between … and …" sentence share this format, so the copy
     * always lines up with the chart (ADR-S2-03: 24-hour MVP axis; per-locale clock styles are a
     * later refinement).
     */
    data class Clock(val time: LocalTime) : TextArg {
        override fun format(locale: Locale): String =
            DateTimeFormatter.ofPattern("HH:mm", locale).format(time)
    }
}

/**
 * Maps domain identifiers to presentation resources.
 *
 * Pure: no `Context`, no `stringResource`, no Android runtime types. Every function returns a
 * resource id (and, for parameterised copy, a [MappedText] whose arguments the caller formats).
 *
 * Fit only changes the *label* of a garment (never its thermal meaning), and only for the cut
 * sensitive catalog entries; every other garment keeps its unisex name.
 */
object GarmentTextMapper {

    // --- Garments -------------------------------------------------------------------------

    @StringRes
    fun garmentNameRes(id: GarmentId, fit: GarmentFit = GarmentFit.UNISEX): Int =
        fitSensitiveNames[id]?.let { variants ->
            when (fit) {
                GarmentFit.MEN -> variants.men
                GarmentFit.WOMEN -> variants.women
                GarmentFit.UNISEX -> null
            }
        } ?: unisexNameRes(id)

    @StringRes
    fun garmentDescriptionRes(id: GarmentId, fit: GarmentFit = GarmentFit.UNISEX): Int {
        // Fit reuses the description on purpose: fabric and use case do not change with the cut,
        // and duplicating 30 sentences would only create translation drift. The parameter keeps
        // the (id, fit) call shape symmetric with [garmentNameRes].
        return descriptionRes.getValue(id)
    }

    @StringRes
    private fun unisexNameRes(id: GarmentId): Int = nameRes.getValue(id)

    private data class FitNames(
        @get:StringRes val men: Int,
        @get:StringRes val women: Int
    )

    /** Only the garments whose cut genuinely differs between the men's and women's catalog. */
    private val fitSensitiveNames: Map<GarmentId, FitNames> = mapOf(
        GarmentId.COTTON_TEE to FitNames(
            men = R.string.home_garment_cotton_tee_name_men,
            women = R.string.home_garment_cotton_tee_name_women
        ),
        GarmentId.POLO_SHIRT to FitNames(
            men = R.string.home_garment_polo_shirt_name_men,
            women = R.string.home_garment_polo_shirt_name_women
        ),
        GarmentId.CARDIGAN to FitNames(
            men = R.string.home_garment_cardigan_name_men,
            women = R.string.home_garment_cardigan_name_women
        ),
        GarmentId.HOODIE to FitNames(
            men = R.string.home_garment_hoodie_name_men,
            women = R.string.home_garment_hoodie_name_women
        ),
        GarmentId.JEANS to FitNames(
            men = R.string.home_garment_jeans_name_men,
            women = R.string.home_garment_jeans_name_women
        ),
        GarmentId.CHINOS to FitNames(
            men = R.string.home_garment_chinos_name_men,
            women = R.string.home_garment_chinos_name_women
        ),
        GarmentId.TROUSERS to FitNames(
            men = R.string.home_garment_trousers_name_men,
            women = R.string.home_garment_trousers_name_women
        ),
        GarmentId.SHORTS to FitNames(
            men = R.string.home_garment_shorts_name_men,
            women = R.string.home_garment_shorts_name_women
        )
    )

    private val nameRes: Map<GarmentId, Int> = mapOf(
        GarmentId.THERMAL_UNDERSHIRT to R.string.home_garment_thermal_undershirt_name,
        GarmentId.LONG_SLEEVE_TEE to R.string.home_garment_long_sleeve_tee_name,
        GarmentId.COTTON_TEE to R.string.home_garment_cotton_tee_name,
        GarmentId.POLO_SHIRT to R.string.home_garment_polo_shirt_name,
        GarmentId.LINEN_TEE to R.string.home_garment_linen_tee_name,
        GarmentId.ULTRA_LIGHT_TANK to R.string.home_garment_ultra_light_tank_name,
        GarmentId.WOOL_SWEATER to R.string.home_garment_wool_sweater_name,
        GarmentId.KNITTED_SWEATER to R.string.home_garment_knitted_sweater_name,
        GarmentId.CARDIGAN to R.string.home_garment_cardigan_name,
        GarmentId.HOODIE to R.string.home_garment_hoodie_name,
        GarmentId.LIGHT_OVERSHIRT to R.string.home_garment_light_overshirt_name,
        GarmentId.DOWN_PARKA to R.string.home_garment_down_parka_name,
        GarmentId.WOOL_COAT to R.string.home_garment_wool_coat_name,
        GarmentId.PUFFER_JACKET to R.string.home_garment_puffer_jacket_name,
        GarmentId.DENIM_JACKET to R.string.home_garment_denim_jacket_name,
        GarmentId.LIGHT_JACKET to R.string.home_garment_light_jacket_name,
        GarmentId.WINDBREAKER to R.string.home_garment_windbreaker_name,
        GarmentId.RAINCOAT to R.string.home_garment_raincoat_name,
        GarmentId.THERMAL_PANTS to R.string.home_garment_thermal_pants_name,
        GarmentId.JEANS to R.string.home_garment_jeans_name,
        GarmentId.CHINOS to R.string.home_garment_chinos_name,
        GarmentId.TROUSERS to R.string.home_garment_trousers_name,
        GarmentId.SHORTS to R.string.home_garment_shorts_name,
        GarmentId.WINTER_BOOTS to R.string.home_garment_winter_boots_name,
        GarmentId.LEATHER_BOOTS to R.string.home_garment_leather_boots_name,
        GarmentId.SNEAKERS to R.string.home_garment_sneakers_name,
        GarmentId.WATERPROOF_SHOES to R.string.home_garment_waterproof_shoes_name,
        GarmentId.LOAFERS to R.string.home_garment_loafers_name,
        GarmentId.SANDALS to R.string.home_garment_sandals_name
    )

    private val descriptionRes: Map<GarmentId, Int> = mapOf(
        GarmentId.THERMAL_UNDERSHIRT to R.string.home_garment_thermal_undershirt_description,
        GarmentId.LONG_SLEEVE_TEE to R.string.home_garment_long_sleeve_tee_description,
        GarmentId.COTTON_TEE to R.string.home_garment_cotton_tee_description,
        GarmentId.POLO_SHIRT to R.string.home_garment_polo_shirt_description,
        GarmentId.LINEN_TEE to R.string.home_garment_linen_tee_description,
        GarmentId.ULTRA_LIGHT_TANK to R.string.home_garment_ultra_light_tank_description,
        GarmentId.WOOL_SWEATER to R.string.home_garment_wool_sweater_description,
        GarmentId.KNITTED_SWEATER to R.string.home_garment_knitted_sweater_description,
        GarmentId.CARDIGAN to R.string.home_garment_cardigan_description,
        GarmentId.HOODIE to R.string.home_garment_hoodie_description,
        GarmentId.LIGHT_OVERSHIRT to R.string.home_garment_light_overshirt_description,
        GarmentId.DOWN_PARKA to R.string.home_garment_down_parka_description,
        GarmentId.WOOL_COAT to R.string.home_garment_wool_coat_description,
        GarmentId.PUFFER_JACKET to R.string.home_garment_puffer_jacket_description,
        GarmentId.DENIM_JACKET to R.string.home_garment_denim_jacket_description,
        GarmentId.LIGHT_JACKET to R.string.home_garment_light_jacket_description,
        GarmentId.WINDBREAKER to R.string.home_garment_windbreaker_description,
        GarmentId.RAINCOAT to R.string.home_garment_raincoat_description,
        GarmentId.THERMAL_PANTS to R.string.home_garment_thermal_pants_description,
        GarmentId.JEANS to R.string.home_garment_jeans_description,
        GarmentId.CHINOS to R.string.home_garment_chinos_description,
        GarmentId.TROUSERS to R.string.home_garment_trousers_description,
        GarmentId.SHORTS to R.string.home_garment_shorts_description,
        GarmentId.WINTER_BOOTS to R.string.home_garment_winter_boots_description,
        GarmentId.LEATHER_BOOTS to R.string.home_garment_leather_boots_description,
        GarmentId.SNEAKERS to R.string.home_garment_sneakers_description,
        GarmentId.WATERPROOF_SHOES to R.string.home_garment_waterproof_shoes_description,
        GarmentId.LOAFERS to R.string.home_garment_loafers_description,
        GarmentId.SANDALS to R.string.home_garment_sandals_description
    )

    // --- Accessories ----------------------------------------------------------------------

    @StringRes
    fun accessoryNameRes(type: AccessoryType): Int = accessoryNameResMap.getValue(type)

    /**
     * Description for the accessory glyph.
     *
     * `AccessoryBadge` currently conveys the accessory through its label, so the glyph is
     * decorative there; this mapping is what a future icon slot (or the notification surface) uses.
     */
    @StringRes
    fun accessoryIconDescriptionRes(type: AccessoryType): Int =
        accessoryIconDescriptionResMap.getValue(type)

    /** Domain urgency -> design-system urgency. */
    fun accessoryPriority(level: PriorityLevel): AccessoryPriority = when (level) {
        PriorityLevel.CRITICAL -> AccessoryPriority.CRITICAL
        PriorityLevel.RECOMMENDED -> AccessoryPriority.RECOMMENDED
        PriorityLevel.OPTIONAL -> AccessoryPriority.OPTIONAL
    }

    private val accessoryNameResMap: Map<AccessoryType, Int> = mapOf(
        AccessoryType.UMBRELLA to R.string.home_accessory_umbrella_name,
        AccessoryType.SUNGLASSES to R.string.home_accessory_sunglasses_name,
        AccessoryType.SUN_HAT to R.string.home_accessory_sun_hat_name,
        AccessoryType.SCARF to R.string.home_accessory_scarf_name,
        AccessoryType.GLOVES to R.string.home_accessory_gloves_name,
        AccessoryType.EXTRA_EVENING_LAYER to R.string.home_accessory_extra_evening_layer_name,
        AccessoryType.SUNSCREEN to R.string.home_accessory_sunscreen_name
    )

    private val accessoryIconDescriptionResMap: Map<AccessoryType, Int> = mapOf(
        AccessoryType.UMBRELLA to R.string.home_accessory_umbrella_icon_description,
        AccessoryType.SUNGLASSES to R.string.home_accessory_sunglasses_icon_description,
        AccessoryType.SUN_HAT to R.string.home_accessory_sun_hat_icon_description,
        AccessoryType.SCARF to R.string.home_accessory_scarf_icon_description,
        AccessoryType.GLOVES to R.string.home_accessory_gloves_icon_description,
        AccessoryType.EXTRA_EVENING_LAYER to R.string.home_accessory_extra_evening_layer_icon_description,
        AccessoryType.SUNSCREEN to R.string.home_accessory_sunscreen_icon_description
    )

    /** Why an accessory is recommended, as template + arguments. */
    fun accessoryReason(reason: AccessoryReason): MappedText = when (reason) {
        is AccessoryReason.RainWindow -> MappedText(
            resId = R.string.home_reason_rain_window,
            args = listOf(TextArg.Clock(reason.start), TextArg.Clock(reason.end))
        )

        is AccessoryReason.RainProbability -> MappedText(
            resId = R.string.home_reason_rain_probability,
            args = listOf(TextArg.Count(reason.peakPercent))
        )

        AccessoryReason.FreezingConditions -> MappedText(R.string.home_reason_freezing)

        is AccessoryReason.Wind -> MappedText(
            resId = R.string.home_reason_wind,
            args = listOf(TextArg.Count(reason.speedKmh))
        )

        is AccessoryReason.UvIndex -> MappedText(
            resId = R.string.home_reason_uv,
            args = listOf(TextArg.Number(reason.peakUv, decimals = 1))
        )

        is AccessoryReason.DiurnalSwing -> MappedText(
            resId = R.string.home_reason_diurnal_swing,
            args = listOf(TextArg.Number(reason.deltaCelsius, decimals = 0))
        )
    }

    // --- Weather --------------------------------------------------------------------------

    /** Reuses the design-system grouping so chips, badges and the timeline share one palette. */
    fun conditionGroup(code: WeatherCode): WeatherConditionGroup =
        WeatherConditionGroup.fromWmoCode(code.code)

    @StringRes
    fun conditionDescriptionRes(code: WeatherCode): Int = conditionRes.getValue(code)

    private val conditionRes: Map<WeatherCode, Int> = mapOf(
        WeatherCode.CLEAR_SKY to R.string.home_condition_clear_sky,
        WeatherCode.MAINLY_CLEAR to R.string.home_condition_mainly_clear,
        WeatherCode.PARTLY_CLOUDY to R.string.home_condition_partly_cloudy,
        WeatherCode.OVERCAST to R.string.home_condition_overcast,
        WeatherCode.FOG to R.string.home_condition_fog,
        WeatherCode.DEPOSITING_RIME_FOG to R.string.home_condition_depositing_rime_fog,
        WeatherCode.DRIZZLE_LIGHT to R.string.home_condition_drizzle_light,
        WeatherCode.DRIZZLE_MODERATE to R.string.home_condition_drizzle_moderate,
        WeatherCode.DRIZZLE_DENSE to R.string.home_condition_drizzle_dense,
        WeatherCode.FREEZING_DRIZZLE_LIGHT to R.string.home_condition_freezing_drizzle_light,
        WeatherCode.FREEZING_DRIZZLE_DENSE to R.string.home_condition_freezing_drizzle_dense,
        WeatherCode.RAIN_SLIGHT to R.string.home_condition_rain_slight,
        WeatherCode.RAIN_MODERATE to R.string.home_condition_rain_moderate,
        WeatherCode.RAIN_HEAVY to R.string.home_condition_rain_heavy,
        WeatherCode.FREEZING_RAIN_LIGHT to R.string.home_condition_freezing_rain_light,
        WeatherCode.FREEZING_RAIN_HEAVY to R.string.home_condition_freezing_rain_heavy,
        WeatherCode.SNOW_FALL_SLIGHT to R.string.home_condition_snow_fall_slight,
        WeatherCode.SNOW_FALL_MODERATE to R.string.home_condition_snow_fall_moderate,
        WeatherCode.SNOW_FALL_HEAVY to R.string.home_condition_snow_fall_heavy,
        WeatherCode.SNOW_GRAINS to R.string.home_condition_snow_grains,
        WeatherCode.RAIN_SHOWERS_SLIGHT to R.string.home_condition_rain_showers_slight,
        WeatherCode.RAIN_SHOWERS_MODERATE to R.string.home_condition_rain_showers_moderate,
        WeatherCode.RAIN_SHOWERS_VIOLENT to R.string.home_condition_rain_showers_violent,
        WeatherCode.SNOW_SHOWERS_SLIGHT to R.string.home_condition_snow_showers_slight,
        WeatherCode.SNOW_SHOWERS_HEAVY to R.string.home_condition_snow_showers_heavy,
        WeatherCode.THUNDERSTORM to R.string.home_condition_thunderstorm,
        WeatherCode.THUNDERSTORM_SLIGHT_HAIL to R.string.home_condition_thunderstorm_slight_hail,
        WeatherCode.THUNDERSTORM_HEAVY_HAIL to R.string.home_condition_thunderstorm_heavy_hail,
        WeatherCode.UNKNOWN to R.string.home_condition_unknown
    )

    // --- Headline and summary -------------------------------------------------------------

    /** Headline copy; `LargeTempSwing` carries the delta the UI formats before substituting it. */
    fun headline(reason: HeadlineReason): MappedText = when (reason) {
        HeadlineReason.SnowAndFrost -> MappedText(R.string.home_headline_snow)
        is HeadlineReason.LargeTempSwing -> MappedText(
            resId = R.string.home_headline_temp_swing,
            args = listOf(TextArg.Number(reason.deltaCelsius, decimals = 0))
        )

        HeadlineReason.RainExpected -> MappedText(R.string.home_headline_rain)
        HeadlineReason.HotAndIntenseUv -> MappedText(R.string.home_headline_hot_uv)
        HeadlineReason.WarmAndPleasant -> MappedText(R.string.home_headline_warm)
        HeadlineReason.Chilly -> MappedText(R.string.home_headline_chilly)
        HeadlineReason.Mild -> MappedText(R.string.home_headline_mild)
    }

    /**
     * Daily summary sentence.
     *
     * The rain clause is chosen from the structured [SummaryData.rainReason] so the sentence and
     * the umbrella badge never disagree.
     */
    fun summary(summary: SummaryData): MappedText {
        val rangeArgs = listOf(
            TextArg.Number(summary.tempMinCelsius, decimals = 0),
            TextArg.Number(summary.tempMaxCelsius, decimals = 0)
        )
        return when (val rain = summary.rainReason) {
            is AccessoryReason.RainWindow -> MappedText(
                resId = R.string.home_summary_rain_window,
                args = rangeArgs + listOf(TextArg.Clock(rain.start), TextArg.Clock(rain.end))
            )

            is AccessoryReason.RainProbability -> MappedText(
                resId = R.string.home_summary_rain_chance,
                args = rangeArgs + TextArg.Count(rain.peakPercent)
            )

            else -> MappedText(resId = R.string.home_summary_plain, args = rangeArgs)
        }
    }
}
