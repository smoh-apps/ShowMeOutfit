package com.smoh.showmeoutfit.ui.home

import com.smoh.showmeoutfit.domain.model.DomainError
import com.smoh.showmeoutfit.domain.model.LocationInfo
import com.smoh.showmeoutfit.domain.model.TodayOutfit

/**
 * What the Home screen renders.
 *
 * [Content] already carries provenance through `todayOutfit.weather.origin`, so the banner and
 * badge logic never has to infer freshness from timestamps.
 */
sealed interface HomeUiState {

    data object Loading : HomeUiState

    data class Content(
        val outfit: TodayOutfit,
        val isRefreshing: Boolean = false
    ) : HomeUiState

    /** No location and no cached recommendation; the user must pick a city. */
    data object Empty : HomeUiState

    data class Error(val error: DomainError) : HomeUiState
}

sealed interface HomeIntent {
    data object Load : HomeIntent

    data object Refresh : HomeIntent

    data class SelectLocation(val location: LocationInfo) : HomeIntent

    data object DismissError : HomeIntent
}

sealed interface HomeEffect {
    data class ShowMessage(val error: DomainError) : HomeEffect

    data object OpenLocationSearch : HomeEffect
}
