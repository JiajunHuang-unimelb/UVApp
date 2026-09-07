package com.example.uvapp.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** On-device Room database for network data that must remain available offline. */
@Database(
    entities = [UvReadingEntity::class, PlaceNameEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun uvReadingDao(): UvReadingDao

    abstract fun placeNameDao(): PlaceNameDao

    companion object {
        private const val DATABASE_NAME = "uvapp.db"

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
                        ).addMigrations(MIGRATION_1_2)
                        .build()
                        .also { database -> instance = database }
            }

        private val MIGRATION_1_2 =
            object : Migration(1, 2) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `place_names` (
                            `locationKey` TEXT NOT NULL,
                            `latitude` REAL NOT NULL,
                            `longitude` REAL NOT NULL,
                            `label` TEXT NOT NULL,
                            `locality` TEXT,
                            `city` TEXT,
                            `state` TEXT,
                            `country` TEXT,
                            `displayName` TEXT NOT NULL,
                            `fetchedAtMillis` INTEGER NOT NULL,
                            PRIMARY KEY(`locationKey`)
                        )
                        """.trimIndent(),
                    )
                }
            }
    }
}
