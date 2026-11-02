package com.smoh.showmeoutfit.ui.onboarding

import app.cash.turbine.test
import com.smoh.showmeoutfit.domain.model.AgeBand
import com.smoh.showmeoutfit.domain.model.GenderPreference
import com.smoh.showmeoutfit.domain.model.LocationInfo
import com.smoh.showmeoutfit.domain.model.ThermalSensitivity
import com.smoh.showmeoutfit.domain.model.UserProfile
import com.smoh.showmeoutfit.domain.repository.UserRepository
import com.smoh.showmeoutfit.domain.usecase.ManageUserProfileUseCase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.time.LocalTime

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var userRepository: FakeUserRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        userRepository = FakeUserRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = OnboardingViewModel(
        manageUserProfile = ManageUserProfileUseCase(userRepository),
        ioDispatcher = dispatcher
    )

    @Test
    fun `starts with neutral defaults and allows continuing with no age`() {
        val state = viewModel().state.value

        assertEquals(GenderPreference.ALL, state.gender)
        assertEquals(AgeBand.UNSPECIFIED, state.ageBand)
        assertEquals(ThermalSensitivity.NEUTRAL, state.thermalSensitivity)
        assertEquals("", state.ageInput)
        assertNull(state.ageError)
        assertTrue(state.canContinue)
    }

    @Test
    fun `typed age is derived into the matching band immediately`() {
        val viewModel = viewModel()

        viewModel.onIntent(OnboardingIntent.AgeChanged("34"))

        assertEquals("34", viewModel.state.value.ageInput)
        assertEquals(AgeBand.ADULT_20_39, viewModel.state.value.ageBand)
        assertNull(viewModel.state.value.ageError)
    }

    @Test
    fun `age band boundaries map to the documented bands`() {
        val viewModel = viewModel()
        val cases = listOf(
            0 to AgeBand.CHILD_0_5,
            5 to AgeBand.CHILD_0_5,
            6 to AgeBand.CHILD_6_12,
            12 to AgeBand.CHILD_6_12,
            13 to AgeBand.TEEN_13_19,
            19 to AgeBand.TEEN_13_19,
            20 to AgeBand.ADULT_20_39,
            39 to AgeBand.ADULT_20_39,
            40 to AgeBand.ADULT_40_59,
            59 to AgeBand.ADULT_40_59,
            60 to AgeBand.SENIOR_60_PLUS,
            120 to AgeBand.SENIOR_60_PLUS
        )

        cases.forEach { (years, expected) ->
            viewModel.onIntent(OnboardingIntent.AgeChanged(years.toString()))
            assertEquals("age $years", expected, viewModel.state.value.ageBand)
        }
    }

    @Test
    fun `non-digit characters are stripped instead of rejected`() {
        val viewModel = viewModel()

        viewModel.onIntent(OnboardingIntent.AgeChanged("3a4 years"))

        assertEquals("34", viewModel.state.value.ageInput)
        assertEquals(AgeBand.ADULT_20_39, viewModel.state.value.ageBand)
    }

    @Test
    fun `blank age clears the band and stays valid`() {
        val viewModel = viewModel()
        viewModel.onIntent(OnboardingIntent.AgeChanged("34"))

        viewModel.onIntent(OnboardingIntent.AgeChanged(""))

        assertEquals(AgeBand.UNSPECIFIED, viewModel.state.value.ageBand)
        assertNull(viewModel.state.value.ageError)
        assertTrue(viewModel.state.value.canContinue)
    }

    @Test
    fun `out-of-range age blocks the primary action and clears the derived band`() {
        val viewModel = viewModel()

        viewModel.onIntent(OnboardingIntent.AgeChanged("150"))

        assertEquals(AgeInputError.OUT_OF_RANGE, viewModel.state.value.ageError)
        assertEquals(AgeBand.UNSPECIFIED, viewModel.state.value.ageBand)
        assertFalse(viewModel.state.value.canContinue)
    }

    @Test
    fun `more than three digits is rejected rather than truncated`() {
        val viewModel = viewModel()

        viewModel.onIntent(OnboardingIntent.AgeChanged("1200"))

        assertEquals("1200", viewModel.state.value.ageInput)
        assertEquals(AgeInputError.OUT_OF_RANGE, viewModel.state.value.ageError)
        assertFalse(viewModel.state.value.canContinue)
    }

    @Test
    fun `submit persists the form as a configured profile and emits completion`() = runTest {
        val viewModel = viewModel()
        viewModel.onIntent(OnboardingIntent.GenderSelected(GenderPreference.WOMEN))
        viewModel.onIntent(OnboardingIntent.AgeChanged("28"))
        viewModel.onIntent(OnboardingIntent.SensitivitySelected(ThermalSensitivity.FEELS_WARM_EASILY))

        viewModel.effects.test {
            viewModel.onIntent(OnboardingIntent.Submit)
            assertEquals(OnboardingEffect.Completed, awaitItem())
        }

        val saved = userRepository.profile.value
        assertTrue(saved.isConfigured)
        assertEquals(GenderPreference.WOMEN, saved.genderPreference)
        assertEquals(AgeBand.ADULT_20_39, saved.ageBand)
        assertEquals(ThermalSensitivity.FEELS_WARM_EASILY, saved.thermalSensitivity)
    }

    @Test
    fun `submit preserves preferences owned by other screens`() = runTest {
        val location = LocationInfo(52.52, 13.405, "Berlin", "Germany")
        userRepository = FakeUserRepository(
            UserProfile(
                selectedLocation = location,
                morningNotificationTime = LocalTime.of(6, 30),
                isNotificationEnabled = false,
                isConfigured = false
            )
        )
        val viewModel = viewModel()
        viewModel.onIntent(OnboardingIntent.AgeChanged("41"))

        viewModel.onIntent(OnboardingIntent.Submit)
        runCurrent()

        val saved = userRepository.profile.value
        assertEquals(location, saved.selectedLocation)
        assertEquals(LocalTime.of(6, 30), saved.morningNotificationTime)
        assertFalse(saved.isNotificationEnabled)
    }

    @Test
    fun `skip marks the profile configured without applying unconfirmed edits`() = runTest {
        val viewModel = viewModel()
        viewModel.onIntent(OnboardingIntent.GenderSelected(GenderPreference.MEN))
        viewModel.onIntent(OnboardingIntent.AgeChanged("70"))

        viewModel.onIntent(OnboardingIntent.Skip)
        runCurrent()

        val saved = userRepository.profile.value
        assertTrue(saved.isConfigured)
        assertEquals(GenderPreference.ALL, saved.genderPreference)
        assertEquals(AgeBand.UNSPECIFIED, saved.ageBand)
    }

    @Test
    fun `a failed save is state, not a silent success`() = runTest {
        userRepository.failWrites = true
        val viewModel = viewModel()

        viewModel.onIntent(OnboardingIntent.AgeChanged("30"))
        viewModel.onIntent(OnboardingIntent.Submit)
        runCurrent()

        assertFalse(viewModel.state.value.isSaving)
        assertTrue(viewModel.state.value.saveFailed)
        assertFalse(userRepository.profile.value.isConfigured)
        assertEquals(0, userRepository.successfulWrites)
    }

    @Test
    fun `a second submit while a save is in flight is ignored`() = runTest {
        val gate = CompletableDeferred<Unit>()
        userRepository.writeGate = gate
        val viewModel = viewModel()
        viewModel.onIntent(OnboardingIntent.AgeChanged("30"))

        viewModel.onIntent(OnboardingIntent.Submit)
        viewModel.onIntent(OnboardingIntent.Submit)
        viewModel.onIntent(OnboardingIntent.Skip)
        gate.complete(Unit)
        runCurrent()

        assertEquals(1, userRepository.writeAttempts)
    }

    @Test
    fun `selecting gender and sensitivity updates the form`() {
        val viewModel = viewModel()

        viewModel.onIntent(OnboardingIntent.GenderSelected(GenderPreference.MEN))
        viewModel.onIntent(OnboardingIntent.SensitivitySelected(ThermalSensitivity.FEELS_COLD_EASILY))

        assertEquals(GenderPreference.MEN, viewModel.state.value.gender)
        assertEquals(
            ThermalSensitivity.FEELS_COLD_EASILY,
            viewModel.state.value.thermalSensitivity
        )
    }

    private class FakeUserRepository(
        initial: UserProfile = UserProfile()
    ) : UserRepository {
        val profile = MutableStateFlow(initial)
        var failWrites = false
        var writeGate: CompletableDeferred<Unit>? = null
        var writeAttempts = 0
        var successfulWrites = 0

        override fun getUserProfile(): Flow<UserProfile> = profile

        override suspend fun updateUserProfile(profile: UserProfile): Result<Unit> {
            writeAttempts++
            writeGate?.await()
            if (failWrites) return Result.failure(IOException("disk full"))
            this.profile.value = profile
            successfulWrites++
            return Result.success(Unit)
        }
    }
}
