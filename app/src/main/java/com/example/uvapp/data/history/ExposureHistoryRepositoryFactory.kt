package com.example.uvapp.data.history

import android.content.Context
import com.example.uvapp.domain.repository.ExposureHistoryRepository

object ExposureHistoryRepositoryFactory {
    fun create(context: Context): ExposureHistoryRepository =
        RoomExposureHistoryRepository(ExposureHistoryDatabase.getInstance(context).exposureHistoryDao())
}
