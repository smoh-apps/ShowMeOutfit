package com.smoh.showmeoutfit.domain.model

/**
 * Expected, user-recoverable failures.
 *
 * Repositories return these through [Result.failure] instead of leaking IO/serialization
 * exceptions into the domain and presentation layers. They carry no user-facing copy: the UI
 * maps each type to a localized message and a recovery action.
 *
 * `CancellationException` is never wrapped in a [DomainError]; it must always propagate.
 */
sealed class DomainError(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause) {

    /** No saved location and no way to obtain one. */
    class NoLocation(cause: Throwable? = null) :
        DomainError("No location available", cause)

    /** The device cannot reach the weather service. */
    class Offline(cause: Throwable? = null) :
        DomainError("Network unavailable", cause)

    /** The service answered, but with an error status or an unusable response. */
    class ApiFailure(cause: Throwable? = null) :
        DomainError("Weather service error", cause)

    /** A response or cached payload could not be parsed. */
    class MalformedData(cause: Throwable? = null) :
        DomainError("Weather data could not be read", cause)

    /** A cached snapshot exists but is too old, from another city, or from another day. */
    class CacheExpired(cause: Throwable? = null) :
        DomainError("Cached weather is out of date", cause)

    /** Anything unexpected; still recoverable through retry. */
    class Unknown(cause: Throwable? = null) :
        DomainError("Unexpected error", cause)
}
