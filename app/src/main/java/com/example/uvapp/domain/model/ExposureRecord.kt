package com.example.uvapp.domain.model

import java.time.LocalDate

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
    val startedAtMillis: Long,
    val recordedThroughMillis: Long,
    val zoneId: String,
    val status: ExposureRecordStatus,
    val days: List<ExposureDayTotal>,
) {
    val activeDurationMillis: Long get() = days.fold(0L) { sum, day -> Math.addExact(sum, day.activeDurationMillis) }
    val doseSed: Double get() = days.sumOf { it.doseSed }
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
