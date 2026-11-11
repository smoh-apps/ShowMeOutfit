package com.smoh.showmeoutfit.ui.settings

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
class SettingsViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var userRepository: FakeUserRepository
    private lateinit var localeController: FakeLocaleController

    private val berlin = LocationInfo(52.52, 13.405, "Berlin", "Germany")

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        localeController = FakeLocaleController()
        userRepository = FakeUserRepository(
            UserProfile(
                selectedLocation = berlin,
                isConfigured = true,
                morningNotificationTime = LocalTime.of(7, 0)
            )
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = SettingsViewModel(
        manageUserProfile = ManageUserProfileUseCase(userRepository),
        ioDispatcher = dispatcher,
        localeController = localeController
    )

    @Test
    fun `state starts from the persisted profile`() = runTest {
        val viewModel = viewModel()
        runCurrent()

        assertFalse(viewModel.state.value.isLoading)
        assertEquals(berlin, viewModel.state.value.profile.selectedLocation)
        assertEquals(LocalTime.of(7, 0), viewModel.state.value.profile.morningNotificationTime)
    }

    @Test
    fun `toggling the morning alert persists it`() = runTest {
        val viewModel = viewModel()
        runCurrent()

        viewModel.onIntent(SettingsIntent.SetNotificationsEnabled(false))
        runCurrent()

        assertFalse(userRepository.profile.value.isNotificationEnabled)
        assertFalse(viewModel.state.value.profile.isNotificationEnabled)
    }

    @Test
    fun `changing the delivery time persists it`() = runTest {
        val viewModel = viewModel()
        runCurrent()

        viewModel.onIntent(SettingsIntent.SetNotificationTime(LocalTime.of(6, 45)))
        runCurrent()

        assertEquals(LocalTime.of(6, 45), userRepository.profile.value.morningNotificationTime)
    }

    @Test
    fun `thermal sensitivity and gender persist`() = runTest {
        val viewModel = viewModel()
        runCurrent()

        viewModel.onIntent(SettingsIntent.SetThermalSensitivity(ThermalSensitivity.FEELS_WARM_EASILY))
        viewModel.onIntent(SettingsIntent.SetGender(GenderPreference.WOMEN))
        runCurrent()

        assertEquals(
            ThermalSensitivity.FEELS_WARM_EASILY,
            userRepository.profile.value.thermalSensitivity
        )
        assertEquals(GenderPreference.WOMEN, userRepository.profile.value.genderPreference)
    }

    @Test
    fun `setting an age persists only the derived band`() = runTest {
        val viewModel = viewModel()
        runCurrent()

        viewModel.onIntent(SettingsIntent.SetAge(34))
        runCurrent()

        assertEquals(AgeBand.ADULT_20_39, userRepository.profile.value.ageBand)
    }

    @Test
    fun `clearing the age returns to the unspecified band`() = runTest {
        userRepository.profile.value = userRepository.profile.value.copy(ageBand = AgeBand.ADULT_20_39)
        val viewModel = viewModel()
        runCurrent()

        viewModel.onIntent(SettingsIntent.SetAge(null))
        runCurrent()

        assertEquals(AgeBand.UNSPECIFIED, userRepository.profile.value.ageBand)
    }

    @Test
    fun `an out-of-range age is rejected without a write`() = runTest {
        val viewModel = viewModel()
        runCurrent()
        val before = userRepository.profile.value

        viewModel.onIntent(SettingsIntent.SetAge(121))
        runCurrent()

        assertEquals(0, userRepository.writeAttempts)
        assertEquals(before, userRepository.profile.value)
    }

    @Test
    fun `reset to GPS clears the saved city but keeps every other preference`() = runTest {
        val viewModel = viewModel()
        runCurrent()

        viewModel.onIntent(SettingsIntent.ResetLocationToGps)
        runCurrent()

        val saved = userRepository.profile.value
        assertNull(saved.selectedLocation)
        assertEquals(LocalTime.of(7, 0), saved.morningNotificationTime)
        assertTrue(saved.isConfigured)
    }

    @Test
    fun `two rapid edits are serialized and both survive`() = runTest {        val gate = CompletableDeferred<Unit>()
        userRepository.writeGate = gate
        val viewModel = viewModel()
        runCurrent()

        viewModel.onIntent(SettingsIntent.SetNotificationsEnabled(false))
        viewModel.onIntent(SettingsIntent.SetNotificationTime(LocalTime.of(5, 30)))
        gate.complete(Unit)
        runCurrent()

        val saved = userRepository.profile.value
        assertFalse("the toggle must not be lost", saved.isNotificationEnabled)
        assertEquals("the time must not be lost", LocalTime.of(5, 30), saved.morningNotificationTime)
    }

    @Test
    fun `a failed write is surfaced and can be dismissed`() = runTest {
        userRepository.failWrites = true
        val viewModel = viewModel()
        runCurrent()

        viewModel.onIntent(SettingsIntent.SetNotificationsEnabled(false))
        runCurrent()

        assertTrue(viewModel.state.value.saveFailed)
        assertFalse(viewModel.state.value.isSaving)

        viewModel.onIntent(SettingsIntent.DismissSaveError)

        assertFalse(viewModel.state.value.saveFailed)
    }

    // --- Language picker (SMO-S5-04) --------------------------------------------------------

    @Test
    fun `the initial language comes from the locale controller`() = runTest {
        localeController = FakeLocaleController(AppLanguage.FRENCH)

        val viewModel = viewModel()
        runCurrent()

        assertEquals(AppLanguage.FRENCH, viewModel.state.value.language)
        assertEquals(0, localeController.setCalls)
    }

    @Test
    fun `switching to Vietnamese updates the state and writes through the controller once`() =
        runTest {
            val viewModel = viewModel()
            runCurrent()
            assertEquals(AppLanguage.SYSTEM, viewModel.state.value.language)

            viewModel.onIntent(SettingsIntent.SetLanguage(AppLanguage.VIETNAMESE))

            assertEquals(AppLanguage.VIETNAMESE, viewModel.state.value.language)
            assertEquals(1, localeController.setCalls)
            assertEquals(AppLanguage.VIETNAMESE, localeController.saved)
        }

    @Test
    fun `switching language does not touch the persisted profile`() = runTest {
        val viewModel = viewModel()
        runCurrent()
        val before = userRepository.profile.value

        viewModel.onIntent(SettingsIntent.SetLanguage(AppLanguage.FRENCH))
        runCurrent()

        assertEquals(0, userRepository.writeAttempts)
        assertEquals(before, userRepository.profile.value)
    }

    private class FakeLocaleController(
        initial: AppLanguage = AppLanguage.SYSTEM
    ) : AppLocaleController {
        var saved: AppLanguage = initial
        var setCalls = 0

        override fun currentLanguage(): AppLanguage = saved

        override fun setLanguage(language: AppLanguage) {
            saved = language
            setCalls++
        }
    }

    private class FakeUserRepository(
        initial: UserProfile = UserProfile()
    ) : UserRepository {
        val profile = MutableStateFlow(initial)
        var failWrites = false
        var writeGate: CompletableDeferred<Unit>? = null
        var writeAttempts = 0

        override fun getUserProfile(): Flow<UserProfile> = profile

        override suspend fun updateUserProfile(profile: UserProfile): Result<Unit> {
            writeAttempts++
            writeGate?.await()
            if (failWrites) return Result.failure(IOException("disk full"))
            this.profile.value = profile
            return Result.success(Unit)
        }
    }
}
