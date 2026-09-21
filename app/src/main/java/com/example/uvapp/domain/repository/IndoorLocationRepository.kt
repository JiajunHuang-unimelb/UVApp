package com.example.uvapp.domain.repository

import com.example.uvapp.domain.model.IndoorLocationsData
import kotlinx.coroutines.flow.Flow

interface IndoorLocationRepository {
    val data: Flow<IndoorLocationsData>
    suspend fun update(transform: (IndoorLocationsData) -> IndoorLocationsData)
}
