# Exposure tracking: data contract and handoff

## Responsibility and current scope

This module stores personal exposure history locally and exposes history, daily totals, and weekly totals.
It does not calculate UV dose, infer sensor context, run a background timer, or create history UI.

- **Yijia / data layer:** Room storage, input validation, atomic checkpoint replacement, history queries,
  daily/weekly summaries, deletion, and persistence tests.
- **Zeming / exposure module:** calculate dose and active time, split measurements at local midnight,
  maintain session identity/revision, trigger saves, and restore/resume the calculation state if required.
- **Frontend:** display history and charts, collect repository flows, surface write/read failures,
  and confirm deletion before invoking the delete API.

Nothing is recorded automatically until the exposure module calls `save`. The factory is ready for
injection; this change deliberately does not change the existing calculator or MainViewModel.

## 给 Zeming：你需要传什么？

每次保存传一个 `ExposureRecord`，它是**当前会话截至这一刻的完整累计快照**，不是本次新增值。

| 字段 | 约定 |
| --- | --- |
| `sessionId: String` | Start 时创建 UUID；同一会话一直使用；Reset/新会话换新 ID。最多 128 字符 |
| `revision: Long` | 从 0 开始；每个新快照递增。失败重试必须原样重发，包括同一个版本号 |
| `startedAtMillis: Long` | 会话开始的 UTC epoch 毫秒，固定不变 |
| `recordedThroughMillis: Long` | 本快照累计到的 UTC epoch 毫秒；必须 >= 开始时间，后续不可倒退 |
| `zoneId: String` | Start 时捕获的时区，例如 `Australia/Melbourne`；该会话内固定 |
| `status` | `ACTIVE`、`PAUSED`、`COMPLETED`；完成/主动结束映射为 COMPLETED |
| `days` | 从开始到本快照，按本会话时区拆分的每日累计数据；后续保存需包含之前所有日期 |
| `days[].date: LocalDate` | 本地日历日期，不能重复 |
| `days[].activeDurationMillis: Long` | 当日有效曝光时长（毫秒），排除暂停时间 |
| `days[].doseSed: Double` | 当日累计曝光剂量，单位 SED；有限且 >= 0，不是 UV index 或百分比 |

例如 23:50 开始、00:10 暂停，需要两个日期的累计值。数据层不把总剂量按时长比例拆分，
因为午夜前后的 UV、遮阴和暂停状态可能不同。计算端应在当地午夜、状态/剂量率变化时结算区间。
`days` 列表可省略没有任何活动的日期，查询会补零；已提交的日期以后不能移除。

## Example: save and query

```kotlin
val history = ExposureHistoryRepositoryFactory.create(applicationContext)

// Two real local-day contributions, calculated by the exposure module.
val zone = ZoneId.of("Australia/Melbourne")
val start = LocalDate.of(2026, 9, 22).atTime(23, 50).atZone(zone).toInstant().toEpochMilli()
val through = LocalDate.of(2026, 9, 23).atTime(0, 10).atZone(zone).toInstant().toEpochMilli()
val snapshot = ExposureRecord(
    sessionId = savedSessionId, // UUID created once at Start, not once per save
    revision = 2L,
    startedAtMillis = start,
    recordedThroughMillis = through,
    zoneId = zone.id,
    status = ExposureRecordStatus.PAUSED,
    days = listOf(
        ExposureDayTotal(LocalDate.of(2026, 9, 22), 600_000L, 0.30),
        ExposureDayTotal(LocalDate.of(2026, 9, 23), 300_000L, 0.05),
    ),
)
history.save(snapshot).onFailure { error ->
    // Show/record the error and keep this exact snapshot for retry.
}

val latestRecords: Flow<List<ExposureRecord>> = history.observeHistory(limit = 50, offset = 0)
val today = LocalDate.now(zone)
val todayTotals = history.observeDaily(today, today.plusDays(1))
val currentWeek = history.observeWeek(today) // Monday through Sunday, seven daily buckets
val savedCheckpoint = history.getSession(savedSessionId)

// Only after a user's explicit delete action / confirmation:
history.deleteSession(savedSessionId)
// history.clearHistory() removes all local exposure records.
```

Imports: models in `com.example.uvapp.domain.model`, factory in `com.example.uvapp.data.history`,
repository interface in `com.example.uvapp.domain.repository`; dates use `java.time` and streams use
`kotlinx.coroutines.flow.Flow`. Call suspend methods from a coroutine, not directly from composition.

## Checkpoint lifecycle and retries

1. On Start: create a stable ID, capture start time/timezone, optionally save an empty revision-0 ACTIVE record.
2. During running: save a cumulative checkpoint periodically (for example every 30 seconds) and on pause.
3. On Resume: continue the same ID and totals, increment revision, set ACTIVE. Paused time adds no dose/time.
4. On finish/reset: save the old session as COMPLETED **before** resetting the calculator; a fresh session gets a new ID.
5. Keep pending writes ordered in the calculation layer. Do not let a failed older save be silently treated as saved.

- Same ID + same revision + identical payload is an accepted no-op, even after completion.
- Same revision with different content, or a lower revision, returns failure.
- Newer snapshots cannot decrease any day's dose/time, omit earlier day rows, or move the checkpoint backwards.
- Start/timezone cannot change. Completed records cannot be edited (exact retry only); explicit deletion is supported.
- Whole-session replacement is transactional, including the revision check. Concurrent callers cannot overwrite
  a newer saved revision with an older one. Different IDs are separate sessions: the producer must not regenerate IDs on retries.
- Validation and storage failures are returned as `Result.failure`; coroutine cancellation is rethrown.
- Query/Flow database failures propagate to the caller; a read failure is not silently displayed as zero exposure.

`getSession` returns the last persisted checkpoint, including the revision needed after process recreation.
Do not assume wall-clock time since that checkpoint was exposure: the data layer does not backfill unobserved time.
Full calculation recovery may require more state than this reporting record (for example the current dose limit);
the exposure module owns that state. An interrupted ACTIVE record remains an incomplete checkpoint until the
producer explicitly resolves it. Unsaved exposure since the last checkpoint can be lost on process termination.

## Daily/weekly semantics

- Duration is measured in milliseconds, dose in SED. Paused intervals do not contribute active duration.
- Daily queries use `[start, endExclusive)`, allow 1..366 days, and include zero-filled dates in ascending order.
- Weekly queries use ISO-style Monday-to-Sunday calendar weeks, including weeks crossing year boundaries.
- Aggregation includes the latest stored ACTIVE, PAUSED, and COMPLETED checkpoints. It is not limited to finished sessions.
- Daily `sessionCount` counts sessions with positive active time on that date. A cross-midnight session can count on
  two dates. There is intentionally no weekly session count obtained by summing those counts.
- Dates are the originally recorded local dates in each session's fixed timezone. Changing the phone timezone later
  does not move historical dose to other dates. Travel sessions may therefore have different stored timezones.
- Day-duration validation uses actual midnight boundaries, including 23/25-hour DST days, and clips to session start/end.
- Dose totals are measurements, not a medical safety budget. Do not sum remaining countdown times, multiply by SPF,
  or interpret a weekly total as a universal safe limit.

## Storage and retention

Database: `exposure_history.db`, schema version 1. Tables: `exposure_sessions` and `exposure_days`.
No location coordinates or account identifiers are stored; this version is for the device's local user, not multiple accounts.

The existing `uvapp.db` is a disposable network cache and has destructive fallback enabled. Personal history is
kept in a separate database with **no destructive migration fallback**, so clearing/rebuilding the UV cache does not
erase it. Existing installations create the new database on first use; the old cache schema is unchanged.
Future history schema versions must supply explicit migrations and tests.

There is no automatic history expiry or cloud synchronization. Records remain until explicit deletion, app data clearing,
or uninstall. The current Android backup/transfer rules exclude all databases, including this history database;
there is no automatic history recovery after uninstall or device transfer in this version.
Deleting a session cascades to its daily rows, so totals update with it. A producer must stop pending saves before a
user deletes an active session or clears history; a later valid save with that ID would otherwise create it again.

## Files and verification

- `domain/model/ExposureRecord.kt`: input and summary types, day/time/dose validation.
- `domain/repository/ExposureHistoryRepository.kt`: public API.
- `data/history/ExposureHistoryEntities.kt`: Room rows and mapping.
- `data/history/ExposureHistoryDao.kt`: atomic snapshots, revision rules, SQL daily aggregation.
- `data/history/ExposureHistoryDatabase.kt`: isolated durable database.
- `data/history/RoomExposureHistoryRepository.kt`: history, zero-filled day/week streams, write errors.
- `data/history/ExposureHistoryRepositoryFactory.kt`: production factory.

Main file paths above are relative to `app/src/main/java/com/example/uvapp/`.

- JVM tests: `app/src/test/java/com/example/uvapp/data/history/ExposureHistoryRepositoryTest.kt`.
  Cover checkpoint retries, stale/conflicting writes, completion, daily/week totals, cross-midnight allocations,
  DST, invalid inputs, deletion, pagination, calendar-year boundaries, failures and cancellation.
- Android tests: `app/src/androidTest/java/com/example/uvapp/data/history/ExposureHistoryDatabaseTest.kt`.
  Cover disk reopen, real SQL aggregation/cascade, concurrent revisions and transaction rollback.
  These require a connected emulator/device; compiling the test APK alone does not mean they ran.

The history UI, calculator checkpoint adapter, automatic save triggers, and process recovery are integration work
for the consuming modules. End-to-end tracking is complete only after those modules call this API and are tested together.

### Verified on 22 September 2026

- `testDebugUnitTest`: 86 tests, 84 passed, 2 opt-in live API tests skipped, no failures/errors.
  Includes 10 new history contract/repository tests.
- `assembleDebug` and `assembleDebugAndroidTest`: passed.
- `connectedDebugAndroidTest` filtered to `ExposureHistoryDatabaseTest`: all 3 passed on Medium_Phone, Android 15.
- New Kotlin files passed the project's ktlint 1.0.1 CLI rules. The plugin's default tasks only scanned build scripts
  on this setup, so the CLI was invoked explicitly for the new source files.
- These checks verify the data module; no history UI or calculator-to-history integration is claimed.
