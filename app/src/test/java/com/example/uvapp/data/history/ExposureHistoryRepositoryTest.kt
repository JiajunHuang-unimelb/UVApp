package com.example.uvapp.data.history

import com.example.uvapp.domain.model.ExposureDayTotal
import com.example.uvapp.domain.model.ExposureRecord
import com.example.uvapp.domain.model.ExposureRecordStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class ExposureHistoryRepositoryTest {
    private val date = LocalDate.of(2026, 9, 22)
    private val dao = FakeHistoryDao()
    private val repository = RoomExposureHistoryRepository(dao)

    @Test fun `checkpoints and exact retries replace rather than double count`() =
        runBlocking {
            val first = record()
            assertTrue(repository.save(first).isSuccess)
            assertTrue(repository.save(first).isSuccess)
            val next = first.copy(revision = 1, days = listOf(ExposureDayTotal(date, 120_000, 0.2)))
            assertTrue(repository.save(next).isSuccess)
            val day = repository.observeDaily(date, date.plusDays(1)).first().single()
            assertEquals(120_000, day.activeDurationMillis)
            assertEquals(0.2, day.doseSed, 0.000001)
            assertEquals(1, day.sessionCount)
            assertEquals(next, repository.getSession(first.sessionId))
        }

    @Test fun `reject stale conflicting decreasing and mutated identity writes`() =
        runBlocking {
            val initial = record().copy(revision = 3)
            repository.save(initial).getOrThrow()
            val invalid =
                listOf(
                    initial.copy(revision = 2),
                    initial.copy(status = ExposureRecordStatus.PAUSED),
                    initial.copy(revision = 4, startedAtMillis = initial.startedAtMillis + 1),
                    initial.copy(revision = 4, zoneId = "UTC"),
                    initial.copy(revision = 4, recordedThroughMillis = initial.recordedThroughMillis - 1),
                    initial.copy(revision = 4, days = emptyList()),
                    initial.copy(revision = 4, days = listOf(ExposureDayTotal(date, 59_999, 0.1))),
                    initial.copy(revision = 4, days = listOf(ExposureDayTotal(date, 60_000, 0.09))),
                )
            invalid.forEach { assertTrue(repository.save(it).isFailure) }
            assertEquals(initial, repository.getSession(initial.sessionId))
        }

    @Test fun `completed records allow exact retry but no later modification`() =
        runBlocking {
            val done = record().copy(status = ExposureRecordStatus.COMPLETED)
            assertTrue(repository.save(done).isSuccess)
            assertTrue(repository.save(done).isSuccess)
            assertTrue(repository.save(done.copy(revision = 1)).isFailure)
        }

    @Test fun `week starts Monday zero fills and sums multiple sessions`() =
        runBlocking {
            repository.save(record()).getOrThrow()
            repository.save(record("second")).getOrThrow()
            val week = repository.observeWeek(date).first()
            assertEquals(LocalDate.of(2026, 9, 21), week.weekStart)
            assertEquals(7, week.days.size)
            assertEquals(0, week.days.first().activeDurationMillis)
            assertEquals(120_000, week.activeDurationMillis)
            assertEquals(0.2, week.doseSed, 0.000001)
            assertEquals(2, week.days[1].sessionCount)
        }

    @Test fun `cross midnight totals retain calculated daily dose without prorating`() =
        runBlocking {
            val zone = ZoneId.of("Australia/Melbourne")
            val start = date.atTime(23, 50).atZone(zone).toInstant().toEpochMilli()
            val end = date.plusDays(1).atTime(0, 10).atZone(zone).toInstant().toEpochMilli()
            val snapshot =
                ExposureRecord(
                    "midnight",
                    0,
                    start,
                    end,
                    zone.id,
                    ExposureRecordStatus.COMPLETED,
                    listOf(ExposureDayTotal(date, 600_000, 0.3), ExposureDayTotal(date.plusDays(1), 300_000, 0.05)),
                )
            repository.save(snapshot).getOrThrow()
            val rows = repository.observeDaily(date, date.plusDays(2)).first()
            assertEquals(listOf(0.3, 0.05), rows.map { it.doseSed })
            assertEquals(listOf(600_000L, 300_000L), rows.map { it.activeDurationMillis })
        }

    @Test fun `invalid values fail before writing`() =
        runBlocking {
            val base = record()
            val invalid =
                listOf(
                    base.copy(sessionId = " "), base.copy(revision = -1), base.copy(zoneId = "not-a-zone"),
                    base.copy(recordedThroughMillis = base.startedAtMillis - 1),
                    base.copy(days = base.days + base.days),
                    base.copy(days = listOf(ExposureDayTotal(date.minusDays(1), 1, 0.1))),
                    base.copy(days = listOf(ExposureDayTotal(date, -1, 0.0))),
                    base.copy(days = listOf(ExposureDayTotal(date, 3_600_001, 0.1))),
                    base.copy(days = listOf(ExposureDayTotal(date, 1, Double.NaN))),
                    base.copy(days = listOf(ExposureDayTotal(date, 1, Double.POSITIVE_INFINITY))),
                    base.copy(days = listOf(ExposureDayTotal(date, 1, -0.1))),
                    base.copy(days = listOf(ExposureDayTotal(date, 0, 0.1))),
                )
            invalid.forEach { assertTrue("Expected failure: $it", repository.save(it).isFailure) }
            assertTrue(repository.observeHistory().first().isEmpty())
        }

    @Test fun `DST days use actual elapsed duration`() {
        val zone = ZoneId.of("Australia/Melbourne")

        fun dayRecord(
            day: LocalDate,
            hours: Long,
        ): ExposureRecord =
            ExposureRecord(
                "dst",
                0,
                day.atStartOfDay(zone).toInstant().toEpochMilli(),
                day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(),
                zone.id,
                ExposureRecordStatus.COMPLETED,
                listOf(ExposureDayTotal(day, hours * 3_600_000, 1.0)),
            )
        dayRecord(LocalDate.of(2026, 10, 4), 23).validated()
        assertThrows(IllegalArgumentException::class.java) { dayRecord(LocalDate.of(2026, 10, 4), 24).validated() }
        dayRecord(LocalDate.of(2026, 4, 5), 25).validated()
    }

    @Test fun `history pagination deletion and clearing affect totals`() =
        runBlocking {
            repository.save(record("a")).getOrThrow()
            repository.save(record("b")).getOrThrow()
            assertEquals("b", repository.observeHistory(limit = 1, offset = 1).first().single().sessionId)
            repository.deleteSession("a").getOrThrow()
            assertNull(repository.getSession("a"))
            assertEquals(1, repository.observeDaily(date, date.plusDays(1)).first().single().sessionCount)
            repository.clearHistory().getOrThrow()
            assertEquals(0.0, repository.observeWeek(date).first().doseSed, 0.0)
        }

    @Test fun `query boundaries and calendar year rollover`() =
        runBlocking {
            assertThrows(IllegalArgumentException::class.java) { repository.observeHistory(0) }
            assertThrows(IllegalArgumentException::class.java) { repository.observeHistory(offset = -1) }
            assertThrows(IllegalArgumentException::class.java) { repository.observeDaily(date, date) }
            assertThrows(IllegalArgumentException::class.java) { repository.observeDaily(date, date.plusDays(367)) }
            val week = repository.observeWeek(LocalDate.of(2027, 1, 1)).first()
            assertEquals(LocalDate.of(2026, 12, 28), week.weekStart)
            assertEquals(LocalDate.of(2027, 1, 3), week.days.last().date)
        }

    @Test fun `storage failure is returned and cancellation propagates`() =
        runBlocking {
            dao.failure = IllegalStateException("disk full")
            assertEquals("disk full", repository.save(record()).exceptionOrNull()?.message)
            dao.failure = CancellationException("cancelled")
            try {
                repository.save(record())
                fail("Cancellation was swallowed")
            } catch (_: CancellationException) {
                // Expected structured-concurrency behavior.
            }
        }

    private fun record(id: String = "session"): ExposureRecord {
        val start = date.atTime(10, 0).atZone(ZoneId.of("Australia/Melbourne")).toInstant().toEpochMilli()
        return ExposureRecord(
            id,
            0,
            start,
            start + 3_600_000,
            "Australia/Melbourne",
            ExposureRecordStatus.ACTIVE,
            listOf(ExposureDayTotal(date, 60_000, 0.1)),
        )
    }
}

/** Exercises the production DAO's snapshot policy; SQLite behavior is tested on Android separately. */
private class FakeHistoryDao : ExposureHistoryDao() {
    private val records = MutableStateFlow<Map<String, ExposureSessionWithDays>>(emptyMap())
    var failure: Exception? = null

    override suspend fun getSession(sessionId: String): ExposureSessionWithDays? {
        failure?.let { throw it }
        return records.value[sessionId]
    }

    override fun observeHistory(
        limit: Int,
        offset: Int,
    ): Flow<List<ExposureSessionWithDays>> =
        records.map { rows ->
            rows.values.sortedWith(
                compareByDescending<ExposureSessionWithDays> { it.session.startedAtMillis }
                    .thenBy { it.session.sessionId },
            ).drop(offset).take(limit)
        }

    override fun observeDaily(
        startDay: Long,
        endDay: Long,
    ): Flow<List<ExposureDayAggregate>> =
        records.map { rows ->
            rows.values.flatMap { it.days }.filter { it.epochDay >= startDay && it.epochDay < endDay }
                .groupBy { it.epochDay }.map { (day, entries) ->
                    ExposureDayAggregate(
                        day,
                        entries.sumOf { it.activeDurationMillis },
                        entries.sumOf { it.doseSed },
                        entries.count { it.activeDurationMillis > 0 },
                    )
                }
        }

    override suspend fun insertSession(session: ExposureSessionEntity) {
        records.value = records.value + (session.sessionId to ExposureSessionWithDays(session, emptyList()))
    }

    override suspend fun insertDays(days: List<ExposureDayEntity>) {
        days.groupBy { it.sessionId }.forEach { (id, entries) ->
            records.value = records.value + (id to records.value.getValue(id).copy(days = entries))
        }
    }

    override suspend fun deleteSession(sessionId: String) {
        records.value = records.value - sessionId
    }

    override suspend fun clearHistory() {
        records.value = emptyMap()
    }
}
