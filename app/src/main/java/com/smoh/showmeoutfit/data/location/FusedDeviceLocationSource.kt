package com.smoh.showmeoutfit.data.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.gms.tasks.Task
import com.smoh.showmeoutfit.domain.model.DomainError
import com.smoh.showmeoutfit.domain.model.LocationInfo
import java.util.Locale
import java.util.TimeZone
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.cancellation.CancellationException

/**
 * Play Services location implementation.
 *
 * Deliberately foreground-only: it never registers a location callback and never requests
 * background location, so the app cannot be woken by location updates.
 */
class FusedDeviceLocationSource(
    private val context: Context
) : DeviceLocationSource {

    private val client by lazy { LocationServices.getFusedLocationProviderClient(context) }

    @SuppressLint("MissingPermission") // Guarded by hasPermission() before every call.
    override suspend fun getLastKnown(): LocationInfo? {
        if (!hasLocationPermission()) return null

        // Bounded: a platform "last known" that never answers must not stall the dashboard.
        val location = withTimeoutOrNull(LAST_KNOWN_TIMEOUT_MILLIS) {
            client.lastLocation.awaitOrNull()
        } ?: return null

        // A cached fix is only representative of where the user is now if it is recent. Older
        // fixes are discarded so the caller requests a fresh one instead of showing another city.
        if (!location.isRecentEnough()) return null

        return location.toLocationInfo()
    }

    @SuppressLint("MissingPermission")
    override suspend fun getCurrent(timeoutMillis: Long): Result<LocationInfo> = withContext(Dispatchers.IO) {
        if (!hasLocationPermission()) {
            return@withContext Result.failure(DomainError.LocationPermissionDenied())
        }
        if (!isLocationEnabled()) {
            return@withContext Result.failure(DomainError.LocationDisabled())
        }

        val cancellation = CancellationTokenSource()
        try {
            val location = withTimeoutOrNull(timeoutMillis) {
                client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cancellation.token)
                    .awaitOrNull()
            }

            if (location == null) {
                Result.failure(DomainError.LocationUnavailable())
            } else {
                // Reverse geocoding gets its own small budget so a slow geocoder cannot eat the
                // location deadline; coordinates alone are still a usable answer.
                Result.success(location.toLocationInfo(RESOLVE_CITY_TIMEOUT_MILLIS))
            }
        } catch (cancellationException: CancellationException) {
            throw cancellationException
        } catch (security: SecurityException) {
            // Permission revoked between the check and the call.
            Result.failure(DomainError.LocationPermissionDenied(security))
        } catch (error: Throwable) {
            Result.failure(DomainError.LocationUnavailable(error))
        } finally {
            runCatching { cancellation.cancel() }
        }
    }

    /** True when the platform fix is recent enough to describe where the user is right now. */
    private fun Location.isRecentEnough(): Boolean = LocationFixFreshness.isRecentEnough(
        fixTimeMillis = time,
        nowMillis = System.currentTimeMillis()
    )

    private fun hasLocationPermission(): Boolean {
        val coarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val fine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return coarse || fine
    }

    private fun isLocationEnabled(): Boolean {
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return false
        return LocationManagerCompat.isLocationEnabled(manager)
    }

    /** Converts a platform fix into a domain location, reverse-geocoding the city name on a best-effort basis. */
    private suspend fun Location.toLocationInfo(
        resolveCityTimeoutMillis: Long = RESOLVE_CITY_TIMEOUT_MILLIS
    ): LocationInfo {
        val address = reverseGeocode(latitude, longitude, resolveCityTimeoutMillis)
        return LocationInfo(
            latitude = latitude,
            longitude = longitude,
            cityName = address?.first.orEmpty(),
            country = address?.second,
            adminRegion = address?.third,
            // A GPS fix is where the device is, so the device zone is the right forecast day.
            timezone = TimeZone.getDefault().id
        )
    }

    /**
     * Resolves a city name for [latitude]/[longitude], bounded by [timeoutMillis].
     *
     * API 33+ uses the asynchronous [Geocoder.GeocodeListener] overload, so the callback is
     * suspended rather than a thread blocked in a network round trip. Below 33 the synchronous
     * overload is unavoidable, so it runs inside [runInterruptible]: cancellation (including the
     * timeout) interrupts the blocking call instead of leaving a thread parked for seconds.
     */
    private suspend fun reverseGeocode(
        latitude: Double,
        longitude: Double,
        timeoutMillis: Long
    ): Triple<String, String?, String?>? =
        withContext(Dispatchers.IO) {
            try {
                if (!Geocoder.isPresent()) return@withContext null

                withTimeoutOrNull(timeoutMillis) {
                    val address = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        geocodeWithListener(latitude, longitude)
                    } else {
                        @Suppress("DEPRECATION") // Listener overload is API 33+; guarded above.
                        runInterruptible(Dispatchers.IO) {
                            Geocoder(context, Locale.getDefault())
                                .getFromLocation(latitude, longitude, 1)
                                ?.firstOrNull()
                        }
                    }
                    address?.toCityTriple()
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                // No network / no geocoder service: the coordinates are still usable.
                null
            }
        }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private suspend fun geocodeWithListener(
        latitude: Double,
        longitude: Double
    ): Address? = suspendCancellableCoroutine { continuation ->
        try {
            Geocoder(context, Locale.getDefault()).getFromLocation(
                latitude,
                longitude,
                1,
                object : Geocoder.GeocodeListener {
                    override fun onGeocode(addresses: MutableList<Address>) {
                        if (continuation.isActive) continuation.resume(addresses.firstOrNull())
                    }

                    override fun onError(errorMessage: String?) {
                        if (continuation.isActive) continuation.resume(null)
                    }
                }
            )
        } catch (error: Throwable) {
            // A missing service or an illegal argument must not crash the notification path.
            if (continuation.isActive) continuation.resume(null)
        }
    }

    /** City-level label only; returns null when the address has no usable locality. */
    private fun Address.toCityTriple(): Triple<String, String?, String?>? {
        val city = locality ?: subAdminArea ?: adminArea ?: return null
        return Triple(city, countryName, adminArea)
    }

    /**
     * Awaits a Play Services task, resolving `null` on failure or cancellation.
     *
     * No extra cancellation hook is needed: callers always cancel their
     * [CancellationTokenSource] in a `finally` block, which aborts the platform request.
     */
    private suspend fun <T> Task<T>.awaitOrNull(): T? = suspendCancellableCoroutine { continuation ->
        addOnCompleteListener { task ->
            if (!continuation.isActive) return@addOnCompleteListener
            continuation.resume(if (task.isSuccessful) task.result else null)
        }
    }

    companion object {
        /** `lastLocation` is a delivery of an existing value; it must never be the bottleneck. */
        const val LAST_KNOWN_TIMEOUT_MILLIS: Long = 2_000L

        /** Budget for turning coordinates into a city name; coordinates alone are still usable. */
        const val RESOLVE_CITY_TIMEOUT_MILLIS: Long = 3_000L
    }
}
