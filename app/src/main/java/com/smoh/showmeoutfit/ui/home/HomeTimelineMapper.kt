package com.smoh.showmeoutfit.ui.home

import com.smoh.showmeoutfit.domain.model.HourlyCondition
import com.smoh.showmeoutfit.ui.components.TimelinePoint
import java.time.LocalTime

/**
 * Turns the hourly forecast into the points the diurnal timeline draws.
 *
 * Pure: the label formatter is injected, so the UI owns locale/format decisions and a unit test can
 * assert the window and the ordering with a deterministic formatter.
 *
 * The window is the product-defined daytime range (07:00–22:00 inclusive) that the recommendation
 * engine itself uses, so the chart never shows hours the outfit does not cover.
 */
object HomeTimelineMapper {

    const val WindowStartHour = 7
    const val WindowEndHour = 22

    fun points(
        hourlyForecast: List<HourlyCondition>,
        hourLabel: (LocalTime) -> String
    ): List<TimelinePoint> = hourlyForecast
        .filter { it.time.hour in WindowStartHour..WindowEndHour }
        .sortedBy { it.time }
        .distinctBy { it.time.hour }
        .map { hour ->
            TimelinePoint(
                hourLabel = hourLabel(hour.time.toLocalTime()),
                temperatureC = hour.temperatureCelsius,
                precipitationProbability = hour.precipitationProbability,
                uvIndex = hour.uvIndex
            )
        }
}
