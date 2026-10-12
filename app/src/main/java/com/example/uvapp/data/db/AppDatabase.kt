package com.example.uvapp.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.uvapp.data.history.ExposureDayEntity
import com.example.uvapp.data.history.ExposureHistoryDao
import com.example.uvapp.data.history.ExposureSessionEntity

/** Shared on-device database for network caches and user exposure history. */
@Database(
    entities = [
        UvReadingEntity::class,
        PlaceNameEntity::class,
        ExposureSessionEntity::class,
        ExposureDayEntity::class,
    ],
    version = 4,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun uvReadingDao(): UvReadingDao

    abstract fun placeNameDao(): PlaceNameDao

    abstract fun exposureHistoryDao(): ExposureHistoryDao

    companion object {
        private const val DATABASE_NAME = "uvapp.db"

        private val MIGRATION_1_2 =
            object : Migration(1, 2) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS place_names (
                            locationKey TEXT NOT NULL,
                            latitude REAL NOT NULL,
                            longitude REAL NOT NULL,
                            label TEXT NOT NULL,
                            locality TEXT,
                            city TEXT,
                            state TEXT,
                            country TEXT,
                            displayName TEXT NOT NULL,
                            fetchedAtMillis INTEGER NOT NULL,
                            PRIMARY KEY(locationKey)
                        )
                        """.trimIndent(),
                    )
                }
            }

        private val MIGRATION_2_3 =
            object : Migration(2, 3) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS exposure_sessions (
                            sessionId TEXT NOT NULL,
                            startedAtMillis INTEGER NOT NULL,
                            recordedThroughMillis INTEGER NOT NULL,
                            zoneId TEXT NOT NULL,
                            status TEXT NOT NULL,
                            PRIMARY KEY(sessionId)
                        )
                        """.trimIndent(),
                    )
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS exposure_days (
                            sessionId TEXT NOT NULL,
                            epochDay INTEGER NOT NULL,
                            activeDurationMillis INTEGER NOT NULL,
                            doseSed REAL NOT NULL,
                            PRIMARY KEY(sessionId, epochDay),
                            FOREIGN KEY(sessionId) REFERENCES exposure_sessions(sessionId)
                                ON UPDATE NO ACTION ON DELETE CASCADE
                        )
                        """.trimIndent(),
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS index_exposure_sessions_startedAtMillis " +
                            "ON exposure_sessions(startedAtMillis)",
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS index_exposure_days_epochDay ON exposure_days(epochDay)",
                    )
                }
            }

        private val MIGRATION_3_4 =
            object : Migration(3, 4) {
                override fun migrate(
                    database: SupportSQLiteDatabase,
                ) {
                    database.execSQL(
                        """
                        ALTER TABLE exposure_days
                        ADD COLUMN directSunDurationMillis INTEGER NOT NULL DEFAULT 0
                        """.trimIndent(),
                    )

                    database.execSQL(
                        """
                        ALTER TABLE exposure_days
                        ADD COLUMN shadeDurationMillis INTEGER NOT NULL DEFAULT 0
                        """.trimIndent(),
                    )

                    database.execSQL(
                        """
                        ALTER TABLE exposure_days
                        ADD COLUMN unknownDurationMillis INTEGER NOT NULL DEFAULT 0
                        """.trimIndent(),
                    )
                }
            }

        internal val MIGRATIONS = listOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)

        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?:
                    Room
                        .databaseBuilder(
                            context.applicationContext,
                            AppDatabase::class.java,
                            DATABASE_NAME,
                        )
                        .addMigrations(*MIGRATIONS.toTypedArray())
                        .build()
                        .also { database -> instance = database }
            }
    }
}
