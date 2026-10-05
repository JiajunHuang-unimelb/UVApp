package com.example.uvapp.domain.model

/** One hourly UV sample; null uv means a gap (no data for that hour). */
data class HourlyUv(val hour: Double, val uv: Double?)

/** One forecast day: weekday label, day-of-month, max UV, sun window and hourly curve. */
data class ForecastDay(
    val weekday: String,
    val dayOfMonth: Int,
    val maxUv: Double,
    /** minutes since midnight, e.g. 06:43 -> 403 */
    val sunriseMinutes: Int?,
    /** minutes since midnight, e.g. 20:11 -> 1211 */
    val sunsetMinutes: Int?,
    val hourly: List<HourlyUv>,
) {
    val band: UvBand get() = UvBand.fromIndex(maxUv)

    /**
     * Interpolated UV at a fractional hour (e.g. 14.5 = 14:30), following the
     * same straight-segment curve the chart draws. Returns null when there is
     * no data around that hour.
     */
    fun uvAt(hourFraction: Double): Double? {
        val exact = hourly.firstOrNull { it.hour == hourFraction }?.uv
        if (exact != null) return exact
        val below = hourly.filter { it.hour <= hourFraction }.maxByOrNull { it.hour }
        val above = hourly.filter { it.hour >= hourFraction }.minByOrNull { it.hour }
        val floor = below?.uv
        val ceil = above?.uv
        return if (below != null && above != null && floor != null && ceil != null && below.hour != above.hour) {
            floor + (ceil - floor) * (hourFraction - below.hour) / (above.hour - below.hour)
        } else {
            floor ?: ceil
        }
    }
}
