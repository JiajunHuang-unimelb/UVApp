package com.example.uvapp.data.history

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.example.uvapp.domain.model.ExposureDayTotal
import com.example.uvapp.domain.model.ExposureRecord
import com.example.uvapp.domain.model.ExposureRecordStatus
import java.time.LocalDate

@Entity(tableName = "exposure_sessions", indices = [Index("startedAtMillis")])
data class ExposureSessionEntity(
    @PrimaryKey val sessionId: String,
    val revision: Long,
    val startedAtMillis: Long,
    val recordedThroughMillis: Long,
    val zoneId: String,
    val status: String,
)

@Entity(
    tableName = "exposure_days",
    primaryKeys = ["sessionId", "epochDay"],
    foreignKeys = [
        ForeignKey(
            entity = ExposureSessionEntity::class,
            parentColumns = ["sessionId"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("epochDay")],
)
data class ExposureDayEntity(
    val sessionId: String,
    val epochDay: Long,
    val activeDurationMillis: Long,
    val doseSed: Double,
)

data class ExposureSessionWithDays(
    @Embedded val session: ExposureSessionEntity,
    @Relation(parentColumn = "sessionId", entityColumn = "sessionId")
    val days: List<ExposureDayEntity>,
) {
    fun toDomain(): ExposureRecord =
        ExposureRecord(
            sessionId = session.sessionId,
            revision = session.revision,
            startedAtMillis = session.startedAtMillis,
            recordedThroughMillis = session.recordedThroughMillis,
            zoneId = session.zoneId,
            status = ExposureRecordStatus.valueOf(session.status),
            days =
                days.sortedBy { it.epochDay }.map {
                    ExposureDayTotal(LocalDate.ofEpochDay(it.epochDay), it.activeDurationMillis, it.doseSed)
                },
        )
}

data class ExposureDayAggregate(
    val epochDay: Long,
    val activeDurationMillis: Long,
    val doseSed: Double,
    val sessionCount: Int,
)
