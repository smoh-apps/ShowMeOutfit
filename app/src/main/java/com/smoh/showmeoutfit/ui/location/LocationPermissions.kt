package com.smoh.showmeoutfit.ui.location

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * Location permission helpers.
 *
 * Only foreground, approximate-or-precise location is ever requested: there is no
 * `ACCESS_BACKGROUND_LOCATION` path in this app.
 */
object LocationPermissions {

    val requested: Array<String> = arrayOf(
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.ACCESS_FINE_LOCATION
    )

    /** True when the user granted precise *or* approximate location. */
    fun isGranted(context: Context): Boolean {
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

    /**
     * Distinguishes "denied once, may ask again" from "denied for good, only settings can help".
     * Must be called after a denial result, not before a request.
     */
    fun isPermanentlyDenied(activity: Activity): Boolean =
        !isGranted(activity) && requested.none { permission ->
            ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
        }
}
