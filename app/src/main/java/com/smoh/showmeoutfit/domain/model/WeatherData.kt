package com.smoh.showmeoutfit.domain.model

import java.time.Duration

/**
 * Where the weather snapshot the user is looking at came from.
 *
 * The UI must never present cached data as live data, so provenance travels with every
 * successful read instead of being inferred from timestamps in the presentation layer.
 */
enum class WeatherOrigin {
    /** Freshly fetched from Open-Meteo during this call. */
    NETWORK,

    /** Same location, same forecast date, fetched less than 3 hours ago. */
    CACHE_FRESH,

    /** Same location, same forecast date, fetched less than 12 hours ago, served after a network failure. */
    CACHE_STALE
}

/**
 * A weather snapshot plus how old it is and where it came from.
 *
 * @property cacheAge elapsed time since [snapshot] was fetched; `null` when [origin] is
 * [WeatherOrigin.NETWORK].
 */
data class WeatherData(
    val snapshot: WeatherSnapshot,
    val origin: WeatherOrigin,
    val cacheAge: Duration? = null
) {
    val isFromCache: Boolean
        get() = origin != WeatherOrigin.NETWORK
}
