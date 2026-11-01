package com.smoh.showmeoutfit.ui.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.smoh.showmeoutfit.domain.model.AgeBand
import com.smoh.showmeoutfit.domain.model.LocationInfo
import com.smoh.showmeoutfit.domain.model.UserProfile
import com.smoh.showmeoutfit.domain.repository.UserRepository
import com.smoh.showmeoutfit.domain.usecase.ManageUserProfileUseCase
import com.smoh.showmeoutfit.ui.theme.ShowMeOutfitTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalTime

@RunWith(AndroidJUnit4::class)
class SettingsScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var userRepository: FakeUserRepository
    private lateinit var viewModel: SettingsViewModel

    private val berlin = LocationInfo(52.52, 13.405, "Berlin", "Germany")

    @Before
    fun setUp() {
        userRepository = FakeUserRepository(
            UserProfile(
                selectedLocation = berlin,
                isConfigured = true,
                morningNotificationTime = LocalTime.of(7, 0)
            )
        )
        viewModel = SettingsViewModel(ManageUserProfileUseCase(userRepository))
    }

    private fun showSettings(onBack: () -> Unit = {}) {
        composeRule.setContent {
            ShowMeOutfitTheme(dynamicColor = false) {
                SettingsRoute(onBack = onBack, viewModel = viewModel)
            }
        }
    }

    @Test
    fun toggling_the_alert_persists_the_choice() {
        showSettings()
        composeRule.onNodeWithTag(SettingsTestTags.SCREEN).assertIsDisplayed()

        composeRule.onNodeWithTag(SettingsTestTags.ALERT_SWITCH).performClick()

        composeRule.waitUntil(TIMEOUT_MILLIS) {
            !userRepository.profile.value.isNotificationEnabled
        }
        assertFalse(userRepository.profile.value.isNotificationEnabled)
    }

    @Test
    fun the_time_row_opens_the_picker_and_confirming_writes_the_profile() {
        showSettings()

        composeRule.onNodeWithTag(SettingsTestTags.ALERT_TIME).performClick()
        composeRule.onNodeWithTag(SettingsTestTags.TIME_DIALOG_CONFIRM).assertIsDisplayed()
        composeRule.onNodeWithTag(SettingsTestTags.TIME_DIALOG_CONFIRM).performClick()

        composeRule.waitUntil(TIMEOUT_MILLIS) { userRepository.writeAttempts >= 1 }
        assertEquals(LocalTime.of(7, 0), userRepository.profile.value.morningNotificationTime)
    }

    @Test
    fun editing_the_age_stores_the_derived_band() {
        showSettings()

        composeRule.onNodeWithTag(SettingsTestTags.AGE_EDIT).performClick()
        composeRule.onNodeWithTag(SettingsTestTags.AGE_INPUT).performTextInput("34")
        composeRule.onNodeWithTag(SettingsTestTags.AGE_SAVE).performClick()

        composeRule.waitUntil(TIMEOUT_MILLIS) {
            userRepository.profile.value.ageBand == AgeBand.ADULT_20_39
        }
    }

    @Test
    fun reset_to_gps_clears_the_saved_city() {
        showSettings()

        composeRule.onNodeWithTag(SettingsTestTags.RESET_LOCATION).performClick()

        composeRule.waitUntil(TIMEOUT_MILLIS) {
            userRepository.profile.value.selectedLocation == null
        }
        assertNull(userRepository.profile.value.selectedLocation)
    }

    @Test
    fun the_back_action_leaves_settings() {
        var wentBack = false
        showSettings(onBack = { wentBack = true })

        composeRule.onNodeWithTag(SettingsTestTags.BACK).performClick()

        composeRule.waitUntil(TIMEOUT_MILLIS) { wentBack }
        assertTrue(wentBack)
    }

    private companion object {
        const val TIMEOUT_MILLIS = 5_000L
    }

    private class FakeUserRepository(
        initial: UserProfile
    ) : UserRepository {
        val profile = MutableStateFlow(initial)
        var writeAttempts = 0

        override fun getUserProfile(): Flow<UserProfile> = profile

        override suspend fun updateUserProfile(profile: UserProfile): Result<Unit> {
            writeAttempts++
            this.profile.value = profile
            return Result.success(Unit)
        }
    }
}
