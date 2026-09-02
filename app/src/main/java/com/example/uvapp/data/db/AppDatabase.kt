package com.example.uvapp.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/** On-device Room database for network data that must remain available offline. */
@Database(
    entities = [UvReadingEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun uvReadingDao(): UvReadingDao

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
                        ).build()
                        .also { database -> instance = database }
            }
    }
}
