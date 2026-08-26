package com.example.uvapp.data.openmeteo

import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/** Creates the production Open-Meteo Retrofit service. */
object OpenMeteoClient {
    private const val BASE_URL = "https://api.open-meteo.com/"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = false
    }

    fun create(
        okHttpClient: OkHttpClient = defaultOkHttpClient(),
    ): OpenMeteoApi =
        Retrofit
            .Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(
                json.asConverterFactory("application/json; charset=utf-8".toMediaType()),
            ).build()
            .create(OpenMeteoApi::class.java)

    private fun defaultOkHttpClient(): OkHttpClient =
        OkHttpClient
            .Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .build()
}
