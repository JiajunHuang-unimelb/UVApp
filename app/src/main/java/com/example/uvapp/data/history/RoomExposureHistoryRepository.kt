package com.example.uvapp.data.history

import com.example.uvapp.domain.model.ExposureDailySummary
import com.example.uvapp.domain.model.ExposureRecord
import com.example.uvapp.domain.model.ExposureWeeklySummary
import com.example.uvapp.domain.repository.ExposureHistoryRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

class RoomExposureHistoryRepository(private val dao: ExposureHistoryDao) : ExposureHistoryRepository {
    override suspend fun save(record: ExposureRecord): Result<Unit> = write { dao.saveSnapshot(record) }

    override suspend fun getSession(sessionId: String): ExposureRecord? = dao.getSession(sessionId)?.toDomain()

    override fun observeHistory(
        limit: Int,
        offset: Int,
    ): Flow<List<ExposureRecord>> {
        require(limit in 1..200 && offset >= 0) { "History requires limit 1..200 and offset >= 0" }
        return dao.observeHistory(limit, offset).map { rows -> rows.map { it.toDomain() } }
    }

    override fun observeDaily(
        start: LocalDate,
        endExclusive: LocalDate,
    ): Flow<List<ExposureDailySummary>> {
        val count = ChronoUnit.DAYS.between(start, endExclusive)
        require(count in 1..366) { "Daily query requires 1..366 days" }
        return dao.observeDaily(start.toEpochDay(), endExclusive.toEpochDay()).map { rows ->
            val byDay = rows.associateBy { it.epochDay }
            List(count.toInt()) { index ->
                val date = start.plusDays(index.toLong())
                val row = byDay[date.toEpochDay()]
                ExposureDailySummary(date, row?.activeDurationMillis ?: 0, row?.doseSed ?: 0.0, row?.sessionCount ?: 0)
            }
        }
    }

    override fun observeWeek(containingDate: LocalDate): Flow<ExposureWeeklySummary> {
        val monday = containingDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        return observeDaily(monday, monday.plusDays(7)).map { ExposureWeeklySummary(monday, it) }
    }

    override suspend fun deleteSession(sessionId: String): Result<Unit> =
        write {
            require(sessionId.isNotBlank()) { "sessionId must not be blank" }
            dao.deleteSession(sessionId)
        }

    override suspend fun clearHistory(): Result<Unit> = write { dao.clearHistory() }

    private suspend fun write(action: suspend () -> Unit): Result<Unit> =
        try {
            action()
            Result.success(Unit)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Result.failure(error)
        }
}
