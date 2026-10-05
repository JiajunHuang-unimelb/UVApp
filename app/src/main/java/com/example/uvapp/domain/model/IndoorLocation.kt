package com.example.uvapp.domain.model

import kotlinx.serialization.Serializable
import kotlin.math.*

@Serializable
data class IndoorLocation(val id: String, val name: String, val latitude: Double, val longitude: Double, val createdAtMillis: Long, val radiusMeters: Double = 100.0)

@Serializable
data class IndoorSuggestion(val id: String, val latitude: Double, val longitude: Double, val capturedAtMillis: Long, val name: String = "")

@Serializable
data class IndoorLocationsData(val locations: List<IndoorLocation> = emptyList(), val pending: IndoorSuggestion? = null, val invitationDismissed: Boolean = false, val suggestionsEnabled: Boolean = false)

fun LocationFix.usableForIndoor(now: Long): Boolean = !isApproximate && accuracyMeters <= 50f && now - capturedAtMillis in 0..30_000

fun IndoorLocation.contains(latitude: Double, longitude: Double): Boolean {
    val dLat = Math.toRadians(latitude - this.latitude)
    val dLon = Math.toRadians(longitude - this.longitude)
    val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(this.latitude)) * cos(Math.toRadians(latitude)) * sin(dLon / 2).pow(2)
    return 6_371_000 * 2 * asin(sqrt(a.coerceIn(0.0, 1.0))) <= radiusMeters
}
