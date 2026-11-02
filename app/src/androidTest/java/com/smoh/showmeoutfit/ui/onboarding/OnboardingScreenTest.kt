package com.smoh.showmeoutfit.ui.onboarding

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.smoh.showmeoutfit.domain.model.AgeBand
import com.smoh.showmeoutfit.domain.model.GenderPreference
import com.smoh.showmeoutfit.domain.model.UserProfile
import com.smoh.showmeoutfit.domain.repository.UserRepository
import com.smoh.showmeoutfit.domain.usecase.ManageUserProfileUseCase
import com.smoh.showmeoutfit.ui.theme.ShowMeOutfitTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OnboardingScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var userRepository: FakeUserRepository
    private lateinit var viewModel: OnboardingViewModel

    @Before
    fun setUp() {
        userRepository = FakeUserRepository()
        viewModel = OnboardingViewModel(ManageUserProfileUseCase(userRepository))
    }

    @Test
    fun typing_an_age_shows_the_band_and_submitting_persists_it() {
        var completed = false
        composeRule.setContent {
            ShowMeOutfitTheme(dynamicColor = false) {
                OnboardingRoute(onCompleted = { completed = true }, viewModel = viewModel)
            }
        }

        composeRule.onNodeWithTag(OnboardingTestTags.AGE_INPUT).performTextInput("34")
        composeRule.onNodeWithTag(OnboardingTestTags.AGE_BAND).assertIsDisplayed()
        composeRule.onNodeWithTag(OnboardingTestTags.CONTINUE).performClick()

        composeRule.waitUntil(5_000) { completed }
        assertEquals(AgeBand.ADULT_20_39, userRepository.profile.value.ageBand)
        assertTrue(userRepository.profile.value.isConfigured)
    }

    @Test
    fun an_out_of_range_age_blocks_continue_and_explains_why() {
        composeRule.setContent {
            ShowMeOutfitTheme(dynamicColor = false) {
                OnboardingScreen(
                    state = OnboardingUiState(
                        ageInput = "150",
                        ageError = AgeInputError.OUT_OF_RANGE
                    ),
                    onIntent = {}
                )
            }
        }

        // The supporting text is inside the text field's merged semantics on purpose: TalkBack
        // reads the error as part of the field. The tag therefore only exists in the unmerged tree.
        composeRule.onNodeWithTag(OnboardingTestTags.AGE_ERROR, useUnmergedTree = true)
            .assertIsDisplayed()
        composeRule.onNodeWithTag(OnboardingTestTags.CONTINUE).assertIsNotEnabled()
    }

    @Test
    fun skip_completes_with_the_stored_defaults() {
        var completed = false
        composeRule.setContent {
            ShowMeOutfitTheme(dynamicColor = false) {
                OnboardingRoute(onCompleted = { completed = true }, viewModel = viewModel)
            }
        }

        composeRule.onNodeWithTag(OnboardingTestTags.GENDER_MEN).performClick()
        composeRule.onNodeWithTag(OnboardingTestTags.SKIP).performClick()

        composeRule.waitUntil(5_000) { completed }
        assertTrue(userRepository.profile.value.isConfigured)
        // The unconfirmed gender tap is not applied by "Skip for now".
        assertEquals(GenderPreference.ALL, userRepository.profile.value.genderPreference)
    }

    private class FakeUserRepository : UserRepository {
        val profile = MutableStateFlow(UserProfile())

        override fun getUserProfile(): Flow<UserProfile> = profile

        override suspend fun updateUserProfile(profile: UserProfile): Result<Unit> {
            this.profile.value = profile
            return Result.success(Unit)
        }
    }
}
