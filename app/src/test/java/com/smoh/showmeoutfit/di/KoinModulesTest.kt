package com.smoh.showmeoutfit.di

import com.smoh.showmeoutfit.data.local.datasource.LocalWeatherDataSource
import com.smoh.showmeoutfit.data.remote.datasource.RemoteWeatherDataSource
import com.smoh.showmeoutfit.domain.engine.IOutfitRecommendationEngine
import com.smoh.showmeoutfit.domain.engine.RuleBasedPresetEngine
import com.smoh.showmeoutfit.domain.repository.LocationRepository
import com.smoh.showmeoutfit.domain.repository.UserRepository
import com.smoh.showmeoutfit.domain.repository.WeatherRepository
import com.smoh.showmeoutfit.domain.usecase.GetTodayOutfitRecommendationUseCase
import com.smoh.showmeoutfit.domain.usecase.ManageUserProfileUseCase
import com.smoh.showmeoutfit.domain.usecase.SearchLocationUseCase
import io.mockk.mockk
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.koin.test.KoinTest
import org.koin.test.inject

class KoinModulesTest : KoinTest {

    private val engine: IOutfitRecommendationEngine by inject()
    private val getTodayOutfitRecommendationUseCase: GetTodayOutfitRecommendationUseCase by inject()
    private val manageUserProfileUseCase: ManageUserProfileUseCase by inject()
    private val searchLocationUseCase: SearchLocationUseCase by inject()

    @Before
    fun setUp() {
        val testMockModule = module {
            single<RemoteWeatherDataSource> { mockk(relaxed = true) }
            single<LocalWeatherDataSource> { mockk(relaxed = true) }
            single<WeatherRepository> { mockk(relaxed = true) }
            single<UserRepository> { mockk(relaxed = true) }
            single<LocationRepository> { mockk(relaxed = true) }
        }

        startKoin {
            modules(
                testMockModule,
                engineModule,
                useCaseModule
            )
        }
    }

    @After
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun `engineModule resolves RuleBasedPresetEngine as IOutfitRecommendationEngine`() {
        assertNotNull(engine)
        assertTrue(engine is RuleBasedPresetEngine)
    }

    @Test
    fun `useCaseModule resolves all domain use cases with injected dependencies`() {
        assertNotNull(getTodayOutfitRecommendationUseCase)
        assertNotNull(manageUserProfileUseCase)
        assertNotNull(searchLocationUseCase)
    }
}
