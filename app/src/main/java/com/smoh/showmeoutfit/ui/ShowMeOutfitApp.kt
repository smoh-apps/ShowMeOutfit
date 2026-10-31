package com.smoh.showmeoutfit.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.smoh.showmeoutfit.ui.home.HomeRoute
import com.smoh.showmeoutfit.ui.location.LocationSearchSheet

/**
 * Screen-level destinations of the app graph.
 *
 * Only [HOME] exists today. The graph is here rather than in [MainActivity] so Sprint 3 can add
 * profile onboarding and settings routes without touching the activity again.
 */
object AppRoutes {
    const val HOME = "home"
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
fun ShowMeOutfitApp(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController()
) {
    var showLocationSearch by rememberSaveable { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = AppRoutes.HOME,
            modifier = Modifier.fillMaxSize()
        ) {
            composable(AppRoutes.HOME) {
                HomeRoute(onOpenLocationSearch = { showLocationSearch = true })
            }
        }

        if (showLocationSearch) {
            LocationSearchSheet(onDismissRequest = { showLocationSearch = false })
        }
    }
}
