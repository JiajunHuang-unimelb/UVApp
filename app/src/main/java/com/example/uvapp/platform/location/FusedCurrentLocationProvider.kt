package com.example.uvapp.platform.location

import android.annotation.SuppressLint
import android.content.Context
import com.example.uvapp.domain.location.CurrentLocationProvider
import com.example.uvapp.domain.location.LocationUnavailableException
import com.example.uvapp.domain.model.Coordinates
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

class FusedCurrentLocationProvider(
    context: Context,
) : CurrentLocationProvider {
    private val client = LocationServices.getFusedLocationProviderClient(context)

    @SuppressLint("MissingPermission")
    override suspend fun getCurrentLocation(): Coordinates =
        suspendCancellableCoroutine { continuation ->
            val cancellationTokenSource = CancellationTokenSource()
            continuation.invokeOnCancellation { cancellationTokenSource.cancel() }

            client
                .getCurrentLocation(
                    Priority.PRIORITY_HIGH_ACCURACY,
                    cancellationTokenSource.token,
                ).addOnSuccessListener { location ->
                    if (location == null) {
                        continuation.resumeWithException(LocationUnavailableException())
                    } else {
                        continuation.resumeWith(
                            Result.success(
                                Coordinates(
                                    latitude = location.latitude,
                                    longitude = location.longitude,
                                ),
                            ),
                        )
                    }
                }.addOnFailureListener { error ->
                    continuation.resumeWithException(LocationUnavailableException(error))
                }.addOnCanceledListener {
                    continuation.cancel()
                }
        }
}
