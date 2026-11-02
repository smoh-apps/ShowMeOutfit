package com.smoh.showmeoutfit.data.scheduler

import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalTime

/**
 * Sprint 3 Doze gate: the morning alarm has to stay registered while the device is idle.
 *
 * Drives the real `AlarmManager` through the production scheduler, forces the emulator into Doze
 * via `dumpsys deviceidle`, and checks the pending alarm survives. This is the automated half of
 * the Doze verification; the shell transcript in the sprint QA report records the same steps.
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

    private fun hasPendingAlarm(): Boolean =
        shell("dumpsys alarm").contains(context.packageName)

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
