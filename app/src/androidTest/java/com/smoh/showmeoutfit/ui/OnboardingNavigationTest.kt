package com.smoh.showmeoutfit.ui

import android.content.Context
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.smoh.showmeoutfit.MainActivity
import com.smoh.showmeoutfit.di.dispatcherModule
import com.smoh.showmeoutfit.di.engineModule
import com.smoh.showmeoutfit.di.presentationModule
import com.smoh.showmeoutfit.di.useCaseModule
import com.smoh.showmeoutfit.domain.model.DomainError
import com.smoh.showmeoutfit.domain.model.LocationInfo
import com.smoh.showmeoutfit.domain.model.UserProfile
import com.smoh.showmeoutfit.domain.model.WeatherData
import com.smoh.showmeoutfit.domain.repository.LocationRepository
import com.smoh.showmeoutfit.domain.repository.UserRepository
import com.smoh.showmeoutfit.domain.repository.WeatherRepository
import com.smoh.showmeoutfit.ui.home.HomeTestTags
import com.smoh.showmeoutfit.ui.onboarding.OnboardingTestTags
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module

/**
 * First-run routing through the real activity, shell, navigation graph and ViewModels.
 *
 * The only fakes are the repository interfaces; [AppViewModel] still makes the start-destination
 * decision from the persisted profile, so removing the onboarding route or the back-stack
 * clearance fails this test.
 */
@RunWith(AndroidJUnit4::class)
class OnboardingNavigationTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var scenario: ActivityScenario<MainActivity>
    private val userRepository = FakeUserRepository()

    @Before
    fun setUp() {
        stopKoin()
        startKoin {
            androidContext(context.applicationContext)
            modules(
                module {
                    single<UserRepository> { userRepository }
                    single<WeatherRepository> { FakeWeatherRepository() }
                    single<LocationRepository> { FakeLocationRepository() }
                },
                dispatcherModule,
                engineModule,
                useCaseModule,
                presentationModule
            )
        }
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    @After
    fun tearDown() {
        scenario.close()
        stopKoin()
    }

    @Test
    fun first_launch_opens_onboarding_and_finishing_it_clears_the_back_stack() {
        composeRule.waitUntil(TIMEOUT_MILLIS) {
            composeRule.onAllNodesWithTag(OnboardingTestTags.SCREEN).fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithTag(OnboardingTestTags.SKIP).performClick()

        // Skip persists a configured profile; the effect navigates to Home and removes onboarding.
        composeRule.waitUntil(TIMEOUT_MILLIS) {
            composeRule.onAllNodesWithTag(HomeTestTags.EMPTY).fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(
            "skip must mark the profile configured",
            true,
            userRepository.profile.value.isConfigured
        )
        assertTrueNoNode(OnboardingTestTags.SCREEN)

        // Back must leave the app, not return to the first-run gate.
        scenario.onActivity { activity ->
            activity.onBackPressedDispatcher.onBackPressed()
        }
        composeRule.waitUntil(TIMEOUT_MILLIS) {
            scenario.state == Lifecycle.State.DESTROYED
        }
    }

    private fun assertTrueNoNode(tag: String) {
        assertEquals(
            0,
            composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().size
        )
    }

    private companion object {
        const val TIMEOUT_MILLIS = 10_000L
    }

    private class FakeUserRepository : UserRepository {
        val profile = MutableStateFlow(UserProfile(isConfigured = false))

        override fun getUserProfile(): Flow<UserProfile> = profile

        override suspend fun updateUserProfile(profile: UserProfile): Result<Unit> {
            this.profile.value = profile
            return Result.success(Unit)
        }
    }

    private class FakeWeatherRepository : WeatherRepository {
        override suspend fun getTodayWeather(
            location: LocationInfo,
            forceRefresh: Boolean
        ): Result<WeatherData> = Result.failure(DomainError.Offline())

        override suspend fun getCachedWeather(): WeatherData? = null
    }

    private class FakeLocationRepository : LocationRepository {
        override suspend fun getCurrentLocation(): Result<LocationInfo> =
            Result.failure(DomainError.LocationUnavailable())

        override suspend fun searchLocation(query: String): Result<List<LocationInfo>> =
            Result.success(emptyList())
    }
}
