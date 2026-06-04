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
    @SerializedName("student_answer") val studentAnswer: String = "",
    @SerializedName("score") val score: Double = 0.0,
    @SerializedName("max_score") val maxScore: Double = 0.0,
    @SerializedName("explanation") val explanation: String = ""
)

data class IngestRequest(
    @SerializedName("course_code") val courseCode: String,
    @SerializedName("year") val year: String,
    @SerializedName("semester") val semester: String,
    @SerializedName("moed") val moed: String,
    @SerializedName("version") val version: String = "v1",
    @SerializedName("questions_text") val questionsText: String? = null,
    @SerializedName("questions_url") val questionsUrl: String? = null,
    @SerializedName("answer_key_text") val answerKeyText: String? = null,
    @SerializedName("answer_key_url") val answerKeyUrl: String? = null
)

data class IngestResponse(
    @SerializedName("doc_id") val docId: String = "",
    @SerializedName("chunks_added") val chunksAdded: Int = 0,
    @SerializedName("chunk_ids") val chunkIds: List<String> = emptyList(),
    @SerializedName("chunk_types") val chunkTypes: Map<String, Int> = emptyMap(),
    @SerializedName("mode") val mode: String = ""
)
