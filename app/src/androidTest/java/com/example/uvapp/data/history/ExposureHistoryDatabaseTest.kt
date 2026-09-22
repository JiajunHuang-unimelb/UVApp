package com.example.uvapp.data.history

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.uvapp.domain.model.ExposureDayTotal
import com.example.uvapp.domain.model.ExposureRecord
import com.example.uvapp.domain.model.ExposureRecordStatus
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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
                repository.save(record().copy(revision = 1, days = listOf(ExposureDayTotal(DATE, 2_000, 0.2)))).getOrThrow()
                assertEquals(0.2, repository.observeWeek(DATE).first().doseSed, 0.000001)
                repository.deleteSession("test").getOrThrow()
                assertTrue(repository.observeHistory().first().isEmpty())
                assertEquals(0.0, repository.observeWeek(DATE).first().doseSed, 0.0)
            } finally {
                db.close()
                context.deleteDatabase(name)
            }
        }

    @Test fun concurrentRevisionsDoNotOverwriteNewerSnapshot() =
        runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val db = Room.inMemoryDatabaseBuilder(context, ExposureHistoryDatabase::class.java).build()
            try {
                val first = RoomExposureHistoryRepository(db.exposureHistoryDao())
                val second = RoomExposureHistoryRepository(db.exposureHistoryDao())
                (0L..15L).map { revision ->
                    async {
                        val repository = if (revision % 2 == 0L) first else second
                        repository.save(
                            record().copy(
                                revision = revision,
                                days = listOf(ExposureDayTotal(DATE, 1_000 + revision, 0.1 + revision)),
                            ),
                        )
                    }
                }.awaitAll()
                assertEquals(15L, first.getSession("test")!!.revision)
                assertEquals(15.1, first.observeWeek(DATE).first().doseSed, 0.000001)
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
                assertTrue(repository.save(record().copy(revision = 1)).isFailure)
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
            0,
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
