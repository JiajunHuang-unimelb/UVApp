package com.example.uvapp.data.history

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.uvapp.data.db.AppDatabase
import com.example.uvapp.data.db.PlaceNameEntity
import com.example.uvapp.data.db.UvReadingEntity
import com.example.uvapp.domain.model.ExposureDayTotal
import com.example.uvapp.domain.model.ExposureRecord
import com.example.uvapp.domain.model.ExposureRecordStatus
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
                assertEquals(3, db.openHelper.readableDatabase.version)
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
                assertEquals(0.1, repository.observeWeek(DATE).first().doseSed, 0.000001)
                repository.clearHistory().getOrThrow()
                assertTrue(repository.observeHistory().first().isEmpty())
                assertEquals(0.0, repository.observeWeek(DATE).first().doseSed, 0.0)
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
            val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
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
            val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
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
            listOf(ExposureDayTotal(DATE, 1_000, 0.1)),
        )
    }

    companion object {
        private val DATE = LocalDate.of(2026, 9, 22)
    }
}
