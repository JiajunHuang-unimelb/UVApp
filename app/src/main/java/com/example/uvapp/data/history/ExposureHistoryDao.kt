package com.example.uvapp.data.history

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.uvapp.domain.model.ExposureRecord
import kotlinx.coroutines.flow.Flow

@Dao
abstract class ExposureHistoryDao {
    @Transaction
    @Query("SELECT * FROM exposure_sessions WHERE sessionId = :sessionId")
    abstract suspend fun getSession(sessionId: String): ExposureSessionWithDays?

    @Transaction
    @Query("SELECT * FROM exposure_sessions ORDER BY startedAtMillis DESC, sessionId ASC LIMIT :limit OFFSET :offset")
    abstract fun observeHistory(
        limit: Int,
        offset: Int,
    ): Flow<List<ExposureSessionWithDays>>

    @Query(
        """
        SELECT epochDay, SUM(activeDurationMillis) AS activeDurationMillis,
               SUM(doseSed) AS doseSed,
               SUM(CASE WHEN activeDurationMillis > 0 THEN 1 ELSE 0 END) AS sessionCount
        FROM exposure_days WHERE epochDay >= :startDay AND epochDay < :endDay
        GROUP BY epochDay ORDER BY epochDay ASC
        """,
    )
    abstract fun observeDaily(
        startDay: Long,
        endDay: Long,
    ): Flow<List<ExposureDayAggregate>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertSession(session: ExposureSessionEntity)

    @Insert
    abstract suspend fun insertDays(days: List<ExposureDayEntity>)

    @Query("DELETE FROM exposure_sessions WHERE sessionId = :sessionId")
    abstract suspend fun deleteSession(sessionId: String)

    @Query("DELETE FROM exposure_sessions")
    abstract suspend fun clearHistory()

    /** Replace a complete record atomically. Equal checkpoint times allow corrections. */
    @Transaction
    open suspend fun saveSnapshot(input: ExposureRecord) {
        val record = input.validated()
        val previous = getSession(record.sessionId)?.session
        if (previous != null) {
            require(record.startedAtMillis == previous.startedAtMillis && record.zoneId == previous.zoneId) {
                "Session start and timezone cannot change"
            }
            require(record.recordedThroughMillis >= previous.recordedThroughMillis) { "Checkpoint time cannot go backwards" }
        }
        // REPLACE removes the old parent's daily rows through the foreign-key cascade.
        insertSession(
            ExposureSessionEntity(
                record.sessionId,
                record.startedAtMillis,
                record.recordedThroughMillis,
                record.zoneId,
                record.status.name,
            ),
        )
        insertDays(
            record.days.map {
                ExposureDayEntity(record.sessionId, it.date.toEpochDay(), it.activeDurationMillis, it.doseSed)
            },
        )
    }
}
