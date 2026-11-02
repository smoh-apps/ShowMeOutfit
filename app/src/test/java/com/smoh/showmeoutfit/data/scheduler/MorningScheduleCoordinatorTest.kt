package com.smoh.showmeoutfit.data.scheduler

import com.smoh.showmeoutfit.domain.model.AgeBand
import com.smoh.showmeoutfit.domain.model.GenderPreference
import com.smoh.showmeoutfit.domain.model.UserProfile
import com.smoh.showmeoutfit.domain.repository.UserRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.time.LocalTime

@OptIn(ExperimentalCoroutinesApi::class)
class MorningScheduleCoordinatorTest {

    private val fakeScheduler = FakeAlarmScheduler()
    private val profileFlow = MutableStateFlow(UserProfile(isConfigured = true))

    private fun coordinator(repository: UserRepository = FakeUserRepository(profileFlow)) =
        MorningScheduleCoordinator(userRepository = repository, alarmScheduler = fakeScheduler)

    @Test
    fun `an enabled configured profile arms the alarm at its stored time`() {
        coordinator().apply(
            UserProfile(
                isConfigured = true,
                isNotificationEnabled = true,
                morningNotificationTime = LocalTime.of(6, 45)
            )
        )

        assertEquals(listOf(LocalTime.of(6, 45)), fakeScheduler.scheduledTimes)
        assertEquals(0, fakeScheduler.cancelCount)
    }

    @Test
    fun `disabling alerts cancels instead of scheduling`() {
        coordinator().apply(
            UserProfile(isConfigured = true, isNotificationEnabled = false)
        )

        assertTrue(fakeScheduler.scheduledTimes.isEmpty())
        assertEquals(1, fakeScheduler.cancelCount)
    }

    @Test
    fun `a profile that never finished onboarding never receives alerts`() {
        coordinator().apply(
            UserProfile(isConfigured = false, isNotificationEnabled = true)
        )

        assertTrue(fakeScheduler.scheduledTimes.isEmpty())
        assertEquals(1, fakeScheduler.cancelCount)
    }

    @Test
    fun `sync reads the latest persisted profile`() = runTest {
        profileFlow.value = UserProfile(
            isConfigured = true,
            isNotificationEnabled = true,
            morningNotificationTime = LocalTime.of(5, 15)
        )

        coordinator().sync()

        assertEquals(listOf(LocalTime.of(5, 15)), fakeScheduler.scheduledTimes)
    }

    @Test
    fun `sync stays silent when the profile cannot be read`() = runTest {
        val failing = object : UserRepository {
            override fun getUserProfile(): Flow<UserProfile> = flow { throw IOException("corrupt") }

            override suspend fun updateUserProfile(profile: UserProfile): Result<Unit> =
                Result.success(Unit)
        }

        coordinator(failing).sync()

        assertTrue(fakeScheduler.scheduledTimes.isEmpty())
        assertEquals(0, fakeScheduler.cancelCount)
    }

    @Test
    fun `starting the observer arms from the current value`() = runTest {
        val coordinator = coordinator()

        coordinator.start(backgroundScope)
        runCurrent()

        assertEquals(listOf(LocalTime.of(7, 0)), fakeScheduler.scheduledTimes)
    }

    @Test
    fun `a settings edit reschedules without a restart`() = runTest {
        val coordinator = coordinator()
        coordinator.start(backgroundScope)
        runCurrent()

        profileFlow.value = profileFlow.value.copy(morningNotificationTime = LocalTime.of(8, 30))
        runCurrent()

        assertEquals(
            listOf(LocalTime.of(7, 0), LocalTime.of(8, 30)),
            fakeScheduler.scheduledTimes
        )
    }

    @Test
    fun `turning alerts off cancels the pending alarm`() = runTest {
        val coordinator = coordinator()
        coordinator.start(backgroundScope)
        runCurrent()

        profileFlow.value = profileFlow.value.copy(isNotificationEnabled = false)
        runCurrent()

        assertEquals(1, fakeScheduler.cancelCount)
    }

    @Test
    fun `an edit that cannot change the schedule does not re-arm the alarm`() = runTest {
        val coordinator = coordinator()
        coordinator.start(backgroundScope)
        runCurrent()

        // Gender and age change the recommendation, not when it is delivered.
        profileFlow.value = profileFlow.value.copy(
            genderPreference = GenderPreference.WOMEN,
            ageBand = AgeBand.ADULT_20_39
        )
        runCurrent()

        assertEquals(1, fakeScheduler.scheduledTimes.size)
        assertEquals(0, fakeScheduler.cancelCount)
    }

    @Test
    fun `a read failure in the observer leaves the pending alarm alone`() = runTest {
        val failingThenHealthy = object : UserRepository {
            private var call = 0
            override fun getUserProfile(): Flow<UserProfile> =
                if (call++ == 0) flow { throw IOException("corrupt") } else profileFlow

            override suspend fun updateUserProfile(profile: UserProfile): Result<Unit> =
                Result.success(Unit)
        }

        coordinator(failingThenHealthy).start(backgroundScope)
        runCurrent()

        // The stream died, so nothing was cancelled and no wrong time was armed.
        assertTrue(fakeScheduler.scheduledTimes.isEmpty())
        assertEquals(0, fakeScheduler.cancelCount)
    }

    private class FakeAlarmScheduler : AlarmScheduler {
        val scheduledTimes = mutableListOf<LocalTime>()
        var cancelCount = 0

        override fun scheduleNext(time: LocalTime) {
            scheduledTimes.add(time)
        }

        override fun cancel() {
            cancelCount++
        }

        override val canScheduleExactAlarms: Boolean = true
    }

    private class FakeUserRepository(
        private val profile: MutableStateFlow<UserProfile>
    ) : UserRepository {
        override fun getUserProfile(): Flow<UserProfile> = profile

        override suspend fun updateUserProfile(profile: UserProfile): Result<Unit> {
            this.profile.value = profile
            return Result.success(Unit)
        }
    }
}
