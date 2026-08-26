package com.example.uvapp.domain.model

/** Identifies whether the visible forecast is fresh, cached, or unavailable. */
enum class UvDataSource {
    NETWORK,
    CACHE,
    NONE,
}

/** Data-layer state exposed to a ViewModel. */
data class UvForecastState(
    val readings: List<UvReading> = emptyList(),
    val source: UvDataSource = UvDataSource.NONE,
    val lastUpdatedMillis: Long? = null,
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null,
)
