package com.example.uvapp.data.history

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.uvapp.domain.model.ExposureDayTotal
import com.example.uvapp.domain.model.ExposureRecord
import com.example.uvapp.domain.model.ExposureRecordStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class ExposureHistoryDatabaseTest {
    @Test fun versionOneMigrationPreservesSessionsAndDailyRows() =
        runBlocking {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val context = instrumentation.targetContext
            val name = "history-migration-${UUID.randomUUID()}.db"
            val original = record()
            // Use the exported v1 schema, including its Room identity, to reproduce an old installation.
            try {
                context.openOrCreateDatabase(name, 0, null).use { old ->
                    val schema = instrumentation.context.assets.open(
                        "com.example.uvapp.data.history.ExposureHistoryDatabase/1.json",
                    ).bufferedReader().use { JSONObject(it.readText()).getJSONObject("database") }
                    val entities = schema.getJSONArray("entities")
                    for (index in 0 until entities.length()) {
                        val entity = entities.getJSONObject(index)
                        val tableName = entity.getString("tableName")
                        old.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", tableName))
                        val indices = entity.getJSONArray("indices")
                        for (position in 0 until indices.length()) {
                            old.execSQL(indices.getJSONObject(position).getString("createSql").replace("\${TABLE_NAME}", tableName))
                        }
                    }
                    val setup = schema.getJSONArray("setupQueries")
                    for (index in 0 until setup.length()) old.execSQL(setup.getString(index))
                    old.execSQL(
                        "INSERT INTO exposure_sessions VALUES (?, ?, ?, ?, ?, ?)",
                        arrayOf<Any>(original.sessionId, 7L, original.startedAtMillis, original.recordedThroughMillis, original.zoneId, original.status.name),
                    )
                    old.execSQL("INSERT INTO exposure_days VALUES (?, ?, ?, ?)", arrayOf<Any>(original.sessionId, DATE.toEpochDay(), 1_000L, 0.1))
                    old.version = 1
                }
                val db = Room.databaseBuilder(context, ExposureHistoryDatabase::class.java, name).build()
                try {
                    val repository = RoomExposureHistoryRepository(db.exposureHistoryDao())
                    assertEquals(original, repository.getSession(original.sessionId))
                    assertEquals(0.1, repository.observeWeek(DATE).first().doseSed, 0.000001)
                    repository.deleteSession(original.sessionId).getOrThrow()
                    assertEquals(0.0, repository.observeWeek(DATE).first().doseSed, 0.0)
                } finally {
                    db.close()
                }
            } finally {
                context.deleteDatabase(name)
            }
        }

    @Test fun recordsSurviveReopenAndDeleteCascadesToDailyTotals() =
        runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val name = "history-test-${UUID.randomUUID()}.db"

            fun open() = Room.databaseBuilder(context, ExposureHistoryDatabase::class.java, name).build()
            var db = open()
            try {
                var repository = RoomExposureHistoryRepository(db.exposureHistoryDao())
                repository.save(record()).getOrThrow()
                db.close()
                db = open()
                repository = RoomExposureHistoryRepository(db.exposureHistoryDao())
                assertEquals(record(), repository.getSession("test"))
                val corrected = record().copy(
                    status = ExposureRecordStatus.COMPLETED,
                    days = listOf(ExposureDayTotal(DATE, 2_000, 0.2)),
                )
                repository.save(corrected).getOrThrow()
                repository.save(corrected).getOrThrow()
                assertEquals(corrected, repository.getSession("test"))
                assertTrue(repository.save(corrected.copy(recordedThroughMillis = corrected.recordedThroughMillis - 1)).isFailure)
                assertTrue(repository.save(corrected.copy(days = listOf(ExposureDayTotal(DATE, -1, 0.1)))).isFailure)
                assertEquals(0.2, repository.observeWeek(DATE).first().doseSed, 0.000001)
                repository.deleteSession("test").getOrThrow()
                assertTrue(repository.observeHistory().first().isEmpty())
                assertEquals(0.0, repository.observeWeek(DATE).first().doseSed, 0.0)
            } finally {
                db.close()
                context.deleteDatabase(name)
            }
        }

    @Test fun dailyAndWeeklyQueriesSumSessionsAndFillMissingDays() =
        runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val db = Room.inMemoryDatabaseBuilder(context, ExposureHistoryDatabase::class.java).build()
            try {
                val repository = RoomExposureHistoryRepository(db.exposureHistoryDao())
                repository.save(record()).getOrThrow()
                repository.save(record().copy(sessionId = "second", days = listOf(ExposureDayTotal(DATE, 2_000, 0.2)))).getOrThrow()
                val week = repository.observeWeek(DATE).first()
                assertEquals(DATE.minusDays(1), week.weekStart)
                assertEquals(7, week.days.size)
                assertEquals(3_000L, week.activeDurationMillis)
                assertEquals(0.3, week.doseSed, 0.000001)
                assertEquals(2, week.days[1].sessionCount)
                assertEquals(0.0, week.days.first().doseSed, 0.0)
                repository.clearHistory().getOrThrow()
                assertEquals(0.0, repository.observeWeek(DATE).first().doseSed, 0.0)
            } finally {
                db.close()
            }
        }

    @Test fun failedReplacementRollsBackParentAndDailyRows() =
        runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val db = Room.inMemoryDatabaseBuilder(context, ExposureHistoryDatabase::class.java).build()
            try {
                val repository = RoomExposureHistoryRepository(db.exposureHistoryDao())
                repository.save(record()).getOrThrow()
                // Fail after the old parent/days are replaced to verify transaction rollback on actual SQLite.
                db.openHelper.writableDatabase.execSQL(
                    "CREATE TRIGGER reject_history_day BEFORE INSERT ON exposure_days BEGIN SELECT RAISE(ABORT, 'test failure'); END",
                )
                assertTrue(repository.save(record().copy(status = ExposureRecordStatus.PAUSED)).isFailure)
                assertEquals(record(), repository.getSession("test"))
                assertEquals(0.1, repository.observeWeek(DATE).first().doseSed, 0.000001)
            } finally {
                db.close()
            }
        }

    private fun record(): ExposureRecord {
        val start = DATE.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        return ExposureRecord(
            "test",
            start,
            start + 60_000,
            "UTC",
            ExposureRecordStatus.ACTIVE,
            listOf(ExposureDayTotal(DATE, 1_000, 0.1)),
        )
    }

    companion object {
        private val DATE = LocalDate.of(2026, 9, 22)
    }
}
