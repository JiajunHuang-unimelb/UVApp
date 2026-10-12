# Exposure History: Persistence API and Integration Guide

This module persists exposure records locally and provides history lists as well as daily and weekly summaries. The calculation module provides exposure dose and duration by context state. `MainViewModel` organizes cumulative records by date and calls the persistence API, while the frontend displays the results.

Persistence is already integrated into `MainViewModel`: it saves complete cumulative records when a session is created, when its state changes, and approximately once per minute while it is running. The SunLog screen reads historical summaries. Merely creating a Repository does not automatically generate records.

## Responsibility Boundaries

| Storage module | Calculation module and `MainViewModel` |
| --- | --- |
| Basic field validation; transactional save, update, and delete operations | UV-to-dose calculations and environmental adjustments |
| Daily/weekly aggregation using the dates supplied by the caller | Excluding paused time and generating daily active durations and doses |
| Queries, pagination, and zero-filling missing dates | Splitting intervals at midnight based on the session time zone, including daylight-saving changes |
| Preserving session identity and preventing older records from overwriting newer ones | Checking whether dates belong to the session, whether active duration exceeds actual elapsed time, and whether dose and duration are consistent |
| Database migrations and write-failure handling | Producing complete cumulative records, deciding when to save, and coordinating sequential retries |

The storage layer persists the caller-provided daily results directly. It does not recalculate day boundaries or determine whether exposure results are plausible. Date allocation and consistency between durations and dose are the caller's responsibilities; these rules must not be described as validations already provided by the storage layer.

## Integration Flow

1. Generate a `sessionId` once when a new session begins, and retain the start time and time zone.
2. Save periodically while the session is running, and optionally on pause and completion.
3. Each save must provide the session's **complete cumulative daily records up to that point**, not only the increments since the previous save.
4. Pausing and resuming keep the same ID. On reset, choosing to save persists the old session as `COMPLETED`; choosing not to save deletes that session's previously written history. The next session uses a new ID.
5. Writes must be serialized. The current `MainViewModel` uses `historySaveMutex` to serialize saves and deletes. If a save fails, subsequent checkpoints still carry complete cumulative records, but there is currently no explicit automatic retry loop.

The data layer replaces the entire record identified by its ID. Repeated saves for the same session do not increase the record count or double-count statistics. Saves with the same recorded-through time may change the status, correct dose or duration values, and remove erroneous daily rows. Completed records can also be corrected.

Retries after a failure should be handled within the same serialized save flow: either retry the current record first or replace it with a newer complete record. Once the newer record has been saved successfully, do not retry the older one. Writes with an earlier recorded-through time are rejected; writes with the same recorded-through time overwrite each other in database execution order, so sequential calls are still required.

## Required Fields

| Field | Meaning |
| --- | --- |
| `sessionId` | Unique session ID; unchanged within a session and across retries, with a new ID for each new session |
| `startedAtMillis` | UTC timestamp in milliseconds; fixed when the session begins |
| `recordedThroughMillis` | UTC timestamp in milliseconds through which this cumulative record is complete; must not precede the previously saved value |
| `zoneId` | Time zone at session start, such as `Australia/Melbourne`; fixed for that session |
| `status` | `ACTIVE`, `PAUSED`, or `COMPLETED` |
| `days` | Complete cumulative data for each date in the session; dates with no activity may be omitted |
| `days[].date` | Local date in the session's time zone; must not be duplicated |
| `days[].activeDurationMillis` | Cumulative non-indoor duration for that date, as provided by the caller: the sum of the following three context-state durations; in milliseconds, excluding paused time, and not dose-weighted duration |
| `days[].directSunDurationMillis` | Cumulative duration in direct sunlight for that date, in milliseconds |
| `days[].shadeDurationMillis` | Cumulative duration in shade for that date, in milliseconds |
| `days[].unknownDurationMillis` | Cumulative duration in an unknown environmental context for that date, in milliseconds |
| `days[].doseSed` | Cumulative dose for that date, in SED; finite and non-negative |

All six parameters of `ExposureDayTotal` must be supplied; the three additional duration fields do not have constructor defaults. Durations come from the calculator's elapsed clock. An accelerated clock in development mode must not be treated directly as real wall-clock duration. Dose is accumulated separately by the calculation module; the storage layer does not recalculate dose from these durations.

The persistence API does not require a snapshot version number; Room manages the database version. Every save replaces the entire record, including removing previously saved dates omitted from the new record. Normal updates must therefore include all previously valid daily totals.

`MainViewModel` splits intervals that cross midnight according to the session time zone. For example, an interval from 23:50 to 00:10 on the next day requires totals for two dates. The current implementation apportions increments in dose and context-state durations in proportion to wall-clock time between checkpoints, rounding durations to whole milliseconds. This is an interval-allocation approximation; it does not recalculate the separate UV levels or environments on either side of midnight.

## Usage Example

```kotlin
val history = ExposureHistoryRepositoryFactory.create(applicationContext)
val zone = ZoneId.of("Australia/Melbourne")
val start = LocalDate.of(2026, 9, 22).atTime(23, 50).atZone(zone).toInstant().toEpochMilli()
val through = LocalDate.of(2026, 9, 23).atTime(0, 10).atZone(zone).toInstant().toEpochMilli()
val record = ExposureRecord(
    sessionId = savedSessionId, // Generate once when the session begins
    startedAtMillis = start,
    recordedThroughMillis = through,
    zoneId = zone.id,
    status = ExposureRecordStatus.PAUSED,
    days = listOf(
        ExposureDayTotal(
            date = LocalDate.of(2026, 9, 22),
            activeDurationMillis = 600_000L,
            directSunDurationMillis = 600_000L,
            shadeDurationMillis = 0L,
            unknownDurationMillis = 0L,
            doseSed = 0.30,
        ),
        ExposureDayTotal(
            date = LocalDate.of(2026, 9, 23),
            activeDurationMillis = 300_000L,
            directSunDurationMillis = 0L,
            shadeDurationMillis = 300_000L,
            unknownDurationMillis = 0L,
            doseSed = 0.05,
        ),
    ),
)
// Call this within the same serialized save flow; inspect the result before processing the next record.
val result = history.save(record)
if (result.isFailure) {
    // Show or log the error; decide here whether to retry or replace it with a newer complete record.
}

val records = history.observeHistory(limit = 50, offset = 0)
val today = LocalDate.now(zone)
val daily = history.observeDaily(today, today.plusDays(1))
val week = history.observeWeek(today)
val checkpoint = history.getSession(savedSessionId)
```

The models are in `domain.model`, and the factory is in `data.history`. Dates use `java.time`. Call suspending methods from coroutines; the UI collects the `Flow` returned by queries. After the user confirms deletion, call `deleteSession` or `clearHistory`, and first stop any related pending saves so that deleted records are not written again.

## Storage-Layer Validation and Query Rules

- The session ID must be valid; the start time and time zone remain fixed, and the recorded-through time must not move backwards.
- Timestamps must fall within basic valid ranges, and the time-zone ID must be valid. Dates must not be duplicated; all four daily duration fields must be non-negative, and doses must be finite and non-negative. On save, the total active duration is checked for overflow and the total dose for finiteness.
- The storage layer does **not** check that active duration equals the sum of the three context-state durations. It also does not validate daily date allocation, duration upper limits, or relationships between zero duration and dose. These checks are the caller's responsibility.
- The session and its daily rows are replaced in a single transaction. Any failure during the operation rolls back the entire write.
- Write failures return `Result.failure`; coroutine cancellation continues to propagate by throwing. Read failures are not disguised as zero exposure.
- History is ordered by session start time in descending order, and queries support pagination. Daily queries cover `[start, endExclusive)`, with a maximum span of 366 days; missing dates are zero-filled.
- Weeks run from Monday through Sunday. Aggregations include active, paused, and completed records. The daily session count includes only sessions with valid exposure on that date; daily counts cannot simply be added to obtain the number of distinct sessions in a week.
- Daily and weekly queries separately aggregate all four duration fields and dose. All fields are zero-filled for dates without records. Session counts continue to use the condition `activeDurationMillis > 0`.
- Historical records use the time zone captured at session start. A later change to the phone's time zone does not redistribute old records across dates.

`getSession` returns the last saved record. It does not fill in exposure that was never observed, nor does it contain the calculator's complete state for restoration. Any exposure accumulated but not saved before the process exits may be lost.

## Storage and Verification

The database has been consolidated into `uvapp.db`, version 4, containing `uv_readings`, `place_names`, `exposure_sessions`, and `exposure_days`. Cache updates and exposure-history clearing affect only their respective tables; they do not delete and recreate the entire database. Future schema changes must include explicit migrations.

`MIGRATION_3_4` is currently registered. It adds three non-null integer columns to `exposure_days`: `directSunDurationMillis`, `shadeDurationMillis`, and `unknownDurationMillis`, each with a SQL default of 0. The migration SQL does not rewrite existing active durations or doses. In old records, active duration previously accumulated only direct-sunlight time; in new records, it accumulates all three non-indoor context states. The migration does not backfill the state-specific breakdown of old records. Consequently, older records may have a nonzero active duration while all three new fields remain zero; the new-record equality must not be used to infer values for historical data.

The code does not provide migrations from database versions 1 or 2 to version 4, nor does it copy records from the former `exposure_history.db`. Room schema compatibility and preservation of old data for the 3→4 migration still require migration testing. Clearing app data is not a substitute for migration verification. Clearing data or uninstalling the app deletes exposure records, caches, settings, and saved indoor places. DataStore continues to hold settings and saved indoor places. Historical schema files are structural references only and cannot replace the results of migration tests.

The exposure-history tables do not store location coordinates or account information, do not expire records automatically, and do not offer cloud synchronization. Records remain until the user deletes them, clears app data, or uninstalls the app. Current backup rules exclude the database.

- `ExposureHistoryDatabaseTest` has been updated for version 4 assertions and the six-parameter daily model. On **2026-10-08**, its targeted run on the **Medium_Phone Android emulator passed all eight tests**. Coverage includes creation of all four tables; isolation between caches and history; persistence and reopening of category-specific durations; repeated saves and corrections; basic input and timestamp protections; same-day multi-session and same-week multi-date aggregation; zero-filling missing dates; cascading deletes; clearing history; rejection of negative values in the three context-specific durations; and transactional rollback. These results validate the storage layer, **not** the 3→4 upgrade migration.

The entire `MainViewModelTest` class is currently commented out and cannot be treated as proof that the save entry point has passed testing. Although the main flow and history UI are integrated, `MainViewModel` save/discard behavior and the history UI still need regression verification. Room schema compatibility and preservation of existing data during the 3→4 upgrade still require a separate migration test. The eight passing storage tests do not replace these checks.
