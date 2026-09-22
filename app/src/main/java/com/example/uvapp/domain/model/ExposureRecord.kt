package com.example.uvapp.domain.model

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

enum class ExposureRecordStatus { ACTIVE, PAUSED, COMPLETED }

/** Cumulative measurements for one local calendar day; calculated by the exposure module. */
data class ExposureDayTotal(
    val date: LocalDate,
    val activeDurationMillis: Long,
    val doseSed: Double,
)

/** Full cumulative snapshot, not an increment. Keep id/start/zone stable across checkpoints. */
data class ExposureRecord(
    val sessionId: String,
    val revision: Long,
    val startedAtMillis: Long,
    val recordedThroughMillis: Long,
    val zoneId: String,
    val status: ExposureRecordStatus,
    val days: List<ExposureDayTotal>,
) {
    val activeDurationMillis: Long get() = days.fold(0L) { sum, day -> Math.addExact(sum, day.activeDurationMillis) }
    val doseSed: Double get() = days.sumOf { it.doseSed }

    /** Checks units, day boundaries (including DST), and impossible measurements. */
    fun validated(): ExposureRecord {
        require(sessionId.isNotBlank() && sessionId.length <= 128) { "sessionId must contain 1..128 characters" }
        require(revision >= 0) { "revision must be non-negative" }
        require(startedAtMillis >= 0 && recordedThroughMillis >= startedAtMillis) { "Invalid session time range" }
        val zone = ZoneId.of(zoneId)
        val firstDay = Instant.ofEpochMilli(startedAtMillis).atZone(zone).toLocalDate()
        val lastDay = Instant.ofEpochMilli(recordedThroughMillis).atZone(zone).toLocalDate()
        require(days.map { it.date }.distinct().size == days.size) { "Duplicate day in snapshot" }
        for (day in days) {
            require(day.date >= firstDay && day.date <= lastDay) { "Day outside session time range" }
            val start = maxOf(startedAtMillis, day.date.atStartOfDay(zone).toInstant().toEpochMilli())
            val end = minOf(recordedThroughMillis, day.date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli())
            require(day.activeDurationMillis in 0..(end - start)) { "Active duration exceeds this day's elapsed time" }
            require(day.doseSed.isFinite() && day.doseSed >= 0) { "Dose must be finite and non-negative SED" }
            require(day.activeDurationMillis > 0 || day.doseSed == 0.0) { "Positive dose requires active time" }
        }
        require(activeDurationMillis <= recordedThroughMillis - startedAtMillis) { "Active duration exceeds elapsed time" }
        require(doseSed.isFinite()) { "Total dose overflow" }
        return copy(days = days.sortedBy { it.date })
    }
}

/** One calendar bucket; sessionCount counts sessions with positive active time in this day. */
data class ExposureDailySummary(
    val date: LocalDate,
    val activeDurationMillis: Long = 0,
    val doseSed: Double = 0.0,
    val sessionCount: Int = 0,
)

/** Monday-inclusive through next-Monday-exclusive; daily rows include zero-activity days. */
data class ExposureWeeklySummary(
    val weekStart: LocalDate,
    val days: List<ExposureDailySummary>,
) {
    val activeDurationMillis: Long get() = days.sumOf { it.activeDurationMillis }
    val doseSed: Double get() = days.sumOf { it.doseSed }
}
