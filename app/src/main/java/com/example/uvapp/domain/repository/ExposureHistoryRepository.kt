package com.example.uvapp.domain.repository

import com.example.uvapp.domain.model.ExposureDailySummary
import com.example.uvapp.domain.model.ExposureRecord
import com.example.uvapp.domain.model.ExposureWeeklySummary
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

interface ExposureHistoryRepository {
    /** Persist a complete snapshot atomically. Exact retries succeed; stale/conflicting writes fail. */
    suspend fun save(record: ExposureRecord): Result<Unit>

    suspend fun getSession(sessionId: String): ExposureRecord?

    /** Latest-started first, including active/paused checkpoints, with stable id tie-breaking. */
    fun observeHistory(
        limit: Int = 50,
        offset: Int = 0,
    ): Flow<List<ExposureRecord>>

    /** Uses recorded local dates, inclusive start and exclusive end, maximum 366 days. */
    fun observeDaily(
        start: LocalDate,
        endExclusive: LocalDate,
    ): Flow<List<ExposureDailySummary>>

    fun observeWeek(containingDate: LocalDate): Flow<ExposureWeeklySummary>

    suspend fun deleteSession(sessionId: String): Result<Unit>

    suspend fun clearHistory(): Result<Unit>
}
