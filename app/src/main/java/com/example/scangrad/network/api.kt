package com.example.scangrad.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

interface FastApiService {

    @POST("api/evaluate")
    suspend fun evaluateSubmission(
        @Body request: EvaluationRequest
    ): Response<EvaluationResponse>

    object ApiClient {
        private const val BASE_URL = "http://10.0.2.2:8000/"

        val fastApiService: FastApiService by lazy {
            Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(FastApiService::class.java)
        }
    }

}