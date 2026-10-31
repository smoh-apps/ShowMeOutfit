package com.smoh.showmeoutfit.ui

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.smoh.showmeoutfit.MainActivity
import com.smoh.showmeoutfit.R
import com.smoh.showmeoutfit.di.appModules
import com.smoh.showmeoutfit.di.engineModule
import com.smoh.showmeoutfit.di.presentationModule
import com.smoh.showmeoutfit.di.useCaseModule
import com.smoh.showmeoutfit.domain.model.DomainError
import com.smoh.showmeoutfit.domain.model.LocationInfo
import com.smoh.showmeoutfit.domain.model.UserProfile
import com.smoh.showmeoutfit.domain.model.WeatherData
import com.smoh.showmeoutfit.domain.model.WeatherOrigin
import com.smoh.showmeoutfit.domain.repository.LocationRepository
import com.smoh.showmeoutfit.domain.repository.UserRepository
import com.smoh.showmeoutfit.domain.repository.WeatherRepository
import com.smoh.showmeoutfit.ui.home.HomeTestTags
import com.smoh.showmeoutfit.ui.home.previewTodayOutfit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import java.time.Duration

/**
 * End-to-end coverage of the wired application.
 *
 * The activity is launched for real, so the test exercises [MainActivity] → [ShowMeOutfitApp] →
 * `NavHost` → `HomeRoute` → `HomeViewModel` → `GetTodayOutfitRecommendationUseCase` → repository,
 * plus the modal `LocationSearchSheet` and its own ViewModel. Only the three repository
 * interfaces are faked: everything between them and the screen is production code wired by the
 * real Koin modules.
 */
@RunWith(AndroidJUnit4::class)
class ShowMeOutfitAppTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

    private lateinit var scenario: ActivityScenario<MainActivity>

    private val userRepository = FakeUserRepository()
    private val weatherRepository = FakeWeatherRepository()
    private val locationRepository = FakeLocationRepository()

    @Before
    fun setUp() {
        // The application started the real graph; swap only the repository boundary.
        stopKoin()
        startKoin {
            androidContext(context.applicationContext)
            modules(
                module {
                    single<UserRepository> { userRepository }
                    single<WeatherRepository> { weatherRepository }
                    single<LocationRepository> { locationRepository }
                },
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
        // Leave the process with the production graph for any later test class.
        startKoin {
            androidContext(context.applicationContext)
            modules(appModules)
        }
    }

    @Test
    fun empty_state_opens_city_search_and_the_dashboard_fills_in() {
        // No saved city and no device fix: the only recovery is the picker.
        waitForText(text(R.string.home_empty_title))
        composeRule.onNodeWithText(text(R.string.home_empty_action)).performClick()

        waitForText(text(R.string.location_search_title))
        composeRule.onNode(hasSetTextAction()).performTextInput("Ber")

        // The 350 ms debounce runs on the real clock, so poll rather than assume a frame.
        waitForText("Berlin, Germany")

        composeRule.onNodeWithText("Berlin, Germany").performClick()

        composeRule.waitUntil(TIMEOUT_MILLIS) {
            composeRule.onAllNodesWithTag(HomeTestTags.CONTENT).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Berlin").assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.location_search_title)).assertDoesNotExist()

        // The network snapshot must not be dressed up as cached data.
        composeRule.onNodeWithTag(HomeTestTags.CACHE_BANNER).assertDoesNotExist()
    }

    @Test
    fun a_stale_snapshot_is_announced_as_cached_data_end_to_end() {
        userRepository.profile.value = UserProfile(selectedLocation = BERLIN)
        weatherRepository.origin = WeatherOrigin.CACHE_STALE
        weatherRepository.cacheAge = Duration.ofHours(5).plusMinutes(20)

        composeRule.waitUntil(TIMEOUT_MILLIS) {
            composeRule.onAllNodesWithTag(HomeTestTags.CACHE_BANNER).fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithTag(HomeTestTags.CACHE_BANNER).assertIsDisplayed()
        // Exact copy: the age must be rendered from the reported provenance, not invented.
        composeRule.onNodeWithText(
            text(R.string.home_banner_cache_stale, "5 hours 20 minutes")
        ).assertIsDisplayed()
    }

    private fun text(id: Int, vararg args: String): String = context.getString(id, *args)

    private fun waitForText(value: String) {
        composeRule.waitUntil(TIMEOUT_MILLIS) {
            composeRule.onAllNodesWithText(value).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private companion object {
        const val TIMEOUT_MILLIS = 10_000L
        val BERLIN = LocationInfo(
            latitude = 52.52,
            longitude = 13.405,
            cityName = "Berlin",
            country = "Germany",
            adminRegion = "Berlin",
            timezone = "Europe/Berlin"
        )
    }

    private class FakeUserRepository : UserRepository {
        val profile = MutableStateFlow(UserProfile(selectedLocation = null))

        override fun getUserProfile(): Flow<UserProfile> = profile

        override suspend fun updateUserProfile(profile: UserProfile): Result<Unit> {
            this.profile.value = profile
            return Result.success(Unit)
        }
    }

    private class FakeWeatherRepository : WeatherRepository {
        var origin: WeatherOrigin = WeatherOrigin.NETWORK
        var cacheAge: Duration? = null

        override suspend fun getTodayWeather(
            location: LocationInfo,
            forceRefresh: Boolean
        ): Result<WeatherData> {
            val base: WeatherData = previewTodayOutfit().weather
            return Result.success(
                base.copy(
                    snapshot = base.snapshot.copy(location = location),
                    origin = origin,
                    cacheAge = cacheAge
                )
            )
        }

        override suspend fun getCachedWeather(): WeatherData? = null
    }

    private class FakeLocationRepository : LocationRepository {
        override suspend fun getCurrentLocation(): Result<LocationInfo> =
            Result.failure(DomainError.LocationUnavailable())

        override suspend fun searchLocation(query: String): Result<List<LocationInfo>> =
            Result.success(if (query.isBlank()) emptyList() else listOf(BERLIN))
    }
}
