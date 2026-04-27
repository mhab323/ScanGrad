package com.example.scangrad.network

import com.google.gson.annotations.SerializedName

data class EvaluationRequest(
    @SerializedName("submission_id") val submissionId: String,
    @SerializedName("course_code") val courseCode: String,
    @SerializedName("extracted_text") val extractedText: String,
    @SerializedName("image_url") val imageUrl: String
)

data class EvaluationResponse(
    @SerializedName("final_score") val finalScore: Int,
    @SerializedName("feedback") val feedback: String,
    @SerializedName("confidence_level") val confidenceLevel: String
)