package com.example.scangrad.data

data class HistoryRecord(
    val id: String,
    val courseCode: String,
    val title: String,
    val dateAndType: String,
    val score: Int,
    val maxScore: Int = 100,
    val statusBadge: HistoryStatus
)

enum class HistoryStatus(val label: String) {
    HIGH_CONFIDENCE("High Confidence"),
    VALIDATED("Validated")
}