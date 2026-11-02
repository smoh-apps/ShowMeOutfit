package com.smoh.showmeoutfit.data.scheduler

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class MorningAlarmTimeTest {

    private val zone = ZoneId.of("Europe/Berlin")
    private val morning = LocalTime.of(7, 0)

    private fun at(hour: Int, minute: Int, day: Int = 1, month: Int = 11): ZonedDateTime =
        ZonedDateTime.of(2026, month, day, hour, minute, 0, 0, zone)

    @Test
    fun `a time later today resolves to today`() {
        val next = MorningAlarmTime.nextTrigger(at(6, 0), morning)

        assertEquals(at(7, 0), next)
    }

    @Test
    fun `a time earlier today resolves to tomorrow`() {
        val next = MorningAlarmTime.nextTrigger(at(8, 30), morning)

        assertEquals(at(7, 0, day = 2), next)
    }

    @Test
    fun `the exact minute already counts as fired and moves to tomorrow`() {
        // Otherwise re-arming at 07:00:00 would immediately fire again.
        val next = MorningAlarmTime.nextTrigger(at(7, 0), morning)

        assertEquals(at(7, 0, day = 2), next)
    }

    @Test
    fun `a fraction of a second before the alarm still resolves to today`() {
        val now = at(6, 59).plusSeconds(59).plusNanos(999_000_000)

        assertEquals(at(7, 0), MorningAlarmTime.nextTrigger(now, morning))
    }

    @Test
    fun `spring forward keeps the wall-clock time instead of adding a fixed day`() {
        // DST starts on 2026-03-29 in Berlin (02:00 -> 03:00).
        val now = at(9, 0, day = 28, month = 3)

        val next = MorningAlarmTime.nextTrigger(now, morning)

        assertEquals(29, next.dayOfMonth)
        assertEquals(LocalTime.of(7, 0), next.toLocalTime())
        assertEquals(2, next.offset.totalSeconds / 3600)
    }

    @Test
    fun `fall back keeps the wall-clock time and the winter offset`() {
        // DST ends on 2026-10-25 in Berlin (03:00 -> 02:00).
        val now = at(9, 0, day = 24, month = 10)

        val next = MorningAlarmTime.nextTrigger(now, morning)

        assertEquals(25, next.dayOfMonth)
        assertEquals(LocalTime.of(7, 0), next.toLocalTime())
        assertEquals(1, next.offset.totalSeconds / 3600)
    }

    @Test
    fun `millisecond helper matches the zoned calculation`() {
        val now = Instant.parse("2026-11-01T05:30:00Z")

        val millis = MorningAlarmTime.nextTriggerMillis(now, zone, morning)

        assertEquals(MorningAlarmTime.nextTrigger(now.atZone(zone), morning).toInstant().toEpochMilli(), millis)
    }

    @Test
    fun `millisecond helper works in a fixed zone`() {
        val utc = ZoneId.of("UTC")
        val now = Instant.parse("2026-11-01T09:00:00Z")

        val millis = MorningAlarmTime.nextTriggerMillis(now, utc, morning)

        assertEquals(Instant.parse("2026-11-02T07:00:00Z").toEpochMilli(), millis)
    }
}
