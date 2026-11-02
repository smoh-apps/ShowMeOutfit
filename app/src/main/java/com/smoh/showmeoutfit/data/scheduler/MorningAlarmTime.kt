package com.smoh.showmeoutfit.data.scheduler

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Pure "next occurrence of a wall-clock time" arithmetic.
 *
 * A time equal to `now` resolves to tomorrow: the alarm for this minute has effectively fired.
 * Conversions go through the zone so DST gaps and overlaps use the platform's rules instead of
 * adding 24 hours to an epoch value.
 */
object MorningAlarmTime {

    fun nextTrigger(now: ZonedDateTime, time: LocalTime): ZonedDateTime {
        val today = now.toLocalDate().atTime(time).atZone(now.zone)
        return if (today.isAfter(now)) {
            today
        } else {
            now.toLocalDate().plusDays(1).atTime(time).atZone(now.zone)
        }
    }

    fun nextTriggerMillis(now: Instant, zone: ZoneId, time: LocalTime): Long =
        nextTrigger(now.atZone(zone), time).toInstant().toEpochMilli()
}
