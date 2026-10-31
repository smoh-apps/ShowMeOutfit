package com.smoh.showmeoutfit.ui.location

import com.smoh.showmeoutfit.domain.model.DomainError
import com.smoh.showmeoutfit.domain.model.LocationInfo

/**
 * State of the city-search sheet.
 *
 * Deliberately free of Android types and user-facing copy: the screen resolves every string from
 * a resource id, so the state stays JVM-testable and localization-ready.
 */
data class LocationSearchUiState(
    val query: String = "",
    val isSearching: Boolean = false,
    val hasSearched: Boolean = false,
    val results: List<LocationInfo> = emptyList(),
    val isLocating: Boolean = false,
    val error: DomainError? = null,
    /** The user denied the location permission; the manual search path must stay available. */
    val permissionDenied: Boolean = false
) {
    /** Distinguishes "your query matched nothing" from "you have not searched yet". */
    val showsEmptyResults: Boolean
        get() = hasSearched && !isSearching && results.isEmpty() && error == null
}

sealed interface LocationSearchIntent {
    data class QueryChanged(val query: String) : LocationSearchIntent

    /** IME search action: skips the debounce. */
    data object SearchNow : LocationSearchIntent

    data object UseCurrentLocation : LocationSearchIntent

    data object LocationPermissionDenied : LocationSearchIntent

    data class SelectLocation(val location: LocationInfo) : LocationSearchIntent

    data object DismissError : LocationSearchIntent
}

sealed interface LocationSearchEffect {
    /** The location is already persisted; the caller only needs to dismiss the sheet. */
    data class LocationSelected(val location: LocationInfo) : LocationSearchEffect

    /** Permanent denial: the only way back is the system settings screen. */
    data object OpenAppSettings : LocationSearchEffect
}
