package com.example.scangrad.network

import com.google.gson.annotations.SerializedName

data class EvaluationRequest(
    @SerializedName("submission_id") val submissionId: String,
    @SerializedName("course_code") val courseCode: String,
    @SerializedName("exam_question") val examQuestion: String,
    @SerializedName("extracted_text") val extractedText: String,
    @SerializedName("image_url") val imageUrl: String? = null
)

data class EvaluationResponse(
    @SerializedName("overall_score") val overallScore: Double = 0.0,
    @SerializedName("evaluations") val evaluations: List<QuestionEvaluation> = emptyList(),
    @SerializedName("general_feedback") val generalFeedback: String = "",
    @SerializedName("confidence_level") val confidenceLevel: String = ""
)

data class QuestionEvaluation(
    @SerializedName("question_id") val questionId: String = "",
    @SerializedName("question_text") val questionText: String = "",
    @SerializedName("score") val score: Double = 0.0,
    @SerializedName("max_score") val maxScore: Double = 0.0,
    @SerializedName("explanation") val explanation: String = ""
)
