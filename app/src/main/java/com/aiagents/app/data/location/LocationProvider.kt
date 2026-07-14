package com.aiagents.app.data.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

enum class LocationFixSource {
    CURRENT,
    LAST_KNOWN,
    MEMORY_CACHE
}

enum class LocationErrorCode {
    PERMISSION_REQUIRED,
    UNAVAILABLE,
    INACCURATE
}

data class DeviceLocation(
    val latitude: Double,
    val longitude: Double,
    val address: String? = null,
    val city: String? = null,
    val country: String? = null,
    val countryCode: String? = null,
    val accuracyMeters: Float? = null,
    val capturedAtMillis: Long = System.currentTimeMillis(),
    val source: LocationFixSource = LocationFixSource.CURRENT,
    val isStale: Boolean = false
) {
    val ageMillis: Long
        get() = (System.currentTimeMillis() - capturedAtMillis).coerceAtLeast(0L)
}

sealed interface DeviceLocationResult {
    data class Success(val location: DeviceLocation) : DeviceLocationResult
    data class Failure(
        val code: LocationErrorCode,
        val userMessage: String
    ) : DeviceLocationResult
}

/**
 * Obtains a recent device location without writing it to disk.
 *
 * The in-memory cache exists only to avoid waking GPS repeatedly during a short tool chain. Callers
 * can omit the street address (weather does this) so precise address information never enters the
 * weather result or the LLM conversation.
 */
@Singleton
class LocationProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "LocationProvider"
        private const val FRESH_FIX_MAX_AGE_MS = 2 * 60 * 1000L
        private const val LAST_KNOWN_MAX_AGE_MS = 15 * 60 * 1000L
        private const val LOCATION_REQUEST_TIMEOUT_MS = 12_000L
        private const val FINE_MAX_ACCURACY_METERS = 1_000f
        private const val COARSE_MAX_ACCURACY_METERS = 10_000f
    }

    private val fusedClient by lazy {
        LocationServices.getFusedLocationProviderClient(context)
    }

    @Volatile
    private var memoryCache: DeviceLocation? = null

    fun hasLocationPermission(): Boolean = hasFineLocationPermission() ||
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

    private fun hasFineLocationPermission(): Boolean = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

    /**
     * Returns a recent fix. A last-known fix is accepted only when it is at most 15 minutes old and
     * meets a permission-appropriate accuracy threshold.
     */
    @Suppress("MissingPermission")
    suspend fun getCurrentLocation(includeAddress: Boolean = true): DeviceLocationResult {
        if (!hasLocationPermission()) {
            return DeviceLocationResult.Failure(
                LocationErrorCode.PERMISSION_REQUIRED,
                "Se necesita permiso de ubicación para completar esta consulta."
            )
        }

        val maxAccuracy = if (hasFineLocationPermission()) {
            FINE_MAX_ACCURACY_METERS
        } else {
            COARSE_MAX_ACCURACY_METERS
        }

        memoryCache
            ?.takeIf { it.ageMillis <= FRESH_FIX_MAX_AGE_MS && it.hasAcceptableAccuracy(maxAccuracy) }
            ?.let { cached ->
                val enriched = if (includeAddress && cached.address == null) {
                    enrichLocation(cached.copy(source = LocationFixSource.MEMORY_CACHE), true)
                } else {
                    cached.copy(source = LocationFixSource.MEMORY_CACHE)
                }
                return DeviceLocationResult.Success(enriched)
            }

        return try {
            val priority = if (hasFineLocationPermission()) {
                Priority.PRIORITY_HIGH_ACCURACY
            } else {
                Priority.PRIORITY_BALANCED_POWER_ACCURACY
            }
            val request = CurrentLocationRequest.Builder()
                .setPriority(priority)
                .setMaxUpdateAgeMillis(FRESH_FIX_MAX_AGE_MS)
                .setDurationMillis(LOCATION_REQUEST_TIMEOUT_MS)
                .build()

            val current = withTimeoutOrNull(LOCATION_REQUEST_TIMEOUT_MS + 1_000L) {
                suspendCancellableCoroutine<Location?> { continuation ->
                    val cancellation = CancellationTokenSource()
                    continuation.invokeOnCancellation { cancellation.cancel() }
                    fusedClient.getCurrentLocation(request, cancellation.token)
                        .addOnSuccessListener { location ->
                            if (continuation.isActive) continuation.resume(location)
                        }
                        .addOnFailureListener { error ->
                            Log.w(TAG, "Current location request failed", error)
                            if (continuation.isActive) continuation.resume(null)
                        }
                }
            }

            val selected = current
                ?.takeIf { it.ageMillis() <= FRESH_FIX_MAX_AGE_MS && it.hasAcceptableAccuracy(maxAccuracy) }
                ?.let { it to LocationFixSource.CURRENT }
                ?: getLastKnownLocation()
                    ?.takeIf {
                        it.ageMillis() <= LAST_KNOWN_MAX_AGE_MS &&
                            it.hasAcceptableAccuracy(maxAccuracy)
                    }
                    ?.let { it to LocationFixSource.LAST_KNOWN }

            if (selected == null) {
                val inaccurate = current != null && !current.hasAcceptableAccuracy(maxAccuracy)
                DeviceLocationResult.Failure(
                    code = if (inaccurate) LocationErrorCode.INACCURATE else LocationErrorCode.UNAVAILABLE,
                    userMessage = if (inaccurate) {
                        "La ubicación disponible no tiene precisión suficiente. Activa la ubicación precisa e inténtalo de nuevo."
                    } else {
                        "No se pudo obtener una ubicación reciente. Verifica que la ubicación del dispositivo esté activa."
                    }
                )
            } else {
                val (fix, source) = selected
                val base = DeviceLocation(
                    latitude = fix.latitude,
                    longitude = fix.longitude,
                    accuracyMeters = if (fix.hasAccuracy()) fix.accuracy else null,
                    capturedAtMillis = fix.time.takeIf { it > 0L } ?: System.currentTimeMillis(),
                    source = source,
                    isStale = fix.ageMillis() > FRESH_FIX_MAX_AGE_MS
                )
                val deviceLocation = enrichLocation(base, includeAddress)
                memoryCache = deviceLocation
                DeviceLocationResult.Success(deviceLocation)
            }
        } catch (error: Exception) {
            Log.e(TAG, "Unable to obtain device location", error)
            DeviceLocationResult.Failure(
                LocationErrorCode.UNAVAILABLE,
                "No se pudo obtener la ubicación del dispositivo en este momento."
            )
        }
    }

    @Suppress("MissingPermission")
    private suspend fun getLastKnownLocation(): Location? =
        suspendCancellableCoroutine { continuation ->
            fusedClient.lastLocation
                .addOnSuccessListener { location ->
                    if (continuation.isActive) continuation.resume(location)
                }
                .addOnFailureListener { error ->
                    Log.w(TAG, "Last-known location request failed", error)
                    if (continuation.isActive) continuation.resume(null)
                }
        }

    private suspend fun enrichLocation(
        location: DeviceLocation,
        includeAddress: Boolean
    ): DeviceLocation = withContext(Dispatchers.IO) {
        try {
            if (!Geocoder.isPresent()) return@withContext location
            @Suppress("DEPRECATION")
            val addresses = Geocoder(context, Locale.getDefault())
                .getFromLocation(location.latitude, location.longitude, 1)
            val address = addresses?.firstOrNull() ?: return@withContext location
            location.copy(
                address = if (includeAddress) address.getAddressLine(0) else null,
                city = address.locality ?: address.subAdminArea ?: address.adminArea,
                country = address.countryName,
                countryCode = address.countryCode
            )
        } catch (error: Exception) {
            // Reverse geocoding is optional; the coordinates/fix remain valid.
            Log.w(TAG, "Reverse geocoding unavailable", error)
            location
        }
    }

    fun getLocationString(location: DeviceLocation): String =
        "${location.latitude},${location.longitude}"

    private fun Location.ageMillis(): Long =
        (System.currentTimeMillis() - time).coerceAtLeast(0L)

    private fun Location.hasAcceptableAccuracy(maxAccuracyMeters: Float): Boolean =
        !hasAccuracy() || accuracy <= maxAccuracyMeters

    private fun DeviceLocation.hasAcceptableAccuracy(maxAccuracyMeters: Float): Boolean =
        accuracyMeters == null || accuracyMeters <= maxAccuracyMeters
}
