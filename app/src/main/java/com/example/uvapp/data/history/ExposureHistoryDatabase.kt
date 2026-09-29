package com.example.uvapp.data.history

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.DeleteColumn
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.AutoMigrationSpec

/** User history is separate from the disposable UV cache. Future schema changes MUST migrate it. */
@Database(
    entities = [ExposureSessionEntity::class, ExposureDayEntity::class],
    version = 2,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2, spec = ExposureHistoryDatabase.RemoveRevision::class)],
)
abstract class ExposureHistoryDatabase : RoomDatabase() {
    abstract fun exposureHistoryDao(): ExposureHistoryDao

    @DeleteColumn(tableName = "exposure_sessions", columnName = "revision")
    class RemoveRevision : AutoMigrationSpec

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
