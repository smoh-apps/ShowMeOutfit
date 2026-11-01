package com.smoh.showmeoutfit.ui.app

import com.smoh.showmeoutfit.domain.model.UserProfile
import com.smoh.showmeoutfit.domain.repository.UserRepository
import com.smoh.showmeoutfit.ui.AppRoutes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `a configured profile starts on home`() = runTest {
        val viewModel = AppViewModel(
            userRepository = FakeUserRepository(profile = UserProfile(isConfigured = true)),
            ioDispatcher = dispatcher
        )
        runCurrent()

        assertEquals(
            AppRoutes.HOME,
            (viewModel.state.value as AppStartState.Ready).startDestination
        )
    }

    @Test
    fun `an unconfigured profile starts on onboarding`() = runTest {
        val viewModel = AppViewModel(
            userRepository = FakeUserRepository(profile = UserProfile(isConfigured = false)),
            ioDispatcher = dispatcher
        )
        runCurrent()

        assertEquals(
            AppRoutes.ONBOARDING,
            (viewModel.state.value as AppStartState.Ready).startDestination
        )
    }

    @Test
    fun `an unreadable profile falls back to onboarding instead of a blank screen`() = runTest {
        val viewModel = AppViewModel(
            userRepository = object : UserRepository {
                override fun getUserProfile(): Flow<UserProfile> =
                    flow { throw IOException("corrupt DataStore") }

                override suspend fun updateUserProfile(profile: UserProfile): Result<Unit> =
                    Result.success(Unit)
            },
            ioDispatcher = dispatcher
        )
        runCurrent()

        assertEquals(
            AppRoutes.ONBOARDING,
            (viewModel.state.value as AppStartState.Ready).startDestination
        )
    }

    private class FakeUserRepository(
        private val profile: UserProfile
    ) : UserRepository {
        private val state = MutableStateFlow(profile)

        override fun getUserProfile(): Flow<UserProfile> = state

        override suspend fun updateUserProfile(profile: UserProfile): Result<Unit> {
            state.value = profile
            return Result.success(Unit)
        }
    }
}
