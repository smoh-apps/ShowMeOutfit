package com.smoh.showmeoutfit.data.scheduler

import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalTime

/**
 * Doze gate for the morning alert.
 *
 * Sprint 3 proved that the pending alarm survives one `force-idle` transition. Sprint 4 (SMO-S4-02)
 * widens the gate to the failure modes that an overnight standby actually exercises:
 * repeated idle/wake cycles, re-scheduling idempotency (one `cancel` must clear everything) and the
 * `RTC_WAKEUP` registration that lets the alarm escape Doze.
 *
 * The physical-device half of the ticket is documented in `scripts/soak/README.md`; this suite is
 * the automated evidence that can run on the API 37 AVD in CI.
 */
@RunWith(AndroidJUnit4::class)
class MorningAlarmDozeTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val scheduler = AndroidAlarmScheduler(context)

    @Before
    fun unplugBattery() {
        shell("dumpsys battery unplug")
    }

    @After
    fun restoreDevice() {
        shell("dumpsys deviceidle unforce")
        shell("dumpsys battery reset")
        scheduler.cancel()
    }

    @Test
    fun the_morning_alarm_survives_a_doze_transition() {
        val target = LocalTime.now().plusMinutes(2).withSecond(0).withNano(0)
        scheduler.scheduleNext(target)

        assertTrue(
            "the alarm was not registered before Doze:\n${shell("dumpsys alarm")}",
            hasPendingAlarm()
        )

        val idle = enterIdle()
        assertTrue("device did not enter Doze; deep state was '${idle}'", idle)

        assertTrue(
            "the alarm disappeared while the device was idle:\n${shell("dumpsys alarm")}",
            hasPendingAlarm()
        )
    }

    /**
     * An overnight standby can cross several maintenance windows. Re-entering Doze after a wake-up
     * must not clear or duplicate the pending wake-up.
     */
    @Test
    fun the_morning_alarm_survives_repeated_idle_cycles() {
        scheduler.scheduleNext(LocalTime.now().plusMinutes(5).withSecond(0).withNano(0))

        repeat(3) { cycle ->
            assertTrue("device did not enter Doze on cycle $cycle", enterIdle())
            assertTrue(
                "the alarm disappeared during idle cycle $cycle:\n${shell("dumpsys alarm")}",
                hasPendingAlarm()
            )
            shell("dumpsys deviceidle unforce")
            assertTrue(
                "the alarm disappeared when Doze ended on cycle $cycle:\n${shell("dumpsys alarm")}",
                hasPendingAlarm()
            )
        }
    }

    /**
     * Every coordinator path may call `scheduleNext` again (start, settings edit, worker). Because
     * the scheduler reuses one immutable `PendingIntent`, repeated calls replace the previous alarm
     * instead of stacking duplicates. A single `cancel` is the proof: it only removes the one
     * `PendingIntent` registered for the morning broadcast.
     */
    @Test
    fun re_scheduling_replaces_the_previous_alarm_instead_of_stacking_it() {
        val now = LocalTime.now().withSecond(0).withNano(0)
        scheduler.scheduleNext(now.plusMinutes(30))
        scheduler.scheduleNext(now.plusMinutes(31))
        scheduler.scheduleNext(now.plusMinutes(32))

        assertTrue(
            "the alarm was not registered after repeated schedules:\n${shell("dumpsys alarm")}",
            hasPendingAlarm()
        )

        scheduler.cancel()

        assertFalse(
            "a duplicate survived the single cancel:\n${shell("dumpsys alarm")}",
            hasPendingAlarm()
        )
    }

    /** `setExactAndAllowWhileIdle` must register an `RTC_WAKEUP` alarm, or Doze never wakes. */
    @Test
    fun the_morning_alarm_is_registered_as_an_rtc_wakeup() {
        scheduler.scheduleNext(LocalTime.now().plusMinutes(30).withSecond(0).withNano(0))

        val pending = pendingAlarmSection()
        val actionIndex = pending.indexOf(MorningAlarmReceiver.ACTION_MORNING_ALARM)
        assertTrue("the action is missing from the pending alarms:\n$pending", actionIndex >= 0)

        val registration = pending.substring((actionIndex - 400).coerceAtLeast(0), actionIndex)
        assertTrue(
            "the morning alarm is not an RTC_WAKEUP registration:\n$registration",
            registration.contains("RTC_WAKEUP")
        )
    }

    /**
     * Proves the check above is really reading the pending registry: cancelling the alarm (which is
     * also what the coordinator does when alerts are switched off) must make it disappear, even
     * though `dumpsys alarm` keeps the cancellation in its history.
     */
    @Test
    fun cancelling_removes_the_pending_alarm() {
        scheduler.scheduleNext(LocalTime.now().plusMinutes(30).withSecond(0).withNano(0))
        assertTrue(
            "the alarm was not registered before cancel:\n${shell("dumpsys alarm")}",
            hasPendingAlarm()
        )

        scheduler.cancel()

        assertFalse(
            "the alarm is still pending after cancel:\n${shell("dumpsys alarm")}",
            hasPendingAlarm()
        )
    }

    /** Polls the deep-idle state; returns whether it reached IDLE. */
    private fun enterIdle(): Boolean {
        shell("dumpsys deviceidle force-idle")
        repeat(15) {
            val deep = shell("dumpsys deviceidle get deep").trim()
            if (deep == "IDLE") return true
            Thread.sleep(1_000)
        }
        return shell("dumpsys deviceidle get deep").trim() == "IDLE"
    }

    /**
     * Whether the morning broadcast is still registered with `AlarmManager`.
     *
     * Two details matter for this not to be a false positive:
     * - `dumpsys alarm` keeps a "Removal history" (and per-package "Alarm Stats") after an alarm is
     *   cancelled or delivered, so a plain package-name match stays true once the alarm is gone.
     *   Only the part before those sections describes what is currently pending.
     * - The package name alone also appears for any other alarm the app might own, so the check
     *   additionally requires the broadcast action `AlarmManager` tags the morning alarm with.
     */
    private fun hasPendingAlarm(): Boolean {
        val pending = pendingAlarmSection()
        return pending.contains(context.packageName) &&
            pending.contains(MorningAlarmReceiver.ACTION_MORNING_ALARM)
    }

    private fun pendingAlarmSection(): String =
        shell("dumpsys alarm").substringBefore("Removal history:")

    private fun shell(command: String): String {
        val descriptor = InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand(command)
        return ParcelFileDescriptor.AutoCloseInputStream(descriptor).bufferedReader().use { it.readText() }
    }

    @Test
    fun the_scheduler_reports_the_exact_alarm_capability() {
        // Records the emulator's grant state; the assertion is that the property matches the OS,
        // not that exact alarms are granted (a clean install requires the user to allow them).
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return

        val platformAllows = context.getSystemService(android.app.AlarmManager::class.java)
            .canScheduleExactAlarms()
        assertEquals(platformAllows, scheduler.canScheduleExactAlarms)
    }
}
