package com.example.uvapp.domain.repository

import com.example.uvapp.domain.model.UvReading

interface CurrentUvRepository {
    suspend fun getCurrentUv(
        latitude: Double,
        longitude: Double,
    ): UvReading
}
