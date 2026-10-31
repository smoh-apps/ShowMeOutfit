package com.smoh.showmeoutfit.data.location

import com.smoh.showmeoutfit.domain.model.LocationInfo

/**
 * Thin abstraction over the platform location provider.
 *
 * Kept behind an interface so [com.smoh.showmeoutfit.data.repository.LocationRepositoryImpl] can be
 * tested on the JVM without Robolectric or Play Services.
 */
interface DeviceLocationSource {

    /**
     * A cached fix, if the platform has one. Must return quickly (no network, no waiting).
     * Returns `null` when no fix is cached.
     */
    suspend fun getLastKnown(): LocationInfo?

    /**
     * Requests a single current fix, bounded by [timeoutMillis].
     *
     * Fails with a [com.smoh.showmeoutfit.domain.model.DomainError] subtype instead of throwing:
     * permission denied, location services disabled, or no fix within the timeout.
     */
    suspend fun getCurrent(timeoutMillis: Long): Result<LocationInfo>
}
