package com.example.scangrad.data

import com.example.scangrad.network.QuestionEvaluation
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

data class Submission(
    val id: String = "",
    val userId: String = "",
    val studentName: String = "",
    val courseCode: String = "",
    val department: String = "",
    val title: String = "",
    val date: String = "",
    val imageUri: String = "",
    val status: String = SubmissionStatus.PENDING.name,
    val score: Double = -1.0,
    val feedback: String = "",
    val confidenceLevel: String = "",
    val evaluations: List<QuestionEvaluation> = emptyList(),
    @ServerTimestamp val createdAt: Date? = null
)

enum class SubmissionStatus { GRADED, PENDING }
