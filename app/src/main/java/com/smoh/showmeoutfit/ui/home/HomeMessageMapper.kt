package com.smoh.showmeoutfit.ui.home

import androidx.annotation.StringRes
import com.smoh.showmeoutfit.R
import com.smoh.showmeoutfit.domain.model.DomainError
import com.smoh.showmeoutfit.domain.model.WeatherOrigin
import java.time.Duration

/**
 * Cache age split into the two units the banner speaks.
 *
 * Pure value object: [CacheAge.of] clamps negative or missing durations to zero so a clock skew or
 * a missing `cacheAge` never renders "-1 hours ago".
 */
data class CacheAge(val hours: Int, val minutes: Int) {

    val isUnderOneHour: Boolean get() = hours == 0

    companion object {
        fun of(age: Duration?): CacheAge {
            val totalMinutes = (age?.seconds ?: 0L).coerceAtLeast(0L) / 60L
            return CacheAge(
                hours = (totalMinutes / 60L).toInt(),
                minutes = (totalMinutes % 60L).toInt()
            )
        }
    }
}

/**
 * Copy for the states that are not the recommendation itself: provenance, errors.
 *
 * Same contract as [GarmentTextMapper]: resource ids only, so no exception text can ever reach the
 * screen and everything is unit-testable on the JVM.
 */
object HomeMessageMapper {

    /**
     * Localized message for a typed failure.
     *
     * A raw `Throwable.message` is never used: [DomainError.Unknown] deliberately collapses to
     * generic copy.
     */
    @StringRes
    fun errorMessageRes(error: DomainError): Int = when (error) {
        is DomainError.NoLocation -> R.string.home_error_no_location
        is DomainError.Offline -> R.string.home_error_offline
        is DomainError.ApiFailure -> R.string.home_error_api
        is DomainError.MalformedData -> R.string.home_error_malformed
        is DomainError.CacheExpired -> R.string.home_error_cache_expired
        is DomainError.Unknown -> R.string.home_error_unknown
    }

    /** Banner copy including the age, or `null` when the snapshot came from the network. */
    @StringRes
    fun cacheBannerWithAgeRes(origin: WeatherOrigin): Int? = when (origin) {
        WeatherOrigin.CACHE_FRESH -> R.string.home_banner_cache_fresh
        WeatherOrigin.CACHE_STALE -> R.string.home_banner_cache_stale
        WeatherOrigin.NETWORK -> null
    }

    /** Banner copy for the case where the repository did not report an age. */
    @StringRes
    fun cacheBannerWithoutAgeRes(origin: WeatherOrigin): Int? = when (origin) {
        WeatherOrigin.CACHE_FRESH -> R.string.home_banner_cache_fresh_no_age
        WeatherOrigin.CACHE_STALE -> R.string.home_banner_cache_stale_no_age
        WeatherOrigin.NETWORK -> null
    }
}
