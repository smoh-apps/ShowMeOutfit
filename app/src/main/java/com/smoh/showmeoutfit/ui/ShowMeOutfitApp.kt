package com.smoh.showmeoutfit.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.smoh.showmeoutfit.ui.app.AppStartState
import com.smoh.showmeoutfit.ui.app.AppStartupLoading
import com.smoh.showmeoutfit.ui.app.AppViewModel
import com.smoh.showmeoutfit.ui.home.HomeRoute
import com.smoh.showmeoutfit.ui.location.LocationSearchSheet
import com.smoh.showmeoutfit.ui.onboarding.OnboardingRoute
import com.smoh.showmeoutfit.ui.settings.SettingsRoute
import org.koin.androidx.compose.koinViewModel

/**
 * Screen-level destinations of the app graph.
 *
 * Onboarding is a real destination rather than a modal overlay: its lifecycle is independent of
 * Home, it can be tested as a route, and completing it clears itself from the back stack.
 */
object AppRoutes {
    const val HOME = "home"
    const val ONBOARDING = "onboarding"
    const val SETTINGS = "settings"
}

/**
 * Stateful entry point. [AppViewModel] reads the profile once and decides the graph's start
 * destination; the rest of the shell is stateless with respect to startup.
 */
@Composable
fun ShowMeOutfitApp(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    appViewModel: AppViewModel = koinViewModel()
) {
    val startState by appViewModel.state.collectAsStateWithLifecycle()

    when (val state = startState) {
        AppStartState.Loading -> AppStartupLoading(modifier = modifier)

        is AppStartState.Ready -> ShowMeOutfitAppContent(
            startDestination = state.startDestination,
            navController = navController,
            modifier = modifier
        )
    }
}

/**
 * Application shell: the navigation graph plus the overlays that sit above every screen.
 *
 * The city search is deliberately **not** a graph destination. It is a modal surface that covers
 * whatever screen is showing, so it lives beside the [NavHost] and is keyed by saveable state. That
 * keeps it dismissible with back, keeps the dashboard's state untouched while it is open, and means
 * the search can be launched from any future screen without adding an edge per screen.
 *
 * The sheet owns its own selection: it persists the chosen city and then calls [onDismissRequest],
 * and the dashboard refreshes through its existing profile observer, so the shell never has to
 * carry the result back.
 */
@Composable
fun ShowMeOutfitAppContent(
    startDestination: String,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController()
) {
    var showLocationSearch by rememberSaveable { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.fillMaxSize()
        ) {
            composable(AppRoutes.HOME) {
                HomeRoute(
                    onOpenLocationSearch = { showLocationSearch = true },
                    onOpenSettings = { navController.navigate(AppRoutes.SETTINGS) }
                )
            }
            composable(AppRoutes.SETTINGS) {
                SettingsRoute(onBack = { navController.popBackStack() })
            }
            composable(AppRoutes.ONBOARDING) {
                OnboardingRoute(
                    onCompleted = {
                        navController.navigate(AppRoutes.HOME) {
                            // Onboarding is a first-run gate, never a place to go back to.
                            popUpTo(AppRoutes.ONBOARDING) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                )
            }
        }

        if (showLocationSearch) {
            LocationSearchSheet(onDismissRequest = { showLocationSearch = false })
        }
    }
}
