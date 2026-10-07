package com.example.uvapp.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
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
    version = 3,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun uvReadingDao(): UvReadingDao

    abstract fun placeNameDao(): PlaceNameDao

    abstract fun exposureHistoryDao(): ExposureHistoryDao

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
                        )
                        // Version 3 supports fresh installations only. Future schema changes
                        // must explicitly migrate user history; never recreate this database.
                        .build()
                        .also { database -> instance = database }
            }
    }
}
