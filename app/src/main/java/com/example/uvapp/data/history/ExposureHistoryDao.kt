package com.example.uvapp.data.history

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.uvapp.domain.model.ExposureRecord
import java.time.ZoneId
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
        SELECT epochDay,
               SUM(activeDurationMillis) AS activeDurationMillis,
               SUM(directSunDurationMillis) AS directSunDurationMillis,
               SUM(shadeDurationMillis) AS shadeDurationMillis,
               SUM(unknownDurationMillis) AS unknownDurationMillis,
               SUM(doseSed) AS doseSed,
               SUM(CASE WHEN activeDurationMillis > 0 THEN 1 ELSE 0 END) AS sessionCount
        FROM exposure_days
        WHERE epochDay >= :startDay AND epochDay < :endDay
        GROUP BY epochDay
        ORDER BY epochDay ASC
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
    open suspend fun saveSnapshot(record: ExposureRecord) {
        validateForStorage(record)
        val previous = getSession(record.sessionId)?.session
        if (previous != null) {
            require(
                record.startedAtMillis == previous.startedAtMillis &&
                    record.zoneId == previous.zoneId,
            ) {
                "Session start and timezone cannot change"
            }
            require(record.recordedThroughMillis >= previous.recordedThroughMillis) {
                "Checkpoint time cannot go backwards"
            }
        }

        // REPLACE removes the old parent's daily rows through the foreign-key cascade.
        insertSession(
            ExposureSessionEntity(
                sessionId = record.sessionId,
                startedAtMillis = record.startedAtMillis,
                recordedThroughMillis = record.recordedThroughMillis,
                zoneId = record.zoneId,
                status = record.status.name,
            ),
        )

        insertDays(
            record.days.map {
                ExposureDayEntity(
                    sessionId = record.sessionId,
                    epochDay = it.date.toEpochDay(),
                    activeDurationMillis = it.activeDurationMillis,
                    directSunDurationMillis = it.directSunDurationMillis,
                    shadeDurationMillis = it.shadeDurationMillis,
                    unknownDurationMillis = it.unknownDurationMillis,
                    doseSed = it.doseSed,
                )
            },
        )
    }

    /** Storage shape and numeric checks only; the producer owns exposure and calendar calculations. */
    private fun validateForStorage(record: ExposureRecord) {
        require(
            record.sessionId.isNotBlank() &&
                record.sessionId.length <= 128,
        ) {
            "sessionId must contain 1..128 characters"
        }

        require(
            record.startedAtMillis >= 0 &&
                record.recordedThroughMillis >= record.startedAtMillis,
        ) {
            "Invalid session time range"
        }

        ZoneId.of(record.zoneId)

        require(record.days.map { it.date }.distinct().size == record.days.size) {
            "Duplicate day in snapshot"
        }

        for (day in record.days) {
            require(day.activeDurationMillis >= 0) {
                "Active duration must be non-negative"
            }
            require(day.directSunDurationMillis >= 0) {
                "Direct sun duration must be non-negative"
            }
            require(day.shadeDurationMillis >= 0) {
                "Shade duration must be non-negative"
            }
            require(day.unknownDurationMillis >= 0) {
                "Unknown duration must be non-negative"
            }
            require(day.doseSed.isFinite() && day.doseSed >= 0) {
                "Dose must be finite and non-negative SED"
            }
        }

        // Check representable totals, without comparing them to elapsed session time.
        require(record.activeDurationMillis >= 0) {
            "Total active duration must fit in Long"
        }
        require(record.doseSed.isFinite()) {
            "Total dose overflow"
        }
    }
}
