package com.example.uvapp.platform.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import androidx.core.location.LocationCompat
import androidx.core.location.LocationManagerCompat
import com.example.uvapp.domain.location.CurrentLocationProvider
import com.example.uvapp.domain.location.LocationResult
import com.example.uvapp.domain.model.LocationFix
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.Granularity
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

/** Google Fused Location implementation for a user-initiated, one-shot foreground fix. */
class FusedCurrentLocationProvider(
    context: Context,
) : CurrentLocationProvider {
    private val appContext = context.applicationContext
    private val client = LocationServices.getFusedLocationProviderClient(appContext)
    private val locationManager = appContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    override suspend fun getCurrentLocation(): LocationResult {
        val hasFinePermission = appContext.hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)
        val hasCoarsePermission = appContext.hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (!hasFinePermission && !hasCoarsePermission) return LocationResult.PermissionDenied
        if (!LocationManagerCompat.isLocationEnabled(locationManager)) return LocationResult.LocationDisabled

        return try {
            val location =
                withTimeout(REQUEST_TIMEOUT_MILLIS) {
                    requestLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY)
                        ?: requestLocation(Priority.PRIORITY_HIGH_ACCURACY)
                }

            if (location == null) {
                LocationResult.Unavailable
            } else {
                LocationResult.Success(
                    LocationFix(
                        latitude = location.latitude,
                        longitude = location.longitude,
                        accuracyMeters = location.safeAccuracyMeters(),
                        capturedAtMillis = location.time.coerceAtLeast(0L),
                        isApproximate = !hasFinePermission,
                        isMock = LocationCompat.isMock(location),
                    ),
                )
            }
        } catch (_: TimeoutCancellationException) {
            LocationResult.Timeout
        } catch (error: CancellationException) {
            throw error
        } catch (_: SecurityException) {
            LocationResult.PermissionDenied
        } catch (_: Exception) {
            LocationResult.Unavailable
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun requestLocation(priority: Int): Location? {
        val cancellationSource = CancellationTokenSource()
        val request =
            CurrentLocationRequest
                .Builder()
                .setPriority(priority)
                .setGranularity(Granularity.GRANULARITY_PERMISSION_LEVEL)
                .setMaxUpdateAgeMillis(MAX_LOCATION_AGE_MILLIS)
                .setDurationMillis(SINGLE_ATTEMPT_DURATION_MILLIS)
                .build()

        return try {
            client.getCurrentLocation(request, cancellationSource.token).await()
        } finally {
            cancellationSource.cancel()
        }
    }

    private fun Context.hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    private fun Location.safeAccuracyMeters(): Float =
        accuracy.takeIf { value -> value.isFinite() && value >= 0f } ?: UNKNOWN_ACCURACY_METERS

    private companion object {
        const val MAX_LOCATION_AGE_MILLIS = 5L * 60L * 1000L
        const val SINGLE_ATTEMPT_DURATION_MILLIS = 6_000L
        const val REQUEST_TIMEOUT_MILLIS = 13_000L
        const val UNKNOWN_ACCURACY_METERS = 100_000f
    }
}
