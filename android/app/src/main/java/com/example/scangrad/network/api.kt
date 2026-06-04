package com.example.scangrad.network // Make sure this matches your actual package

import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

interface FastApiService {

    @POST("api/evaluate")
    suspend fun evaluateSubmission(
        @Body request: EvaluationRequest
    ): Response<EvaluationResponse>

    @POST("api/ingest")
    suspend fun ingestExam(
        @Body request: IngestRequest
    ): Response<IngestResponse>

    object ApiClient {
        private var retrofit: Retrofit? = null
        var isReady: Boolean = false

        private val httpClient: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .writeTimeout(0, TimeUnit.MILLISECONDS)
            .callTimeout(0, TimeUnit.MILLISECONDS)
            .build()

        fun initialize(dynamicBaseUrl: String) {
            retrofit = Retrofit.Builder()
                .baseUrl(dynamicBaseUrl)
                .client(httpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
            isReady = true
        }

        val fastApiService: FastApiService
            get() = retrofit?.create(FastApiService::class.java)
                ?: throw IllegalStateException("Fatal Error: You must call ApiClient.initialize(url) before making requests.")
    }
}