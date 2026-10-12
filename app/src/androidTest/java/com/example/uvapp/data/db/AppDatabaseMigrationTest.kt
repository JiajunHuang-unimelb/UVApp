package com.example.uvapp.data.db

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.uvapp.data.history.RoomExposureHistoryRepository
import com.example.uvapp.domain.model.ExposureDayTotal
import com.example.uvapp.domain.model.ExposureRecord
import com.example.uvapp.domain.model.ExposureRecordStatus
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @get:Rule
    val helper = MigrationTestHelper(instrumentation, AppDatabase::class.java)

    @Test
    fun migrate1To4() = verifyMigration(1)

    @Test
    fun migrate2To4() = verifyMigration(2)

    @Test
    fun migrate3To4() = verifyMigration(3)

    private fun verifyMigration(version: Int) = runBlocking {
        val context = instrumentation.targetContext
        val name = "migration-" + UUID.randomUUID() + ".db"
        val migrations = AppDatabase.MIGRATIONS.toTypedArray()
        try {
            helper.createDatabase(name, version).use { db ->
                db.execSQL(
                    "INSERT INTO uv_readings VALUES ('cache', 1000, -37.8, 144.9, 7.0, 7.5, 20, 2000)",
                )
                if (version >= 2) {
                    db.execSQL(
                        "INSERT INTO place_names VALUES " +
                            "('cache', -37.8, 144.9, 'Melbourne', NULL, NULL, NULL, NULL, 'Melbourne, Australia', 2000)",
                    )
                }
                if (version >= 3) {
                    db.execSQL(
                        "INSERT INTO exposure_sessions VALUES ('session', 1000, 3000, 'UTC', 'COMPLETED')",
                    )
                    db.execSQL("INSERT INTO exposure_days VALUES ('session', 20000, 2000, 0.2)")
                }
            }
            // Room checks columns, primary keys, foreign keys and indices against the exported v4 schema.
            helper.runMigrationsAndValidate(name, 4, true, *migrations).close()
            val db = Room.databaseBuilder(context, AppDatabase::class.java, name)
                .addMigrations(*migrations)
                .build()
            try {
                assertEquals(
                    listOf(UvReadingEntity("cache", 1000, -37.8, 144.9, 7.0, 7.5, 20, 2000)),
                    db.uvReadingDao().getForecast("cache"),
                )
                assertEquals(
                    if (version >= 2) {
                        PlaceNameEntity(
                            "cache", -37.8, 144.9, "Melbourne", null, null, null, null,
                            "Melbourne, Australia", 2000,
                        )
                    } else {
                        null
                    },
                    db.placeNameDao().getPlaceName("cache"),
                )
                val history = RoomExposureHistoryRepository(db.exposureHistoryDao())
                val expected = ExposureRecord(
                    sessionId = "session",
                    startedAtMillis = 1000,
                    recordedThroughMillis = 3000,
                    zoneId = "UTC",
                    status = ExposureRecordStatus.COMPLETED,
                    days = listOf(ExposureDayTotal(LocalDate.ofEpochDay(20000), 2000, 0, 0, 0, 0.2)),
                )
                if (version >= 3) {
                    assertEquals(expected, history.getSession("session"))
                } else {
                    assertNull(history.getSession("session"))
                    history.save(expected).getOrThrow()
                    assertEquals(expected, history.getSession("session"))
                }
                history.deleteSession("session").getOrThrow()
                assertNull(history.getSession("session"))
                db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM exposure_days").use { cursor ->
                    cursor.moveToFirst()
                    assertEquals(0, cursor.getInt(0))
                }
            } finally {
                db.close()
            }
        } finally {
            context.deleteDatabase(name)
        }
    }
}
