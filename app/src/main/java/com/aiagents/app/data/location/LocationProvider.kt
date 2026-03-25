package com.aiagents.app.data.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

data class DeviceLocation(
    val latitude: Double,
    val longitude: Double,
    val address: String? = null,
    val city: String? = null,
    val country: String? = null
)

@Singleton
class LocationProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "LocationProvider"
    }

    private val fusedClient by lazy {
        LocationServices.getFusedLocationProviderClient(context)
    }

    fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    @Suppress("MissingPermission")
    suspend fun getCurrentLocation(): DeviceLocation? {
        if (!hasLocationPermission()) {
            Log.w(TAG, "Location permission not granted")
            return null
        }

        return try {
            val location = suspendCancellableCoroutine { cont ->
                val cts = CancellationTokenSource()
                cont.invokeOnCancellation { cts.cancel() }

                fusedClient.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cts.token)
                    .addOnSuccessListener { loc ->
                        cont.resume(loc)
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Failed to get location", e)
                        cont.resume(null)
                    }
            }

            if (location == null) {
                // Fallback to last known location
                val lastLocation = suspendCancellableCoroutine { cont ->
                    fusedClient.lastLocation
                        .addOnSuccessListener { loc -> cont.resume(loc) }
                        .addOnFailureListener { cont.resume(null) }
                }
                lastLocation?.let { loc ->
                    buildDeviceLocation(loc.latitude, loc.longitude)
                }
            } else {
                buildDeviceLocation(location.latitude, location.longitude)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting location", e)
            null
        }
    }

    private fun buildDeviceLocation(lat: Double, lng: Double): DeviceLocation {
        var address: String? = null
        var city: String? = null
        var country: String? = null

        try {
            @Suppress("DEPRECATION")
            val geocoder = Geocoder(context, Locale.getDefault())
            val addresses = geocoder.getFromLocation(lat, lng, 1)
            if (!addresses.isNullOrEmpty()) {
                val addr = addresses[0]
                address = addr.getAddressLine(0)
                city = addr.locality ?: addr.subAdminArea
                country = addr.countryName
            }
        } catch (e: Exception) {
            Log.w(TAG, "Geocoder failed", e)
        }

        return DeviceLocation(
            latitude = lat,
            longitude = lng,
            address = address,
            city = city,
            country = country
        )
    }

    fun getLocationString(location: DeviceLocation): String {
        return "${location.latitude},${location.longitude}"
    }
}
