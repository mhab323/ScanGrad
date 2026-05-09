package com.example.scangrad.network

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

    object ApiClient {
        private const val BASE_URL = "http://10.0.2.2:8000/"

        // No timeout — grading can take as long as it needs.
        // OkHttp treats 0 as "infinite" for read/write/call.
        private val httpClient: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .writeTimeout(0, TimeUnit.MILLISECONDS)
            .callTimeout(0, TimeUnit.MILLISECONDS)
            .build()

        val fastApiService: FastApiService by lazy {
            Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(httpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(FastApiService::class.java)
        }
    }

}
