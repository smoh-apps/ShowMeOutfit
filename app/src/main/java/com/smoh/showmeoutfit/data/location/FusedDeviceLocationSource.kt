package com.smoh.showmeoutfit.data.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
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
        val location = client.lastLocation.awaitOrNull() ?: return null
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
                Result.success(location.toLocationInfo())
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
    private suspend fun Location.toLocationInfo(): LocationInfo {
        val address = reverseGeocode(latitude, longitude)
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

    private suspend fun reverseGeocode(latitude: Double, longitude: Double): Triple<String, String?, String?>? =
        withContext(Dispatchers.IO) {
            try {
                if (!Geocoder.isPresent()) return@withContext null
                @Suppress("DEPRECATION") // The listener overload is API 33+; the sync one still works.
                val results = Geocoder(context, Locale.getDefault())
                    .getFromLocation(latitude, longitude, 1)
                val address = results?.firstOrNull() ?: return@withContext null
                val city = address.locality
                    ?: address.subAdminArea
                    ?: address.adminArea
                    ?: return@withContext null
                Triple(city, address.countryName, address.adminArea)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                // No network / no geocoder service: the coordinates are still usable.
                null
            }
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
}
