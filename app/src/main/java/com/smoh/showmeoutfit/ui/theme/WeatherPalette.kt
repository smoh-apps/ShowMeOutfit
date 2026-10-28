package com.smoh.showmeoutfit.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Semantic weather families used by the presentation layer.
 *
 * This is a UI-only enum: it intentionally has no dependency on `domain.model.WeatherCode`.
 * Data/domain code that needs a mapping can use [fromWmoCode] for the WMO weather codes.
 */
enum class WeatherConditionGroup {
    CLEAR,
    CLOUDY,
    RAIN,
    SNOW,
    STORM,
    HEAT,
    COLD;

    companion object {
        /**
         * Maps an Open-Meteo / WMO weather code to a semantic group.
         *
         * [HEAT] and [COLD] are temperature-derived and therefore never returned by this helper.
         */
        fun fromWmoCode(code: Int): WeatherConditionGroup = when (code) {
            0, 1 -> CLEAR
            2, 3, 45, 48 -> CLOUDY
            51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82 -> RAIN
            71, 73, 75, 77, 85, 86 -> SNOW
            95, 96, 99 -> STORM
            else -> CLOUDY
        }
    }
}

/**
 * Color contract for one weather family in one theme.
 *
 * Contrast (verified for both themes):
 * - [onAccent] on [accent]: >= 6.0 : 1
 * - [onContainer] on [container]: >= 10.0 : 1
 * - [accent] on [container]: >= 5.1 : 1
 *
 * All pairs clear WCAG AA for normal text (4.5 : 1), so these roles may be used for
 * text as well as icons.
 */
@Immutable
data class WeatherColors(
    val accent: Color,
    val onAccent: Color,
    val container: Color,
    val onContainer: Color,
)

/**
 * Resolved weather palette for the current theme.
 *
 * Prefer [LocalWeatherPalette] inside composables; use [weatherPaletteFor] in plain Kotlin
 * (for example a future ViewModel-free mapper or a screenshot test).
 */
@Immutable
data class WeatherPalette(
    val isDark: Boolean,
    private val groups: Map<WeatherConditionGroup, WeatherColors>,
) {
    fun colorsFor(group: WeatherConditionGroup): WeatherColors = groups.getValue(group)

    operator fun get(group: WeatherConditionGroup): WeatherColors = colorsFor(group)
}

private fun weatherColors(
    accent: Long,
    onAccent: Long,
    container: Long,
    onContainer: Long,
) = WeatherColors(
    accent = Color(accent),
    onAccent = Color(onAccent),
    container = Color(container),
    onContainer = Color(onContainer),
)

private val LightWeatherColors: Map<WeatherConditionGroup, WeatherColors> = mapOf(
    WeatherConditionGroup.CLEAR to weatherColors(0xFF8A5300, 0xFFFFFFFF, 0xFFFFF1D6, 0xFF3B2400),
    WeatherConditionGroup.CLOUDY to weatherColors(0xFF3D5868, 0xFFFFFFFF, 0xFFE7EEF3, 0xFF1A2A33),
    WeatherConditionGroup.RAIN to weatherColors(0xFF0B5A8A, 0xFFFFFFFF, 0xFFDCEFFA, 0xFF062437),
    WeatherConditionGroup.SNOW to weatherColors(0xFF1F6A85, 0xFFFFFFFF, 0xFFE2F2F8, 0xFF0A2A35),
    WeatherConditionGroup.STORM to weatherColors(0xFF4A3B8C, 0xFFFFFFFF, 0xFFE9E4F8, 0xFF211A45),
    WeatherConditionGroup.HEAT to weatherColors(0xFFA32B20, 0xFFFFFFFF, 0xFFFFE3DE, 0xFF3D0B06),
    WeatherConditionGroup.COLD to weatherColors(0xFF2A5FA8, 0xFFFFFFFF, 0xFFDEE8FB, 0xFF0B1E3C),
)

private val DarkWeatherColors: Map<WeatherConditionGroup, WeatherColors> = mapOf(
    WeatherConditionGroup.CLEAR to weatherColors(0xFFFFC46B, 0xFF3A2200, 0xFF3A2A10, 0xFFFFE2B4),
    WeatherConditionGroup.CLOUDY to weatherColors(0xFFB9CBD6, 0xFF16232B, 0xFF25333B, 0xFFDCE7EE),
    WeatherConditionGroup.RAIN to weatherColors(0xFF8CCBF2, 0xFF04283D, 0xFF0E3448, 0xFFCFE9F9),
    WeatherConditionGroup.SNOW to weatherColors(0xFFA5DCEF, 0xFF062B37, 0xFF103640, 0xFFD5F0F8),
    WeatherConditionGroup.STORM to weatherColors(0xFFC4B8FF, 0xFF221A4D, 0xFF2C2550, 0xFFE6E0FF),
    WeatherConditionGroup.HEAT to weatherColors(0xFFFFB4A4, 0xFF4A100A, 0xFF4A1A14, 0xFFFFDAD3),
    WeatherConditionGroup.COLD to weatherColors(0xFFADC8FF, 0xFF0A2350, 0xFF16294A, 0xFFD9E3FF),
)

/**
 * Pure, non-composable lookup. Safe to call from previews, screenshot tests, or any JVM code
 * that already knows whether the dark theme is active.
 */
fun weatherPaletteFor(group: WeatherConditionGroup, isDark: Boolean): WeatherColors =
    (if (isDark) DarkWeatherColors else LightWeatherColors).getValue(group)

/** Builds the full [WeatherPalette] for one theme; used by `ShowMeOutfitTheme`. */
fun weatherPalette(isDark: Boolean): WeatherPalette = WeatherPalette(
    isDark = isDark,
    groups = if (isDark) DarkWeatherColors else LightWeatherColors,
)

/**
 * Ambient weather palette. Read it as `LocalWeatherPalette.current[WeatherConditionGroup.RAIN]`.
 *
 * The default value is the light palette so previews and standalone component previews render
 * without a theme wrapper; `ShowMeOutfitTheme` always provides the value matching `darkTheme`.
 */
val LocalWeatherPalette = staticCompositionLocalOf { weatherPalette(isDark = false) }
