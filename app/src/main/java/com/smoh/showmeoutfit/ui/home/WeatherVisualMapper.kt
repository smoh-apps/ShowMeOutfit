package com.smoh.showmeoutfit.ui.home

import androidx.annotation.DrawableRes
import com.smoh.showmeoutfit.R
import com.smoh.showmeoutfit.domain.model.WeatherCode

/**
 * Maps a WMO [WeatherCode] to one of the nine duotone sky icons of the v2.0.0 pack.
 *
 * Pure presentation: no `Context`, no `stringResource`, no image loading. The caller hands the
 * returned `@DrawableRes` id to `painterResource`.
 *
 * The `when` has no `else`: a new [WeatherCode] without artwork is a compile error instead of a
 * silently blank icon at runtime. Several codes deliberately share one sky: the pack is
 * icon-first, so intensity differences (light vs. violent showers) stay in the spoken condition
 * text and never produce near-identical artwork.
 *
 * The vectors carry explicit hex fills rather than theme attributes, so a sky looks the same in
 * light and dark themes; the top-end `accentColor` of the capsule is used for chrome, not for
 * recolouring the art.
 */
object WeatherVisualMapper {

    /** Duotone sky icon for [code]. */
    @DrawableRes
    fun skyIconRes(code: WeatherCode): Int = when (code) {
        WeatherCode.CLEAR_SKY,
        WeatherCode.MAINLY_CLEAR -> R.drawable.ic_weather_sunny

        WeatherCode.PARTLY_CLOUDY -> R.drawable.ic_weather_partly_cloudy

        WeatherCode.OVERCAST -> R.drawable.ic_weather_cloudy

        WeatherCode.FOG,
        WeatherCode.DEPOSITING_RIME_FOG -> R.drawable.ic_weather_fog

        WeatherCode.DRIZZLE_LIGHT,
        WeatherCode.DRIZZLE_MODERATE,
        WeatherCode.DRIZZLE_DENSE,
        WeatherCode.FREEZING_DRIZZLE_LIGHT,
        WeatherCode.FREEZING_DRIZZLE_DENSE -> R.drawable.ic_weather_drizzle

        WeatherCode.RAIN_SLIGHT,
        WeatherCode.RAIN_MODERATE,
        WeatherCode.RAIN_HEAVY,
        WeatherCode.FREEZING_RAIN_LIGHT,
        WeatherCode.FREEZING_RAIN_HEAVY,
        WeatherCode.RAIN_SHOWERS_SLIGHT,
        WeatherCode.RAIN_SHOWERS_MODERATE,
        WeatherCode.RAIN_SHOWERS_VIOLENT -> R.drawable.ic_weather_rain

        WeatherCode.SNOW_FALL_SLIGHT,
        WeatherCode.SNOW_FALL_MODERATE,
        WeatherCode.SNOW_FALL_HEAVY,
        WeatherCode.SNOW_GRAINS,
        WeatherCode.SNOW_SHOWERS_SLIGHT,
        WeatherCode.SNOW_SHOWERS_HEAVY -> R.drawable.ic_weather_snow

        WeatherCode.THUNDERSTORM,
        WeatherCode.THUNDERSTORM_SLIGHT_HAIL,
        WeatherCode.THUNDERSTORM_HEAVY_HAIL -> R.drawable.ic_weather_thunderstorm

        WeatherCode.UNKNOWN -> R.drawable.ic_weather_unknown
    }
}
