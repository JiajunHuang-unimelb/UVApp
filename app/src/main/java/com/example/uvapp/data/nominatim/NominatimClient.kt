package com.example.uvapp.data.nominatim

import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/** Creates the policy-compliant production Nominatim service. */
object NominatimClient {
    const val DEFAULT_BASE_URL = "https://nominatim.openstreetmap.org/"
    const val ATTRIBUTION = "© OpenStreetMap contributors"

    private const val USER_AGENT =
        "UVApp/0.1.0 (https://github.com/JiajunHuang-unimelb/UVApp)"

    private val json =
        Json {
            ignoreUnknownKeys = true
        }

    fun create(
        baseUrl: String = DEFAULT_BASE_URL,
        okHttpClient: OkHttpClient = defaultOkHttpClient(),
    ): NominatimApi =
        Retrofit
            .Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(
                json.asConverterFactory("application/json; charset=utf-8".toMediaType()),
            ).build()
            .create(NominatimApi::class.java)

    private fun defaultOkHttpClient(): OkHttpClient =
        OkHttpClient
            .Builder()
            .addNetworkInterceptor { chain ->
                val request =
                    chain
                        .request()
                        .newBuilder()
                        .header("User-Agent", USER_AGENT)
                        .header("Accept", "application/json")
                        .build()
                chain.proceed(request)
            }.connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .build()
}
