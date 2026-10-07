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
                        .addMigrations(MIGRATION_3_4)
                        .build()
                        .also { database -> instance = database }
            }
    }
}
