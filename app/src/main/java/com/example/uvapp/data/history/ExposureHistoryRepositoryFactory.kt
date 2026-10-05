package com.example.uvapp.data.history

import android.content.Context
import com.example.uvapp.data.db.AppDatabase
import com.example.uvapp.domain.repository.ExposureHistoryRepository

object ExposureHistoryRepositoryFactory {
    fun create(context: Context): ExposureHistoryRepository =
        RoomExposureHistoryRepository(AppDatabase.getInstance(context).exposureHistoryDao())
}
