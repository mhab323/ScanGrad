package com.example.scangrad.network

class EvaluationRepository {

    suspend fun sendForGrading(request: EvaluationRequest): Result<EvaluationResponse> {
        return try {
            val response = FastApiService.ApiClient.fastApiService.evaluateSubmission(request)

            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Server Error: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

class IngestRepository {

    suspend fun ingest(request: IngestRequest): Result<IngestResponse> {
        return try {
            val response = FastApiService.ApiClient.fastApiService.ingestExam(request)

            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Server Error: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}