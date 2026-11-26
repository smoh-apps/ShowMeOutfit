package com.smoh.showmeoutfit.ui.home

import com.smoh.showmeoutfit.R
import com.smoh.showmeoutfit.domain.model.WeatherCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards for the icon-first weather pack: every WMO code resolves to one of exactly nine sky
 * icons, the groups match the product mapping, and the nine icons are nine distinct resources.
 */
class WeatherVisualMapperTest {

    private val expectedMapping: List<Pair<WeatherCode, Int>> = listOf(
        WeatherCode.CLEAR_SKY to R.drawable.ic_weather_sunny,
        WeatherCode.MAINLY_CLEAR to R.drawable.ic_weather_sunny,
        WeatherCode.PARTLY_CLOUDY to R.drawable.ic_weather_partly_cloudy,
        WeatherCode.OVERCAST to R.drawable.ic_weather_cloudy,
        WeatherCode.FOG to R.drawable.ic_weather_fog,
        WeatherCode.DEPOSITING_RIME_FOG to R.drawable.ic_weather_fog,
        WeatherCode.DRIZZLE_LIGHT to R.drawable.ic_weather_drizzle,
        WeatherCode.DRIZZLE_MODERATE to R.drawable.ic_weather_drizzle,
        WeatherCode.DRIZZLE_DENSE to R.drawable.ic_weather_drizzle,
        WeatherCode.FREEZING_DRIZZLE_LIGHT to R.drawable.ic_weather_drizzle,
        WeatherCode.FREEZING_DRIZZLE_DENSE to R.drawable.ic_weather_drizzle,
        WeatherCode.RAIN_SLIGHT to R.drawable.ic_weather_rain,
        WeatherCode.RAIN_MODERATE to R.drawable.ic_weather_rain,
        WeatherCode.RAIN_HEAVY to R.drawable.ic_weather_rain,
        WeatherCode.FREEZING_RAIN_LIGHT to R.drawable.ic_weather_rain,
        WeatherCode.FREEZING_RAIN_HEAVY to R.drawable.ic_weather_rain,
        WeatherCode.SNOW_FALL_SLIGHT to R.drawable.ic_weather_snow,
        WeatherCode.SNOW_FALL_MODERATE to R.drawable.ic_weather_snow,
        WeatherCode.SNOW_FALL_HEAVY to R.drawable.ic_weather_snow,
        WeatherCode.SNOW_GRAINS to R.drawable.ic_weather_snow,
        WeatherCode.RAIN_SHOWERS_SLIGHT to R.drawable.ic_weather_rain,
        WeatherCode.RAIN_SHOWERS_MODERATE to R.drawable.ic_weather_rain,
        WeatherCode.RAIN_SHOWERS_VIOLENT to R.drawable.ic_weather_rain,
        WeatherCode.SNOW_SHOWERS_SLIGHT to R.drawable.ic_weather_snow,
        WeatherCode.SNOW_SHOWERS_HEAVY to R.drawable.ic_weather_snow,
        WeatherCode.THUNDERSTORM to R.drawable.ic_weather_thunderstorm,
        WeatherCode.THUNDERSTORM_SLIGHT_HAIL to R.drawable.ic_weather_thunderstorm,
        WeatherCode.THUNDERSTORM_HEAVY_HAIL to R.drawable.ic_weather_thunderstorm,
        WeatherCode.UNKNOWN to R.drawable.ic_weather_unknown
    )

    @Test
    fun `every weather code maps to its expected sky icon`() {
        expectedMapping.forEach { (code, expected) ->
            assertEquals("wrong sky icon for $code", expected, WeatherVisualMapper.skyIconRes(code))
        }
    }

    @Test
    fun `the mapping covers all weather codes with no duplicates or omissions`() {
        // The catalog size is part of the contract: 29 WMO codes today.
        assertEquals(29, WeatherCode.entries.size)
        assertEquals("weather code listed twice", expectedMapping.size, expectedMapping.map { it.first }.toSet().size)
        assertEquals(
            WeatherCode.entries.toSet(),
            expectedMapping.map { it.first }.toSet()
        )
        assertEquals(
            0,
            WeatherCode.entries.count { WeatherVisualMapper.skyIconRes(it) == 0 }
        )
    }

    @Test
    fun `the pack exposes exactly nine distinct sky icons`() {
        val used = WeatherCode.entries.map { WeatherVisualMapper.skyIconRes(it) }.toSet()
        assertEquals(9, used.size)
        assertEquals(
            setOf(
                R.drawable.ic_weather_sunny,
                R.drawable.ic_weather_partly_cloudy,
                R.drawable.ic_weather_cloudy,
                R.drawable.ic_weather_fog,
                R.drawable.ic_weather_drizzle,
                R.drawable.ic_weather_rain,
                R.drawable.ic_weather_snow,
                R.drawable.ic_weather_thunderstorm,
                R.drawable.ic_weather_unknown
            ),
            used
        )
        assertTrue("a sky icon id is zero", used.none { it == 0 })
    }
}
