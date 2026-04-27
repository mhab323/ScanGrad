package com.example.scangrad.data

import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

data class Submission(
    val id: String = "",
    val userId: String = "",
    val courseCode: String = "",
    val department: String = "",
    val title: String = "",
    val date: String = "",
    val imageUri: String = "",
    val status: String = SubmissionStatus.PENDING.name,
    val score: Int = -1,
    val feedback: String = "",
    val confidenceLevel: String = "",
    @ServerTimestamp val createdAt: Date? = null
)

enum class SubmissionStatus { GRADED, PENDING }
