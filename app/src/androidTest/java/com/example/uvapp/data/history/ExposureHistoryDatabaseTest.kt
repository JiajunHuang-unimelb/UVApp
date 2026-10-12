package com.example.uvapp.data.history

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.uvapp.data.db.AppDatabase
import com.example.uvapp.data.db.PlaceNameEntity
import com.example.uvapp.data.db.UvReadingEntity
import com.example.uvapp.domain.model.ExposureDayTotal
import com.example.uvapp.domain.model.ExposureDailySummary
import com.example.uvapp.domain.model.ExposureRecord
import com.example.uvapp.domain.model.ExposureRecordStatus
import com.example.uvapp.domain.model.ExposureWeeklySummary
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class ExposureHistoryDatabaseTest {
    @Test fun freshDatabaseCreatesAllFourTables() =
        runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
            try {
                val tables = mutableSetOf<String>()
                db.openHelper.readableDatabase.query(
                    "SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%' AND name NOT IN ('android_metadata', 'room_master_table')",
                ).use { cursor ->
                    while (cursor.moveToNext()) tables.add(cursor.getString(0))
                }
                assertEquals(setOf("uv_readings", "place_names", "exposure_sessions", "exposure_days"), tables)
                assertEquals(4, db.openHelper.readableDatabase.version)
                assertTrue(db.uvReadingDao().getForecast("test-place").isEmpty())
                assertTrue(db.placeNameDao().getPlaceNames().isEmpty())
                assertTrue(RoomExposureHistoryRepository(db.exposureHistoryDao()).observeHistory().first().isEmpty())
            } finally {
                db.close()
            }
        }

    @Test fun cacheUpdatesAndHistoryClearingStayIsolated() =
        runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
            try {
                val repository = RoomExposureHistoryRepository(db.exposureHistoryDao())
                repository.save(record()).getOrThrow()
                db.uvReadingDao().insertReadings(listOf(cachedUv()))
                db.placeNameDao().upsert(cachedPlace())
                val updatedUv = cachedUv().copy(uvIndex = 7.0, fetchedAtMillis = 2_000)
                val updatedPlace = cachedPlace().copy(label = "Updated place", fetchedAtMillis = 2_000)
                db.uvReadingDao().replaceForecast(updatedUv.locationKey, listOf(updatedUv))
                db.placeNameDao().upsert(updatedPlace)
                assertEquals(record(), repository.getSession("test"))
                assertEquals(listOf(record()), repository.observeHistory().first())
                assertEquals(0.1, repository.observeWeek(DATE).first().doseSed, 0.000001)
                repository.clearHistory().getOrThrow()
                assertTrue(repository.observeHistory().first().isEmpty())
                assertEmptyWeek(repository.observeWeek(DATE).first())
                assertEquals(listOf(updatedUv), db.uvReadingDao().getForecast(updatedUv.locationKey))
                assertEquals(updatedPlace, db.placeNameDao().getPlaceName(updatedPlace.locationKey))
            } finally {
                db.close()
            }
        }

    @Test fun recordsSurviveReopenAndDeleteCascadesToDailyTotals() =
        runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val name = "history-test-${UUID.randomUUID()}.db"

            fun open() = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
            var db = open()
            try {
                var repository = RoomExposureHistoryRepository(db.exposureHistoryDao())
                repository.save(record()).getOrThrow()
                db.close()
                db = open()
                repository = RoomExposureHistoryRepository(db.exposureHistoryDao())
                assertEquals(record(), repository.getSession("test"))
                assertEquals(listOf(record()), repository.observeHistory().first())
                val corrected = record().copy(
                    status = ExposureRecordStatus.COMPLETED,
                    days = listOf(correctedDay()),
                )
                repository.save(corrected).getOrThrow()
                repository.save(corrected).getOrThrow()
                assertEquals(corrected, repository.getSession("test"))
                assertEquals(listOf(corrected), repository.observeHistory().first())
                assertTrue(repository.save(corrected.copy(recordedThroughMillis = corrected.recordedThroughMillis - 1)).isFailure)
                assertTrue(repository.save(corrected.copy(days = listOf(correctedDay().copy(activeDurationMillis = -1)))).isFailure)
                assertEquals(corrected, repository.getSession("test"))
                val week = repository.observeWeek(DATE).first()
                assertEquals(2_000L, week.activeDurationMillis)
                assertEquals(500L, week.directSunDurationMillis)
                assertEquals(700L, week.shadeDurationMillis)
                assertEquals(800L, week.unknownDurationMillis)
                assertEquals(0.2, week.doseSed, 0.000001)
                assertEquals(1, week.days[1].sessionCount)
                repository.deleteSession("test").getOrThrow()
                assertTrue(repository.observeHistory().first().isEmpty())
                assertEmptyWeek(repository.observeWeek(DATE).first())
            } finally {
                db.close()
                context.deleteDatabase(name)
            }
        }

    @Test fun dailyAndWeeklyQueriesSumSessionsAndFillMissingDays() =
        runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
            try {
                val repository = RoomExposureHistoryRepository(db.exposureHistoryDao())
                repository.save(record()).getOrThrow()
                repository.save(
                    record().copy(
                        sessionId = "second",
                        recordedThroughMillis = record().recordedThroughMillis + 86_400_000L,
                        days = listOf(
                            correctedDay(),
                            ExposureDayTotal(
                                date = DATE.plusDays(1),
                                activeDurationMillis = 600L,
                                directSunDurationMillis = 100L,
                                shadeDurationMillis = 200L,
                                unknownDurationMillis = 300L,
                                doseSed = 0.05,
                            ),
                        ),
                    ),
                ).getOrThrow()
                val daily = repository.observeDaily(DATE, DATE.plusDays(2)).first()
                assertEquals(
                    listOf(
                        ExposureDailySummary(
                            date = DATE,
                            activeDurationMillis = 3_000L,
                            directSunDurationMillis = 700L,
                            shadeDurationMillis = 1_000L,
                            unknownDurationMillis = 1_300L,
                            doseSed = 0.1 + 0.2,
                            sessionCount = 2,
                        ),
                        ExposureDailySummary(
                            date = DATE.plusDays(1),
                            activeDurationMillis = 600L,
                            directSunDurationMillis = 100L,
                            shadeDurationMillis = 200L,
                            unknownDurationMillis = 300L,
                            doseSed = 0.05,
                            sessionCount = 1,
                        ),
                    ),
                    daily,
                )
                val week = repository.observeWeek(DATE).first()
                assertEquals(DATE.minusDays(1), week.weekStart)
                assertEquals(7, week.days.size)
                assertEquals(3_600L, week.activeDurationMillis)
                assertEquals(800L, week.directSunDurationMillis)
                assertEquals(1_200L, week.shadeDurationMillis)
                assertEquals(1_600L, week.unknownDurationMillis)
                assertEquals(0.35, week.doseSed, 0.000001)
                assertEquals(daily, week.days.subList(1, 3))
                week.days.filter { it.date != DATE && it.date != DATE.plusDays(1) }.forEach {
                    assertEquals(ExposureDailySummary(date = it.date), it)
                }
                repository.deleteSession("second").getOrThrow()
                assertEquals(listOf(record()), repository.observeHistory().first())
                val remaining = repository.observeDaily(DATE, DATE.plusDays(2)).first()
                assertEquals(1_000L, remaining[0].activeDurationMillis)
                assertEquals(200L, remaining[0].directSunDurationMillis)
                assertEquals(300L, remaining[0].shadeDurationMillis)
                assertEquals(500L, remaining[0].unknownDurationMillis)
                assertEquals(0.1, remaining[0].doseSed, 0.000001)
                assertEquals(1, remaining[0].sessionCount)
                assertEquals(ExposureDailySummary(date = DATE.plusDays(1)), remaining[1])
                repository.clearHistory().getOrThrow()
                assertEmptyWeek(repository.observeWeek(DATE).first())
            } finally {
                db.close()
            }
        }

    @Test fun failedReplacementRollsBackParentAndDailyRows() =
        runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
            try {
                val repository = RoomExposureHistoryRepository(db.exposureHistoryDao())
                repository.save(record()).getOrThrow()
                // Fail after the old parent/days are replaced to verify transaction rollback on actual SQLite.
                db.openHelper.writableDatabase.execSQL(
                    "CREATE TRIGGER reject_history_day BEFORE INSERT ON exposure_days BEGIN SELECT RAISE(ABORT, 'test failure'); END",
                )
                assertTrue(repository.save(record().copy(status = ExposureRecordStatus.PAUSED, days = listOf(correctedDay()))).isFailure)
                assertEquals(record(), repository.getSession("test"))
                assertEquals(listOf(record()), repository.observeHistory().first())
                val week = repository.observeWeek(DATE).first()
                assertEquals(1_000L, week.activeDurationMillis)
                assertEquals(200L, week.directSunDurationMillis)
                assertEquals(300L, week.shadeDurationMillis)
                assertEquals(500L, week.unknownDurationMillis)
                assertEquals(0.1, week.doseSed, 0.000001)
            } finally {
                db.close()
            }
        }

    @Test fun negativeDirectSunDurationIsRejectedWithoutReplacingHistory() =
        runBlocking { assertRejectedDuration(record().days.single().copy(directSunDurationMillis = -1)) }

    @Test fun negativeShadeDurationIsRejectedWithoutReplacingHistory() =
        runBlocking { assertRejectedDuration(record().days.single().copy(shadeDurationMillis = -1)) }

    @Test fun negativeUnknownDurationIsRejectedWithoutReplacingHistory() =
        runBlocking { assertRejectedDuration(record().days.single().copy(unknownDurationMillis = -1)) }

    private suspend fun assertRejectedDuration(invalidDay: ExposureDayTotal) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val repository = RoomExposureHistoryRepository(db.exposureHistoryDao())
            val original = record()
            repository.save(original).getOrThrow()
            val before = repository.observeWeek(DATE).first()
            assertTrue(repository.save(original.copy(status = ExposureRecordStatus.PAUSED, days = listOf(invalidDay))).isFailure)
            assertEquals(original, repository.getSession("test"))
            assertEquals(listOf(original), repository.observeHistory().first())
            assertEquals(before, repository.observeWeek(DATE).first())
        } finally {
            db.close()
        }
    }

    private fun assertEmptyWeek(week: ExposureWeeklySummary) {
        assertEquals(DATE.minusDays(1), week.weekStart)
        assertEquals(List(7) { ExposureDailySummary(date = week.weekStart.plusDays(it.toLong())) }, week.days)
        assertEquals(0L, week.activeDurationMillis)
        assertEquals(0L, week.directSunDurationMillis)
        assertEquals(0L, week.shadeDurationMillis)
        assertEquals(0L, week.unknownDurationMillis)
        assertEquals(0.0, week.doseSed, 0.0)
    }

    private fun correctedDay() =
        ExposureDayTotal(
            date = DATE,
            activeDurationMillis = 2_000L,
            directSunDurationMillis = 500L,
            shadeDurationMillis = 700L,
            unknownDurationMillis = 800L,
            doseSed = 0.2,
        )

    private fun cachedUv() = UvReadingEntity("test-place", 1_000, -37.8, 145.0, 3.0, 4.0, 20, 1_000)

    private fun cachedPlace() =
        PlaceNameEntity("test-place", -37.8, 145.0, "Melbourne", null, "Melbourne", "Victoria", "Australia", "Melbourne, Australia", 1_000)

    private fun record(): ExposureRecord {
        val start = DATE.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        return ExposureRecord(
            "test",
            start,
            start + 60_000,
            "UTC",
            ExposureRecordStatus.ACTIVE,
            listOf(
                ExposureDayTotal(
                    date = DATE,
                    activeDurationMillis = 1_000L,
                    directSunDurationMillis = 200L,
                    shadeDurationMillis = 300L,
                    unknownDurationMillis = 500L,
                    doseSed = 0.1,
                ),
            ),
        )
    }

    companion object {
        private val DATE = LocalDate.of(2026, 9, 22)
    }
}
