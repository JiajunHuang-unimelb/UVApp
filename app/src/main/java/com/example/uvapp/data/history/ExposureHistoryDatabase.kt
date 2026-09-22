package com.example.uvapp.data.history

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/** User history is separate from the disposable UV cache. Future schema changes MUST migrate it. */
@Database(entities = [ExposureSessionEntity::class, ExposureDayEntity::class], version = 1, exportSchema = true)
abstract class ExposureHistoryDatabase : RoomDatabase() {
    abstract fun exposureHistoryDao(): ExposureHistoryDao

    companion object {
        @Volatile private var instance: ExposureHistoryDatabase? = null

        fun getInstance(context: Context): ExposureHistoryDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    ExposureHistoryDatabase::class.java,
                    "exposure_history.db",
                ).build().also { instance = it }
            }
    }
}
