package com.example.uvapp

import com.example.uvapp.domain.model.ExposureDailySummary
import com.example.uvapp.domain.model.ExposureRecord
import com.example.uvapp.domain.model.ExposureWeeklySummary
import com.example.uvapp.domain.repository.ExposureHistoryRepository
import com.example.uvapp.viewmodel.MainViewModel
import com.example.uvapp.viewmodel.SUN_LOG_WEEK_COUNT
import com.example.uvapp.viewmodel.SettingsViewModel
import com.example.uvapp.viewmodel.Tab
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Sun log ViewModel tests. Kept apart from MainViewModelTest, which is commented out on main,
 * so the Sun log pager state stays covered.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SunLogViewModelTest {
    private val mainDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `sun log loads this week and the three before it and opens on this week`() {
        val history = FakeExposureHistoryRepository()
        val vm = buildViewModel(history)
        settle()
        val today = Instant.ofEpochMilli(NOW_MILLIS).atZone(ZoneId.systemDefault()).toLocalDate()
        val thisMonday = today.minusDays((today.dayOfWeek.value - 1).toLong())

        // One daily query covers all four weeks, so every pager page is ready up front.
        assertEquals(listOf(thisMonday.minusWeeks(3) to thisMonday.plusWeeks(1)), history.dailyRequests)
        val weeks = vm.state.value.sunLogWeeks
        assertEquals(
            listOf(thisMonday.minusWeeks(3), thisMonday.minusWeeks(2), thisMonday.minusWeeks(1), thisMonday),
            weeks.map { it.weekStart },
        )
        assertTrue(weeks.all { it.days.size == 7 })
        assertEquals(SUN_LOG_WEEK_COUNT - 1, vm.state.value.sunLogPage)
    }

    @Test
    fun `sun log remembers the settled week across tab switches`() {
        val vm = buildViewModel(FakeExposureHistoryRepository())
        settle()

        vm.onSunLogPageSettled(1)
        vm.onTabSelected(Tab.HOME)
        vm.onTabSelected(Tab.SUN_LOG)
        assertEquals(1, vm.state.value.sunLogPage)
    }

    @Test
    fun `sun log numbers default to percent and switch to time`() {
        val vm = buildViewModel(FakeExposureHistoryRepository())
        settle()
        assertFalse(vm.state.value.sunLogShowsTime)

        vm.onSunLogShowTime(true)
        vm.onTabSelected(Tab.HOME)
        assertTrue(vm.state.value.sunLogShowsTime)
    }

    private fun buildViewModel(history: FakeExposureHistoryRepository) =
        MainViewModel(
            settingsViewModel = SettingsViewModel(FakeUserPreferencesRepository()),
            nowMillis = { NOW_MILLIS + mainDispatcher.scheduler.currentTime },
            historyRepository = history,
        )

    private fun settle() {
        mainDispatcher.scheduler.advanceTimeBy(701)
        mainDispatcher.scheduler.runCurrent()
    }

    private class FakeExposureHistoryRepository : ExposureHistoryRepository {
        val dailyRequests = mutableListOf<Pair<LocalDate, LocalDate>>()

        override suspend fun save(record: ExposureRecord): Result<Unit> = Result.success(Unit)

        override suspend fun getSession(sessionId: String): ExposureRecord? = null

        override fun observeHistory(
            limit: Int,
            offset: Int,
        ): Flow<List<ExposureRecord>> = flowOf(emptyList())

        // Like Room's observeDaily: every day in the range, zero-filled when there is no data.
        override fun observeDaily(
            start: LocalDate,
            endExclusive: LocalDate,
        ): Flow<List<ExposureDailySummary>> {
            dailyRequests += start to endExclusive
            val days = mutableListOf<ExposureDailySummary>()
            var date = start
            while (date.isBefore(endExclusive)) {
                days += ExposureDailySummary(date)
                date = date.plusDays(1)
            }
            return flowOf(days)
        }

        override fun observeWeek(containingDate: LocalDate): Flow<ExposureWeeklySummary> =
            flowOf(ExposureWeeklySummary(containingDate, emptyList()))

        override suspend fun deleteSession(sessionId: String): Result<Unit> = Result.success(Unit)

        override suspend fun clearHistory(): Result<Unit> = Result.success(Unit)
    }

    private companion object {
        const val NOW_MILLIS = 1_800_000L
    }
}
