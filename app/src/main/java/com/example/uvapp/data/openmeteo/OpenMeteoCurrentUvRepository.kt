package com.example.uvapp.data.openmeteo

import com.example.uvapp.domain.model.UvReading
import com.example.uvapp.domain.repository.CurrentUvRepository
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

class OpenMeteoCurrentUvRepository internal constructor(
    private val api: OpenMeteoApi,
) : CurrentUvRepository {
    override suspend fun getCurrentUv(
        latitude: Double,
        longitude: Double,
    ): UvReading =
        api.getCurrentUv(
            latitude = latitude,
            longitude = longitude,
            current = CURRENT_UV_QUERY,
            timezone = MELBOURNE_TIMEZONE,
        ).toDomain()

    companion object {
        private const val BASE_URL = "https://api.open-meteo.com/"
        private const val CURRENT_UV_QUERY = "uv_index"
        private const val MELBOURNE_TIMEZONE = "Australia/Melbourne"

        fun create(): CurrentUvRepository {
            val json = Json { ignoreUnknownKeys = true }
            val api =
                Retrofit.Builder()
                    .baseUrl(BASE_URL)
                    .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                    .build()
                    .create(OpenMeteoApi::class.java)

            return OpenMeteoCurrentUvRepository(api)
        }
    }
}
